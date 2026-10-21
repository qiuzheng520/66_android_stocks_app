package com.example.stocks.net;

import android.os.SystemClock;

import java.util.ArrayDeque;

/**
 * 请求限流器：
 * 1. 最小请求间隔（毫秒级节流）；
 * 2. 每分钟最大请求数（滑动窗口配额）。
 *
 * 触发限流时不直接失败，而是返回需要等待的毫秒数，由上层排队后再发请求，
 * 这样多个 Tab 同时加载时只会依次慢一点，不会出现“请求过于频繁”的错误。
 */
public class RateLimiter {

    /** 两次请求之间的最小间隔 */
    private static final long MIN_INTERVAL_MS = 1500L;
    /** 一分钟内允许的最大请求数 */
    private static final int MAX_PER_MINUTE = 20;
    /** 统计窗口长度 */
    private static final long WINDOW_MS = 60_000L;

    private final ArrayDeque<Long> window = new ArrayDeque<>();
    /** 上一次请求（预约）的时间点 */
    private long lastAcquireAt = 0L;

    /**
     * 申请一次请求许可。
     *
     * @return 需要等待的毫秒数；0 表示可以立即发起请求
     */
    public synchronized long acquireDelayMs() {
        long now = SystemClock.elapsedRealtime();

        // 清理过期记录
        while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
            window.removeFirst();
        }

        long wait;
        if (window.size() >= MAX_PER_MINUTE) {
            // 超出每分钟配额：等窗口内最早的一条过期
            long oldest = window.peekFirst();
            wait = Math.max(MIN_INTERVAL_MS, oldest + WINDOW_MS - now);
        } else {
            // 节流：距上一次（预约的）请求时间不足就排队
            wait = Math.max(0L, lastAcquireAt + MIN_INTERVAL_MS - now);
        }

        long fireAt = now + wait;
        lastAcquireAt = fireAt;
        window.addLast(fireAt);
        return wait;
    }

    /** 仅用于测试/调试：清空限流状态 */
    public synchronized void reset() {
        window.clear();
        lastAcquireAt = 0L;
    }
}
