package com.example.stocks.model;

/**
 * 行情条目数据模型。
 * 字段：名称、现价、涨跌、涨跌幅、详情链接、所属分类。
 */
public class MarketItem {

    /** 品种名称，例如“原油” */
    private String name;
    /** 现价（已解析为数字，可能为 null 表示页面未给出） */
    private Double price;
    /** 涨跌额（正数上涨 / 负数下跌） */
    private Double change;
    /** 涨跌幅百分比（正数上涨 / 负数下跌） */
    private Double changePercent;
    /** 详情页完整 URL，点击 item 时用 WebView 打开 */
    private String detailUrl;
    /** 所属分类 */
    private MarketCategory category;
    /** 国家/地区两位代码，例如 us、jp、ea（欧元区）；没有国旗时为 null */
    private String flagCode;

    public MarketItem() {
    }

    public MarketItem(String name, Double price, Double change,
                      Double changePercent, String detailUrl, MarketCategory category) {
        this.name = name;
        this.price = price;
        this.change = change;
        this.changePercent = changePercent;
        this.detailUrl = detailUrl;
        this.category = category;
    }

    /**
     * 涨跌方向：1 上涨、-1 下跌、0 平盘、2 数据未知。
     * 红跌绿涨的颜色由该方向决定。
     */
    public int getDirection() {
        Double v = change != null ? change : changePercent;
        if (v == null) {
            return 2;
        }
        if (v > 0) {
            return 1;
        }
        if (v < 0) {
            return -1;
        }
        return 0;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public Double getChange() {
        return change;
    }

    public void setChange(Double change) {
        this.change = change;
    }

    public Double getChangePercent() {
        return changePercent;
    }

    public void setChangePercent(Double changePercent) {
        this.changePercent = changePercent;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }

    public MarketCategory getCategory() {
        return category;
    }

    public void setCategory(MarketCategory category) {
        this.category = category;
    }

    public String getFlagCode() {
        return flagCode;
    }

    public void setFlagCode(String flagCode) {
        this.flagCode = flagCode;
    }
}
