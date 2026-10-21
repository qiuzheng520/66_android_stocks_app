package com.example.stocks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.stocks.R;
import com.example.stocks.adapter.CategoryPagerAdapter;
import com.example.stocks.model.MarketCategory;
import com.example.stocks.utils.AppLog;
import com.example.stocks.utils.SearchQuery;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

/**
 * 首页：Toolbar + TabLayout + ViewPager2。
 * Toolbar 上的 SearchView 做本地搜索，关键字通过 SearchQuery 下发给各 Tab 的列表。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CategoryPagerAdapter pagerAdapter;
    /** 崩溃提示只弹一次 */
    private boolean crashDialogShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLog.i(TAG, "onCreate 开始");
        setContentView(R.layout.activity_main);

        setSupportActionBar(findViewById(R.id.toolbar));

        ViewPager2 viewPager = findViewById(R.id.viewPager);
        TabLayout tabLayout = findViewById(R.id.tabLayout);

        // 离屏页面数量限制（默认 1 页，够用且省内存）
        viewPager.setOffscreenPageLimit(1);

        pagerAdapter = new CategoryPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Tab 标题：商品 / 股市 / 币种 / 加密货币 / 债券
        // 注意：这里只依赖分类枚举，不去取 Fragment 实例（Fragment 此时还没创建，取其分类会空指针）
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            MarketCategory category = pagerAdapter.getCategory(position);
            tab.setText(getString(category != null
                    ? category.getTitleRes() : R.string.tab_commodities));
        }).attach();

        AppLog.i(TAG, "onCreate 完成，分类数：" + pagerAdapter.getItemCount());
    }

    @Override
    protected void onResume() {
        super.onResume();
        showCrashDialogIfNeeded();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setQueryHint(getString(R.string.search_hint));
            searchView.setIconifiedByDefault(false);
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    return false;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    SearchQuery.setKeyword(newText);
                    return true;
                }
            });
        }

        // 收起搜索框时清空关键字
        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                SearchQuery.setKeyword("");
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_log) {
            // 日志入口：没有 adb 时在这里查看 / 导出运行日志
            startActivity(new Intent(this, LogActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * 上次运行发生过崩溃时（CrashHandler 写入了摘要），弹窗提示用户查看日志。
     */
    private void showCrashDialogIfNeeded() {
        if (crashDialogShown || !AppLog.hasCrashSummary()) {
            return;
        }
        crashDialogShown = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_detected_title)
                .setMessage(R.string.crash_detected_msg)
                .setPositiveButton(R.string.crash_view_log, (dialog, which) -> {
                    AppLog.markCrashReviewed();
                    startActivity(new Intent(this, LogActivity.class));
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    AppLog.markCrashReviewed();
                })
                .setCancelable(false)
                .show();
    }
}
