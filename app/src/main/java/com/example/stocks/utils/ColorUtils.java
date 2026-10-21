package com.example.stocks.utils;

import android.content.Context;

import androidx.core.content.ContextCompat;

import com.example.stocks.R;
import com.example.stocks.model.MarketItem;

/**
 * 涨跌配色工具：红跌绿涨（国际市场习惯），平盘/未知为灰色。
 */
public final class ColorUtils {

    private ColorUtils() {
    }

    /** 根据行情条目返回涨跌文字颜色 */
    public static int getChangeColor(Context context, MarketItem item) {
        if (item == null) {
            return ContextCompat.getColor(context, R.color.flat_gray);
        }
        switch (item.getDirection()) {
            case 1: // 上涨 → 绿色
                return ContextCompat.getColor(context, R.color.rise_green);
            case -1: // 下跌 → 红色
                return ContextCompat.getColor(context, R.color.fall_red);
            case 0: // 平盘 → 灰色
            default:
                return ContextCompat.getColor(context, R.color.flat_gray);
        }
    }
}
