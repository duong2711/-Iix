# XiaoAI Back Bridge — Module bổ sung cho GeminiGuard

**Trạng thái:** mã nguồn thử nghiệm, chưa build/kiểm thử thành APK trên Xiaomi 13 Ultra.

## Mục tiêu

1. Người dùng nói "Hey Xiao AI".
2. **GeminiGuard hiện tại** phát hiện Xiao AI và mở Gemini.
3. GeminiGuard gửi một **broadcast explicit** cho XiaoAI Back Bridge — **KHÔNG mở Activity** của Bridge.
4. Bridge kết nối ADB shell cục bộ (127.0.0.1:5555) và gửi `input keyevent 4`.
5. Nếu cửa sổ `voice_assist_root` vẫn đang nhận Back, Xiao AI đóng; Gemini phía sau lộ ra.

Lệnh `am force-stop com.miui.voiceassist` không được đưa vào bản mặc định: nó có thể làm gián đoạn Hotword hoặc dịch vụ đã mở Gemini. Trước tiên nên dùng Back, vì trên máy bạn đã thử thành công.

## Giới hạn bắt buộc

- App Android bình thường **không thể** chạy `adb shell input` chỉ nhờ `Runtime.exec()`; shell Android chạy dưới UID của app, không có `INJECT_EVENTS`.
- Bridge chỉ chạy được nếu **adbd đã lắng nghe TCP 5555** và khóa ADB của Bridge đã được máy cho phép.
- `adb tcpip 5555` **không được đảm bảo duy trì sau khởi động lại**. Có thể cần kết nối PC để kích hoạt lại. Không cần root/Trợ năng/Shizuku, nhưng **không phải giải pháp độc lập hoàn toàn**.
- Chỉ hoạt động từ Android 14 (API 34), nhờ cơ chế kiểm tra danh tính sender `getSentFromUid()`.
- Cần sửa GeminiGuard để gửi broadcast. **Cài riêng Bridge không tự nhận biết Xiao AI**. Không có listener chạy nền liên tục.
- Bật ADB TCP mở cổng gỡ lỗi mạng; chỉ bật khi dùng và trong mạng tin cậy. Tắt bằng `adb usb` khi không cần. Không phát hành APK kèm khóa ADB đã cấp quyền.

## Build

Mở thư mục này bằng Android Studio, đồng bộ Gradle và Build > Build APK(s).
Hoặc đẩy nguyên thư mục vào repository mới để workflow `.github/workflows/build.yml` chạy và lấy artifact `XiaoAIBackBridge-debug`.

## Thiết lập lần đầu

1. USB tới PC; bật `USB debugging`, xác nhận hộp thoại RSA.
2. Trên CMD của PC: `adb devices` sau đó `adb tcpip 5555`.
3. Rút USB sau khi lệnh thành công; mở **XiaoAI Back Bridge** trên điện thoại, chọn **Kiểm tra / cấp quyền ADB**. Chấp nhận hộp thoại quyền gỡ lỗi của Bridge nếu có. Nhấn lại để kiểm tra phải thấy UID shell (thường `uid=2000(shell)`).
4. Nếu Bridge không kết nối, khả năng HyperOS đã không cho TCP ADB cục bộ, hoặc đã khởi động lại; phải kiểm tra `adb tcpip 5555` và quyền RSA. Không có cách vượt giới hạn từ app bình thường.
5. Có thể thử nút **Thử BACK sau 5 giây**, nhanh chóng gọi Xiao AI trước khi hết 5 giây để kiểm tra.

## Sửa GeminiGuard

Repo người dùng: https://github.com/minhdat5122010-dev/GeminiGuard/tree/main . Nhánh `main` hiện lưu nguồn trong `GeminiGuard-v2.0.zip` (không bung thành tập tin văn bản trên repo). Bản sao nhị phân đó không truy xuất được nội dung bằng kết nối GitHub hiện tại, vì vậy **module này chưa được gộp vào mã nguồn GeminiGuard**.

- Giải nén `GeminiGuard-v2.0.zip` trên PC.
- Thêm file Java từ `GEMINIGUARD_INTEGRATION.java.txt` với tên `XiaoAiBackTrigger.java` vào package đang dùng (mẫu package `com.gemini.guard`).
- Trong hàm GeminiGuard **đang phát hiện Xiao AI và mở Gemini**, sau lệnh mở Gemini, thêm `XiaoAiBackTrigger.send(getApplicationContext());`.
- Không dùng `startActivity()` để bật Bridge vì sẽ chuyển focus vào Bridge và `BACK` có thể đóng nhầm Bridge.
- Nếu GeminiGuard có thể phát hiện Xiao AI nhưng chưa mở Gemini, cũng có thể mở Gemini trước rồi gửi broadcast.

## Kiểm tra sự kiện

`adb logcat -s XiaoAIBackBridge:I` có thể hiển thị kết quả.
Khi nhận broadcast hợp lệ Bridge ghi `Đã gửi BACK qua ADB` vào giao diện lúc người dùng mở lại ứng dụng.

## Cấu trúc

- `MainActivity.java`: cấp quyền / kiểm tra ADB, nút thử 5 giây.
- `RelayReceiver.java`: nhận broadcast **chỉ từ UID thuộc `com.gemini.guard`**, một lần mỗi ≥1.2 giây, một lệnh Back, không mở màn hình.
- `LocalAdbClient.java`: ADB protocol client tối thiểu tới `127.0.0.1:5555`.
- `AdbKeyStore.java`: khóa RSA host riêng trong bộ nhớ riêng ứng dụng.

## Rủi ro

Khi dùng `adb tcpip 5555`, các thiết bị khác cùng mạng có thể nhìn thấy cổng ADB (nhưng vẫn phải được ủy quyền). Chỉ sử dụng trong mạng đáng tin cậy; Google không bảo đảm quyền hay persistence sau khởi động.
