# Tạo APK XiaoAI Back Bridge

**Trạng thái:** Đây là **mã nguồn**, CHƯA PHẢI tệp APK đã được biên dịch. Dự án chưa được thử trên máy Xiaomi 13 Ultra.

## Cách dễ nhất: Build trên GitHub Actions (không cần Android Studio)

1. Tạo một repository **riêng** trên GitHub (chẳng hạn `XiaoAIBackBridge`).
2. Giải nén toàn bộ tệp ZIP này. Tải các nội dung *bên trong thư mục XiaoAIBackBridge* lên **gốc** repository mới, giữ nguyên thư mục `.github/workflows`.
3. Vào tab **Actions** → workflow **Build XiaoAI Back Bridge APK** → **Run workflow**; hoặc chờ workflow chạy sau khi push lên nhánh `main`.
4. Khi workflow có dấu tích xanh, mở run → phần **Artifacts** → tải **XiaoAI-Back-Bridge-APK**. Giải nén artifact sẽ thấy `app-debug.apk`.
5. Cài bằng `adb install -r app-debug.apk` hoặc chuyển APK sang điện thoại để cài.

## Cách build trên Windows (nếu đã có Android Studio)

- Dùng Android Studio SDK Manager cài Android SDK Platform 35 và Build-Tools 35.0.0.
- Cài JDK 17; bảo đảm `java` có trong `PATH`.
- Nhấp đúp `BUILD_APK_WINDOWS.cmd`. Script tự tải Gradle 8.9 nếu chưa có.
- APK (nếu build thành công) sẽ được sao chép thành `XiaoAIBackBridge-debug.apk` bên cạnh script.

## Lưu ý quan trọng

- Cài Bridge **không đủ** để tự động đóng Xiao AI; cần tích hợp lệnh gửi broadcast vào GeminiGuard theo `GEMINIGUARD_INTEGRATION.java.txt`.
- Bridge không có quyền `INJECT_EVENTS`. Nó chỉ gửi Back thông qua `127.0.0.1:5555` khi `adbd` đã chạy ở chế độ TCP và đã chấp nhận khóa ADB riêng của Bridge.
- Không root và không Trợ năng, nhưng `adb tcpip 5555` có thể mất sau khởi động lại; không bảo đảm chạy độc lập vĩnh viễn.
- Chỉ bật ADB TCP trong mạng tin cậy. Lệnh tắt: `adb usb` khi kết nối USB.
- **Không gửi lệnh `am force-stop com.miui.voiceassist` mặc định** vì có thể gián đoạn tính năng đánh thức bằng giọng nói.
