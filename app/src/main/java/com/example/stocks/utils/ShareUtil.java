package com.example.stocks.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;

/**
 * 文件分享工具：把日志文件交给其它应用打开（文件管理器、文本编辑器、聊天工具等）。
 * 通过 FileProvider 授予临时读权限，不需要存储权限。
 */
public final class ShareUtil {

    /** FileProvider 授权名（需与 AndroidManifest 中一致） */
    public static final String AUTHORITY_SUFFIX = ".fileprovider";

    private ShareUtil() {
    }

    /** 生成可分享的 content Uri */
    public static Uri fileUri(Context context, File file) {
        return FileProvider.getUriForFile(context,
                context.getPackageName() + AUTHORITY_SUFFIX, file);
    }

    /**
     * 用其它应用打开文件（系统弹出选择器）。
     *
     * @param activity 发起方
     * @param file     文件
     * @param mimeType MIME 类型
     * @return 是否成功拉起选择器
     */
    public static boolean openWithOtherApp(Activity activity, File file, String mimeType) {
        if (activity == null) {
            return false;
        }
        if (file == null || !file.exists()) {
            Toast.makeText(activity, "日志文件不存在", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            Uri uri = fileUri(activity, file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            AppLog.e("ShareUtil", "没有可以打开该文件的应用", e);
            Toast.makeText(activity, "没有可以打开该文件的应用", Toast.LENGTH_SHORT).show();
            return false;
        } catch (Throwable t) {
            AppLog.e("ShareUtil", "打开文件失败", t);
            Toast.makeText(activity, "打开文件失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 分享文件（微信、邮件、便签等）。
     */
    public static boolean shareFile(Activity activity, File file, String mimeType, String title) {
        if (activity == null || file == null || !file.exists()) {
            return false;
        }
        try {
            Uri uri = fileUri(activity, file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mimeType);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(chooser);
            return true;
        } catch (Throwable t) {
            AppLog.e("ShareUtil", "分享文件失败", t);
            Toast.makeText(activity, "分享失败：" + t.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /** 复制文本到剪贴板 */
    public static boolean copyToClipboard(Context context, String text) {
        if (context == null || text == null) {
            return false;
        }
        try {
            ClipboardManager cm =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return false;
            }
            cm.setPrimaryClip(ClipData.newPlainText("stocks-log", text));
            return true;
        } catch (Throwable t) {
            AppLog.e("ShareUtil", "复制到剪贴板失败", t);
            return false;
        }
    }
}
