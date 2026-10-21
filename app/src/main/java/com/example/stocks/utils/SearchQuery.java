package com.example.stocks.utils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 全局搜索关键字。
 *
 * 原来的实现是 Activity 直接持有各 Tab 的 Fragment 实例再逐个推送关键字，
 * 但 Fragment 由 FragmentStateAdapter 管理，Activity 持有的实例不一定是界面上的那个
 * （屏幕旋转后尤其容易错位），搜索会失效。
 * 这里改成全局单例 + 监听者，Fragment 自己注册监听，行为与原来一致但更稳。
 */
public final class SearchQuery {

    /** 关键字变化回调 */
    public interface Listener {
        void onKeywordChanged(String keyword);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile String keyword = "";

    private SearchQuery() {
    }

    /** 当前关键字（小写、去空格） */
    public static String getKeyword() {
        return keyword;
    }

    /** 设置关键字并通知所有监听者 */
    public static void setKeyword(String text) {
        String value = text == null ? "" : text.trim().toLowerCase(java.util.Locale.US);
        if (value.equals(keyword)) {
            return;
        }
        keyword = value;
        for (Listener listener : LISTENERS) {
            try {
                listener.onKeywordChanged(value);
            } catch (Throwable ignored) {
                // 单个监听者异常不影响其它
            }
        }
    }

    /** 注册监听者 */
    public static void register(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    /** 注销监听者 */
    public static void unregister(Listener listener) {
        if (listener != null) {
            LISTENERS.remove(listener);
        }
    }
}
