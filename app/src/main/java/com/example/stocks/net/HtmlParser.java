package com.example.stocks.net;

import com.example.stocks.model.MarketCategory;
import com.example.stocks.model.MarketItem;
import com.example.stocks.utils.NumberUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;

/**
 * Jsoup HTML 解析器：从 TradingEconomics 列表页提取行情数据。
 *
 * 解析策略（按网站当前结构，2026 版页面）：
 * 1. 数据行：每行带 data-symbol 属性，例如 data-symbol="CL1:COM"；
 * 2. 名称：td.datatable-item-first 里的 a 标签（b 标签内是名称，div 里是单位）；
 * 3. 现价：td#p；涨跌额：td#nch；涨跌幅：td#pch（债券页没有 %Chg 列，用现价反推）；
 * 4. 涨跌单元格的符号来自 data-value 属性或 market-negative-image 样式，
 *    因为页面上的负数只显示绝对值。
 *
 * 兼容旧结构：若找不到 data-symbol 行，则退回按单元格顺序解析。
 *
 * 重要声明：本项目仅个人学习。网站页面一旦改版，本解析器会失效；
 * 高频抓取还可能触发网站反爬封禁 IP。
 */
public final class HtmlParser {

    private HtmlParser() {
    }

    /**
     * 解析列表页 HTML。
     *
     * @param html     页面源码
     * @param category 所属分类（用于补全详情链接与回填数据模型）
     * @return 行情条目列表，解析不到数据时返回空列表
     */
    public static List<MarketItem> parse(String html, MarketCategory category) {
        List<MarketItem> result = new ArrayList<>();
        if (html == null || html.trim().isEmpty()) {
            return result;
        }

        // 传入 baseUrl，便于 Jsoup 生成绝对路径链接
        Document doc = Jsoup.parse(html, category.getListUrl());

        // 新版：数据行带 data-symbol
        Elements rows = doc.select("tr[data-symbol]");
        if (rows.isEmpty()) {
            // 旧版：id="table" 的主表格
            rows = doc.select("table#table tr:has(td)");
        }
        if (rows.isEmpty()) {
            // 再兜底：带 heatmap 样式的表格
            rows = doc.select("table.table-heatmap tr:has(td)");
        }
        if (rows.isEmpty()) {
            rows = doc.select("table tr:has(td)");
        }

        for (Element row : rows) {
            MarketItem item = parseRow(row, category);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    /** 解析单行数据，无法识别时返回 null */
    private static MarketItem parseRow(Element row, MarketCategory category) {
        // ---------- 1. 名称与链接 ----------
        Element nameCell = row.selectFirst("td.datatable-item-first");
        if (nameCell == null) {
            nameCell = firstCellWithLink(row);
        }
        if (nameCell == null) {
            return null;
        }
        Element link = nameCell.selectFirst("a");
        String name = extractName(nameCell, link);
        if (name.isEmpty()) {
            return null;
        }
        String detailUrl = buildUrl(link, category);

        // ---------- 2. 现价 / 涨跌 / 涨跌幅 ----------
        Double price = readNumber(row.selectFirst("td#p"));
        Double change = readSignedNumber(row.selectFirst("td#nch"));
        Double percent = readNumber(row.selectFirst("td#pch"));

        // 旧结构没有 id 标记时，按单元格顺序兜底解析
        if (price == null) {
            Double[] legacy = parseByPosition(row);
            price = legacy[0];
            if (change == null) {
                change = legacy[1];
            }
            if (percent == null) {
                percent = legacy[2];
            }
        }

        // 没有价格的行基本是分组标题或广告行
        if (price == null) {
            return null;
        }

        // 页面只给涨跌幅时，反推涨跌额
        if (change == null && percent != null) {
            change = NumberUtils.deriveChange(price, percent);
        }
        // 债券列表页没有 %Chg 列，用现价和涨跌额反推涨跌幅
        if (percent == null && change != null && price != 0d) {
            percent = change / price * 100d;
        }

        MarketItem item = new MarketItem();
        item.setName(name);
        item.setPrice(price);
        item.setChange(change);
        item.setChangePercent(percent);
        item.setDetailUrl(detailUrl);
        item.setCategory(category);
        item.setFlagCode(parseFlagCode(row));
        return item;
    }

    /**
     * 提取行首的国家/地区标记，例如 &lt;div class='flag flag-us'&gt; → "us"。
     * 股市、币种、债券列表页有，商品与加密货币没有。
     */
    private static String parseFlagCode(Element row) {
        // class 形如 "flag flag-us"，所以用包含匹配而不是前缀匹配
        Element flag = row.selectFirst("div[class*=flag-]");
        if (flag == null) {
            return null;
        }
        String cls = flag.attr("class");
        int idx = cls.indexOf("flag-");
        if (idx < 0) {
            return null;
        }
        String code = cls.substring(idx + "flag-".length()).trim().toLowerCase(java.util.Locale.US);
        // 只保留字母代码（去掉可能带上的其它 class）
        int end = 0;
        while (end < code.length() && Character.isLetter(code.charAt(end))) {
            end++;
        }
        code = code.substring(0, end);
        return code.isEmpty() ? null : code;
    }

    /** 名称：优先取链接里的文字（单位在 div 里，不会混进来） */
    private static String extractName(Element nameCell, Element link) {
        if (link != null) {
            Element bold = link.selectFirst("b");
            String text = bold != null ? bold.text() : link.text();
            text = text.trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return nameCell.text().trim();
    }

    /** 详情链接：优先用 Jsoup 解析出的绝对地址 */
    private static String buildUrl(Element link, MarketCategory category) {
        if (link == null) {
            return "";
        }
        // abs:href 依赖 Jsoup.parse 的 baseUrl；为空时兜底拼接
        String url = link.attr("abs:href");
        if (!url.isEmpty()) {
            return url;
        }
        String href = link.attr("href");
        if (href.startsWith("/")) {
            return MarketCategory.BASE_URL + href.substring(1);
        }
        if (href.startsWith("http")) {
            return href;
        }
        return "";
    }

    /** 第一个含链接的单元格（兼容旧结构） */
    private static Element firstCellWithLink(Element row) {
        for (Element td : row.select("> td")) {
            Element a = td.selectFirst("a");
            if (a != null && !a.text().trim().isEmpty()) {
                return td;
            }
        }
        return null;
    }

    /**
     * 读取单元格里的数值：优先用 data-value（带符号），否则解析文本。
     */
    private static Double readNumber(Element cell) {
        if (cell == null) {
            return null;
        }
        String dataValue = cell.attr("data-value");
        if (!dataValue.isEmpty()) {
            Double v = NumberUtils.parseNumber(dataValue);
            if (v != null) {
                return v;
            }
        }
        return NumberUtils.parseNumber(cell.text());
    }

    /**
     * 读取涨跌额：页面只显示绝对值，符号由 data-value 或
     * market-negative-image 样式给出，这里还原成带符号的数值。
     */
    private static Double readSignedNumber(Element cell) {
        if (cell == null) {
            return null;
        }
        String dataValue = cell.attr("data-value");
        if (!dataValue.isEmpty()) {
            Double v = NumberUtils.parseNumber(dataValue);
            if (v != null) {
                return v;
            }
        }
        Double v = NumberUtils.parseNumber(cell.text());
        if (v == null) {
            return null;
        }
        // 负数样式：页面只显示绝对值，需要补上负号
        if (v > 0 && cell.select(".market-negative-image").size() > 0) {
            return -v;
        }
        return v;
    }

    /**
     * 旧结构兜底：按单元格顺序取 价格 / 涨跌 / 涨跌幅。
     *
     * @return {price, change, percent}，取不到为 null
     */
    private static Double[] parseByPosition(Element row) {
        Double[] result = new Double[]{null, null, null};
        Elements tds = row.select("> td");
        if (tds.size() < 2) {
            return result;
        }
        int start = 0;
        for (int i = 0; i < tds.size(); i++) {
            if (tds.get(i).selectFirst("a") != null) {
                start = i + 1;
                break;
            }
        }
        Double price = null;
        Double change = null;
        Double percent = null;
        for (int i = start; i < tds.size(); i++) {
            String txt = tds.get(i).text().trim();
            if (txt.isEmpty() || NumberUtils.looksLikeDate(txt)) {
                continue;
            }
            boolean isPercent = txt.contains("%");
            Double value = NumberUtils.parseNumber(txt);
            if (value == null) {
                continue;
            }
            if (isPercent) {
                if (percent == null) {
                    percent = value;
                }
            } else if (price == null) {
                price = value;
            } else if (change == null) {
                change = value;
            }
        }
        result[0] = price;
        result[1] = change;
        result[2] = percent;
        return result;
    }
}
