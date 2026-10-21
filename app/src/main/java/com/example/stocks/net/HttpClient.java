package com.example.stocks.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;

/**
 * OkHttp 单例封装：统一超时、UA 与请求头，降低被风控拦截的概率。
 * OkHttp 的 enqueue 回调运行在子线程，满足“子线程请求”要求。
 */
public final class HttpClient {

    /** 自定义 User-Agent，模拟桌面浏览器 */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static volatile HttpClient instance;

    private final OkHttpClient client;

    private HttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)   // 建连超时
                .readTimeout(20, TimeUnit.SECONDS)      // 读取超时
                .writeTimeout(15, TimeUnit.SECONDS)     // 写入超时
                .retryOnConnectionFailure(true)         // 连接失败自动重试一次
                .build();
    }

    public static HttpClient get() {
        if (instance == null) {
            synchronized (HttpClient.class) {
                if (instance == null) {
                    instance = new HttpClient();
                }
            }
        }
        return instance;
    }

    public OkHttpClient getClient() {
        return client;
    }

    /** 构造带浏览器请求头的 GET 请求 */
    public static Request buildGet(String url) {
        return new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .get()
                .build();
    }
}
