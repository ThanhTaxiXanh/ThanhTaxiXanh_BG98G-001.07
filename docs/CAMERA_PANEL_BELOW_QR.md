# Camera giao thông dưới mã QR — màn hình Android Auto

## Thay đổi giao diện

- Preset mặc định **stacked** giữ nguyên lưới: lịch/tuyến ở trên, mã QR ở giữa, **Camera phía trước** ở dưới.
- Mã panel nội bộ vẫn là `DashboardPanel.SIGNS` để không phá tệp cấu hình đã lưu; tên hiển thị là **Camera**.
- Ô Camera hiển thị tối đa ba cảnh báo `camera` / `redlight_camera` được khớp với hướng/tuyến, theo khoảng cách gần nhất. Cài đặt cảnh báo riêng từng loại vẫn được tôn trọng.
- Khi không có GPS, cảnh báo bị ẩn và hiển thị trạng thái chờ. Khi nguồn OSM không có camera phù hợp, giao diện không tự tạo camera hoặc khẳng định đường chắc chắn không có camera.
- Biểu tượng giới hạn tốc độ đã có trên bản đồ bên trái (`LauncherRenderer.drawBubbles`) được giữ nguyên. Ảnh QR và khoảng trắng bảo vệ mã QR không thay đổi.
- Ô này là **cảnh báo camera giao thông**, không phải video trực tiếp từ camera vật lý hay camera IP.

## Chú ý phạm vi Android Auto

Trong debug/personal Android Auto, `CarService` gọi `LauncherRenderer.render` để vẽ bảng hai cột, gồm ô QR và Camera. Bản release sử dụng `renderNavigationSurface` (không có widget QR/Camera) do ràng buộc nền tảng hiện hành. Thành công trên preview điện thoại **không chứng minh** các widget đã xuất hiện trên Android Auto thật.

## Kiểm thử

1. `gradle :app:testDebugUnitTest`: kiểm tra lọc loại camera, phân loại cài đặt, tránh trùng, thứ tự gần nhất.
2. `gradle :app:assembleDebug`: tạo APK debug.
3. Trên màn hình xe 934×844 trong môi trường debug cá nhân, chọn preset `02 · Ba tầng`: xác nhận QR ở giữa và Camera phía trước dưới QR, không còn nhãn “Giới hạn tốc độ” trong ô dưới QR.
4. Xác minh GPS có/không có fix, camera tốc độ/đèn đỏ, khoảng cách gần/xa và việc tắt cảnh báo. Kiểm tra QR vẫn quét được.
5. Không đánh dấu đã kiểm thử trên xe hoặc video live nếu chưa có bằng chứng thực tế.
