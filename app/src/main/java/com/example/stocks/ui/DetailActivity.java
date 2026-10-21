package com.example.stocks.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.stocks.R;
import com.example.stocks.utils.AppLog;

/**
 * 品种详情页：内嵌 WebView 打开 zh.tradingeconomics.com 的详情页面。
 * 仅本地展示，不采集任何用户数据。
 */
public class DetailActivity extends AppCompatActivity {

    private static final String TAG = "DetailActivity";
    private static final String EXTRA_URL = "extra_url";
    private static final String EXTRA_TITLE = "extra_title";

    /** 桌面 UA：移动版页面有时会跳转到 App 下载引导，桌面 UA 更稳 */
    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private WebView webView;
    private ProgressBar progressBar;

    public static void start(Context context, String url, String title) {
        Intent intent = new Intent(context, DetailActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TITLE, title);
        context.startActivity(intent);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        progressBar = findViewById(R.id.progressBar);
        webView = findViewById(R.id.webView);

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null && !title.isEmpty()) {
            setTitle(title);
        }

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        if (url == null || url.isEmpty()) {
            Toast.makeText(this, R.string.detail_no_url, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initWebView();
        AppLog.i(TAG, "加载详情页：" + url);
        webView.loadUrl(url);
    }

    /** 配置 WebView */
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);            // 详情页依赖 JS 渲染部分内容
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString(DESKTOP_UA);
        settings.setTextZoom(100); // 忽略系统字体缩放，避免网页排版错乱

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // 站内跳转继续在当前 WebView 打开
                String host = request.getUrl().getHost();
                String siteHost = "zh.tradingeconomics.com";
                if (host != null && (host.endsWith(siteHost) || host.endsWith("tradingeconomics.com"))) {
                    return false;
                }
                // 外链交给系统浏览器
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                startActivity(intent);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    progressBar.setVisibility(View.GONE);
                    AppLog.e(TAG, "详情页加载失败：" + request.getUrl()
                            + " code=" + error.getErrorCode() + " " + error.getDescription());
                    // 网络异常提示
                    Toast.makeText(DetailActivity.this, R.string.detail_load_failed,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // 顶部加载进度
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
    }

    @Override
    protected void onPause() {
        if (webView != null) {
            webView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // 详情页内支持后退历史
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
