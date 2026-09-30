### 4.3. Ngưỡng phát hiện ban đầu

| Tín hiệu | Ngưỡng MVP | Hành động | Ghi chú (Đo khi nhìn thẳng - Cần hiệu chỉnh khi đặt cam bên phải) |
| :--- | :--- | :--- | :--- |
| **Mắt nhắm liên tục** | $\ge \mathbf{\color{red}1,2\text{ giây}}$ | Cảnh báo local | Đo khi nhìn thẳng. Cam đặt bên phải làm mắt gần to hơn mắt xa; cần hiệu chỉnh lại ngưỡng EAR cá nhân theo góc nhìn nghiêng trước khi áp mốc thời gian này. |
| **Quay đầu khỏi hướng đường** | $\|\text{yaw}\| \ge \mathbf{\color{red}30^\circ}$ trong $\ge \mathbf{\color{red}2\text{ giây}}$ | Cảnh báo local | Đo khi nhìn thẳng vào cam ($0^\circ$). Cam đặt bên phải khiến tài xế nhìn thẳng đường đã có $\text{yaw}$ lệch sẵn $\mathbf{\color{red}15^\circ - 30^\circ}$; bắt buộc phải bù trừ góc lệch tĩnh ($\text{yaw}_{\text{raw}} - \text{yaw}_{\text{offset}}$) để tránh báo sai liên tục. |
| **Gục đầu** | Pitch lệch baseline $\ge \mathbf{\color{red}20^\circ}$ trong $\ge \mathbf{\color{red}1,5\text{ giây}}$ | Cảnh báo local | Đo khi nhìn thẳng. Giá đỡ cam trên taplo bên phải thường hơi ngửa lên; baseline pitch phải lấy mẫu thực tế trên xe thay vì cố định $0^\circ$. |
| **PERCLOS nghi ngờ** | $\ge \mathbf{\color{red}12,5\%}$ trên cửa sổ $\mathbf{\color{red}3\text{ phút}}$ | Tăng risk score | Đo khi nhìn thẳng. PERCLOS phụ thuộc vào EAR; cần chuẩn hóa ngưỡng EAR theo góc nhìn chéo để tránh tăng điểm rủi ro ảo do mắt xa cam bị nhận diện nhầm là nhắm. |
| **PERCLOS cao** | $\ge \mathbf{\color{red}25\%}$ trên cửa sổ $\mathbf{\color{red}3\text{ phút}}$ | Cảnh báo mạnh | Đo khi nhìn thẳng. Cần ngưỡng nhắm mắt đã bù sai số góc nghiêng và đảm bảo đủ độ bao phủ mẫu hợp lệ (valid coverage) trên cửa sổ 3 phút trước khi kích hoạt cảnh báo mạnh. |

*(Ghi chú: Các số liệu in **màu đỏ** là thông số đo ở điều kiện chuẩn mặt nhìn thẳng, cần hiệu chỉnh lại khi đặt camera hồng ngoại ở bên phải tài xế).*
