# DMS Android v1

Ứng dụng Java dùng CameraX (camera trước) và MediaPipe Face Landmarker để đo EAR, MAR và góc đầu. Bộ quyết định theo thời gian nằm trong `DmsDecisionEngine.java`; các ngưỡng thử nghiệm nằm trong `DmsConfig.java`.

## Chạy sau khi Android Studio đồng bộ xong

1. Mở project `DMS_appDetect` trong Android Studio và chờ Gradle Sync.
2. Chọn điện thoại Android có camera trước (min SDK 24), cấp quyền camera.
3. Trước khi thử cảnh báo mắt, bấm **Kiểm tra mắt 11 giây**. App chờ 3 giây, ghi 4 giây mắt mở rồi 4 giây mắt nhắm (1 tiếng bíp lúc mở, 2 tiếng bíp lúc nhắm), tự dừng và hiển thị trung vị EAR của hai giai đoạn. Đưa trọn mặt vào khung, giữ nguyên góc điện thoại và đủ sáng. Trong bài thử không phát chuông cảnh báo.
4. Chỉ khi phép đo mắt mở/nhắm tách rõ, app lưu ngưỡng cá nhân và nạp ngưỡng đó vào DmsDecisionEngine cho lần **Bắt đầu giám sát** tiếp theo. Ngưỡng đang dùng hiện trên màn hình và được ghi trong CSV. Nếu bài thử không đạt, cảnh báo mắt vẫn tắt; có thể thử lại. Các cảnh báo pose/miệng vẫn là ngưỡng thử nghiệm.
5. **Kính râm** là chế độ chọn tay: EAR bị vô hiệu, vẫn theo dõi góc đầu và miệng khi đo được.
6. **Viền sáng** chỉ là thử nghiệm màn hình; chưa được xác nhận có giúp camera trong xe thật.

Mô hình `face_landmarker.task` được đóng gói trong `app/src/main/assets`. Ứng dụng không cần backend hay mạng để suy luận. Mỗi phiên tạo một CSV trong thư mục riêng của app; không lưu ảnh/video.

## Ngáp và góc đầu trong v1.4

Khi người thử ngáp ở xa, app từng báo “Đầu cúi xuống”. Trước thay đổi này, góc cúi vượt -12° trong 400 ms được xử lý trước ngáp (MAR > 0,52 trong 700 ms); khuôn mặt khá nhỏ vẫn được dùng cho pose. Đây là hai cơ chế có thể giải thích triệu chứng, chưa có bản ghi của đúng lần lỗi để khẳng định độ lệch góc đến từ đâu.

Bản v1.4 chỉ dùng pose và miệng khi bề ngang mặt tối thiểu 80 px và 16% bề ngang ảnh. Ngáp cần mặt gần chính diện: yaw lệch không quá 12°, roll không quá 15°, pitch từ -18° đến +14° so với tư thế hiệu chuẩn. Khi miệng đang mở và mặt còn gần chính diện, mức pitch xuống nhẹ từ -12° đến -18° không bị gọi là cúi đầu; cúi rõ hơn vẫn được báo. Các ngưỡng này là thay đổi nhỏ để người thử xác nhận, chưa phải ngưỡng đã được xác nhận cho mọi vị trí lắp.

## Hướng cúi/ngửa đã sửa trong v1.5

Hai ảnh thử trên camera trước cho bằng chứng trực tiếp: ngửa đầu thật hiện PITCH -35,3° nhưng app gọi “Đầu cúi xuống”; cúi đầu thật hiện PITCH +43,6° nhưng app gọi “Đầu ngửa lên”. Dấu pitch ở phần trích xuất đã được đảo để quy ước mới là cúi âm, ngửa dương. Khi miệng mở và mặt vẫn hướng gần chính diện, v1.5 cho phép pitch dao động từ -18° đến +20° trước khi gọi là lệch đầu; khi miệng đóng vẫn giữ -12°/+14°. Cúi/ngửa rõ rệt vẫn được cảnh báo. Cần thử lại trên điện thoại; nếu thay nguồn camera hay hướng xoay ảnh thì phải xác nhận lại dấu.
## Giới hạn hiện tại

- Chưa có MobileNetV3 hoặc nhận diện kính râm tự động.
- Các ngưỡng EAR/MAR/góc đầu và dấu của góc Euler cần xác nhận trên điện thoại lắp đúng vị trí.
- Một khuôn mặt được theo dõi; chưa xác minh đó chắc chắn là người cầm lái.
- Log trên Redmi K40S từng có khoảng cách giữa kết quả vượt 150 ms; vì vậy cảnh báo mắt dùng nhiều quan sát mắt đóng trong cửa sổ 1,2 giây, yêu cầu ít nhất 5 mẫu và độ trải 700 ms. Khoảng trống không được tự tính là mắt nhắm. PERCLOS vẫn chỉ cộng thời gian giữa các mẫu cách nhau tối đa 150 ms.
- Phân tích giới hạn khoảng 12,5 frame/giây và ưu tiên camera 640×480; FPS AI và thời gian từ lúc gửi ảnh tới kết quả được hiển thị. Đây không phải độ trễ toàn phần từ lúc camera chụp.
- Chưa đo tỷ lệ báo nhầm/bỏ sót hoặc độ trễ toàn phần trên xe thật. Cần bài thử có nhãn trước khi đánh giá cảnh báo mắt.
- Không dùng ứng dụng thử nghiệm này như thiết bị an toàn đã được kiểm định.

Bộ unit test cho logic nằm ở `app/src/test/java/com/example/dms_appdetect/DmsDecisionEngineTest.java`. Gradle unit test và debug build đã chạy; app đã được cài trên Redmi K40S. Bài thử mắt có nhãn trên Redmi K40S đạt: trung vị mắt mở 0,315, mắt nhắm 0,050, 29 mẫu ổn định mỗi nhóm, ngưỡng cá nhân 0,248. Cần kiểm tra riêng cảnh báo khi giám sát thật và các điều kiện ánh sáng/góc lắp khác.
