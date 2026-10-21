package com.example.stocks.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.stocks.R;
import com.example.stocks.utils.AppLog;
import com.example.stocks.utils.ShareUtil;

import java.io.File;

/**
 * 崩溃提示页：崩溃后由 AlarmManager 拉起（见 CrashHandler）。
 * 展示崩溃摘要，并提供「用其它应用打开日志文件」「复制」「退出应用」。
 */
public class CrashActivity extends AppCompatActivity {

    /** 崩溃摘要文件路径 */
    public static final String EXTRA_CRASH_FILE = "extra_crash_file";

    private TextView tvCrash;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

        tvCrash = findViewById(R.id.tvCrash);

        String path = getIntent().getStringExtra(EXTRA_CRASH_FILE);
        String summary = readSummary(path);
        tvCrash.setText(summary.isEmpty() ? getString(R.string.crash_no_detail) : summary);

        findViewById(R.id.btnOpen).setOnClickListener(v -> openWithOtherApp());
        findViewById(R.id.btnCopy).setOnClickListener(v -> copy());
        findViewById(R.id.btnQuit).setOnClickListener(v -> {
            // 回到首页，用户可以继续用应用并从菜单里打开日志
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finishAffinity();
        });
    }

    /** 读取崩溃摘要（优先用传入路径，其次用默认文件） */
    private String readSummary(String path) {
        try {
            if (path != null && !path.isEmpty()) {
                File f = new File(path);
                if (f.exists() && f.length() > 0) {
                    return readFile(f);
                }
            }
            String s = AppLog.readCrashSummary();
            if (!s.isEmpty()) {
                return s;
            }
            // 摘要没有就展示日志尾部（里面有完整堆栈）
            String log = AppLog.readLog();
            if (log.isEmpty()) {
                return "";
            }
            int idx = log.lastIndexOf("========== 崩溃开始 ==========");
            return idx >= 0 ? log.substring(idx) : tail(log, 2000);
        } catch (Throwable t) {
            return "";
        }
    }

    private String readFile(File f) {
        try {
            java.io.InputStream in = new java.io.FileInputStream(f);
            try {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    bos.write(buf, 0, len);
                }
                return new String(bos.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
        } catch (Throwable t) {
            return "";
        }
    }

    private String tail(String s, int n) {
        return s.length() <= n ? s : s.substring(s.length() - n);
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

    /** 复制崩溃信息 */
    private void copy() {
        String text = tvCrash.getText().toString();
        if (ShareUtil.copyToClipboard(this, text)) {
            Toast.makeText(this, R.string.log_copied, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.log_copy_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
