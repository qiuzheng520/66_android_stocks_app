package com.example.stocks.utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.example.stocks.ui.CrashActivity;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全局崩溃处理器。
 *
 * 崩溃时做三件事：
 * 1. 把完整堆栈同步写入日志文件（AppLog），保证进程被杀后内容仍在；
 * 2. 把崩溃摘要写入 last-crash.txt，下次启动时由首页提示；
 * 3. 尽力用 AlarmManager 拉起崩溃页面（进程即将被系统杀掉，直接 startActivity 会被一起杀掉）。
 *
 * 最后把异常交回系统默认处理，进程按原样结束。
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {

    private static final String TAG = "CrashHandler";

    private final Context appContext;
    private final Thread.UncaughtExceptionHandler defaultHandler;

    public CrashHandler(Context context) {
        this.appContext = context.getApplicationContext();
        this.defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            String report = buildReport(t, e);
            AppLog.writeRaw(report);
            AppLog.writeCrashSummary(buildSummary(e));
            Log.e(TAG, "捕获到未处理异常:\n" + report);
        } catch (Throwable ignored) {
            // 写日志失败也要继续走默认流程
        }

        try {
            scheduleCrashActivity();
        } catch (Throwable ignored) {
            // 拉不起崩溃页面也没关系，下次启动会提示
        }

        // 交回系统默认处理（结束进程）
        if (defaultHandler != null) {
            defaultHandler.uncaughtException(t, e);
        } else {
            Process.killProcess(Process.myPid());
            System.exit(10);
        }
    }

    /** 生成完整崩溃报告（写入日志文件） */
    private String buildReport(Thread thread, Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();

        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        StringBuilder sb = new StringBuilder();
        sb.append("\n========== 崩溃开始 ==========\n");
        sb.append("时间: ").append(fmt.format(new Date())).append('\n');
        sb.append("线程: ").append(thread != null ? thread.getName() : "unknown").append('\n');
        sb.append("进程: ").append(Process.myPid()).append('\n');
        sb.append("系统: ").append(android.os.Build.VERSION.RELEASE)
                .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        sb.append("机型: ").append(android.os.Build.MANUFACTURER)
                .append(' ').append(android.os.Build.MODEL).append('\n');
        sb.append("异常: ").append(e.getClass().getName()).append('\n');
        sb.append("信息: ").append(e.getMessage()).append('\n');
        sb.append("堆栈:\n").append(sw.toString());
        sb.append("\n========== 崩溃结束 ==========\n");
        return sb.toString();
    }

    /** 生成崩溃摘要（下次启动提示用，只要一行） */
    private String buildSummary(Throwable e) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return fmt.format(new Date()) + " "
                + e.getClass().getName() + ": " + e.getMessage()
                + "\n日志文件: " + AppLog.getLogPath();
    }

    /**
     * 延迟拉起崩溃页面。
     * 进程马上就会被系统杀掉，所以用 AlarmManager 定时触发，等新进程起来后再显示。
     */
    private void scheduleCrashActivity() {
        if (appContext == null) {
            return;
        }
        Intent intent = new Intent(appContext, CrashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        intent.putExtra(CrashActivity.EXTRA_CRASH_FILE, AppLog.getCrashFile().getAbsolutePath());

        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext, 9527, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager alarmManager =
                (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 1200L, pendingIntent);
        }
    }
}
