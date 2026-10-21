package com.example.stocks.net;

import android.os.Handler;
import android.os.Looper;

import com.example.stocks.model.MarketCategory;
import com.example.stocks.model.MarketItem;
import com.example.stocks.utils.AppLog;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 行情数据仓库：缓存 + 限流 + 异步请求 + 子线程解析。
 *
 * 调用流程：
 * 1. 非强制刷新时先查 60s 内存缓存，命中直接回主线程；
 * 2. 未命中则过限流器，被拦截时回“请求过于频繁”；
 * 3. OkHttp 异步请求（回调在子线程）；
 * 4. 在单线程执行器中用 Jsoup 解析 HTML；
 * 5. 写入缓存，切回主线程通知 UI。
 */
public class QuoteRepository {

    private static final String TAG = "QuoteRepository";

    /** 数据回调（始终回主线程） */
    public interface DataCallback {
        /** @param fromCache 本次数据是否来自内存缓存 */
        void onSuccess(List<MarketItem> items, boolean fromCache);

        /** 网络异常 / 限流 / 解析失败 */
        void onError(String message);
    }

    private static volatile QuoteRepository instance;

    private final MemoryCache<List<MarketItem>> cache =
            new MemoryCache<>(MemoryCache.DEFAULT_TTL_MS, 8); // 60s 缓存，最多 8 个分类
    private final RateLimiter rateLimiter = new RateLimiter();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 解析放到独立子线程执行，避免阻塞 OkHttp 回调线程与主线程 */
    private final ExecutorService parseExecutor = Executors.newSingleThreadExecutor();

    private QuoteRepository() {
    }

    public static QuoteRepository get() {
        if (instance == null) {
            synchronized (QuoteRepository.class) {
                if (instance == null) {
                    instance = new QuoteRepository();
                }
            }
        }
        return instance;
    }

    /**
     * 加载某个分类的行情。
     *
     * @param bypassCache true=下拉刷新强制走网络；false=优先使用 60s 缓存
     * @param callback    结果回调（主线程）
     */
    public void load(final MarketCategory category, boolean bypassCache, final DataCallback callback) {
        if (category == null || callback == null) {
            return;
        }

        // 1) 缓存命中
        if (!bypassCache) {
            List<MarketItem> cached = cache.get(category.name());
            if (cached != null && !cached.isEmpty()) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(cached, true);
                    }
                });
                return;
            }
        }

        // 2) 请求限流：不直接失败，而是排队等一会儿再发
        final long delay = rateLimiter.acquireDelayMs();
        if (delay > 0) {
            AppLog.i(TAG, "限流排队，" + delay + "ms 后请求 " + category);
            mainHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doRequest(category, callback);
                }
            }, delay);
            return;
        }

        doRequest(category, callback);
    }

    /** 真正发起请求（OkHttp 异步，回调在子线程） */
    private void doRequest(final MarketCategory category, final DataCallback callback) {
        final Call call = HttpClient.get().getClient().newCall(HttpClient.buildGet(category.getListUrl()));
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call c, IOException e) {
                if (c.isCanceled()) {
                    return;
                }
                AppLog.e(TAG, "请求失败 " + category + ": " + errMsg(e));
                postError(callback, errMsg(e));
            }

            @Override
            public void onResponse(Call c, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        AppLog.e(TAG, "HTTP 异常 " + category + " code=" + response.code()
                                + " url=" + category.getListUrl());
                        postError(callback, describeHttpError(response.code()));
                        return;
                    }
                    ResponseBody body = response.body();
                    final String html = body != null ? body.string() : "";
                    final int statusCode = response.code();
                    AppLog.i(TAG, "HTTP " + statusCode + " " + category
                            + " 长度=" + html.length());

                    // 4) 子线程解析 HTML
                    parseExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                List<MarketItem> items = HtmlParser.parse(html, category);
                                if (items.isEmpty()) {
                                    // 页面结构变了（改版）或返回了验证页
                                    AppLog.e(TAG, "解析失败 " + category + "（HTTP " + statusCode + "）");
                                    postError(callback, "解析失败，页面结构可能已改版");
                                    return;
                                }
                                cache.put(category.name(), items); // 5) 写入 60s 缓存
                                mainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(items, false);
                                    }
                                });
                            } catch (Exception e) {
                                AppLog.e(TAG, "数据处理异常 " + category, e);
                                postError(callback, "数据处理异常：" + errMsg(e));
                            }
                        }
                    });
                } catch (IOException e) {
                    postError(callback, errMsg(e));
                } finally {
                    response.close();
                }
            }
        });
    }

    /** 把 HTTP 状态码翻译成对用户友好的提示 */
    private static String describeHttpError(int code) {
        if (code == 404) {
            return "页面路径已变更（HTTP 404），网站改版了";
        }
        if (code == 403) {
            return "访问被拒绝（HTTP 403），可能触发了反爬限制";
        }
        if (code == 429) {
            return "请求过于频繁（HTTP 429），请稍后再试";
        }
        return "服务器返回 HTTP " + code;
    }

    /** 清空缓存，用于“全部刷新”之类场景 */
    public void clearCache() {
        cache.clear();
    }

    private void postError(final DataCallback callback, final String message) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onError(message);
            }
        });
    }

    private static String errMsg(Exception e) {
        String msg = e.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return msg;
    }
}
