package com.example.stocks.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.stocks.R;
import com.example.stocks.model.MarketCategory;
import com.example.stocks.model.MarketItem;
import com.example.stocks.utils.ColorUtils;
import com.example.stocks.utils.FlagUtils;
import com.example.stocks.utils.NumberUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 行情列表适配器：名称 / 现价 / 涨跌 / 涨跌幅，红跌绿涨。
 * 支持设置完整数据与关键字过滤结果。
 */
public class QuoteAdapter extends RecyclerView.Adapter<QuoteAdapter.QuoteViewHolder> {

    /** item 点击回调 */
    public interface OnItemClickListener {
        void onItemClick(MarketItem item);
    }

    private final List<MarketItem> items = new ArrayList<>();
    private OnItemClickListener listener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    /** 整体替换数据 */
    public void setItems(List<MarketItem> data) {
        items.clear();
        if (data != null) {
            items.addAll(data);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_quote, parent, false);
        return new QuoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull QuoteViewHolder holder, int position) {
        final MarketItem item = items.get(position);
        holder.bind(item);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class QuoteViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvName;
        private final TextView tvFlag;
        private final TextView tvPrice;
        private final TextView tvChange;
        private final TextView tvPercent;

        QuoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvFlag = itemView.findViewById(R.id.tvFlag);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvChange = itemView.findViewById(R.id.tvChange);
            tvPercent = itemView.findViewById(R.id.tvPercent);
        }

        void bind(MarketItem item) {
            tvName.setText(item.getName());
            tvPrice.setText(NumberUtils.formatPrice(item.getPrice()));
            tvChange.setText(NumberUtils.formatChange(item.getChange()));
            tvPercent.setText(NumberUtils.formatPercent(item.getChangePercent()));

            // 国旗：股市、币种按国家/地区显示（债券页也有国旗数据，需要时在此放开）
            String flag = "";
            MarketCategory category = item.getCategory();
            if (category == MarketCategory.STOCKS || category == MarketCategory.CURRENCIES) {
                flag = FlagUtils.toEmoji(item.getFlagCode());
            }
            tvFlag.setText(flag);
            tvFlag.setVisibility(flag.isEmpty() ? View.GONE : View.VISIBLE);

            // 红跌绿涨：涨跌额与涨跌幅同色
            int color = ColorUtils.getChangeColor(itemView.getContext(), item);
            tvChange.setTextColor(color);
            tvPercent.setTextColor(color);
        }
    }
}
