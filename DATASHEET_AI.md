# NHẬT KÝ PROMPT ENGINEERING & BẢNG DỮ LIỆU AI (AI DATASHEET)
**Học phần:** Lập trình Web - Học kỳ 1, Năm học 2025 - 2026  
**Đề tài:** Chuỗi Cửa Hàng Bán Lẻ Thiết Bị Điện Tử TechChain (ESC.STORE)  
**Nhóm thực hiện:** Nhóm 24 - Lớp: 24110CL1A  
**Giảng viên hướng dẫn:** ThS. Nguyễn Hữu Trung  

---

## 1. Giới thiệu tổng quan về việc ứng dụng AI
Trong khuôn khổ đồ án môn học, Nhóm 24 đã chủ động ứng dụng Trợ lý Trí tuệ Nhân tạo (Google Gemini / Antigravity) làm công cụ hỗ trợ (Pair-Programming Partner) xuyên suốt các giai đoạn:
1. **Thiết kế kiến trúc hệ thống & chuẩn hóa CSDL**: Tối ưu hóa mô hình 34 bảng quan hệ đạt chuẩn 3NF.
2. **Hiện thực hóa các thuật toán nghiệp vụ phức tạp**: Định tuyến đơn hàng đa chi nhánh (`SmartOrderRoutingService`) và Khóa bi quan chống bán âm kho (`StockTransferServiceImpl`).
3. **Phát triển bộ kiểm thử tự động toàn diện**: Sinh 256 bài kiểm thử (211 Java tests + 45 JavaScript tests) đạt tỷ lệ 100% Green.
4. **Thiết kế trải nghiệm giao diện người dùng (UI/UX)**: Sân khấu 3D Rolodex xoay sản phẩm trực quan bằng CSS 3D Transforms và Dải Banner Carousel tỉ lệ chuẩn 1320x380 px.
5. **Sinh tài nguyên đồ họa tiếp thị**: Tạo hình ảnh sản phẩm banner tiếp thị chiến dịch công nghệ.

---

## 2. Bảng Danh Mục Prompt AI Mẫu & Kết Quả Sinh Mã

| STT | Phân Loại & Mục Đích | Nội Dung Prompt Chi Tiết (Input Prompt) | Mô Hình AI | Kết Quả Sinh Ra & Tích Hợp Vào Codebase |
|:---:|:---|:---|:---:|:---|
| **01** | **Thiết kế CSDL 34 Bảng (Data Architecture)** | *"Thiết kế mô hình dữ liệu chuẩn 3NF cho website thương mại điện tử chuỗi bán lẻ thiết bị công nghệ TechChain với 4 chi nhánh tại TP.HCM. Cần quản lý: người dùng (5 role: Admin, Staff, Shipper, Customer, Guest), sản phẩm với nhiều biến thể (SKU, màu sắc, dung lượng), tồn kho riêng biệt theo chi nhánh, đơn hàng đa trạng thái, coupon giảm giá, sổ địa chỉ chuẩn 3 cấp Việt Nam và nhật ký kiểm toán giá vốn COGS. Viết script DDL chuẩn T-SQL cho Microsoft SQL Server 2022."* | Gemini 1.5 Pro | Script `database.sql` gồm 34 bảng quan hệ, các ràng buộc toàn vẹn khóa ngoại `ON DELETE NO ACTION`, chỉ mục đánh trên các khóa tra cứu thường xuyên (`order_id`, `sku_id`, `branch_id`). |
| **02** | **Thuật toán Định tuyến & Khóa bi quan** | *"Viết Spring Boot 3 Service thực hiện nghiệp vụ điều chuyển kho giữa 2 chi nhánh `StockTransferService`. Yêu cầu sử dụng JPA `LockModeType.PESSIMISTIC_WRITE` để khóa bản ghi inventory nguồn và đích, xử lý tính toán trong một `@Transactional` để ngăn chặn hiện tượng Race Condition âm tồn kho khi có nhiều yêu cầu điều chuyển đồng thời. Viết kèm Unit Test mô phỏng 10 luồng đồng thời bằng `ExecutorService`."* | Gemini 1.5 Pro | `StockTransferServiceImpl.java` với cơ chế khóa hàng độc quyền (tương đương `SELECT ... WITH (UPDLOCK, ROWLOCK)` trong SQL Server), xử lý an toàn 100% trong môi trường đa luồng. |
| **03** | **Kiểm thử tự động (Automated Test Suites)** | *"Xây dựng bộ kiểm thử JUnit 5 + MockMvc kiểm tra toàn diện chức năng đăng ký tài khoản có xác thực OTP (`RegistrationOtpTest`). Bao gồm các ca kiểm thử: gửi OTP lần 1 thành công, gửi lại trong 60s bị chặn (cooldown 429), nhập sai mã OTP 3 lần bị khóa, OTP quá hạn 5 phút bị từ chối, và nhập đúng OTP thì kích hoạt tài khoản thành công."* | Gemini 1.5 Flash | 18 Test cases JUnit 5 kiểm thử xác thực OTP, bảo vệ hệ thống khỏi tấn công Brute-force và Spam Mail. |
| **04** | **Sân khấu 3D Rolodex Carousel** | *"Viết đoạn mã CSS 3D Transforms và JavaScript thuần tạo hiệu ứng Sân khấu xoay Rolodex Carousel cho các sản phẩm công nghệ nổi bật ở trang chủ. Hiệu ứng cần hỗ trợ chuột lăn (mouse wheel), tự động xoay khi idle, tương thích mượt mà trên nền tảng Bootstrap 5.3 Dark Mode và responsive tốt trên điện thoại di động."* | Gemini 1.5 Pro | Module `src/main/resources/static/js/rolodex-carousel.js` và CSS 3D phối cảnh (`perspective: 1200px`), xoay thẻ sản phẩm mượt mà đạt chuẩn 60 FPS. |
| **05** | **Sinh Banner Tiếp thị (Image Gen)** | *"A high-tech sleek horizontal promotional banner for electronics store, showcasing modern MacBook Pro, iPhone 16 Pro Max, and gaming accessories on a minimalist dark metallic surface with cyan and amber neon edge lighting, ultra-realistic, 8k resolution, aspect ratio 1320:380, clean commercial advertising style --ar 1320:380 --v 6.0"* | Midjourney v6 / Gemini Imagen | Bộ ảnh banner chất lượng cao tỉ lệ 1320x380 px đặt tại trang chủ `src/main/resources/static/images/banners/`. |
| **06** | **Thống kê Báo cáo Lợi nhuận P&L (Chart.js)** | *"Viết đoạn mã Spring Data JPA Repository và Chart.js để vẽ biểu đồ tròn phân bố 10 trạng thái đơn hàng (Đơn mới, Đã xác nhận, Đang chuẩn bị hàng, Đang giao, Đã giao, Hủy, Trả hàng/Hoàn tiền...) và biểu đồ cột doanh thu thuần so với giá vốn hàng bán (COGS) theo từng tháng. Tách rời legend và canvas để không bị co giật khi responsive."* | Gemini 1.5 Pro | `admin-dashboard.js` & `DashboardService.java` xử lý gom nhóm dữ liệu theo tháng, tách rời legend và canvas HTML5 chuẩn Chart.js v4. |

---

## 3. Quy trình Kiểm duyệt & Tích hợp Mã nguồn của Nhóm
Toàn bộ mã nguồn và tài nguyên do AI đề xuất đều trải qua quy trình 4 bước thẩm định nghiêm ngặt của các thành viên trong Nhóm 24 trước khi được commit vào Git:
1. **Kiểm tra cú pháp & cấu trúc**: Rà soát tính tương thích với Java 17 LTS và Spring Boot 3.3.4.
2. **Kiểm thử tự động**: Chạy toàn bộ 256 bài automated test (`mvn test` và `node test`), đảm bảo không có bất kỳ regression bug nào.
3. **Thử nghiệm trên trình duyệt**: Kiểm tra giao diện trên nhiều độ phân giải màn hình (Desktop 1920x1080, Laptop 1366x768, Mobile 375x812).
4. **Đánh giá Code Review**: Nhóm trưởng và thành viên phụ trách mảng chức năng duyệt Pull Request trước khi merge vào nhánh `dev` và `main`.
