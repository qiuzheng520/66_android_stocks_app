package com.example.stocks.utils;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 本地文件日志。
 *
 * 没有 adb 连接时，日志写入外部私有目录下的文本文件：
 * /sdcard/Android/data/com.example.stocks/files/logs/stocks-log.txt
 * 该文件可通过应用内「日志」页面用其它应用（文件管理器、文本编辑器、聊天工具）打开。
 *
 * 说明：
 * 1. 所有写文件操作都在 synchronized 块内完成，避免多线程写乱行；
 * 2. 崩溃时需要同步落盘，因此提供 writeRaw() 供 CrashHandler 调用；
 * 3. 文件超过上限后自动轮转，保留最近一份历史日志。
 */
public final class AppLog {

    /** logcat 中的统一 TAG */
    private static final String TAG = "StocksApp";

    /** 日志文件名 */
    public static final String LOG_FILE_NAME = "stocks-log.txt";
    /** 轮转后的历史日志文件名 */
    public static final String HISTORY_FILE_NAME = "stocks-log-prev.txt";
    /** 最近一次崩溃的摘要文件名（下次启动时读取并提示） */
    public static final String CRASH_FILE_NAME = "last-crash.txt";

    /** 日志子目录名（位于外部私有目录下） */
    private static final String LOG_DIR_NAME = "logs";
    /** 单个日志文件最大字节数，超过后轮转 */
    private static final long MAX_FILE_SIZE = 512 * 1024L;
    /** 读取日志展示时最多加载的字符数（防止大文件卡 UI） */
    private static final int MAX_READ_CHARS = 100_000;

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static final Object LOCK = new Object();

    private static File logDir;
    private static File logFile;
    private static File crashFile;
    private static boolean initialized;

    private AppLog() {
    }

    /**
     * 初始化日志目录（Application 启动时调用一次）。
     *
     * @param context 上下文
     */
    public static void init(Context context) {
        if (context == null) {
            return;
        }
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            try {
                Context app = context.getApplicationContext();
                File base = app.getExternalFilesDir(LOG_DIR_NAME);
                if (base == null) {
                    // 外部存储不可用时退回内部存储
                    base = new File(app.getFilesDir(), LOG_DIR_NAME);
                }
                if (!base.exists() && !base.mkdirs()) {
                    base = new File(app.getFilesDir(), LOG_DIR_NAME);
                    base.mkdirs();
                }
                logDir = base;
                logFile = new File(logDir, LOG_FILE_NAME);
                crashFile = new File(logDir, CRASH_FILE_NAME);
                initialized = true;
            } catch (Throwable t) {
                Log.e(TAG, "日志初始化失败", t);
            }
        }
    }

    /** 当前日志文件（可能为 null，未初始化时） */
    public static File getLogFile() {
        synchronized (LOCK) {
            return logFile;
        }
    }

    /** 最近一次崩溃摘要文件 */
    public static File getCrashFile() {
        synchronized (LOCK) {
            return crashFile;
        }
    }

    /** 日志目录 */
    public static File getLogDir() {
        synchronized (LOCK) {
            return logDir;
        }
    }

    /** 日志文件绝对路径（给用户看的） */
    public static String getLogPath() {
        File f = getLogFile();
        return f != null ? f.getAbsolutePath() : "(未初始化)";
    }

    // ------------------------------------------------------------------
    // 常规日志
    // ------------------------------------------------------------------

    public static void i(String tag, String msg) {
        write("I", tag, msg, null);
    }

    public static void d(String tag, String msg) {
        write("D", tag, msg, null);
    }

    public static void w(String tag, String msg) {
        write("W", tag, msg, null);
    }

    public static void w(String tag, String msg, Throwable t) {
        write("W", tag, msg, t);
    }

    public static void e(String tag, String msg) {
        write("E", tag, msg, null);
    }

    public static void e(String tag, String msg, Throwable t) {
        write("E", tag, msg, t);
    }

    /**
     * 写一条日志：同时输出到 logcat 与文件。
     *
     * @param level 日志级别 I/D/W/E
     * @param tag   标签
     * @param msg   内容
     * @param t     异常，可为 null
     */
    public static void write(String level, String tag, String msg, Throwable t) {
        String line = buildLine(level, tag, msg, t);
        switch (level) {
            case "E":
                Log.e(TAG, line, t);
                break;
            case "W":
                Log.w(TAG, line, t);
                break;
            case "D":
                Log.d(TAG, line);
                break;
            default:
                Log.i(TAG, line);
                break;
        }
        writeRaw(line + "\n");
    }

    /** 生成一行日志文本 */
    private static String buildLine(String level, String tag, String msg, Throwable t) {
        String time;
        synchronized (TIME_FORMAT) {
            time = TIME_FORMAT.format(new Date());
        }
        StringBuilder sb = new StringBuilder(128);
        sb.append(time).append(' ').append(level).append('/').append(tag).append(": ").append(msg);
        if (t != null) {
            sb.append('\n').append(stackTraceToString(t));
        }
        return sb.toString();
    }

    /**
     * 同步写入原始文本（崩溃时使用，必须立即落盘）。
     *
     * @param text 文本内容
     */
    public static void writeRaw(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            File file = logFile;
            if (file == null) {
                return;
            }
            try {
                rotateIfNeeded(file, text.length());
                OutputStream out = new FileOutputStream(file, true);
                try {
                    out.write(text.getBytes(UTF8));
                    out.flush();
                } finally {
                    closeQuietly(out);
                }
            } catch (Throwable t) {
                Log.e(TAG, "写日志文件失败", t);
            }
        }
    }

    /** 记录一次崩溃摘要（供下次启动提示） */
    public static void writeCrashSummary(String summary) {
        if (summary == null || summary.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            File file = crashFile;
            if (file == null) {
                return;
            }
            try {
                OutputStream out = new FileOutputStream(file, false);
                try {
                    out.write(summary.getBytes(UTF8));
                    out.flush();
                } finally {
                    closeQuietly(out);
                }
            } catch (Throwable t) {
                Log.e(TAG, "写崩溃摘要失败", t);
            }
        }
    }

    /** 文件过大时轮转：把当前日志改名为历史日志 */
    private static void rotateIfNeeded(File file, int incomingLength) {
        try {
            if (!file.exists() || file.length() + incomingLength <= MAX_FILE_SIZE) {
                return;
            }
            File history = new File(file.getParentFile(), HISTORY_FILE_NAME);
            if (history.exists()) {
                history.delete();
            }
            file.renameTo(history);
        } catch (Throwable ignored) {
            // 轮转失败不影响继续写入
        }
    }

    // ------------------------------------------------------------------
    // 读取 / 清理
    // ------------------------------------------------------------------

    /** 读取当前日志内容（超长时只保留尾部） */
    public static String readLog() {
        return readFileTail(getLogFile());
    }

    /** 读取崩溃摘要 */
    public static String readCrashSummary() {
        return readFileTail(getCrashFile());
    }

    /** 读取文件尾部内容 */
    private static String readFileTail(File file) {
        if (file == null || !file.exists()) {
            return "";
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                bos.write(buf, 0, len);
            }
            String all = new String(bos.toByteArray(), UTF8);
            if (all.length() > MAX_READ_CHARS) {
                return "……（日志过长，仅显示最后 " + MAX_READ_CHARS + " 字符）……\n"
                        + all.substring(all.length() - MAX_READ_CHARS);
            }
            return all;
        } catch (Throwable t) {
            return "读取日志失败：" + t;
        } finally {
            closeQuietly(in);
        }
    }

    /** 清空日志文件与崩溃摘要 */
    public static void clear() {
        synchronized (LOCK) {
            deleteQuietly(logFile);
            deleteQuietly(crashFile);
            deleteQuietly(new File(logDir, HISTORY_FILE_NAME));
        }
    }

    /** 是否存在尚未查看的崩溃摘要 */
    public static boolean hasCrashSummary() {
        File f = getCrashFile();
        return f != null && f.exists() && f.length() > 0;
    }

    /** 标记崩溃摘要已查看（删除摘要文件） */
    public static void markCrashReviewed() {
        synchronized (LOCK) {
            deleteQuietly(crashFile);
        }
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    /** 异常转字符串 */
    public static String stackTraceToString(Throwable t) {
        if (t == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private static void deleteQuietly(File f) {
        if (f != null && f.exists()) {
            f.delete();
        }
    }
}
