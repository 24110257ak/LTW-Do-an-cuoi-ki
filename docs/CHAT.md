# Chat tư vấn khách hàng – chi nhánh

## Chạy file SQL duy nhất

1. Dừng ứng dụng.
2. Mở SQL Server Management Studio, kết nối đúng server và chạy **toàn bộ `database.sql` bằng F5**. Đây là file SQL duy nhất trong project.
3. Nếu chưa có database, script tự tạo `electronics_store_chain`, đủ 27 bảng và dữ liệu mẫu. Nếu đã có bảng ứng dụng, script giữ dữ liệu, bổ sung bảng thiếu và nâng cấp chat, không nạp lại demo.
4. Kiểm tra kết quả: `app_tables = 27`, `client_message_id_bytes = 128`. Các bước schema, nâng cấp và seed có transaction/rollback; phần tạo database chỉ chạy nếu database chưa tồn tại.
5. Khởi động ứng dụng: `.\mvnw.cmd spring-boot:run`.
6. Admin gán chi nhánh đang hoạt động cho STAFF; tài khoản cần được kích hoạt/xác thực.
7. Mở hai trình duyệt/profile: CUSTOMER bấm bong bóng Tư vấn và chọn chi nhánh; STAFF vào `/staff/chat`, tiếp nhận phiên và trả lời.

File gộp giữ lịch sử, điền mã cho tin nhắn cũ, sửa unique chống trùng, xử lý cột mốc đọc cũ và tạo index. Dữ liệu staff mẫu được gán nhánh sau khi nạp branches; token checkout dùng unique index có điều kiện để cho phép nhiều NULL. Bộ test không tự chạy file này lên database thật.

## Luồng và quyền

- Khách chưa đăng nhập thấy nút đăng nhập để chat. CUSTOMER được chọn chi nhánh active để tạo phiên; đây là lựa chọn tư vấn của khách, không phải quyền chọn chi nhánh quản lý của STAFF.
- Một khách và một chi nhánh tái sử dụng phiên OPEN hiện có. Khóa hàng người dùng ngăn hai request mở đồng thời tạo hai phiên mới. Phiên CLOSED giữ nguyên dữ liệu; bắt đầu tư vấn lại tạo phiên mới để tiếp nhận. Giao diện nối tin của mọi phiên cùng khách và chi nhánh thành một cuộc trò chuyện liên tục, nên chọn nhánh là thấy lại nội dung trước đó.
- STAFF nhận danh sách theo `User.branch` trong DB; thay request/session trên trình duyệt không đổi được phạm vi này. ADMIN có thể chọn chi nhánh và tiếp nhận thay nhân viên.
- Mỗi phiên có một nhân viên phụ trách. Người cùng chi nhánh có thể xem hộp thư nhưng không trả lời hoặc kết thúc thay người đã tiếp nhận; admin có thể tiếp quản. Nếu nhân viên cũ bị khóa hoặc chuyển nhánh, nhân viên hợp lệ khác được tiếp nhận lại.
- CUSTOMER có thể kết thúc phiên của mình. Phiên đã kết thúc hoặc chi nhánh inactive không nhận tin mới.
- Mọi lần đọc/gửi/tiếp nhận/kết thúc/đánh dấu đã đọc đều xác minh tài khoản và phạm vi từ DB. Backend bỏ qua mọi `senderId`, `senderName`, `branchId` giả trong payload gửi tin.

## Lưu trữ và chống trùng

- `chat_sessions`: khách, chi nhánh, nhân viên phụ trách, OPEN/CLOSED, thời điểm tạo/kết thúc/tin mới nhất và hai mốc đọc `customer_last_read_id`, `staff_last_read_id`.
- `chat_messages`: nội dung NVARCHAR(2000), người gửi, thời điểm, ID phiên và `client_message_id`.
- Unique `(session_id,client_message_id)` và khóa hàng phiên bảo đảm gửi lại cùng yêu cầu không lưu/phát hai tin. Mã đã dùng với nội dung hoặc người gửi khác bị từ chối.
- `staff_last_read_id` là mốc đọc chung của hộp thư chi nhánh, không phải một mốc riêng cho từng nhân viên. Khi khách gửi nhiều tin, số chưa đọc tăng; người có quyền mở phiên sẽ cập nhật mốc đọc.
- Nội dung/names render bằng `textContent`; không diễn giải thẻ HTML hoặc script khách gửi.

## API web và WebSocket

API `/chat/api/**` sử dụng session đăng nhập Thymeleaf. POST bắt buộc có CSRF header lấy từ bootstrap. Không dùng token JWT của nhánh `/api/**` để thay cho phiên web này.

| Phương thức | Đường dẫn | Chức năng |
|---|---|---|
| GET | `/chat/api/bootstrap` | Tài khoản tối thiểu, danh sách nhánh được chọn và CSRF token |
| GET | `/chat/api/sessions?branchId=&page=0` | 30 phiên/trang, theo quyền |
| GET | `/chat/api/conversations?branchId=&page=0` | Hộp thư STAFF/ADMIN: 30 khách đã nhắn/trang, mỗi khách một mục trong nhánh, tổng chưa đọc qua các phiên |
| GET | `/chat/api/sessions/{id}/conversation` | STAFF/ADMIN lấy cuộc trò chuyện của khách: chọn phiên OPEN để trả lời, nếu không có thì chọn phiên mới nhất |
| POST | `/chat/api/sessions` | CUSTOMER tạo/lấy phiên mở với `{branchId}` |
| GET | `/chat/api/sessions/{id}` | Trạng thái và số chưa đọc |
| GET | `/chat/api/sessions/{id}/messages` | 50 tin gần nhất trong riêng một phiên |
| GET | `/chat/api/sessions/{id}/conversation/messages` | 50 tin gần nhất của cùng khách và chi nhánh qua mọi phiên; hỗ trợ `beforeId`/`afterId` |
| GET | `/chat/api/sessions/{id}/messages?beforeId=...` | Tải tin cũ hơn |
| GET | `/chat/api/sessions/{id}/messages?afterId=...` | Tải bù tin mới theo ID, có phân trang |
| POST | `/chat/api/messages` | Gửi `{sessionId,content,clientMessageId}` |
| POST | `/chat/api/sessions/{id}/claim` | STAFF/ADMIN tiếp nhận |
| POST | `/chat/api/sessions/{id}/close` | Kết thúc phiên |
| POST | `/chat/api/sessions/{id}/read` | Đánh dấu đọc đến `{messageId}` thuộc phiên |

Frontend gửi bằng HTTP để nhận xác nhận/lỗi rõ ràng; nhận thông báo tức thời bằng SockJS/STOMP ở `/ws`. Backend vẫn hỗ trợ SEND `/app/chat.sendMessage` với cùng payload và service kiểm tra quyền.

- CONNECT phải kèm CSRF header và sử dụng principal từ phiên đăng nhập. Không nhận danh tính từ STOMP login/native headers.
- Client chỉ đăng ký `/user/queue/chat` và `/user/queue/chat-errors`. Các topic chat cũ, queue đã resolve của người khác và SEND thẳng vào broker đều bị chặn.
- Event `{type,sessionId,message}` chỉ phát sau khi transaction commit đến khách của phiên, STAFF đang hoạt động trong nhánh và ADMIN. Không phát lên kênh chat toàn chi nhánh cho khách đăng ký.
- Khi socket ngắt, giao diện tự kết nối lại và vẫn có thể gửi bằng HTTP. Tải bù mỗi 15 giây khi cửa sổ chat đang mở; cursor `afterId` lấy bù nhiều trang khi lỡ hơn 50 tin. Nội dung lỗi chưa gửi vẫn nằm trong ô nhập để gửi lại với cùng mã.
- API và event chỉ trả DTO, không serialize entity User/password. Endpoint giữ same-origin, không mở wildcard origin.

## Kiểm thử

- `ChatFeatureTest`: quyền phiên/chi nhánh, giả người gửi, read/unread, đóng/mở, phân trang, cursor tải bù, gửi trùng, validation, HTTP CSRF và render Thymeleaf. Có kiểm tra luồng liên tục qua phiên đóng/mở, phân trang qua ranh giới phiên, giữ thời gian và cách ly khách/chi nhánh.
- `ChatStompIntegrationTest`: đi qua StompSubProtocolHandler, Spring Security, broker thật trong tiến trình, controller và transaction; kiểm tra thông báo sau commit, không gửi sang khách/chi nhánh khác, CONNECT thiếu CSRF, SEND và mở/gửi đồng thời.
- JavaScript: `node --test src/test/js/chat.test.cjs` kiểm tra gửi qua API giả lập, render nội dung XSS như văn bản, CSRF, giữ bản nháp/gửi lại, khóa bộ chọn nhánh STAFF, tải lại tin qua nhiều phiên, mốc thời gian, gửi vào đúng phiên mở, đánh dấu đọc đúng phiên và cuộn tải tin cũ.
- `ChatSocketIT`: bài kiểm tra tùy chọn với Tomcat và socket TCP thật, chỉ chạy khi máy cho phép loopback: `.\mvnw.cmd -Dtest=ChatSocketIT test`.
- Trong phiên triển khai này, môi trường chặn kết nối loopback nên chưa xác minh TCP và SQL Server thật. Các bài API/broker dùng H2 và không tác động DB thật.

Tính năng hiện hỗ trợ tin văn bản, lịch sử, tiếp nhận, số chưa đọc và kết thúc phiên. File/ảnh và trạng thái đang gõ chưa được triển khai.


Lượt kiểm tra cập nhật hộp thư staff theo khách: 40 bài Java (`ChatFeatureTest`, `ChatStompIntegrationTest`, `StaffBranchAuthorizationTest`) và 14 bài JavaScript đều thành công. Bộ `SystemFixesIntegrationTest` phụ thuộc cấu hình SQL Server mặc định không nằm trong lượt H2 này. Trình chạy trong môi trường hạn chế dùng các bản JUnit/Surefire/Byte Buddy đã có trong cache và Java agent, vì không thể tải thêm dependency hoặc tự attach agent.


## Widget khách hàng thu gọn

Bong bóng mở danh sách chi nhánh; việc mở danh sách không tạo phiên và không đánh dấu tin là đã đọc. Nhấn một chi nhánh sẽ mở/lấy phiên tư vấn và hiện ngay tin cũ của cùng khách với chi nhánh đó, kể cả các phiên đã đóng. Không cần tìm từng phiên qua nút Lịch sử. Giao diện lấy 50 tin gần nhất trước, tự tải thêm khi cuộn lên và vẫn có nút Tải tin nhắn trước; nội dung nối liên tục theo thứ tự ID, có mốc thời gian giữa ngày/đợt tư vấn và thời gian của từng tin. Gửi tin vẫn dùng phiên OPEN hiện tại; mốc đọc được gửi theo đúng phiên gốc của từng tin. Hộp thư STAFF giữ bố cục hai cột. Danh sách bên trái chỉ liệt kê khách đã gửi tin, mỗi khách một mục trong nhánh; các phiên cũ được gộp ở backend trước khi phân trang. Số chưa đọc cộng trên mọi phiên của cùng khách/nhánh. Nhấn tên khách sẽ thấy nội dung cũ và mới liền mạch, kèm mốc thời gian và cuộn tải tin cũ như phía khách hàng. Khi khách bắt đầu một phiên OPEN mới, mục đang chọn tự cập nhật phiên trả lời, giữ nội dung đang xem và bản nháp. Khách chỉ có phiên CLOSED vẫn có thể xem lại; staff chờ khách bắt đầu tư vấn lại trước khi trả lời. Luật tiếp nhận và nhân viên phụ trách vẫn được áp dụng; staff không tạo phiên thay khách. Quay lại rồi chọn cùng phiên đang mở giữ nguyên bản nháp. Mỗi lần mở lại bong bóng sẽ trở về danh sách chi nhánh. Popup rộng tối đa 350px và giới hạn chiều cao theo màn hình; phần tin nhắn/danh sách có cuộn riêng. STAFF không thấy ô chọn nhánh trong hộp thư; ADMIN vẫn được chọn. Thay đổi này không yêu cầu sửa schema hay chạy lại SQL.


## Đối chiếu database sau khi chạy SQL

Kết quả cuối `database.sql` hiển thị server/database thực tế, `app_tables = 27`, `client_message_id_bytes = 128` và cờ `seeded_demo`. Dữ liệu demo chỉ được nạp khi trước lúc chạy chưa có bảng ứng dụng nào; chạy lại không xóa hay ghi đè tài khoản, đơn hàng, tồn kho và lịch sử chat hiện có.

Tin chat thiếu mã được gán `legacy-<message_id>`; các mã trùng trong cùng phiên được cấp mã legacy khác trước khi đặt unique `(session_id,client_message_id)`. Nội dung, người gửi, thời điểm và ID tin cũ được giữ nguyên.

Nếu lỗi, transaction schema/nâng cấp/seed được rollback và lỗi gốc được ném lại. Không cần chọn hay chạy file nâng cấp riêng.
