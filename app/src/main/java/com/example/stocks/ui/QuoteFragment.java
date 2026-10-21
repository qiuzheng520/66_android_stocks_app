package com.example.stocks.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.stocks.R;
import com.example.stocks.adapter.QuoteAdapter;
import com.example.stocks.model.MarketCategory;
import com.example.stocks.model.MarketItem;
import com.example.stocks.net.QuoteRepository;
import com.example.stocks.utils.AppLog;
import com.example.stocks.utils.SearchQuery;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 单个行情分类页面：SwipeRefresh 下拉刷新 + RecyclerView 列表。
 * 搜索关键字由 SearchQuery 单例统一下发，避免 Activity 直接持有 Fragment 实例。
 */
public class QuoteFragment extends Fragment implements SearchQuery.Listener {

    private static final String TAG = "QuoteFragment";
    private static final String ARG_CATEGORY = "arg_category";

    private MarketCategory category;
    private QuoteAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;

    /** 全量数据（网络返回/缓存） */
    private final List<MarketItem> allItems = new ArrayList<>();
    /** 当前本地搜索关键字（由 SearchQuery 下发） */
    private String keyword = "";
    /** 是否已经成功加载过一次 */
    private boolean loadedOnce = false;
    /** 防止重复点击下拉刷新时并发请求 */
    private boolean loading = false;

    public static QuoteFragment newInstance(MarketCategory category) {
        QuoteFragment fragment = new QuoteFragment();
        // 立即赋值：Fragment 的 onCreate 尚未执行时，外部也可能需要知道分类，
        // 否则取 category 会得到 null（曾导致首页启动空指针闪退）
        fragment.category = category != null ? category : MarketCategory.COMMODITIES;
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, fragment.category.name());
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        String name = args != null ? args.getString(ARG_CATEGORY) : null;
        try {
            category = MarketCategory.valueOf(name);
        } catch (Exception e) {
            if (category == null) {
                category = MarketCategory.COMMODITIES;
            }
        }
        AppLog.i(TAG, "onCreate 分类=" + category);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_quote, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        swipeRefresh = view.findViewById(R.id.swipeRefresh);
        tvEmpty = view.findViewById(R.id.tvEmpty);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);

        // 下拉刷新配色
        swipeRefresh.setColorSchemeResources(
                R.color.swipe_colors_1, R.color.swipe_colors_2, R.color.swipe_colors_3);
        swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // 下拉刷新：强制走网络，绕过 60s 缓存
                load(true);
            }
        });

        adapter = new QuoteAdapter();
        adapter.setOnItemClickListener(new QuoteAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(MarketItem item) {
                openDetail(item);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // 首次加载（允许命中缓存）
        swipeRefresh.setRefreshing(true);
        load(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        // 订阅搜索关键字
        SearchQuery.register(this);
        keyword = SearchQuery.getKeyword();
        applyFilter();
    }

    @Override
    public void onStop() {
        super.onStop();
        SearchQuery.unregister(this);
    }

    /** 当前分类（Tab 标题等使用，保证不会返回 null） */
    public MarketCategory getCategory() {
        if (category == null) {
            Bundle args = getArguments();
            String name = args != null ? args.getString(ARG_CATEGORY) : null;
            try {
                category = MarketCategory.valueOf(name);
            } catch (Exception e) {
                category = MarketCategory.COMMODITIES;
            }
        }
        return category;
    }

    /** 加载数据 */
    private void load(final boolean bypassCache) {
        if (loading || category == null) {
            if (loading) {
                swipeRefresh.setRefreshing(false);
            }
            return;
        }
        loading = true;
        swipeRefresh.setRefreshing(true);
        if (!loadedOnce && allItems.isEmpty()) {
            tvEmpty.setText(R.string.state_loading);
            tvEmpty.setVisibility(View.VISIBLE);
        }

        AppLog.i(TAG, "开始加载 " + category + (bypassCache ? "（强制刷新）" : ""));

        QuoteRepository.get().load(category, bypassCache, new QuoteRepository.DataCallback() {
            @Override
            public void onSuccess(List<MarketItem> items, boolean fromCache) {
                if (!isAdded()) {
                    return;
                }
                loading = false;
                swipeRefresh.setRefreshing(false);
                allItems.clear();
                allItems.addAll(items);
                loadedOnce = true;
                AppLog.i(TAG, "加载成功 " + category + " 条数=" + items.size()
                        + (fromCache ? "（缓存）" : "（网络）"));
                applyFilter();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) {
                    return;
                }
                loading = false;
                swipeRefresh.setRefreshing(false);
                AppLog.e(TAG, "加载失败 " + category + ": " + message);
                // 网络异常提示
                Toast.makeText(requireContext(),
                        getString(R.string.refresh_failed) + "：" + message,
                        Toast.LENGTH_SHORT).show();
                if (allItems.isEmpty()) {
                    tvEmpty.setText(getString(R.string.state_error_prefix) + message);
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    applyFilter(); // 保留旧数据，仅提示失败
                }
            }
        });
    }

    /** 搜索关键字变化回调（由 SearchQuery 触发） */
    @Override
    public void onKeywordChanged(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
        if (isAdded()) {
            applyFilter();
        }
    }

    /** 按关键字过滤并刷新列表 */
    private void applyFilter() {
        if (adapter == null || tvEmpty == null) {
            return;
        }
        List<MarketItem> shown = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) {
            shown.addAll(allItems);
        } else {
            for (MarketItem item : allItems) {
                String name = item.getName() == null ? "" : item.getName().toLowerCase(Locale.US);
                if (name.contains(keyword)) {
                    shown.add(item);
                }
            }
        }
        adapter.setItems(shown);

        if (shown.isEmpty()) {
            if (allItems.isEmpty()) {
                tvEmpty.setText(loadedOnce ? R.string.state_empty : R.string.state_loading);
            } else {
                tvEmpty.setText(R.string.state_no_match);
            }
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    /** 点击 item：用 WebView 打开品种详情页 */
    private void openDetail(MarketItem item) {
        String url = item.getDetailUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(requireContext(), R.string.detail_no_url, Toast.LENGTH_SHORT).show();
            return;
        }
        AppLog.i(TAG, "打开详情 " + item.getName() + " -> " + url);
        DetailActivity.start(requireContext(), url, item.getName());
    }

    /** 供外部触发刷新（目前未使用，保留扩展） */
    public void refresh() {
        if (isAdded()) {
            load(true);
        }
    }
}
