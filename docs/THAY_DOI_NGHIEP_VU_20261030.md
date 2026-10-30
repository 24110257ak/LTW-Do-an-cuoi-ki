# Các thay đổi chức năng và logic nghiệp vụ TechChain

## 1. Mua hàng và giới hạn số lượng

- Thêm nút **Mua ngay**, chuyển sản phẩm và số lượng đã chọn sang checkout độc lập với giỏ hàng.
- Giới hạn tối đa **10 đơn vị cho cùng một sản phẩm trong mỗi giỏ hoặc mỗi đơn**, cộng số lượng của các biến thể. Giỏ có 1 máy thì chỉ thêm tối đa 9 máy cùng sản phẩm.
- Kiểm tra giới hạn khi thêm giỏ, cập nhật số lượng và đặt hàng; giao diện hiển thị số lượng còn được thêm và khóa thao tác khi vượt giới hạn.

## 2. Checkout và mã giảm giá

- Thống nhất cách tính tiền giữa xem trước và tạo đơn: **tiền hàng − giảm giá + phí giao hàng**, thay cho phí cố định trên giao diện.
- Tính lại tiền khi đổi địa chỉ, hãng vận chuyển, hình thức nhận hàng hoặc coupon; phản hồi cũ không ghi đè lựa chọn mới.
- Trước khi tạo đơn, kiểm tra lại sản phẩm/biến thể còn kinh doanh, giá, giới hạn số lượng, hãng vận chuyển, chi nhánh hoạt động và tồn kho đủ cho toàn đơn.
- Chọn chi nhánh đủ hàng theo thứ tự ưu tiên: cùng tỉnh/quận, cùng tỉnh, rồi mã chi nhánh.
- Chuẩn hóa coupon và kiểm tra thời hạn, phạm vi chi nhánh, đơn tối thiểu, mức giảm và lượt sử dụng. Phần trăm tối đa 100; cấu hình tiền/phần trăm dùng số nguyên.
- Xem trước không chiếm lượt coupon. Tạo đơn khóa và tăng lượt trong cùng giao dịch; đặt hàng lỗi thì rollback; hủy/nhận hoàn chỉ trả lượt một lần.
- Chặn sửa `usedCount` qua form quản trị và giữ thời điểm tạo coupon khi chỉnh sửa.

## 3. Thanh toán và hoàn tiền VNPay mô phỏng

- Thêm phiên thanh toán gắn với từng đơn, lưu đúng số tiền, mã tham chiếu và thời hạn **15 phút**; khách chọn kết quả thành công, thất bại hoặc hủy.
- Thanh toán thành công chuyển phiên sang `SUCCESS`, đơn sang `PAID`; thất bại, hủy hoặc hết hạn thì hủy đơn và giải phóng tồn kho/coupon một lần.
- Xử lý hết hạn khi mở phiên và bằng tác vụ định kỳ 60 giây. Chỉ chủ đơn được xem và xác nhận kết quả phiên.
- Chặn ghi `PAID` thủ công ngoài phiên. Gửi lại kết quả đã xử lý không thu tiền hoặc hoàn kho lặp.
- Thêm form **hoàn toàn bộ tiền mô phỏng** dành cho ADMIN, bắt buộc lý do. Áp dụng cho PICKUP đang `READY_FOR_PICKUP` hoặc đơn `RETURNED` có phiếu kiểm đếm; phải có phiên mô phỏng thanh toán thành công, số tiền khớp tổng đơn.
- Lưu chứng từ hoàn gồm mã, số tiền, admin, thời gian và lý do; cập nhật thanh toán sang `REFUNDED`, đơn sang `RETURNED_REFUND`.
- Đơn PICKUP chưa xuất hàng được giải phóng phần giữ hàng một lần. Đơn đã kiểm đếm nhận hoàn không cộng tồn lần nữa. Khóa đơn và chứng từ duy nhất ngăn hoàn tiền hai lần.
- Thanh toán/hoàn tiền dùng dữ liệu mô phỏng, không yêu cầu thông tin thẻ thật hoặc chuyển tiền ngân hàng.

## 4. Vòng đời và danh sách đơn hàng

- Kiểm soát chuyển trạng thái theo các luồng:

| Luồng | Trạng thái |
| --- | --- |
| Giao tận nơi | `NEW → CONFIRMED → SHIPPING → DELIVERED` |
| Nhận tại cửa hàng | `NEW → CONFIRMED → READY_FOR_PICKUP → DELIVERED` |
| Giao thất bại | `SHIPPING → DELIVERY_FAILED → SHIPPING` hoặc `RETURNING` |
| Nhận hoàn đơn bán | `RETURNING → RETURNED` qua phiếu kiểm đếm |

- Đơn VNPAY chưa thanh toán chỉ được hủy, chưa được xác nhận hoặc giao; đơn đã trả tiền dùng thao tác hoàn có kiểm soát khi đủ điều kiện.
- Bỏ lối đi tắt đến `DELIVERED`, `DELIVERY_FAILED`, `RETURNED` trong dropdown: phải dùng form giao hàng, báo thất bại hoặc kiểm đếm tương ứng.
- Ghi `completedAt` khi hoàn tất giao/nhận hàng; thêm ghi chú mới mà vẫn giữ ghi chú đơn trước đó.
- Bổ sung đủ trạng thái trên trang đơn khách hàng, giới hạn phân trang an toàn, giữ bộ lọc khi chuyển trang và sắp xếp ổn định theo `createdAt`, `orderId`.

## 5. Hạn giữ hàng và khách không đến nhận — C03

- Đơn COD mới ở `NEW` có hạn xử lý mặc định **2 giờ**; hết hạn mà chưa thanh toán thì hủy và giải phóng tài nguyên một lần.
- Đơn PICKUP chuyển sang `READY_FOR_PICKUP` có hạn nhận mặc định **48 giờ**. Admin/staff có thể đặt lại hạn từ **1–168 giờ** tính từ lúc thao tác.
- Nhắc khách một lần qua thông báo trong hệ thống trong **6 giờ trước hạn nhận**; hiển thị hạn hẹn trên trang đơn của khách.
- Quá hạn nhận: COD chưa thanh toán được hủy và trả phần giữ hàng; đơn đã thanh toán được ghi nhận no-show và chuyển vào danh sách cần xử lý, không tự hoàn tiền.
- Thêm nút xử lý khách không đến nhận sau hạn hẹn và danh sách đơn cần hỗ trợ trên dashboard theo phạm vi chi nhánh.
- Khách sở hữu đơn PICKUP ở `READY_FOR_PICKUP` được gửi yêu cầu hỗ trợ hủy kèm lý do; yêu cầu này không tự hủy, hoàn kho hoặc hoàn tiền.
- Không áp dụng một hạn tự hủy chung cho mọi trạng thái. Xác nhận đơn xóa hạn của trạng thái NEW; dữ liệu đơn cũ không bị tự gán hạn hồi tố.

## 6. Đăng ký tài khoản và OTP — G01

- Dữ liệu đăng ký tạm lưu mật khẩu đã mã hóa và thời hạn mặc định 30 phút; kiểm tra lại username, email, số điện thoại trước khi tạo user/cart.
- Chỉ xóa thông tin đăng ký tạm sau khi giao dịch xác thực và tạo tài khoản thành công. Đăng ký lại bị cooldown không ghi đè thông tin đang chờ xác thực.
- Gửi lại OTP chuyển từ GET sang **POST có CSRF**; yêu cầu bị giới hạn không vô hiệu hóa mã cũ.
- Mỗi **email + mục đích** phải chờ tối thiểu **60 giây** giữa hai lần gửi và tối đa **5 lần/giờ**. Mỗi **IP + mục đích** được tối đa **20 lần/giờ**.
- Giới hạn **5 lượt kiểm tra OTP**, sau đó tạm khóa **15 phút** nếu chưa xác thực thành công. Gửi lại mã không xóa lượt thử hoặc bỏ khóa.
- Bộ đếm lưu trong DB, tách theo mục đích đăng ký/quên mật khẩu và vẫn được lưu khi giao dịch kiểm tra OTP sai rollback.
- Chuẩn hóa email trước khi tính giới hạn; IP lấy từ địa chỉ kết nối, không tin trực tiếp header chuyển tiếp do client tự gửi.

## 7. Sổ địa chỉ khách hàng

- Thêm thao tác sửa, xóa có xác nhận và đặt địa chỉ mặc định; kiểm tra địa chỉ thuộc đúng khách đang đăng nhập.
- Địa chỉ đầu tiên tự làm mặc định. Xóa địa chỉ mặc định sẽ chọn một địa chỉ còn lại; khóa tài khoản khi cập nhật để tránh có nhiều mặc định do yêu cầu đồng thời.
- Checkout cho chọn địa chỉ đã lưu để điền người nhận, số điện thoại và địa chỉ; sửa trên checkout chỉ áp dụng cho đơn đang đặt.
- Giữ thông tin đã nhập khi form lỗi; sửa sổ địa chỉ không thay bản chụp thông tin của đơn cũ.
- Bổ sung xử lý tên địa phương rút gọn, bỏ phản hồi cũ khi đổi địa chỉ nhanh, nút tải lại và giữ dữ liệu đã lưu nếu dịch vụ địa chỉ lỗi.

## 8. Điều chuyển kho

- Hủy phiếu `APPROVED` được trả kho vì hàng chưa rời đi; hủy `IN_TRANSIT` chuyển sang `RETURNING`, chưa cộng tồn kho nguồn.
- Thêm form kiểm đếm nhận hoàn theo biến thể; số thực nhận từ 0 đến số đã chuyển, phải đủ các dòng và ghi lý do khi thiếu.
- Chỉ cộng số thực nhận về kho nguồn; lưu số lượng nhận và thời gian rồi chuyển `RETURNED`. Xác nhận lại không cộng kho hai lần.
- Chỉ hoàn tất nhận tại kho đích khi phiếu còn `IN_TRANSIT`.
- Thống nhất thứ tự khóa sản phẩm/tồn kho giữa tạo đơn, cập nhật kho và điều chuyển; lỗi hoàn kho làm rollback giao dịch thay vì bị bỏ qua.

## 9. Kiểm đếm nhận hoàn đơn bán — S02

- Thêm phiếu nhận hoàn theo từng item, gồm số lượng **tốt/bán được, hỏng, thiếu**, lý do chênh lệch và ghi chú chung.
- Kiểm tra đủ item của đơn, không trùng hoặc dùng item đơn khác; số lượng không âm và **tốt + hỏng + thiếu = số đã xuất**.
- Chỉ hàng tốt được cộng vào tồn bán của chi nhánh. Ví dụ xuất 3 máy, nhận 2 tốt và 1 hỏng thì tồn bán chỉ tăng 2.
- Lưu người kiểm đếm, thời gian, chi tiết chênh lệch và nhật ký kho; nhận hoàn lặp không tăng tồn hoặc trả lượt coupon lần nữa.
- Giao thất bại không hoàn kho ngay. Đơn đã thanh toán sau nhận hoàn được đánh dấu cần xử lý hoàn tiền riêng.

## 10. Nhận đơn và xác nhận giao hàng — H01, H03

- Nhận đơn qua service có transaction và khóa dòng, thay cho gán shipper trực tiếp tại controller.
- Chỉ nhận đơn `DELIVERY`, đang `SHIPPING`, chưa có tài xế và thuộc phạm vi được giao; đơn VNPAY phải đã thanh toán.
- Kiểm tra tài xế/chi nhánh hoạt động. Tài xế gán chi nhánh chỉ nhận cùng chi nhánh; tài xế không gán chi nhánh nhận trong toàn chuỗi.
- Chặn ghi đè tài xế khác; hai người nhận đồng thời chỉ một người thành công. Chính tài xế gọi lại trên đơn của mình nhận kết quả nhất quán.
- Form giao thành công bắt buộc **người nhận và số tiền thực thu**: COD bằng toàn bộ số tiền phải thu, VNPAY bằng 0. Xác nhận vẫn kiểm tra người được gán và phạm vi quyền.
- Lưu từng lần giao thành công/thất bại với người thao tác, thời gian, kết quả, thực thu/người nhận hoặc lý do thất bại; gửi lặp không tạo thêm lần giao trùng.

## 11. Thu COD và đối soát nộp quỹ — H03

- Tách trạng thái **đã giao, đã thu và đã nộp quỹ**. Giao thành công ghi nhận thực thu và `PAID`, chưa tự bật đối soát nộp quỹ.
- Trang shipper có số tiền còn phải thu, đã thu, chưa nộp, đã nộp và danh sách đơn tương ứng.
- Admin/staff xác nhận nộp quỹ riêng, lưu thời gian và mã chứng từ; kiểm tra quyền/chi nhánh và chặn đối soát lặp.
- Tổng hợp tiền đã nộp theo mã chứng từ/đợt; có thể dùng cùng mã cho nhiều đơn trong một đợt.
- Bản ghi COD cũ thiếu số thực thu được đánh dấu chưa xác nhận, không tự coi là số tiền đã kiểm chứng.
- Sửa thông báo “tự động đối soát tiền mặt vào sổ quỹ” thành thông báo đúng rằng nộp quỹ cần đối soát riêng.

## 12. Khai báo và cập nhật giá vốn — A02

- Thêm trường khai báo giá vốn khi tạo biến thể và form cập nhật giá vốn riêng dành cho ADMIN.
- Phân biệt **chưa nhập giá vốn** với **đã xác nhận giá vốn bằng 0**. Giá 0 phải xác nhận rõ; cập nhật cần lý do.
- Kiểm tra giá vốn là VND nguyên không âm, tối đa 18 chữ số; chặn sửa giá vốn/cờ xác nhận qua dữ liệu form lồng để bỏ qua kiểm soát.
- Lưu nhật ký giá cũ/mới, người sửa, thời gian, lý do và trạng thái xác nhận trước đó; hiển thị 50 lần cập nhật gần nhất của sản phẩm.
- Khi đặt hàng, chụp cả giá vốn và cờ xác nhận vào từng dòng đơn. Sửa giá vốn hiện tại không thay snapshot các đơn cũ.
- Cảnh báo đơn/doanh thu có giá vốn chưa xác nhận trên chi tiết đơn và dashboard; vẫn cho đặt hàng với dữ liệu chưa khai báo.

## 13. Thích và bỏ thích sản phẩm

- Thêm nút trái tim trên trang chủ, danh sách, chi tiết sản phẩm và nút bỏ thích tại trang yêu thích.
- Lưu trạng thái theo khách hàng trong DB; khách chưa đăng nhập được chuyển tới đăng nhập khi thao tác.
- Nút gửi POST có CSRF với trạng thái mong muốn `liked=true/false`; gửi lại không đảo trạng thái ngoài ý muốn và yêu cầu đồng thời không tạo mục trùng.
- Chỉ thêm sản phẩm đang hoạt động, vẫn cho bỏ thích sản phẩm ngừng bán. Nút thích không bị thao tác chuyển trang của thẻ sản phẩm chặn.

## 14. Báo cáo tài chính và dashboard

- Doanh thu hàng hóa chỉ cộng đơn `DELIVERED`, tính bằng `subtotal − discountAmount`, không cộng phí giao hàng. Giá vốn/lợi nhuận tổng cũng chỉ lấy đơn đã giao.
- Tách chỉ số giá trị đơn đang xử lý, thanh toán `PAID` có cờ mô phỏng và không có cờ mô phỏng; số tiền thanh toán dùng tổng đơn gồm phí giao hàng.
- Thêm bộ lọc từ tháng đến tháng cho biểu đồ trạng thái; mặc định tháng hiện tại theo giờ Việt Nam, có lựa chọn xem tất cả.
- Bộ lọc dựa trên ngày tạo đơn và trạng thái hiện tại; KPI toàn hệ thống giữ phạm vi riêng. Di chuột hiển thị số đơn và phần trăm; thêm bảng 30 ngày theo `completedAt`.
- Sắp xếp nhật ký kho theo thời gian rồi ID giảm dần để ổn định khi nhiều biến động trùng thời điểm.

## 15. Cấu trúc dữ liệu và các sửa đổi kỹ thuật

- Bổ sung bảng phiên thanh toán mô phỏng, giới hạn OTP, phiếu/dòng nhận hoàn đơn bán, lịch sử lần giao, nhật ký giá vốn và chứng từ hoàn tiền.
- Bổ sung các cột về thanh toán mô phỏng, thời điểm hoàn tất, chống giải phóng kho/coupon lặp, hạn giữ/nhắc/no-show, yêu cầu hỗ trợ, số thực thu và xác nhận giá vốn.
- Bổ sung số thực nhận/thời gian nhận hoàn cho điều chuyển kho, các ràng buộc unique/check/khóa ngoại và chỉ mục phục vụ tra cứu/báo cáo; mở rộng trạng thái thanh toán mô phỏng với `REFUNDED`.
- `database.sql` hỗ trợ tạo phần còn thiếu và nâng cấp schema trong transaction, thay cho xóa/tạo lại DB; dữ liệu demo chỉ nạp khi chưa có bảng ứng dụng. Thêm script nâng cấp riêng `docs/sql/20261030_business_fixes.sql`.
- Dữ liệu giao hàng cũ chưa có `completedAt` dùng `updated_at` làm mốc xấp xỉ; giá vốn dương cũ được gán cờ đã khai báo khi thêm cột lần đầu, giá 0 cũ vẫn chưa xác nhận.
- Sửa trang danh mục dùng đúng `imageUrl` dạng URL ảnh hoặc icon Bootstrap.
- Trả HTTP 400 khi thiếu/sai kiểu tham số, 405 khi dùng phương thức không hỗ trợ và 429 cho lỗi giới hạn OTP ở API.
- Cập nhật Lombok 1.18.48 và khai báo annotation processor để hỗ trợ biên dịch.

## 16. Đồng bộ độ chính xác các cột tiền với SQL Server

### Lỗi và nguyên nhân

Sau khi script nâng cấp schema chạy thành công, log khởi động vẫn có thể báo `The object 'DF__...' is dependent on column ...`. Hibernate đang cố đổi các cột tiền thành `numeric(38,2)` trong khi `database.sql` khai báo `DECIMAL(18,0)` và có ràng buộc `DEFAULT`. SQL Server chặn thao tác đổi kiểu khi còn ràng buộc phụ thuộc.

Trong SQL Server, `DECIMAL` và `NUMERIC` là hai tên tương đương. Chỗ cần đồng bộ là độ chính xác và số chữ số thập phân `(18,0)` so với `(38,2)`.

Nguyên nhân ở mapping: các trường `BigDecimal` này chưa khai báo `precision` và `scale`. Cả bản source lưu trước lẫn bản hiện tại đều có chỗ thiếu này; không đủ bằng chứng để kết luận lần nâng cấp schema vừa rồi mới tạo ra lỗi. Thông báo `(0 rows affected)` trong script cập nhật dữ liệu không phải lỗi.

### Phạm vi sửa

Thêm `precision = 18, scale = 0` cho 16 trường trong 6 Entity, theo đúng kiểu cột của SQL gốc:

| Entity | Cột được đồng bộ |
| --- | --- |
| `Product` | `base_price` |
| `ProductVariant` | `cost_price`, `price`, `promo_price` |
| `DeliveryProvider` | `base_fee` |
| `Coupon` | `discount_value`, `min_order_amt`, `max_discount` |
| `Order` | `shipping_fee`, `subtotal`, `discount_amount`, `total_amount`, `total_cost`, `gross_profit` |
| `OrderItem` | `unit_price`, `cost_price` |

`discount_value` có thể là số tiền hoặc phần trăm; mapping giữ quy định số nguyên `DECIMAL(18,0)` của SQL gốc, không bổ sung hỗ trợ phần trăm lẻ. Các cột tiền mới đã có `(18,0)` được giữ nguyên; `Product.avgRating` vẫn dùng `(3,2)` để lưu điểm đánh giá có phần thập phân.

Ví dụ sau sửa:

```java
@Column(name = "total_amount", nullable = false, precision = 18, scale = 0)
@Builder.Default
private BigDecimal totalAmount = BigDecimal.ZERO;
```

### Tác động và khởi chạy lại

- Giữ nguyên kiểu Java `BigDecimal`, công thức tính giá, giảm giá, phí giao hàng, giá vốn và lợi nhuận, cũng như thuộc tính nullable và giá trị mặc định trong Java.
- Thay đổi này chỉ sửa mapping Entity; không thêm lệnh sửa/xóa dữ liệu, không sửa script SQL và không đổi `ddl-auto=update` để che cảnh báo.
- Nếu cột thực tế đã là `DECIMAL(18,0)` hoặc `NUMERIC(18,0)`, mapping mới khớp độ chính xác đó, tránh yêu cầu tự đổi thành `(38,2)`.
- Nếu database thực tế có cột đã thành `(38,2)`, cần kiểm tra dữ liệu có phần lẻ hoặc vượt 18 chữ số trước khi cho Hibernate cập nhật schema. Chưa thể xác nhận trực tiếp kiểu cột hiện tại vì kết nối SQL Server bị môi trường thực thi chặn.
- Dừng ứng dụng rồi biên dịch/chạy lại từ source bằng JDK 17. Nếu chạy bằng JAR, cần đóng gói lại bằng `./mvnw.cmd clean package` trước khi chạy JAR; JAR cũ không chứa mapping mới.

Kiểm tra kiểu cột trong SSMS trước khi chạy lại nếu database từng được Hibernate tự đổi kiểu:

```sql
USE electronics_store_chain;
GO
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE,
       NUMERIC_PRECISION, NUMERIC_SCALE
FROM INFORMATION_SCHEMA.COLUMNS
WHERE DATA_TYPE IN ('decimal', 'numeric')
ORDER BY TABLE_NAME, ORDINAL_POSITION;
```

### Kết quả kiểm tra

- Biên dịch source và test bằng JDK 17 thành công.
- Khởi tạo metadata Hibernate 6.5.3 với `SQLServerDialect`, không kết nối database: cả **23 cột `BigDecimal`** khớp độ chính xác và số chữ số thập phân trong `database.sql` (gồm các cột bổ sung bằng `ALTER TABLE`). Điểm đánh giá khớp `(3,2)`, các cột tiền/giá trị giảm giá khớp `(18,0)`.
- Chạy lại 4 bộ test hiện có: `BusinessFixesTest`, `CommerceIntegrityTest`, `FinancialAndLogisticsTest`, `SimulationAndPurchaseTest`: **92 test, 0 thất bại, 0 lỗi, 0 bỏ qua** trên H2 chế độ MSSQLServer.
- Lần kiểm tra dùng Surefire 3.5.6, JUnit Jupiter 5.12.2 và Byte Buddy agent có sẵn trong cache để phù hợp môi trường thực thi; các tùy chọn chỉ truyền khi chạy Maven, không sửa `pom.xml`.
- Log kiểm tra lưu tại `../sync-work/fixes_20261030/money-precision-tests.log` và `../sync-work/fixes_20261030/money-schema-check.log`. Chưa xác nhận khởi động với SQL Server thực tế vì kết nối bị chặn; kiểm tra offline và H2 không thay thế bước kiểm tra này.

## 17. Sửa biểu đồ trạng thái đơn hàng trên dashboard admin

### Lỗi và nguyên nhân

Biểu đồ cũ đặt cả vòng doughnut và chú giải canvas của 10 trạng thái trong một khung 250 × 250 px. Chú giải nhiều dòng chiếm phần lớn chiều cao, khiến vùng vẽ vòng tròn bị thu nhỏ. Khi khoảng thời gian không có đơn, dataset toàn số 0 cũng không vẽ được lát biểu đồ. Danh sách màu cũ chỉ có 5 màu cho 10 trạng thái.

### Thay đổi

- Tách chú giải thành danh sách HTML ngoài canvas; mỗi dòng có màu, tên trạng thái, số đơn và tỷ lệ. Tên dài tự xuống dòng, số liệu không bị ép vào chú giải canvas.
- Cấp riêng vùng vẽ cao 260 px, rộng theo card và tối đa 300 px; tổng số đơn nằm ở giữa vòng tròn. CSS được giới hạn trong các class của biểu đồ này.
- Khi không có đơn, hiển thị vòng xám, tổng 0 và thông báo rõ ràng. Giá trị `[1]` chỉ dùng để vẽ vòng xám, không cộng vào tổng/số liệu và không bật tooltip trên vòng giả lập.
- Gán 10 màu theo mã trạng thái để màu không đổi khi thứ tự dữ liệu thay đổi. Hover vẫn hiển thị số đơn và phần trăm trên tổng đơn của kỳ đã chọn.
- Chú giải dùng nút hỗ trợ bàn phím để ẩn/hiện từng trạng thái; cập nhật `aria-pressed`. Khi kỳ không có dữ liệu, các nút này bị vô hiệu hóa.
- Giữ bộ lọc tháng, mặc định tháng hiện tại, lựa chọn tất cả thời gian và các truy vấn/KPI backend.

Các file liên quan: `templates/admin/dashboard.html`, `static/css/admin-dashboard.css`, `static/js/admin-dashboard.js` trong `src/main/resources`; kiểm thử JavaScript tại `src/test/js/admin-dashboard.test.cjs`.

### Kiểm tra

- **5/5 test JavaScript đạt**: kỳ trống vẫn có vòng xám nhưng tổng là 0, đủ chú giải/màu cho 10 trạng thái, tỷ lệ tooltip đúng và màu ổn định khi đảo thứ tự, nút ẩn/hiện cập nhật biểu đồ và trạng thái truy cập, dữ liệu thiếu/sai không tạo NaN hoặc tổng âm.
- **4/4 test `DashboardMonthFilterTest` đạt**: render dashboard qua MockMvc, lọc qua tháng nhuận/cuối năm, khoảng tháng không hợp lệ, mặc định tháng hiện tại và lựa chọn tất cả thời gian.
- Log: `../sync-work/fixes_20261030/dashboard-chart-js-tests.log`, `../sync-work/fixes_20261030/dashboard-chart-mvc-tests.log`.
- Chưa xác minh trực quan trên Chrome: công cụ điều khiển giao diện dừng vì không xác định được URL hiện tại. Cần tải lại trang để kiểm tra trên trình duyệt đang sử dụng.

## 18. Khôi phục các tab gốc trong lịch sử đơn hàng khách hàng

- Áp dụng đúng tại trang **`/orders`**, phần **Quản Lý Đơn Hàng** trong ảnh tham chiếu `image(1).png`.
- Nhóm đầu giữ kiểu Bootstrap nav-pills bo tròn và nhãn cũ: **Tất cả, Đơn mới, Đã xác nhận, Đang giao, Đã giao, Đã hủy, Trả hàng/Hoàn tiền**.
- Nút **`>>`** cạnh thanh tab chỉ mở tab **Sẵn sàng nhận tại cửa hàng** (`READY_FOR_PICKUP`). Nút đổi thành **`<<`** để quay lại. Không tạo tab riêng cho giao thất bại, đang chuyển hoàn và đã nhận hoàn.
- Tab **Đang giao** lấy các đơn `SHIPPING` và `DELIVERY_FAILED`. Tab **Trả hàng/Hoàn tiền** lấy `RETURNING`, `RETURNED`, `RETURNED_REFUND`. Nhãn trên mỗi đơn vẫn hiện trạng thái thực tế, giúp khách theo dõi từng bước mà không phải chuyển qua nhiều tab.
- Chuyển nhóm bằng JavaScript chỉ đổi các tab đang hiển thị. Bấm một tab vẫn gửi đúng `status`, đưa về `page=0` và giữ kích thước trang; truy vấn chỉ lấy đơn của khách đang đăng nhập.
- Server tự mở nhóm bổ sung khi chọn `READY_FOR_PICKUP`, nên trạng thái đang chọn không bị ẩn khi tải lại hoặc chuyển trang đơn hàng. Liên kết cũ với `DELIVERY_FAILED` chọn tab Đang giao; `RETURNING` hoặc `RETURNED` chọn tab Trả hàng/Hoàn tiền.
- Thêm service riêng `getCustomerOrderHistory` và truy vấn `findByUserUserIdAndStatusIn` để lọc theo nhóm trong database trước khi đếm và phân trang, luôn giới hạn theo khách đang đăng nhập. Các hàm `getOrdersByUser` cũ vẫn lọc đúng trạng thái đơn lẻ như trước; không thay enum, trạng thái lưu trong DB hay quy trình chuyển trạng thái của nhân viên/shipper.
- Đã bỏ mũi tên phân nhóm thêm nhầm vào chú giải dashboard admin; giữ bản sửa vòng tròn, chú giải ngoài canvas và xử lý kỳ trống ở mục 17.
- File chính: `src/main/resources/templates/customer/order-history.html`, `src/main/resources/static/js/customer-order-tabs.js`. Test tại `src/test/js/customer-order-tabs.test.cjs` và `CustomerOrderFilterTest`.
- Kiểm tra bản gộp trạng thái: **13/13 test MockMvc/H2** và **3/3 test JavaScript** đạt. Bao gồm chỉ còn một tab bổ sung, đúng nhãn tiến trình trên đơn, lọc nhóm không lẫn khách hàng, tổng trang đúng và không trùng đơn, liên kết trạng thái cũ chọn đúng tab, API service lọc đơn lẻ giữ nguyên hành vi. Log Java tại `../sync-work/fixes_20261030/customer-order-groups-tests.log`. Chưa xác minh trực quan trên trình duyệt đang chạy.
