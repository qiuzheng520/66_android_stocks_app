package com.example.stocks.utils;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数字工具类：从网页脏文本中提取数值，并按行情展示规范格式化。
 */
public final class NumberUtils {

    /** 匹配 1,234.56 / 12.3 / 0.0000123 等数字（含千分位） */
    private static final Pattern NUMBER_PATTERN =
            Pattern.compile("([-+]?\\d{1,3}(?:,\\d{3})+(?:\\.\\d+)?|[-+]?\\d+(?:\\.\\d+)?)");

    private NumberUtils() {
    }

    /**
     * 从网页单元格文本中解析数字。
     * 支持千分位逗号、百分号、货币符号、单位文字、括号负数、Unicode 负号。
     *
     * @return 解析失败返回 null
     */
    public static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace('\u00A0', ' ').replace('\u2212', '-').trim();
        if (text.isEmpty() || text.equals("--") || text.equalsIgnoreCase("N/A")) {
            return null;
        }

        // 括号表示负数，例如 (1.23)
        boolean parenNegative = text.startsWith("(") && text.endsWith(")");

        Matcher matcher = NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String num = matcher.group(1).replace(",", "");
        double value;
        try {
            value = Double.parseDouble(num);
        } catch (NumberFormatException e) {
            return null;
        }

        // 括号负数，或原字符串里数字前带负号
        if (parenNegative) {
            value = -Math.abs(value);
        } else if (matcher.group(1).startsWith("-")) {
            value = -Math.abs(value);
        }
        // 上限保护：明显异常的超大值视为脏数据
        if (Math.abs(value) > 1e15) {
            return null;
        }
        return value;
    }

    /**
     * 判断单元格是否是日期（日期列需要跳过，避免被当成价格）。
     * 注意：不能把 "4,321.00" 这类价格误判为日期，因此只识别
     * 斜杠/短横日期（10/20/2026、2026-10-20）与英文月份。
     */
    public static boolean looksLikeDate(String raw) {
        if (raw == null) {
            return false;
        }
        String text = raw.trim();
        // 10/20、10/20/2026、2026-10-20、20.10.2026 等
        if (text.matches("\\d{1,4}[/\\-]\\d{1,2}([/\\-]\\d{2,4})?")) {
            return true;
        }
        if (text.matches("\\d{1,2}\\.\\d{1,2}\\.\\d{2,4}")) {
            return true;
        }
        // 英文月份缩写：Oct 20 / October 2026
        String lower = text.toLowerCase(Locale.US);
        return lower.matches(".*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec).*");
    }

    /**
     * 格式化现价：千分位 + 自适应小数位。
     * 小币种/加密货币价格很小，需要更多小数位。
     */
    public static String formatPrice(Double v) {
        if (v == null) {
            return "--";
        }
        double abs = Math.abs(v);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat df;
        if (abs >= 1) {
            df = new DecimalFormat("#,##0.00", symbols);
        } else if (abs >= 0.01) {
            df = new DecimalFormat("#,##0.0000", symbols);
        } else {
            // 极小价格：至少 4 位小数，最多 8 位，自动去掉尾部多余的 0
            df = new DecimalFormat("0.0000####", symbols);
        }
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(v);
    }

    /** 格式化涨跌额：带 +/- 号 */
    public static String formatChange(Double v) {
        if (v == null) {
            return "--";
        }
        double abs = Math.abs(v);
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        DecimalFormat df;
        if (abs >= 1) {
            df = new DecimalFormat("#,##0.00", symbols);
        } else if (abs >= 0.01) {
            df = new DecimalFormat("#,##0.0000", symbols);
        } else {
            df = new DecimalFormat("0.0000####", symbols);
        }
        df.setRoundingMode(RoundingMode.HALF_UP);
        String s = df.format(v);
        // 正数补 + 号
        return v > 0 ? "+" + s : s;
    }

    /** 格式化涨跌幅：带 +/- 号和百分号 */
    public static String formatPercent(Double v) {
        if (v == null) {
            return "--";
        }
        return String.format(Locale.US, "%+.2f%%", v);
    }

    /**
     * 页面只给出涨跌幅时，用现价反推涨跌额。
     * price = old * (1 + p/100) => change = price - price/(1+p/100)
     */
    public static Double deriveChange(Double price, Double percent) {
        if (price == null || percent == null) {
            return null;
        }
        double ratio = 1 + percent / 100.0;
        if (Math.abs(ratio) < 1e-9) {
            return null;
        }
        return price - price / ratio;
    }
}
