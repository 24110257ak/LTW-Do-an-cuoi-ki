1, Tên đề tài: Chọn theo danh sách hoặc đề xuất thêm.
¨¨1. Xây dựng website bán hàng theo mô hình chuỗi cửa hàng......... bằng Spring Boot + JSP/JSTL+ Bootstrap + JPA + SQLServer/MySQL/postgreSQL + WebSocket + Decorator Sitemesh + JWT+Cloudairy
¨¨2. Xây dựng website bán hàng theo mô hình chuỗi cửa hàng......... bằng Spring Boot + Thymeleaf+ Bootstrap + JPA + SQLServer/MySQL/postgreSQL + WebSocket + JWT +Cloudairy.
¨¨3. Xây dựng trang tư vấn sinh viên QAUTE bằng Spring Boot + JSP/JSTL+ Bootstrap + JPA + WebSocket+ Mongodb/SQLServer/MySQL/postgreSQL +Decorator Sitemesh + JWT +Cloudairy.
¨¨4. Xây dựng trang tư vấn sinh viên QAUTE bằng Spring Boot + Thymeleaf + Bootstrap + JPA + WebSocket+ Mongodb/SQLServer/MySQL/postgreSQL + JWT +Cloudairy.
¨¨5. Xây dựng website bán hoa StarShop theo mô hình chuỗi cửa hàng bằng Spring Boot + JSP/JSTL+ Bootstrap + JPA + SQLServer/MySQL/postgreSQL+Decorator Sitemesh+JWT+Websocket +Cloudairy.
¨¨6. Xây dựng website bán mỹ phẩm OneShop theo mô hình chuỗi cửa hàng bằng Spring boot + Thymeleaf+ Bootstrap + JPA + SQLServer/MySQL/ postgreSQL  +Decorator Sitemesh+ JWT+Cloudairy.
¨¨7. Xây dựng trang mạng xã hội ALOUTE bằng Spring boot + JSP/JSTL + Bootstrap + JPA  + Mongodb/SQLServer/MySQL/postgreSQL+Decorator Sitemesh+JWT+Cloudairy
¨8. Xây dựng trang mạng xã hội ALOUTE bằng Spring boot + Thymeleaf + Bootstrap + JPA + Firebase + Mongodb/SQLServer/MySQL/postgreSQL+JWT+ Websocket
¨¨9. Xây dựng website quản lý chuỗi giao nhận Logistic UTEExpress bằng Spring boot + JSP/JSTL + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL+JWT+Websocket.
¨¨10. Xây dựng website quản lý chuỗi giao nhận Logistic UTEExpress bằng Spring boot + Thymeleaf + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL+JWT+Websocket.
¨¨11. Xây dựng website chuỗi bán trà sữa UTETra bằng Spring boot + Thymeleaf + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL+JWT+websocket+Cloudairy.
¨¨12. Xây dựng website chuỗi bán trà sữa UTETra bằng Spring boot + JSP/JSTL + Decorator Sitemesh + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL+JWT+websocket+Cloudairy.
¨¨13. Xây dựng website quản lý thiết bị IOTs bằng Spring boot + Thymeleaf + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL + JWT+websocket+ Cloudairy
¨¨14. Xây dựng website quản lý chuỗi cửa hàng sách cũ  bằng Spring boot + Thymeleaf + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL + JWT+websocket+ Cloudairy
¨¨15. Xây dựng website quản lý chuỗi cửa hàng sách cũ  bằng Spring boot + JSP/JSTL+ Bootstrap + JPA + SQLServer/MySQL/ postgreSQL + JWT+websocket+ Cloudairy
¨¨16. Xây dựng website hỗ trợ tìm kiếm cứu nạn thiên tai  bằng Spring boot + Thymeleaf + Bootstrap + JPA + SQLServer/MySQL/ postgreSQL + JWT+websocket+ Cloudairy
 
2. Nhóm: tối đa 03 sinh viên /1 nhóm. Đăng ký thành viên nhóm và đề tài tại link này: 

3. Nội dung:

a. Khảo sát hiện trạng và phân tích thiết kế hệ thống

ví dụ:
D:\lap_trinh_web\Đồ án cuối kì\KhaoSat_PhanTichHeThong.docx

b. Tiến hành phân công nhiệm vụ từng thành viên code theo các chức năng theo User+Guest; Vendor, Manager, Admin,Shipper tương ứng trong bước a. Sử dụng 01 template Boostrap để làm giao diện đẹp.

- Chức năng chung: tìm kiếm và lọc sản phẩm, đăng ký tài khoản có gửi mã OTP kích hoạt qua Email, đăng nhập, đăng xuất, quên mật khẩu có gửi mã OTP kích hoạt qua Email. Mật khẩu phải được mã hóa. Nếu dùng Spring boot thì dùng Spring Security.

Guest: Giao diện Trang chủ (hiển thị sản phẩm bán trên 10 sản phẩm của các shop (sắp xếp theo thứ tự từ lớn đến nhỏ)
User: Giao diện trang chủ, trang sản phẩm theo danh mục, 20 (sản phẩm mới, bán chạy, đánh giá, yêu thích) nhất được phân trang (hoặc lazy loading), trang profile user (có quản lý địa chỉ nhận hàng khác nhau nếu làm đề tài về Bán hàng), trang chi tiết sản phẩm, giỏ hàng được lưu trên database, thanh toán(COD, VNPAY hoặc MOMO), quản lý lịch sử mua hàng theo trạng thái (đơn hàng mới, đã xác nhận, đang giao, đã giao, hủy, trả hàng- hoàn tiền), thích sản phẩm, sản phẩm đã xem, đánh giá sản phẩm đã mua, bình luận (text (tối thiểu 50 ký tự), hình ảnh/video) sản phẩm đã mua, chọn mã giảm giá,...
Vendor (Seller): có các quyền của User và thêm các chức năng: đăng ký shop, quản lý trang chủ shop, quản lý sản phẩm của mình, quản lý đơn hàng của shop theo trạng thái (đơn hàng mới, đã xác nhận, đã lấy hàng, đang giao, đã giao, hủy, trả hàng- hoàn tiền), tạo chương trình khuyến mãi, quản lý doanh thu của shop.
Manager: Quản lý từng cửa hàng: tìm kiếm và quản lý user, quản lý sản phẩm của từng shop, Quản lý doanh mục, quản lý chiết khấu app cho các shop, quản lý chương trình khuyến mãi (giảm % sản phẩm, giảm phí vận chuyển), quản lý nhà vận chuyển (tên nhà vận chuyển, phí vận chuyển)..
Admin: tìm kiếm và quản lý user, quản lý sản phẩm của từng shop, Quản lý doanh mục, quản lý chiết khấu app cho các shop, quản lý chương trình khuyến mãi (giảm % sản phẩm, giảm phí vận chuyển), quản lý nhà vận chuyển (tên nhà vận chuyển, phí vận chuyển).
Shipper (phần thêm): Quản lý đơn hàng được phân công đi giao, thống kê đơn hàng được phân công giao.
c. Các đề tài không phải bán hàng thì sẽ có role: Guest, User, Manager, Admin. các chức năng cho từng role sẽ thảo luận với giáo viên để quyết định.

d. Viết báo cáo mà mình được phân công ứng với vai trò từng User+Guest; Vendor, Admin, shipper theo từng thành viên trong nhóm.


e. Tiêu chí chấm điểm: thang điểm 100 và quy đổi sang thang điểm 10

Quản lý, phân công nhiệm vụ các thành viên và có quá trình commit Github: 1 điểm
Đúng cấu trúc project theo mô hình 3 tầng: 0.5 điểm
Đúng và đầy đủ tính logic, validation, security,.. của các chức năng từng role: 6 điểm
Giao diện đẹp, dễ sử dụng và Responsive: 1 điểm
Các chức năng sáng tạo: đối với đề tài bán hàng (ví dụ: thanh toán qua ví điện tử, JWT, Phiếu giảm giá, websocket...) các đề tài khác với các chức năng tương đương (websocket): 1 điểm. 
Khả năng báo cáo và trả lời câu hỏi: 0.5 điểm
f. Thời gian:

Nộp báo cáo cuối kỳ: (link nộp báo cáo xem trong mục cuối kỳ)
Báo cáo cuối kỳ: .


Báo cáo cuối kỳ môn lập trình WEB

Thời gian báo cáo: 7g00 - 18g00 ngày 31/10/2026 tại A5-203

Nội dung các em cần thực hiện:

1. Với sự hỗ trợ của AI, Nhóm hãy viết tài liệu bằng file .docx chi tiết hướng dẫn xây dựng từng chức năng của ứng dụng gồm các phần:các hình kiến trúc úng dụng, các hình sơ đồ hoạt động của chức năng + hình diễn giải Source Code, hình diễn giải giao diện, file hướng dẫn setup ứng dụng, danh sách các prompt nhóm đã sử dụng để AI hỗ trợ sinh code, sinh hình,...

2. Nộp source code Source Code + Database(.sql) + datasheet AI (nếu có) và Link Github trên drive. Hạn nộp trước khi nhóm báo cáo 30 phút nhóm báo cáo.

3. Nộp video thao tác các chức năng đề tài, video phải có mặt của các thành viên nhóm, từng thành viên giới thiệu về mình và các chức năng tham gia làm.

4. Upload mục 1, 2 và 3 lên link. Mỗi nhóm tạo 01 thư mục mang tên số thứ tự nhóm nhé: https://drive.google.com/drive/folders/1iXhQTN2-9io2GM2O1N99AKwTkKKWhHm5?usp=sharing

5. Nhân bản sheet mẫu và đặt tên sheet là số thứ tự nhóm rồi điền tên đề tài và thành viên nhóm rồi tự chấm điểm vào link sau: 

https://docs.google.com/spreadsheets/d/1n06w1VvAOCKUNeMDPng52zAgjLW9hhSG/edit?usp=sharing&ouid=118102604914311269082&rtpof=true&sd=true