package com.example.stocks.model;

import com.example.stocks.R;

/**
 * 行情分类（对应网站不同栏目的列表页）。
 * 注意：网站改版可能导致路径失效，仅个人学习使用。
 */
public enum MarketCategory {

    COMMODITIES("commodities", R.string.tab_commodities),
    STOCKS("stocks", R.string.tab_stocks),
    CURRENCIES("currencies", R.string.tab_currencies),
    CRYPTO("crypto", R.string.tab_crypto),
    BONDS("bonds", R.string.tab_bonds);

    /** 网站基础地址（简体中文站） */
    public static final String BASE_URL = "https://zh.tradingeconomics.com/";

    private final String path;
    private final int titleRes;

    MarketCategory(String path, int titleRes) {
        this.path = path;
        this.titleRes = titleRes;
    }

    /** 列表页完整 URL，例如 https://zh.tradingeconomics.com/commodities */
    public String getListUrl() {
        return BASE_URL + path;
    }

    /** Tab 标题资源 id */
    public int getTitleRes() {
        return titleRes;
    }
}
