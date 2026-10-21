package com.example.stocks.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.stocks.R;
import com.example.stocks.utils.AppLog;
import com.example.stocks.utils.ShareUtil;

import java.io.File;

/**
 * 日志查看页：显示本地日志文件内容，并可用其它应用打开 / 分享 / 复制。
 * 没有 adb 时，这是拿到崩溃信息的入口（首页菜单 → 日志）。
 */
public class LogActivity extends AppCompatActivity {

    private TextView tvPath;
    private TextView tvLog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_log);

        tvPath = findViewById(R.id.tvPath);
        tvLog = findViewById(R.id.tvLog);

        findViewById(R.id.toolbar).setOnClickListener(v -> finish());

        findViewById(R.id.btnOpen).setOnClickListener(v -> openWithOtherApp());
        findViewById(R.id.btnShare).setOnClickListener(v -> share());
        findViewById(R.id.btnCopy).setOnClickListener(v -> copy());
        findViewById(R.id.btnClear).setOnClickListener(v -> clear());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    /** 重新读取日志文件并显示 */
    private void refresh() {
        File file = AppLog.getLogFile();
        tvPath.setText(getString(R.string.log_path, AppLog.getLogPath()));
        String content = AppLog.readLog();
        tvLog.setText(content.isEmpty() ? getString(R.string.log_empty) : content);
        tvLog.setTextIsSelectable(true);
    }

    /** 用其它应用打开日志文件 */
    private void openWithOtherApp() {
        File file = AppLog.getLogFile();
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.log_file_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        ShareUtil.openWithOtherApp(this, file, "text/plain");
    }

    /** 分享日志文件 */
    private void share() {
        File file = AppLog.getLogFile();
        if (file == null || !file.exists()) {
            Toast.makeText(this, R.string.log_file_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        ShareUtil.shareFile(this, file, "text/plain", getString(R.string.log_share_title));
    }

    /** 复制日志内容 */
    private void copy() {
        String content = AppLog.readLog();
        if (content.isEmpty()) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ShareUtil.copyToClipboard(this, content)) {
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.log_copy_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 清空日志 */
    private void clear() {
        AppLog.clear();
        AppLog.i("LogActivity", "日志已被清空");
        refresh();
        Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show();
    }
}
