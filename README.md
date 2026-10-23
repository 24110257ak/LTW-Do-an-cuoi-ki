# 📱 ĐỒ ÁN CUỐI KỲ: HỆ THỐNG WEBSITE BÁN HÀNG THEO MÔ HÌNH CHUỖI CỬA HÀNG THIẾT BỊ ĐIỆN TỬ

> **Môn học:** Lập trình WEB  
> **Trường Đại học:** Đại học Công nghệ Kỹ thuật Thành phố Hồ Chí Minh  
> **Khoa:** Công nghệ Thông tin  
> **Thời gian báo cáo:** 7h00 - 18h00 ngày 31/10/2026 tại phòng A5-203  
> **Số thứ tự nhóm:** `Nhóm 24`  
> **Mã đề tài:** `27`  
> **Đề tài:** **Xây dựng website bán hàng theo mô hình chuỗi cửa hàng thiết bị điện tử bằng Spring Boot + Thymeleaf + Bootstrap + JPA + SQLServer + WebSocket + JWT + Cloudinary**  
> **Repository GitHub:** [https://github.com/24110257ak/LTW-Do-an-cuoi-ki.git](https://github.com/24110257ak/LTW-Do-an-cuoi-ki.git)  

---

## 👥 1. THÀNH VIÊN THỰC HIỆN & PHÂN CÔNG NHIỆM VỤ (NHÓM 24)

*(Phần phân công nhiệm vụ chi tiết sẽ được cập nhật và hoàn thiện theo tiến độ thực tế)*

| STT | Mã sinh viên | Họ và tên | Vai trò | Phân công Module |
| :---: | :---: | :--- | :---: | :--- |
| **1** | `24110257` | **Trần Vũ Anh Khoa** | Thành viên | *(Cập nhật sau)* |
| **2** | `24110292` | **Mai Huy Nhật** | **Nhóm trưởng** | *(Cập nhật sau)* |
| **3** | `24110304` | **Đặng Nhật Phúc** | Thành viên | *(Cập nhật sau)* |

---

## 🎯 2. TIÊU CHÍ ĐÁNH GIÁ ĐỒ ÁN (THANG ĐIỂM 10)

| Tiêu chuẩn đánh giá | Trọng số | Nội dung đáp ứng của Đồ án Nhóm 24 |
| :--- | :---: | :--- |
| **1. Quản lý Git & Phân công nhiệm vụ** | **1.0 đ** | Phân công nhiệm vụ rành mạch, tuân thủ Git Flow chuẩn (`main`, `dev`, các nhánh `feature/*`), lịch sử commit minh bạch. |
| **2. Cấu trúc Project đúng chuẩn 3 tầng** | **0.5 đ** | Chuẩn Spring Boot 3-tier: Controller (Web & API) - Service - Repository - Entity/DTO, phân chia package mạch lạc. |
| **3. Nghiệp vụ, Logic, Validation & Security** | **6.0 đ** | Đầy đủ 5 roles (Guest, User, Vendor/Store Manager, Admin, Shipper), xử lý validation đầy đủ (`jakarta.validation`), bảo mật Spring Security 6 + OTP kích hoạt qua Email/Terminal. |
| **4. Giao diện Đẹp, Thân thiện & Responsive** | **1.0 đ** | Template Bootstrap 5.3 hiện đại, tối ưu di động, máy tính bảng và desktop, trải nghiệm mượt mà. |
| **5. Tính năng Sáng tạo & Nâng cao** | **1.0 đ** | - Xác thực bảo mật kép với **JWT**.<br>- **WebSocket STOMP** thông báo đơn hàng realtime và Livechat tư vấn.<br>- Tích hợp lưu trữ CDN **Cloudinary**.<br>- Thanh toán trực tuyến **VNPAY Sandbox**.<br>- Hệ thống Quản lý **Chuỗi cửa hàng & Tồn kho đa chi nhánh (Multi-branch Inventory)**.<br>- **Điều chuyển kho liên chi nhánh** với quy trình duyệt & khóa chống Deadlock.<br>- **Sổ nhật ký kiểm toán tồn kho (Audit Trail)** & Chống đặt trùng đơn (**Checkout Idempotency**).<br>- **Kiểm thử tự động (Automated Testing)** đạt chuẩn với JUnit 5 & H2 Database. |
| **6. Khả năng Báo cáo & Trả lời phản biện** | **0.5 đ** | Báo cáo chi tiết, video demo rõ mặt 3 thành viên, nắm vững source code và kiến trúc. |

---

## 🏛️ 3. KIẾN TRÚC HỆ THỐNG & CÔNG NGHỆ BẮT BUỘC

### 3.1. Sơ đồ Kiến trúc 3 Tầng (3-Tier Architecture)

```mermaid
graph TD
    subgraph Presentation_Layer["TẦNG TRÌNH DIỄN (PRESENTATION TIER)"]
        UI_Web["Thymeleaf Views + Bootstrap 5 + SockJS/Stomp Client"]
        API_Client["REST Client / Mobile App (JWT Bearer Token)"]
    end

    subgraph Controller_Security_Layer["TẦNG BẢO MẬT & ĐIỀU KHIỂN"]
        Security["Spring Security 6 (BCrypt, Session & JWT Filter)"]
        Web_Controller["Spring MVC Controllers (@Controller)"]
        API_Controller["REST Controllers (@RestController)"]
        WS_Controller["WebSocket STOMP Message Handler"]
    end

    subgraph Service_Business_Layer["TẦNG NGHIỆP VỤ (SERVICE / BUSINESS TIER)"]
        Auth_Service["Auth & Email OTP Service"]
        Product_Service["Product & Branch Inventory Service"]
        Order_Service["Order & Checkout & VNPAY Service"]
        Media_Service["Cloudinary Media Storage Service"]
        Chat_Service["Realtime Live Chat & Notification Service"]
    end

    subgraph Data_Access_Layer["TẦNG TRUY XUẤT DỮ LIỆU (DATA ACCESS TIER)"]
        Spring_Data_JPA["Spring Data JPA Repositories"]
        Hibernate_ORM["Hibernate ORM Engine"]
        SQL_Server[("Microsoft SQL Server")]
        Cloudinary_Cloud[("Cloudinary Cloud Media CDN")]
    end

    UI_Web --> Security
    API_Client --> Security
    Security --> Web_Controller
    Security --> API_Controller
    Security --> WS_Controller

    Web_Controller --> Service_Business_Layer
    API_Controller --> Service_Business_Layer
    WS_Controller --> Service_Business_Layer

    Service_Business_Layer --> Spring_Data_JPA
    Service_Business_Layer --> Cloudinary_Cloud
    Spring_Data_JPA --> Hibernate_ORM
    Hibernate_ORM --> SQL_Server
```

### 3.2. Ngăn xếp công nghệ (Technology Stack)
- **Backend:** Java 17 LTS, Spring Boot 3.3.4
  - `spring-boot-starter-web`
  - `spring-boot-starter-data-jpa`
  - `spring-boot-starter-security`
  - `spring-boot-starter-validation`
  - `spring-boot-starter-mail` (Gửi mã OTP xác thực đăng ký / đổi mật khẩu + Terminal fallback mode)
  - `spring-boot-starter-websocket` (STOMP Protocol Realtime)
  - `com.cloudinary:cloudinary-http44:1.38.0` (Lưu trữ ảnh mây)
  - `io.jsonwebtoken:jjwt` (JWT Token)
- **Database:** Microsoft SQL Server 2019/2022 + SSMS (27 bảng quan hệ, collation `Vietnamese_CI_AS`)
- **Frontend:** Thymeleaf 3, Bootstrap 5.3, Bootstrap Icons, FontAwesome 6, Chart.js, SockJS, STOMP.js
- **Build tool:** Apache Maven

---

## 🏬 4. CHI TIẾT TÍNH NĂNG THEO TỪNG VAI TRÒ (ROLES)

### 4.1. Khách vãng lai (Guest)
- Xem trang chủ với danh sách sản phẩm nổi bật, sản phẩm bán chạy (>10 sản phẩm sắp xếp giảm dần).
- Tìm kiếm từ khóa theo tên máy, hãng sản xuất (Apple, Samsung, Asus, Dell, Sony,...).
- Lọc sản phẩm theo danh mục, khoảng giá, trạng thái còn hàng tại các chi nhánh.
- Xem chi tiết sản phẩm: hình ảnh, thông số kỹ thuật, giá theo phiên bản (ROM/RAM/Màu), các chi nhánh cửa hàng đang còn hàng sẵn.
- Đăng ký tài khoản người dùng mới: Nhập thông tin -> Hệ thống tự động phát sinh **mã OTP và gửi qua Email / Terminal** -> Xác thực OTP kích hoạt tài khoản.
- Đăng nhập (Session Web hoặc API cấp JWT).
- Quên mật khẩu: Nhập email -> Nhận mã OTP qua Email / Terminal -> Đặt lại mật khẩu mới được mã hóa BCrypt.

### 4.2. Khách hàng đã đăng nhập (User / Customer)
- **Trang cá nhân (Profile):** Quản lý thông tin, đổi ảnh đại diện (Cloudinary), đổi mật khẩu, **sổ địa chỉ nhận hàng đa điểm** (địa chỉ nhà, công ty, ghi chú).
- **Trang mua sắm & Khám phá:** Phân trang 20 sản phẩm/trang theo tiêu chí (Mới nhất, Bán chạy nhất, Đánh giá cao nhất, Yêu thích nhất).
- **Yêu thích & Đã xem:** Thêm sản phẩm vào danh sách Yêu thích (Wishlist), lưu lịch sử Sản phẩm đã xem gần đây.
- **Giỏ hàng lưu trữ Database:** Giỏ hàng đồng bộ trên tài khoản, chọn số lượng, chọn biến thể, tự động tính tổng tiền.
- **Mã giảm giá (Coupon / Voucher):** Nhập mã khuyến mãi để được trừ trực tiếp số tiền hoặc % đơn hàng.
- **Đặt hàng linh hoạt (Checkout):**
  - *Hình thức 1:* **Giao hàng tận nhà** (Chọn địa chỉ nhận hàng, chọn phương thức vận chuyển).
  - *Hình thức 2:* **Nhận tại chi nhánh (Store Pickup)** (Chọn chi nhánh cửa hàng thuận tiện nhất để ra nhận máy).
  - *Phương thức thanh toán:* COD (Thanh toán khi nhận hàng) hoặc VNPAY Sandbox (Cổng thanh toán thẻ ATM / QR code).
- **Lịch sử đơn hàng (Order History):** Theo dõi đơn hàng theo 6 trạng thái chuẩn:
  1. `NEW` (Đơn hàng mới)
  2. `CONFIRMED` (Đã xác nhận)
  3. `SHIPPING` (Đang giao hàng)
  4. `DELIVERED` (Đã giao thành công)
  5. `CANCELLED` (Đã hủy)
  6. `RETURNED_REFUND` (Trả hàng - Hoàn tiền)
- **Đánh giá & Bình luận sản phẩm:** Khách hàng chỉ được đánh giá các sản phẩm đã mua thành công; bình luận hỗ trợ nhập nội dung chi tiết (tối thiểu 50 ký tự) kèm tải ảnh thực tế (Cloudinary).
- **Live Chat tư vấn:** Khung chat trực tuyến thời gian thực (WebSocket) trò chuyện với tư vấn viên chi nhánh.

### 4.3. Quản lý Chi nhánh / Cửa hàng (Staff / Store Manager)
- Đăng nhập vào trang quản trị Chi nhánh chuyên biệt (`/staff/**`).
- **Quản lý Kho hàng Chi nhánh (Branch Stock Management):** Nhập và điều chỉnh số lượng tồn kho từng sản phẩm/biến thể trực tiếp tại quầy.
- **Xử lý Đơn hàng Chi nhánh:**
  - Tiếp nhận đơn hàng giao từ chi nhánh hoặc đơn khách hẹn nhận tại chi nhánh.
  - Cập nhật trạng thái: Đơn mới -> Xác nhận -> Đang giao -> Đã giao / Hủy đơn.
  - Nhận thông báo **Realtime (WebSocket STOMP)** chuông và toast nổi tức thì ngay khi có khách đặt đơn mới tại chi nhánh.
- **Chuyển đổi Chi nhánh làm việc:** Nhân viên có thể linh hoạt chuyển chi nhánh để kiểm tra tồn kho và đơn hàng tương ứng.
- **Live Chat Support:** Tiếp nhận tin nhắn tư vấn từ khách hàng và phản hồi trực tiếp thời gian thực.

### 4.4. Quản trị viên Toàn chuỗi (Admin / HQ Manager)
- **Dashboard Thống kê Toàn diện (`/admin/dashboard`):** Thẻ KPI tổng hợp, biểu đồ doanh thu theo chi nhánh (Chart.js bar chart) và biểu đồ cơ cấu trạng thái đơn hàng (donut chart).
- **Quản lý Sản phẩm chung (`/admin/products`):** CRUD sản phẩm, thông số kỹ thuật, cấu hình biến thể (ROM, RAM, Màu sắc), tải ảnh sản phẩm lên Cloudinary CDN.
- **Phân bổ Tồn kho Toàn chuỗi:** Thiết lập số lượng tồn kho ban đầu cho từng biến thể tại 4 chi nhánh cửa hàng.
- **Quản lý Danh mục & Thương hiệu:** CRUD danh mục phân cấp và hãng sản xuất.
- **Quản lý Tài khoản (User Management):** Xem danh sách, kích hoạt/khóa tài khoản, chuyển đổi quyền hạn (CUSTOMER, STAFF, SHIPPER, ADMIN).
- **Quản lý Mã giảm giá (Coupons):** Tạo và quản lý voucher khuyến mãi toàn hệ thống.
- **Quản lý Đơn vị Vận chuyển:** Cấu hình các nhà vận chuyển và bảng phí giao hàng.
- **Quản lý Banner Quảng cáo:** CRUD hình ảnh banner hiển thị ngoài trang chủ.

### 4.5. Nhân viên Giao hàng (Shipper - Module Mở rộng)
- Nhận danh sách các đơn hàng được gán đi giao thuộc chi nhánh.
- Cập nhật trạng thái: Đang giao -> Đã giao thành công / Hủy đơn.
- Thống kê đơn giao thành công và tiền COD cần nộp lại.

### 4.6. Hệ thống RESTful API & Swagger OpenAPI 3.0 (Tích hợp JWT)
- **Chuẩn hóa API RESTful:** Hỗ trợ đầy đủ các phương thức HTTP (GET, POST, PUT, DELETE) với cấu trúc phản hồi chuẩn hóa ApiResponse<T>.
- **Bảo mật Token Bearer JWT:** Cấp phát mã JWT sau khi đăng nhập qua /api/auth/login, bảo vệ các API nghiệp vụ quan trọng qua Filter JwtAuthenticationFilter.
- **Tài liệu trực quan tương tác Swagger UI:**
  - Đường dẫn truy cập: http://localhost:8080/swagger-ui.html
  - OpenAPI 3.0 JSON Schema: http://localhost:8080/api-docs
  - Hỗ trợ nút **Authorize** nhập mã Bearer JWT token trực tiếp trên trình duyệt để gọi test API trực tiếp.
- **Danh mục các Controller RESTful đã triển khai:**
  - AuthApiController (/api/auth/**): Đăng nhập, Đăng ký, Lấy thông tin tài khoản hiện hành (/me).
  - ProductApiController (/api/products/**): Tìm kiếm, Lọc đa tiêu chí, Chi tiết sản phẩm & biến thể, Quản trị sản phẩm.
  - CategoryApiController (/api/categories/**): Tra cứu danh mục công khai & Phân cấp danh mục.
  - OrderApiController (/api/orders/**): Đặt hàng (Checkout), Lịch sử đơn cá nhân, Hủy đơn, Shipper giao hàng & Kế toán đối soát COD.
  - FinancialApiController (/api/admin/financial/**): Báo cáo tài chính toàn chuỗi (Doanh thu, Giá vốn COGS, Lợi nhuận gộp), Hiệu quả chi nhánh, Cảnh báo tồn kho an toàn & Đề xuất điều chuyển tự động.

---

## 🌿 5. CHIẾN LƯỢC PHÂN NHÁNH GIT (GIT FLOW STRATEGY)

Để đảm bảo đạt điểm tối đa tiêu chí *"Quản lý, phân công nhiệm vụ các thành viên và có quá trình commit Github"* (1.0 điểm), nhóm triển khai mô hình Git Flow bài bản, minh bạch với các nhánh thực tế đã hoàn thiện trên Repository:

```text
main (Phiên bản phát hành chính thức, mã nguồn hoàn chỉnh sẵn sàng chấm điểm)
  │
  └── dev (Nhánh tích hợp tổng thể - Integration Branch)
        │
        ├── feature/database-and-entity         (Phase 1: Khởi tạo CSDL 27 bảng, JPA Entities, Enums & Seed Data)
        ├── feature/security-jwt-and-email-otp  (Phase 2: Spring Security 6 kép, JWT Filter, OTP Email/Terminal, Repositories & Cloudinary)
        ├── feature/customer-and-public-portal  (Phase 3: Trang chủ, Catalog 20 sp/trang, Chi tiết, Tra cứu kho, Giỏ hàng, Checkout, Review, Wishlist)
        ├── feature/admin-and-staff-portal      (Phase 4 & 5: HQ Admin Dashboard, Quản lý sản phẩm, Staff Portal chi nhánh, Kho hàng, WebSocket STOMP & Live Chat)
        ├── docs/final-report                   (Phase 6: Hoàn thiện Báo cáo tổng thể, Setup Guide, Chuẩn hóa tài liệu)
        ├── feature/inventory-transfer-and-audit-log (Phase 7: Điều chuyển kho liên chi nhánh, Audit Trail, Khóa bi quan, Idempotency & Bộ Test tự động)
        ├── feature/smart-logistics-and-cogs    (Phase 8: Logistics thông minh, Smart Routing, Giá vốn COGS, Lợi nhuận gộp & Đối soát COD)
        └── feature/rest-api-and-swagger-jwt    (Phase 9: Hệ thống RESTful API chuẩn OpenAPI 3.0 với xác thực Bearer JWT & Swagger UI)
```

### Bảng theo dõi các nhánh chức năng thực tế trên GitHub:
| STT | Tên nhánh (Branch Name) | Giai đoạn (Phase) | Nội dung công việc thực tế | Trạng thái tích hợp |
| :---: | :--- | :---: | :--- | :---: |
| **1** | `main` | Production | Mã nguồn chính thức, chạy ổn định, sẵn sàng báo cáo chấm điểm. | ✅ Đã đồng bộ |
| **2** | `dev` | Integration | Nhánh trung gian tích hợp các tính năng trước khi đưa vào `main`. | ✅ Đã đồng bộ |
| **3** | `feature/database-and-entity` | Phase 1 | CSDL SQL Server 27 bảng, 16 Entities, 8 Enums, seed data mẫu. | ✅ Đã merge vào `dev` |
| **4** | `feature/security-jwt-and-email-otp` | Phase 2 | Spring Security 6 kép, JWT, OTP (Email + Terminal mode), Cloudinary CDN. | ✅ Đã merge vào `dev` |
| **5** | `feature/customer-and-public-portal` | Phase 3 | Portal khách hàng: Catalog 20 sp/trang, Bộ lọc, Giỏ hàng, Checkout, Tra cứu kho. | ✅ Đã merge vào `dev` |
| **6** | `feature/admin-and-staff-portal` | Phase 4 & 5 | Admin & Staff Portal, Tồn kho đa chi nhánh, WebSocket STOMP & Live Chat. | ✅ Đã merge vào `dev` |
| **7** | `docs/final-report` | Phase 6 | Báo cáo chi tiết, tài liệu hướng dẫn và hồ sơ nghiệm thu đồ án. | ✅ Đã merge vào `dev` |
| **8** | `feature/inventory-transfer-and-audit-log` | Phase 7 | Điều chuyển kho liên chi nhánh, Sổ nhật ký kiểm toán (Audit Trail), Khóa bi quan, Chống đặt trùng đơn (Idempotency) & 9 Test cases tự động. | ✅ Đã merge vào `main` |

---

## ⚙️ 6. HƯỚNG DẪN CÀI ĐẶT & CHẠY ỨNG DỤNG (SETUP GUIDE)

### Bước 1: Chuẩn bị Môi trường
- Cài đặt **JDK 17** (Kiểm tra bằng lệnh: `java -version`).
- Cài đặt **Microsoft SQL Server 2019/2022** và **SSMS**.
- Cài đặt **Maven 3.8+** hoặc sử dụng Maven Wrapper (`mvnw.cmd`) kèm theo dự án.

### Bước 2: Thiết lập Cơ sở dữ liệu SQL Server
1. Mở SQL Server Management Studio (SSMS).
2. Mở file `database.sql` và nhấn **Execute (F5)** (hoặc chạy bằng lệnh `sqlcmd`).
3. Script sẽ tự động tạo database `electronics_store_chain` cùng toàn bộ 27 bảng và nạp đầy đủ dữ liệu mẫu (10 tài khoản, 4 chi nhánh, 12 sản phẩm công nghệ, 31 biến thể, tồn kho chi nhánh, coupons).

### Bước 3: Cấu hình `application.properties`
Chỉnh sửa file cấu hình tại `src/main/resources/application.properties` nếu có thay đổi thông số cục bộ:
```properties
# Cấu hình CSDL SQL Server
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=electronics_store_chain;encrypt=false;trustServerCertificate=true
spring.datasource.username=sa
spring.datasource.password=123

# Cấu hình Cloudinary CDN
cloudinary.cloud-name=your_cloud_name
cloudinary.api-key=your_api_key
cloudinary.api-secret=your_api_secret

# Cấu hình Spring Mail & Chế độ Terminal OTP
# Mặc định terminal-mode=true: in mã OTP trực tiếp ra Terminal/Console khi test (tiện lợi, không cần tài khoản Gmail thật)
app.mail.terminal-mode=true
app.mail.otp-expiry-minutes=5

spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=your_email@gmail.com
spring.mail.password=your_app_password
```

### Bước 4: Khởi chạy ứng dụng
Mở Terminal tại thư mục dự án và thực hiện **1 trong 2 cách**:

**Cách 1 (Nhanh & ổn định nhất - Khuyên dùng):**
```powershell
# Chạy trực tiếp file JAR đã đóng gói
java -jar target/electronics-chain-store-1.0.0-SNAPSHOT.jar
```

**Cách 2 (Sử dụng Maven):**
```powershell
.\mvnw.cmd spring-boot:run
```
Ứng dụng sẽ hoạt động tại: **`http://localhost:8080`**

### Bước 5: Danh sách tài khoản thử nghiệm (Test Accounts)
| Tài khoản | Mật khẩu | Quyền hạn (Role) | Chức năng kiểm thử |
| :--- | :--- | :--- | :--- |
| `admin` | `Admin@123` | `ROLE_ADMIN` | Quản trị viên toàn chuỗi, xem biểu đồ doanh thu, quản lý sản phẩm, phân quyền. |
| `staff_q1` | `Staff@123` | `ROLE_STAFF` | Nhân viên chi nhánh Quận 1, tiếp nhận đơn hàng, chuông realtime, quản lý tồn kho tại quầy. |
| `staff_thuduc` | `Staff@123` | `ROLE_STAFF` | Nhân viên chi nhánh TP. Thủ Đức. |
| `shipper1` | `Shipper@123` | `ROLE_SHIPPER` | Nhân viên giao hàng, cập nhật trạng thái đơn giao. |
| `customer1` | `Customer@123` | `ROLE_CUSTOMER` | Khách hàng mua sắm, đặt hàng giao/nhận tại cửa hàng, chat tư vấn, đánh giá. |

---

## 📁 7. CHECKLIST HỒ SƠ BÁO CÁO CUỐI KỲ (HẠN CHÓT 31/10/2026)

- [x] Khởi tạo Repository GitHub và tài liệu kế hoạch `README.md`.
- [x] Hoàn thành toàn bộ Source Code chức năng trên các nhánh Git Flow chuẩn.
- [x] File script `database.sql` đầy đủ 27 bảng, quan hệ khóa ngoại và dữ liệu mẫu phong phú.
- [x] Cấu hình Terminal OTP Mode tiện lợi cho việc kiểm thử và chấm điểm đồ án.
- [ ] File tài liệu báo cáo `.docx` đầy đủ hình kiến trúc, sơ đồ hoạt động, giải thích code, ảnh UI, hướng dẫn cài đặt và bảng tổng hợp Prompt AI.
- [ ] Video demo chức năng có xuất hiện mặt 3 thành viên giới thiệu và thuyết minh.
- [ ] Upload đầy đủ vào thư mục Google Drive mang tên `24`.
- [ ] Tạo sheet mang tên `24` trên bảng điểm chung của lớp và tự chấm điểm theo quy định.

---
*© 2026 Nhóm 24 - Lập trình Web - Đại học Công nghệ Kỹ thuật Thành phố Hồ Chí Minh. Mọi quyền được bảo lưu.*
