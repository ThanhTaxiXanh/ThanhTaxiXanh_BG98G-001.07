# Camera trực tiếp dưới VietQR — kế thừa TTX Build 83

**Mốc nguồn:** GitHub Actions #83 (commit `b72ddfe549376ef286231c5a0386ba1c3a5788ce`, `feature/ai-camera-review`).

## Chức năng triển khai

- **Android Auto debug/personal:** `LauncherRenderer` vẽ khung Camera trực tiếp vào tile `DashboardPanel.SIGNS`. Preset mặc định `stacked` giữ nguyên tọa độ lưới: trên Lịch / điều hướng, giữa VietQR, dưới Camera. Tên panel được đổi nhưng **ID `SIGNS` vẫn giữ nguyên** để không hỏng cấu hình Dashboard đã lưu.
- **Điện thoại Android:** tab **AI Camera** → `AiCameraActivity` có preview 16:9, URL RTSP, nút Kết nối/Cập nhật và Ngắt. `PhoneDashboardActivity` cũng render hình ở ô Camera của dashboard preview.
- **Nguồn giải mã:** Media3 ExoPlayer RTSP 1.4.1 phát không tiếng ra `CameraFrameSink`, một `SurfaceTexture` tiêu thụ khung hình trên EGL background thread. Chụp bitmap 480×270 tối đa khoảng 2 FPS cho tile và preview, không phụ thuộc Activity trên điện thoại còn mở.
- **Vòng đời:** bộ giải mã chỉ chạy khi người dùng bật Camera và đang có ít nhất một màn hình sử dụng (Car debug, phone preview, AI Camera). Thoát hết màn hình sẽ giải phóng player, surface và EGL.
- **Giới hạn tốc độ:** bong bóng trên bản đồ ở `drawBubbles` giữ nguyên, không còn ô tốc độ riêng bên dưới QR.
- **Không ảnh hưởng** module YOLOv8/VTSR, 70mai offline import, xADAS, cấu hình cảnh báo biển báo và VietQR.

## Cách dùng

1. Kết nối điện thoại Android với Wi-Fi của 70mai A500S Plus. Với mạng Wi-Fi không có Internet, xác minh điện thoại vẫn sử dụng đúng kết nối Wi-Fi.
2. Trong TTX, vào **AI Camera**, tìm ô **CAMERA TRỰC TIẾP**, nhập URL RTSP do camera trả về và chạm **Kết nối / cập nhật Camera trực tiếp**.
3. URL mặc định `rtsp://192.168.0.1/` chỉ là endpoint ứng viên từ profile G5XX của xADAS; **chưa xác minh A500S Plus trả video cho URL này**, có thể cần handshake riêng. Kiểm tra `SeventyMaiWifiProbe` trước, nhưng `OPTIONS` thành công không chứng minh `PLAY` được.
4. Kiểm tra preview có hình thực. Mở chế độ Android Auto debug/personal, preset **02 · Ba tầng**, kiểm tra hình dưới VietQR và QR quét bình thường.
5. Nếu khung chỉ hiện trạng thái, kiểm tra kết nối Wi-Fi, URL, khả năng RTSP camera và firmware. Không hiển thị hình giả khi chưa nhận dữ liệu.
6. Nhấn **Ngắt Camera trực tiếp** để tắt, hoặc thoát các màn hình để dừng decoder; cấu hình nguồn được giữ riêng trong `ttx_live_camera`.

## Ràng buộc chưa xác nhận

- **Không có kết nối camera vật lý trong CI.** Biên dịch thành công và unit tests không xác minh RTSP handshake/video A500S, chất lượng preview thực, hoặc Android Auto head-unit đã nhận hình.
- **Android Auto release** vẫn dùng `renderNavigationSurface` map-only theo giới hạn nền tảng, không hiển thị nội dung Camera/QR. Không vô hiệu hóa khóa an toàn Android Auto.
- Luồng hiện tại là hình ảnh trực tiếp phục vụ cá nhân/debug, **không chạy inference YOLOv8 trực tiếp trên stream**. AI nhập video/ảnh đã có trong Build 83 được giữ độc lập.
- Camera trên màn hình xe có thể gây mất tập trung. Không tương tác với cài đặt khi đang lái xe.

## Kiểm thử

- `gradle :app:assembleDebug :app:testDebugUnitTest`
- `gradle :app:assembleDebugAndroidTest :app:lintDebug`
- Thử nghiệm điện thoại thật và Android Auto 934×844 còn **PENDING** cho đến khi có camera thật và host tương thích.
