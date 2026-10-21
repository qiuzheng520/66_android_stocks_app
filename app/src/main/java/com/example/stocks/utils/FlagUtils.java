package com.example.stocks.utils;

import java.util.Locale;

/**
 * 国旗工具：把网站给出的国家/地区代码（如 us、jp、ea）转成国旗 emoji。
 *
 * 实现方式是把两位代码转成 Unicode 区域指示符号（Regional Indicator），
 * 例如 "us" → U+1F1FA U+1F1F8 → 🇺🇸。
 * 系统字体（NotoColorEmoji）支持时会显示国旗，不支持时退化为两个字母（如 US）。
 */
public final class FlagUtils {

    private FlagUtils() {
    }

    /**
     * 国家代码转国旗 emoji。
     *
     * @param code 两位国家/地区代码，例如 us、jp、gb
     * @return 国旗 emoji；代码非法时返回空字符串
     */
    public static String toEmoji(String code) {
        if (code == null) {
            return "";
        }
        String c = code.trim().toLowerCase(Locale.US);
        // 网站用 ea 表示欧元区，对应欧盟旗 EU
        if ("ea".equals(c)) {
            c = "eu";
        }
        // 兼容少数页面用 uk 表示英国
        if ("uk".equals(c)) {
            c = "gb";
        }
        if (c.length() != 2 || !c.matches("[a-z]{2}")) {
            return "";
        }
        int first = Character.toUpperCase(c.charAt(0)) - 'A' + 0x1F1E6;
        int second = Character.toUpperCase(c.charAt(1)) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }
}
