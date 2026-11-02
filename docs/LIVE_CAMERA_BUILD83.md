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


## Phát hiện thực tế từ thử nghiệm Build #85 (02/11/2026)

- Người dùng kiểm tra Wi-Fi 70mai A500S Plus và nhận **`RTSP/1.0 200 OK`** cho lệnh OPTIONS qua `network.socketFactory`.
- Bộ phát Media3 ở Build #85 báo **`ERROR_CODE_IO_UNSPECIFIED`**; không có frame trong ô điện thoại. Kết quả không đủ để kết luận camera đã có/không có luồng RTSP.
- Khác biệt mã nguồn: probe dùng Wi-Fi socket trực tiếp; ExoPlayer cũ dùng socket hệ thống mặc định, có thể bị Android định tuyến qua LTE khi Wi-Fi 70mai không có Internet.

## Bản vá tiếp theo (sau Build #85)

1. `CameraWifiNetwork`: tìm `NetworkCapabilities.TRANSPORT_WIFI` để chọn socket Wi-Fi mà không thay đổi default route của toàn ứng dụng.
2. `LiveCameraController`: dùng `RtspMediaSource.Factory().setSocketFactory(wifi.socketFactory)` và `setForceUseRtpTcp(...)`; mặc định dùng RTP/TCP, có checkbox chuyển về UDP trong AI Camera; tăng timeout đọc video tới 12 giây.
3. `RtspWifiDiagnostic`: chạy **DESCRIBE** chỉ-đọc qua Wi-Fi để tìm dữ liệu SDP có `m=video`, lần lượt trên URL đang nhập và các ứng viên G5XX (gốc, `/livestream/12`, `/liveRTSP/av1`, `/liveRTSP/v1`, `/liveRTSP/av2`, `/liveRTSP/av4`). Danh sách này chỉ là **đường dẫn ứng viên**, không được coi là đã hoạt động trên A500S Plus.
4. AI Camera hiện kết quả từng URL, nếu có SDP video thì tự điền ứng viên vào ô URL; người dùng **phải bấm Kết nối** để thử SETUP/PLAY thật. Không tự gửi lệnh CGI/handshake chưa xác minh.
5. Nếu chẩn đoán chỉ được 200 OPTIONS mà mọi DESCRIBE thất bại, kiểm tra app 70mai/xADAS và firmware trước. Dự án **chưa có** quy trình handshake hãng được xác minh.

### Thử lại trên điện thoại

- Kết nối Wi-Fi `70mai_...` trên Android. Nếu hiện 'Wi-Fi không có Internet', chọn tiếp tục giữ kết nối.
- TTX → AI Camera → **Chẩn đoán đường phát (DESCRIBE / SDP)** → chờ kết quả hiện trên cùng trang.
- Nếu tìm thấy `DESCRIBE 200 + SDP VIDEO`, bấm **Kết nối / cập nhật Camera trực tiếp** (TCP bật mặc định). Nếu thất bại, thử bỏ chọn TCP để dùng UDP; ghi lại dòng lỗi và trạng thái SDP.
- Nếu SDP không có `m=video`, không có bằng chứng URL đó phát hình; **200 OPTIONS không có nghĩa camera sẵn sàng PLAY**.
- Chỉ khi preview điện thoại thật hiện hình mới kiểm tra ô dưới VietQR trong Android Auto debug; **không thao tác trên điện thoại khi xe đang di chuyển**.

**Giới hạn:** Đã sửa định tuyến theo API Media3, nhưng chưa thử với A500S Plus thật. Android Auto release giữ ràng buộc map-only; không thay đổi cơ chế an toàn Android Auto.
