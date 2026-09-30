# Hướng Dẫn & Tài Liệu Kỹ Thuật Hệ Thống DMS (Driver Monitoring System v2)

---

## 1. Tổng Quan Dự Án

Ứng dụng **DMS (Driver Monitoring System v2)** là giải pháp giám sát trạng thái tài xế theo thời gian thực trên Android, ứng dụng thị giác máy tính cục bộ (**Edge AI - MediaPipe Face Landmarker**) không phụ thuộc kết nối mạng/máy chủ.

Phiên bản **v2** được nâng cấp toàn diện dựa trên đặc tả `Risk-score-v2.md`:
* **Điểm rủi ro động $R(t)$**: Tích hợp các hệ số bối cảnh $K_{\text{bối cảnh}}$ (Vận tốc, Khung giờ sinh học, Thời gian lái liên tục).
* **Cơ chế Giữ nhiệt & Hạ nhiệt (Hold Time & Cooldown Decay)**: Điểm số không giảm tức thì mà duy trì cảnh giác, sau đó hạ nhiệt đều đặn khi tài xế tập trung lái xe an toàn.
* **Bảng điều khiển hiệu chỉnh (Tester Tuning Panel)**: Cho phép tester linh hoạt điều chỉnh trực tiếp các tham số trên màn hình hoặc thu gọn xem trọn vẹn khuôn mặt.
* **Chế độ Thử nghiệm 1 Phút (1-Minute Fleet Safety Test)**: Tự động đánh giá, tổng kết vi phạm và xếp hạng tài xế (Rank A / B / C / D) theo tiêu chuẩn quản lý đội xe.

---

## 2. Kiến Trúc Phân Tầng Decoupled (SOLID & Clean Architecture)

Hệ thống được tái cấu trúc thành các module độc lập, tách biệt trách nhiệm:

```
app/src/main/java/com/example/dms_appdetect/
├── camera/                  # Module quản lý CameraX
│   └── DmsCameraManager.java      # Lifecycle camera, bind Surface, tính độ rọi Luma
├── vision/                  # Module thị giác máy tính AI
│   └── FaceLandmarkDetector.java  # Xử lý frame bất đồng bộ, trích xuất EAR, MAR, Euler Angles
├── calibration/             # Module hiệu chuẩn mắt cá nhân hóa
│   └── EyeCalibrator.java         # Test 11s (3s chuẩn bị, 4s mở mắt, 4s nhắm mắt với tiếng bíp)
├── risk/                    # Module tính toán & quyết định điểm rủi ro v2
│   ├── RiskConfig.java            # Cấu hình ngưỡng vi phạm & hệ số bối cảnh K
│   ├── RiskDecision.java          # Trạng thái rủi ro, phân cấp cảnh báo (NONE, NOTICE, WARNING, CRITICAL)
│   ├── RiskEngine.java            # Tính toán R(t), Hold Time, Cooldown, Fleet Safety Scorecard
│   └── OneMinuteTestSession.java  # Phiên test 60 giây, thread-safe UI, hiển thị Dialog xếp loại
├── tuning/                  # Module bảng điều khiển dành cho tester
│   └── TuningController.java      # Presets, Sliders, Cooldown multipliers, ẩn/hiện bảng điều khiển
├── overlay/                 # Module vẽ giao diện trực quan
│   └── FaceOverlayView.java       # Vẽ lưới điểm khuôn mặt (Face Mesh), bounding box, chỉ số
├── AlertPlayer.java         # Âm thanh cảnh báo (tự động ngắt tiếng ngay khi mắt mở / nhìn thẳng)
└── MainActivity.java        # Orchestrator kết nối các module với View
```

---

## 3. Công Thức Tính Điểm Rủi Ro Động $R(t)$

### 3.1. Công thức cốt lõi
$$R(t) = \min\left(100, \sum \text{BasePoints}_i \times K_{\text{bối cảnh}}\right)$$

Trong đó:
* **$\text{BasePoints}$**:
  * Nhắm mắt vi phạm: `+25` điểm
  * Mất tập trung (quay đầu / nhìn lệch hướng): `+15` điểm
  * Ngáp / mệt mỏi: `+10` điểm
  * Gục đầu: `+20` điểm
* **$K_{\text{bối cảnh}} = K_{\text{vận tốc}} \times K_{\text{khung giờ}} \times K_{\text{thời gian lái}}$**:

| Hệ số | Mức cấu hình | Giá trị |
| :--- | :--- | :--- |
| **$K_{\text{vận tốc}}$** | Xe dừng ($0$ km/h)<br>Đô thị ($1-50$ km/h)<br>Quốc lộ ($51-80$ km/h)<br>Cao tốc ($>80$ km/h) | `0.5`<br>`1.0`<br>`1.2`<br>`1.5` |
| **$K_{\text{khung giờ}}$** | Ban ngày (08:00 - 12:00, 14:00 - 18:00)<br>Buổi trưa (12:00 - 14:00)<br>Buổi tối (18:00 - 22:00)<br>Ban đêm (22:00 - 06:00) | `1.0`<br>`1.2`<br>`1.1`<br>`1.5` |
| **$K_{\text{thời gian lái}}$** | Dưới 2 giờ liên tục<br>Từ 2 đến 4 giờ liên tục<br>Trên 4 giờ liên tục (luật GTĐB cấm) | `1.0`<br>`1.3`<br>`1.8` |

### 3.2. Cơ chế Hold Time & Cooldown Decay
* **Thời gian giữ nhiệt (Hold Time)**: Khi phát sinh vi phạm mới, điểm $R(t)$ được giữ nguyên trong khoảng thời gian quy định (mặc định thử nghiệm là **10 giây**, có thể kéo dài lên **30 giây**) nhằm duy trì trạng thái cảnh giác, không để điểm giảm ảo khi tài xế vừa mở mắt chớp nhoáng.
* **Thời gian hạ nhiệt (Cooldown Decay)**: Sau khi hết Hold Time mà không phát sinh thêm vi phạm:
  $$\text{Tốc độ giảm cơ bản} = 0.4 \text{ điểm/giây} \quad (\approx 4 \text{ điểm mỗi 10 giây})$$
* **Hỗ trợ tester**: Trên giao diện có nút x1, x3, x5 tốc độ hạ nhiệt để tester kiểm tra luồng giảm điểm nhanh chóng mà không cần chờ đợi lâu.

### 3.3. Phân Cấp Mức Độ Cảnh Báo
* **$R(t) < 30$** $\rightarrow$ **AN TOÀN (NONE)**: Thanh chỉ số màu xanh, không phát âm thanh.
* **$30 \le R(t) < 60$** $\rightarrow$ **CHÚ Ý (NOTICE)**: Thanh chỉ số màu vàng, âm nhắc nhở nhẹ.
* **$60 \le R(t) < 85$** $\rightarrow$ **CẢNH BÁO (WARNING)**: Thanh chỉ số màu cam, chuông cảnh báo bíp lặp lại.
* **$R(t) \ge 85$** $\rightarrow$ **NGUY HIỂM (CRITICAL)**: Thanh chỉ số màu đỏ thẫm chớp nháy, còi báo động khẩn cấp.
> **Lưu ý an toàn trải nghiệm**: Ngay khi tài xế mở mắt hoặc nhìn thẳng trở lại, còi cảnh báo sẽ **lập tức ngắt tiếng** để không gây hoảng loạn.

---

## 4. Bảng Điểm Đánh Giá An Toàn Đội Xe (Fleet Safety Score)

Trong chế độ thử nghiệm 60 giây (**1-Minute Test Mode**):
$$\text{Safety Score} = \max\left(0, 100 - \left(N_{\text{Critical}} \times 20 + N_{\text{Warning}} \times 10 + N_{\text{Notice}} \times 5\right)\right)$$

| Điểm An Toàn | Hạng | Đánh giá |
| :---: | :---: | :--- |
| **$\ge 90$** | **Hạng A** | Lái xe rất an toàn, độ tập trung cao. |
| **$75 - 89$** | **Hạng B** | Đạt yêu cầu, có một số vi phạm nhẹ. |
| **$60 - 74$** | **Hạng C** | Cảnh báo: Tần suất mệt mỏi / lơ là đáng lưu ý. |
| **$< 60$** | **Hạng D** | Nguy cơ cao: Khuyến nghị dừng xe nghỉ ngơi ngay lập tức. |

---

## 5. Hướng Dẫn Sử Dụng Dành Cho Tester

### 5.1. Khởi động và Hiệu Chuẩn Mắt
1. Cài đặt APK lên thiết bị Android (Yêu cầu Android 7.0 / SDK 24 trở lên, khuyên dùng Android 10+).
2. Mở ứng dụng và cấp quyền truy cập Camera.
3. Bấm **"Kiểm tra mắt 11 giây"**:
   - Nhìn thẳng vào camera: 3 giây chuẩn bị.
   - Nghe 1 tiếng bíp: Giữ mắt mở tự nhiên trong 4 giây.
   - Nghe 2 tiếng bíp: Nhắm mắt trong 4 giây.
   - App tự động tính toán ngưỡng EAR cá nhân phù hợp với góc lắp camera và mắt của bạn.
4. Bấm **"Bắt đầu giám sát"** để kích hoạt hệ thống phát hiện.

### 5.2. Sử Dụng Bảng Điều Khiển (Tuning Panel)
* **Presets Khung Giờ & Tốc Độ**: Nhấn nút nhanh `Sáng (1.0x)`, `Trưa (1.2x)`, `Đêm (1.5x)`, `Phố (1.0x)`, `Cao tốc (1.5x)` để đổi nhanh bối cảnh mà không cần nhập liệu.
* **Thời gian giữ nhiệt (Hold Time)**: Kéo slider từ 3s đến 30s. Bạn sẽ thấy dòng trạng thái:
  - `⏳ Giữ nhiệt: còn Xs (chưa giảm)` khi vừa vi phạm.
  - `❄️ Đang hạ nhiệt (-Xđ/10s)` khi tài xế nhìn thẳng an toàn.
* **Tốc độ hạ nhiệt**: Bấm `1x`, `3x`, `5x` để tua nhanh quá trình cooldown.
* **Ẩn / Hiện Bảng Điều Khiển**: Nhấn nút `▼ Ẩn bảng điều khiển (Xem full mặt)` để toàn bộ bảng cài đặt thu nhỏ xuống đáy, giúp tester quan sát toàn bộ khuôn mặt trên camera góc rộng. Nhấn lại `▲ Mở bảng điều khiển` khi muốn tinh chỉnh.
* **Bật / Tắt Face Mesh**: Nút `Bật/Tắt Face Mesh` cho phép ẩn lưới 468 điểm mốc để quan sát tự nhiên.

### 5.3. Thử Nghiệm Bài Test 1 Phút
* Nhấn nút **"Bắt đầu Test 1 Phút"**.
* Nút sẽ đếm ngược: `⏳ Đang test: 59s...` $\rightarrow$ `0s`.
* Khi hết 60 giây, ứng dụng tự động hiển thị Hộp thoại Báo Cáo An Toàn:
  - Tổng số lần vi phạm Notice, Warning, Critical.
  - Điểm rủi ro cao nhất đạt tới ($R_{\max}$).
  - Điểm an toàn tổng kết (Safety Score / 100) và Xếp loại tài xế (Rank A/B/C/D).

---

## 6. Hướng Dẫn Build & Triển Khai (Build & Deploy)

### 6.1. File APK Đã Build Sẵn
- Đường dẫn: [`app/build/outputs/apk/debug/app-debug.apk`](file:///c:/Users/Admin/Desktop/buildphase/app-DEV02-DMS/app/build/outputs/apk/debug/app-debug.apk)
- Dung lượng: ~58 MB (đã tích hợp trọn vẹn model AI `face_landmarker.task`).

### 6.2. Các Lệnh Thao Tác Nhanh (Windows PowerShell)

* **Build lại APK Debug:**
  ```powershell
  .\gradlew.bat assembleDebug
  ```

* **Chạy toàn bộ 23 Unit Tests (Kiểm tra công thức toán & logic rủi ro):**
  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```

* **Cài đặt APK trực tiếp lên điện thoại qua ADB:**
  ```powershell
  & "C:\Users\Admin\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk
  ```

* **Mở thư mục chứa file APK trong Windows Explorer:**
  ```powershell
  explorer.exe app\build\outputs\apk\debug
  ```

---
*Tài liệu được cập nhật tự động đồng bộ cùng hệ thống `app-DEV02-DMS`.*
