package com.ldd.xiaoaitogemini;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Setup and diagnostic screen only. No background monitor. */
public final class MainActivity extends Activity {
    private TextView result;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(32), dp(22), dp(22));
        scroll.addView(root);
        root.addView(text("XiaoAI Back Bridge", 25, true));
        root.addView(text("Gửi BACK tới Xiao AI qua ADB nội bộ, không cần root hoặc Trợ năng. Không tự kích hoạt được khi GeminiGuard chưa gửi broadcast.", 16, false));
        root.addView(text("Cài đặt trước qua máy tính: adb tcpip 5555. Mở app, bấm Kiểm tra ADB, chấp nhận khóa RSA nếu được hỏi.", 15, false));

        Button check = new Button(this);
        check.setText("Kiểm tra / cấp quyền ADB");
        root.addView(check);
        check.setOnClickListener(v -> runCommand("id", false));

        Button back = new Button(this);
        back.setText("Thử BACK sau 5 giây");
        root.addView(back);
        back.setOnClickListener(v -> {
            result.setText("5 giây nữa gửi BACK; hãy chuyển sang cửa sổ Xiao AI...");
            ui.postDelayed(() -> runCommand("input keyevent 4; echo __BRIDGE_RC:$?", true), 5000);
        });

        result = text("Chưa kiểm tra ADB", 16, false);
        result.setTextIsSelectable(true);
        root.addView(result);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        if (result != null) {
            String last = getSharedPreferences("status", Context.MODE_PRIVATE).getString("last", null);
            if (last != null) result.setText(last);
        }
    }

    private void runCommand(String command, boolean testBack) {
        new Thread(() -> {
            String msg;
            try {
                String output = new LocalAdbClient(getApplicationContext()).shell(command);
                msg = (testBack ? "BACK: " : "ADB: ") + output;
            } catch (Exception ex) {
                msg = "Không kết nối được ADB: " + ex.getMessage()
                    + "\nKiểm tra lại adb tcpip 5555, quyền RSA và cổng đang mở.";
            }
            String finalMessage = msg;
            ui.post(() -> result.setText(finalMessage));
        }, "adb-test").start();
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(Color.rgb(32, 39, 48));
        if (bold) t.setTypeface(null, 1);
        t.setPadding(0, dp(8), 0, dp(14));
        return t;
    }
    private int dp(int n) { return Math.round(getResources().getDisplayMetrics().density * n); }
}
