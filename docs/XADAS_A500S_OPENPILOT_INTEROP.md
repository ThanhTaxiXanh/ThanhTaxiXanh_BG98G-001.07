# TTX · Tích hợp 70mai A500S Plus và nghiên cứu kiến trúc xADAS/Openpilot

Ngày ghi nhận: 2026-11-02. Nhánh: `feature/build83-camera-display-under-qr`, kế thừa Build 83 qua PR #3.

## Mục đích và mức độ bằng chứng

- Người dùng đang nghiên cứu **xADAS 0.2.7**, mã gói `com.kaibatech.drivedeck.xadas`, để lấy video trực tiếp từ **70mai A500S Plus**, và sử dụng hình ảnh làm đầu vào nhận diện **làn xe, vạch đường và biển báo** trong ứng dụng riêng TTX.
- Các dấu hiệu từ **báo cáo phân tích tĩnh xADAS 0.2.7 đã có trong thư viện dự án** gồm: `G5XX`, `rtsp-70mai`, tiền tố SSID `70mai_`; `libxadas_ncnn.so`, `libonnxruntime.so`, `libopencv_java4.so`; `assets/driving_vision_fp32.onnx`, `assets/driving_vision_ncnn.bin`, `assets/driving_vision_ncnn.param`. Chưa có thử nghiệm thực thi xADAS với camera vật lý. Tệp XAPK tải lại cần được đối chiếu hash khi môi trường phân tích nhị phân khả dụng.
- **Không chép mã dịch ngược hoặc trọng số xADAS** khi chưa xác minh quyền sử dụng. Tham khảo hành vi/giao thức để viết triển khai độc lập; chỉ tái sử dụng mã/mô hình có giấy phép phù hợp.

## 1. RTSP A500S: điều xác minh được / chưa xác minh

### Phát hiện từ thử nghiệm người dùng TTX Build 85

- Socket gắn rõ mạng Wi-Fi trả `RTSP/1.0 200 OK` cho **OPTIONS** tới `192.168.0.1:554`.
- Media3 v1.4.1 ở Build 85 báo `ERROR_CODE_IO_UNSPECIFIED` khi thử phát `rtsp://192.168.0.1/`. Điều đó **không chứng minh** camera thiếu stream, cũng **không chứng minh** endpoint gốc có video.
- Từ Build 94, Media3 tạo `RtspMediaSource` sử dụng `wifi.socketFactory`, không thay đổi default network của toàn ứng dụng.

### Phát hiện công khai từ RTSP A500S Plus (không phải thiết bị của người dùng)

- Hội thảo thực nghiệm 4PDA, ngày 10/11/2025: `DESCRIBE rtsp://192.168.0.1:554/livestream/12` → **SDP H.264** gồm `m=video`, `a=rtpmap:96 H264/90000`, `a=fmtp` có `sprop-parameter-sets`, `a=control:track1`.
- `Content-Base: rtsp://192.168.0.1/00000008/` thay đổi theo phiên. `SETUP` phải dùng giá trị `Content-Base` hiện thời cộng `track1`; **không được hardcode** chuỗi `/00000008/`.
- Bản ghi đó dùng `Transport: RTP/AVP/UDP`, do đó TTX mới thử UDP trước; nút checkbox vẫn cho phép ép RTP-over-RTSP TCP. Việc xADAS hoặc firmware A500S của người dùng chấp nhận UDP/TCP phải kiểm tra thực tế.
- Nguồn: https://4pda.to/forum/index.php?showtopic=1018592&st=11600 (bài #11610 và log cùng trang).

### Triển khai độc lập hiện tại trong TTX

- `camera/CameraWifiNetwork.kt`: chọn rõ Wi-Fi camera ngay cả khi Android chọn dữ liệu di động làm Internet mặc định.
- `camera/LiveCameraController.kt`: Media3 RTP/UDP hoặc RTP/TCP, một nguồn decoder chia sẻ preview điện thoại và ô dưới QR trên Android Auto debug.
- `camera/RtspWifiDiagnostic.kt`: chỉ gửi `DESCRIBE`, đọc `application/sdp`, nhận diện `m=video`, H.264, SPS/PPS, `Content-Base` và `track1` (đã rút gọn/sanitized). Không thực thi vendor CGI, không điều khiển camera.
- Mặc định mới của `LiveCameraConfig`: `rtsp://192.168.0.1/livestream/12`. Cấu hình URL đã lưu trước đó **không bị ghi đè**. User vẫn phải xác nhận kết nối.
- Thiết kế đúng theo RFC 2326 (ưu tiên `Content-Base` khi giải nghĩa `a=control`): https://www.rfc-editor.org/rfc/rfc2326
- Tài liệu Media3 về RTSP/SPS-PPS/RTP TCP-UDP/SocketFactory: https://developer.android.com/media/media3/exoplayer/rtsp

### Kiểm tra trên camera thực

1. Khi xe **đã dừng**, đảm bảo A500S bật Wi-Fi, điện thoại đang ở Wi-Fi `70mai_...`. Kết nối Android Auto không dây có thể tranh chấp radio Wi-Fi với camera; xác minh chế độ kết nối thực tế, cân nhắc Android Auto qua USB hoặc thiết bị hỗ trợ đồng thời nếu cần.
2. Xác minh ứng dụng 70mai chính chủ xem live được, sau đó **đóng phiên xem** để tránh xung đột RTSP.
3. TTX → AI Camera → `Chẩn đoán đường phát (DESCRIBE / SDP)`: ưu tiên `/livestream/12`, xem codec, đường `Content-Base` và `track1`. `OPTIONS 200` chỉ là kiểm tra dịch vụ.
4. Nếu `DESCRIBE 200 + SDP VIDEO`, bấm Kết nối với UDP trước; thử TCP nếu UDP lỗi. Nếu kết nối lỗi, lưu **mã lỗi** và trạng thái SDP, không chia sẻ token/mật khẩu camera.
5. Sau khi điện thoại nhận hình thực, xác minh ô dưới QR trên Android Auto debug và hiệu năng. Build/CI không thể kiểm tra camera thật.
6. Nếu chỉ `OPTIONS` thành công nhưng `DESCRIBE` không có SDP: tìm đường dẫn/handshake phù hợp firmware. Không tự gửi CGI mang tính thay đổi cấu hình hoặc pairing bằng dữ liệu suy đoán.

## 2. xADAS vision/Openpilot: điều thấy được

Theo báo cáo tĩnh xADAS 0.2.7:

- Mô hình `driving_vision_fp32.onnx` khoảng **92,94 MB**, hai tensor vào `img` và `big_img`, cả hai kiểu `uint8`, shape `[1,12,128,256]`; đầu ra `vout_pass`, `float32 [1,1576]`.
- Bộ hỗ trợ NCNN có `in0`, `in1`, `out0`; mã nhận dạng `OpenpilotLane`, `laneProbs`, `EgoLane`, `roadLaneCount`, `laneOffset`.
- Hai tensor 12-channel **không phải ảnh RGB thông thường**. Phải xác minh camera calibration, frame packing/preprocessing và cách giải 1576 giá trị trước khi khẳng định chạy được. Không tự giả định shape là mô hình phân loại biển báo.
- Phát hiện cấu trúc Openpilot không chứng minh xADAS có khả năng điều khiển vô lăng, chân ga/phanh; ứng dụng TTX chỉ hướng tới **hiển thị thông tin quan sát, không điều khiển xe**.
- `PartnerLinkService`, `IXadasLink`, `...action.ALERT`, `DISTANCE_M`, `SEQ` được tìm thấy trong xADAS, nhưng chưa xác minh API, permission, Pro, kiểu dữ liệu; **không có bằng chứng chia sẻ raw RTSP/lane tensors** qua Binder.

Openpilot upstream là phần mềm mã nguồn mở MIT (có ngoại lệ từng thành phần), **trọng số/mô hình của bản đóng gói trong xADAS không tự động có cùng giấy phép**. Xem https://github.com/commaai/openpilot và giấy phép từng mô hình.

## 3. Hai pipeline AI cần phát triển độc lập

### A — Lane / vạch đường

`A500S decoded frames` → camera intrinsics/pitch/yaw calibration → lựa chọn mô hình lane **được cấp phép riêng** → xác định vạch trái/phải chứa tâm xe + xác suất, số làn quan sát được và vạch liền/đứt (nếu mô hình hỗ trợ) → làm mượt theo thời gian → *gợi ý trên màn hình / log tạm*.

Các output như `egoLane` và `roadLaneCount` phải mang `confidence`, `modelVersion`, `timestamp`, `cameraCalibration`, `validUntil`. Không coi số làn quan sát được = số làn pháp lý; không tự động ghi `turn:lanes` vào OSM hoặc chọn làn khi chưa có map matching và định hướng tuyến.

### B — Traffic signs / tốc độ

`A500S decoded frames` → crop/vùng biển → **VTSR YOLOv8 đã có trong PR #1 Build 83** → nếu là biển tốc độ, cần đọc được chữ số trong ảnh (OCR/number model riêng, không dùng loại P-127 generic để suy số) → gắn thời gian + GPS có kiểm chứng, bằng chứng vùng ảnh, góc nhìn/chiều tuyến → hàng chờ phê duyệt → điểm GeoJSON cá nhân.

Không thay giới hạn tốc độ hợp pháp trên bản đồ bằng một phát hiện AI. Bản đồ tiếp tục sử dụng dữ liệu đã xác minh + giới hạn biển áp dụng cho đoạn đường đúng chiều.

### Schema khuyến nghị cho dữ liệu quan sát

`id, cameraSource, frameTimestampElapsedMs, captureUtc?, latitude?, longitude?, gpsAccuracyM?, headingDeg?, osmWayId?, travelDirection?, observationKind, classLabel, confidence, modelName, modelVersion, boundingBox?, evidenceImageRef, reviewStatus, reviewerConfirmedAt?, expiresAt?`.

- `observationKind`: `LANE_GEOMETRY`, `LANE_COUNT_SUGGESTION`, `TRAFFIC_SIGN`, `SPEED_LIMIT_SIGN`.
- Giữ **hai lớp dữ liệu riêng**: kết quả AI tạm thời/không xác minh (phục vụ overlay) và quan sát được người dùng xác nhận (private GeoJSON).
- Dữ liệu GPS trong MP4 70mai có thể nằm ở sidecar riêng: không gán toạ độ điện thoại hiện tại cho biển trong video đã quay từ trước.
- Khi live, ước lượng độ trễ RTSP trước khi gắn tọa độ điện thoại; dùng GNSS có accuracy, so khớp hướng đường, không ghi khi vị trí không chắc.
- Nên làm kiểm thử offline trên clip + GPS có timestamp trước khi bật pipeline live tốn CPU và điện.

## 4. Giới hạn nền tảng và an toàn

- Camera/QR trên Android Auto đang dùng **debug/personal surface**. Bản release theo ràng buộc hiện tại vẫn map-only; không bỏ khóa an toàn Android Auto.
- Không truy cập trái phép hoặc cố vượt qua pairing 70mai. Giao thức quan sát được dùng để xây tính năng trên thiết bị người dùng sở hữu.
- **Không nhúng** tài nguyên binary xADAS/Openpilot đóng gói chưa rõ giấy phép. Phát triển nguồn độc lập, huấn luyện/dùng mô hình có giấy phép phù hợp.
- Camera A500S Plus chính hãng có cảnh báo Lane Departure, Forward Collision, Forward Vehicle Start; tài liệu chính thức **không xác nhận giao diện xuất metadata ADAS cho app bên thứ ba**. Nguồn: https://70mai.store/pages/support-dash-cam-pro-plus
- Thiết bị thật, firmware và nguồn RTSP/SDP phải được xác minh trước khi coi là hoạt động.
