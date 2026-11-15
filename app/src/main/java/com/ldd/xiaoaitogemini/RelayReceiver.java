package com.ldd.xiaoaitogemini;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Triggered ONLY by GeminiGuard's explicit broadcast with shareIdentity enabled.
 * It does not launch an Activity: doing so would cause BACK to close this bridge
 * instead of the Xiao AI trusted system overlay.
 *
 * It requires an already-authorized ADB daemon at 127.0.0.1:5555.
 */
public final class RelayReceiver extends BroadcastReceiver {
    public static final String ACTION = "com.ldd.xiaoaitogemini.RUN_BACK";
    private static final String TRUSTED_SENDER = "com.gemini.guard";
    private static final AtomicLong LAST = new AtomicLong(0);

    @Override public void onReceive(Context ctx, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        int uid = getSentFromUid();  // API 34+. Requires sender's BroadcastOptions.shareIdentity.
        if (!isTrusted(ctx, uid)) {
            Log.w("XiaoAIBackBridge", "Rejected broadcast; sender UID=" + uid);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long old = LAST.get();
        if (now - old < 1200 || !LAST.compareAndSet(old, now)) return;

        PendingResult pending = goAsync();
        Context app = ctx.getApplicationContext();
        Thread thread = new Thread(() -> {
            try {
                // ADB daemon executes this command as SHELL, not the app UID.
                String output = new LocalAdbClient(app).shell("input keyevent 4; echo __BRIDGE_RC:$?");
                boolean ok = output.contains("__BRIDGE_RC:0");
                app.getSharedPreferences("status", Context.MODE_PRIVATE).edit()
                    .putString("last", ok ? "Đã gửi BACK qua ADB" : "BACK chưa xác nhận: " + output)
                    .apply();
                Log.i("XiaoAIBackBridge", "BACK result=" + output);
            } catch (Exception ex) {
                app.getSharedPreferences("status", Context.MODE_PRIVATE).edit()
                    .putString("last", "Lỗi: " + ex.getMessage()).apply();
                Log.e("XiaoAIBackBridge", "Cannot inject BACK", ex);
            } finally {
                pending.finish();
            }
        }, "one-shot-back");
        thread.start();
    }

    private static boolean isTrusted(Context context, int uid) {
        if (uid == Process.INVALID_UID) return false;
        String[] packages = context.getPackageManager().getPackagesForUid(uid);
        if (packages == null) return false;
        for (String pkg : packages) if (TRUSTED_SENDER.equals(pkg)) return true;
        return false;
    }
}
