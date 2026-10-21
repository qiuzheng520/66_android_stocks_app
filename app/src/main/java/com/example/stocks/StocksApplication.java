package com.example.stocks;

import android.app.Application;
import android.os.Build;

import com.example.stocks.utils.AppLog;
import com.example.stocks.utils.CrashHandler;

/**
 * 应用入口：初始化文件日志与全局崩溃处理器。
 * 没有 adb 时，崩溃堆栈会写到 AppLog.getLogPath() 指向的文本文件里。
 */
public class StocksApplication extends Application {

    private static final String TAG = "App";

    @Override
    public void onCreate() {
        super.onCreate();

        // 1) 初始化日志文件
        AppLog.init(this);

        // 2) 安装全局崩溃处理器
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(this));

        // 3) 记录启动信息，方便定位问题
        AppLog.i(TAG, "==================== 应用启动 ====================");
        AppLog.i(TAG, "版本: " + readVersionName());
        AppLog.i(TAG, "系统: Android " + Build.VERSION.RELEASE
                + " (API " + Build.VERSION.SDK_INT + ")");
        AppLog.i(TAG, "机型: " + Build.MANUFACTURER + " " + Build.MODEL);
        AppLog.i(TAG, "日志文件: " + AppLog.getLogPath());
    }

    /** 读取应用版本号（取不到就返回未知） */
    private String readVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "未知";
        }
    }
}
