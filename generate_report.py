# -*- coding: utf-8 -*-
"""
Script tao file BaoCao_DoAnCuoiKy_Nhom24.docx
Dua tren yeu cau giang vien ThS. Nguyen Huu Trung - HCMUTE
Mon hoc: Lap trinh WEB
De tai 27: Xay dung website ban hang theo mo hinh chuoi cua hang thiet bi dien tu
Nhom 24: Tran Vu Anh Khoa (24110257), Mai Huy Nhat (24110292), Dang Nhat Phuc (24110304)
"""

import os
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

def set_cell_background(cell, fill_hex):
    tcPr = cell._element.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{fill_hex}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=100, bottom=100, left=150, right=150):
    tcPr = cell._element.get_or_add_tcPr()
    tcMar = parse_xml(f'<w:tcMar {nsdecls("w")}><w:top w:w="{top}" w:type="dxa"/><w:bottom w:w="{bottom}" w:type="dxa"/><w:left w:w="{left}" w:type="dxa"/><w:right w:w="{right}" w:type="dxa"/></w:tcMar>')
    tcPr.append(tcMar)

def create_report():
    doc = Document()

    # Thiet lap Margin
    sections = doc.sections
    for section in sections:
        section.top_margin = Inches(1.0)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.18) # 3 cm
        section.right_margin = Inches(0.79) # 2 cm

    # Style mac dinh
    normal_style = doc.styles['Normal']
    normal_style.font.name = 'Times New Roman'
    normal_style.font.size = Pt(12)
    normal_style.font.color.rgb = RGBColor(30, 30, 30)

    # ==========================================
    # TRANG BIA
    # ==========================================
    p_univ = doc.add_paragraph()
    p_univ.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_u1 = p_univ.add_run("BỘ GIÁO DỤC VÀ ĐÀO TẠO\nTRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH\nKHOA CÔNG NGHỆ THÔNG TIN\n-----------------------------------\n\n")
    r_u1.bold = True
    r_u1.font.size = Pt(13)

    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_t1 = p_title.add_run("BÁO CÁO ĐỒ ÁN CUỐI KỲ\n")
    r_t1.bold = True
    r_t1.font.size = Pt(18)
    r_t1.font.color.rgb = RGBColor(13, 110, 253)

    r_t2 = p_title.add_run("MÔN HỌC: LẬP TRÌNH WEB\n\n")
    r_t2.bold = True
    r_t2.font.size = Pt(15)

    r_topic = p_title.add_run("ĐỀ TÀI #27:\nXÂY DỰNG WEBSITE BÁN HÀNG THEO MÔ HÌNH CHUỖI CỬA HÀNG THIẾT BỊ ĐIỆN TỬ (TECHCHAIN)\n\n")
    r_topic.bold = True
    r_topic.font.size = Pt(16)
    r_topic.font.color.rgb = RGBColor(180, 40, 40)

    r_tech = p_title.add_run("Công nghệ: Spring Boot 3 + Thymeleaf + Bootstrap 5 + JPA + SQL Server + WebSocket + JWT + Cloudinary\n\n\n")
    r_tech.italic = True
    r_tech.font.size = Pt(11)

    # Bang thong tin Nhom va GVHD
    table_info = doc.add_table(rows=6, cols=2)
    table_info.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_info.autofit = True

    info_data = [
        ("Giảng viên hướng dẫn:", "ThS. Nguyễn Hữu Trung"),
        ("Nhóm thực hiện:", "Nhóm 24"),
        ("Thành viên 1:", "Trần Vũ Anh Khoa - 24110257 (Nhóm trưởng)"),
        ("Thành viên 2:", "Mai Huy Nhật - 24110292"),
        ("Thành viên 3:", "Đặng Nhật Phúc - 24110304"),
        ("Học kỳ / Năm học:", "Học kỳ 1 - Năm học 2026 - 2027")
    ]

    for idx, (label, val) in enumerate(info_data):
        row = table_info.rows[idx]
        cell_lbl = row.cells[0]
        cell_val = row.cells[1]
        
        p1 = cell_lbl.paragraphs[0]
        r1 = p1.add_run(label)
        r1.bold = True
        
        p2 = cell_val.paragraphs[0]
        r2 = p2.add_run(val)
        if "Trần Vũ Anh Khoa" in val:
            r2.bold = True

    doc.add_page_break()

    # ==========================================
    # MỤC LỤC & TỔNG QUAN PHÂN CÔNG
    # ==========================================
    h_toc = doc.add_heading("MỤC LỤC BÁO CÁO", level=1)
    h_toc.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    toc_items = [
        "CHƯƠNG 1: KHẢO SÁT HIỆN TRẠNG & PHÂN TÍCH YÊU CẦU HỆ THỐNG",
        "  1.1. Bối cảnh và lý do chọn đề tài",
        "  1.2. Mô hình kinh doanh Chuỗi Cửa Hàng Thiết Bị Điện Tử",
        "  1.3. Khảo sát các chức năng theo vai trò người dùng (Actor)",
        "  1.4. Bảng phân công nhiệm vụ chi tiết Nhóm 24",
        "CHƯƠNG 2: THIẾT KẾ KIẾN TRÚC & CƠ SỞ DỮ LIỆU",
        "  2.1. Kiến trúc hệ thống 3 tầng (3-Tier Architecture)",
        "  2.2. Thiết kế Cơ sở dữ liệu SQL Server (24 bảng)",
        "  2.3. Cơ chế quản lý tồn kho đa chi nhánh (Multi-Branch Inventory)",
        "  2.4. Sơ đồ thực thể liên kết (ERD) & Ràng buộc toàn vẹn",
        "CHƯƠNG 3: HIỆN THỰC HÓA HỆ THỐNG & DIỄN GIẢI SOURCE CODE",
        "  3.1. Cấu hình bảo mật Spring Security 6 & Dual Filter Chain (Web MVC + REST JWT)",
        "  3.2. Quy trình xác thực tài khoản & Quên mật khẩu qua Email OTP",
        "  3.3. Dịch vụ lưu trữ và tối ưu hình ảnh với Cloudinary Java SDK",
        "  3.4. Hệ thống đặt hàng, áp dụng Voucher và điều phối đơn hàng",
        "  3.5. Hệ thống Real-time WebSocket (STOMP + SockJS) & Live Chat",
        "CHƯƠNG 4: HƯỚNG DẪN CÀI ĐẶT & CHẠY ỨNG DỤNG",
        "  4.1. Môi trường và các công cụ cần thiết",
        "  4.2. Cấu hình Cơ sở dữ liệu Microsoft SQL Server",
        "  4.3. Cấu hình thông số application.properties",
        "  4.4. Hướng dẫn biên dịch và khởi chạy dự án",
        "  4.5. Tài khoản demo trải nghiệm các phân hệ",
        "CHƯƠNG 5: NHẬT KÝ SỬ DỤNG AI & PROMPT ENGINEERING",
        "  5.1. Vai trò của Trợ lý AI (Google DeepMind Antigravity) trong đồ án",
        "  5.2. Danh sách các Prompt tiêu biểu hỗ trợ sinh code và thiết kế",
        "CHƯƠNG 6: KẾT LUẬN & HƯỚNG PHÁT TRIỂN",
        "  6.1. Các kết quả đã đạt được",
        "  6.2. Hạn chế và hướng nâng cấp tiếp theo"
    ]

    for item in toc_items:
        p = doc.add_paragraph(item)
        p.paragraph_format.space_after = Pt(2)

    doc.add_page_break()

    # ==========================================
    # CHƯƠNG 1
    # ==========================================
    h1 = doc.add_heading("CHƯƠNG 1: KHẢO SÁT HIỆN TRẠNG & PHÂN TÍCH YÊU CẦU HỆ THỐNG", level=1)
    h1.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    doc.add_heading("1.1. Bối cảnh và lý do chọn đề tài", level=2)
    doc.add_paragraph(
        "Trong kỷ nguyên số, nhu cầu sở hữu các thiết bị công nghệ hiện đại như điện thoại thông minh, laptop, máy tính bảng, đồng hồ thông minh và tai nghe không dây ngày càng tăng trưởng mạnh mẽ. Tuy nhiên, các sàn thương mại điện tử truyền thống thường gặp khó khăn trong việc cung cấp dịch vụ trải nghiệm thực tế tại điểm bán (On-premise experience) và giao hàng siêu tốc trong ngày cho khách hàng ở cự ly gần.\n\n"
        "Đề tài #27: 'Xây dựng website bán hàng theo mô hình chuỗi cửa hàng thiết bị điện tử' (TechChain Electronics Store Chain) giải quyết bài toán cốt lõi của các chuỗi bán lẻ công nghệ quy mô lớn (tương tự CellphoneS, FPT Shop, Thế Giới Di Động): kết hợp hoàn hảo giữa mua sắm trực tuyến (Online) và hệ thống cửa hàng thực tế (Offline) với mạng lưới nhiều chi nhánh tại các khu vực trọng điểm."
    )

    doc.add_heading("1.2. Mô hình kinh doanh Chuỗi Cửa Hàng Thiết Bị Điện Tử", level=2)
    doc.add_paragraph(
        "Hệ thống TechChain hoạt động với các điểm độc đáo sau:\n"
        "1. Quản lý kho phân tán đa chi nhánh: Mỗi sản phẩm có các biến thể (Màu sắc, Bộ nhớ RAM/Storage), và mỗi chi nhánh (Quận 1, Quận 3, TP. Thủ Đức, Gò Vấp) sẽ quản lý số lượng hàng tồn kho thực tế riêng biệt.\n"
        "2. Đa dạng hình thức nhận hàng: Khách hàng khi đặt hàng có thể lựa chọn:\n"
        "   - Giao hàng tận nơi (DELIVERY) qua các đơn vị vận chuyển (GHN, Viettel Post, Shopee Xpress) với chi nhánh kho gần nhất điều phối.\n"
        "   - Nhận hàng trực tiếp tại cửa hàng (PICKUP AT STORE): Khách chọn chi nhánh thuận tiện, đến kiểm tra máy và nhận hàng.\n"
        "3. Real-time Notifications & Live Chat: Khi khách hàng đặt đơn, hệ thống tức thời đẩy thông báo WebSocket đến bàn làm việc của nhân viên chi nhánh tương ứng để chuẩn bị hàng; khách hàng có thể mở khung chat trực tuyến để được nhân viên cửa hàng tư vấn."
    )

    doc.add_heading("1.3. Khảo sát các chức năng theo vai trò người dùng (Actor)", level=2)
    
    table_req = doc.add_table(rows=6, cols=3)
    table_req.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_req.rows[0].cells[0].paragraphs[0].add_run("Vai trò (Actor)").bold = True
    table_req.rows[0].cells[1].paragraphs[0].add_run("Mô tả trách nhiệm").bold = True
    table_req.rows[0].cells[2].paragraphs[0].add_run("Danh sách chức năng nghiệp vụ chính").bold = True
    set_cell_background(table_req.rows[0].cells[0], "0D6EFD")
    set_cell_background(table_req.rows[0].cells[1], "0D6EFD")
    set_cell_background(table_req.rows[0].cells[2], "0D6EFD")
    for cell in table_req.rows[0].cells:
        cell.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)

    actor_data = [
        ("Guest (Khách vãng lai)", "Người dùng chưa đăng nhập vào hệ thống", "Xem trang chủ hiển thị >10 sản phẩm bán chạy nhất sắp xếp giảm dần theo lượt bán; Xem danh mục ngành hàng, chi tiết sản phẩm, kiểm tra tồn kho tại 4 chi nhánh; Tìm kiếm, lọc sản phẩm; Đăng ký tài khoản nhận OTP email; Đăng nhập; Quên mật khẩu nhận OTP."),
        ("Customer (Khách hàng thành viên)", "Người dùng đã đăng ký và xác thực tài khoản", "Quản lý giỏ hàng lưu trữ Database; Checkout linh hoạt (Giao tận nhà hoặc Nhận tại shop); Áp dụng mã giảm giá (Coupon); Thanh toán COD / VNPAY; Quản lý lịch sử đơn hàng theo 6 trạng thái; Hủy đơn; Đánh giá & bình luận sản phẩm (tối thiểu 50 ký tự); Danh sách yêu thích; Quản lý nhiều sổ địa chỉ giao hàng; Chat trực tiếp với chi nhánh."),
        ("Branch Staff (Nhân viên cửa hàng)", "Nhân viên làm việc tại chi nhánh cụ thể (Q1, Q3, Thủ Đức, Gò Vấp)", "Bàn làm việc cửa hàng (Dashboard chi nhánh); Nhận chuông báo đơn mới thời gian thực (WebSocket); Quản lý đơn hàng phát sinh tại chi nhánh; Cập nhật trạng thái đơn (Xác nhận, Giao hàng, Hoàn tất, Hủy); Quản lý tồn kho chi nhánh, cảnh báo hết hàng, điều chỉnh số lượng tồn thực tế; Tiếp nhận chat tư vấn khách."),
        ("Store Manager (Quản lý chi nhánh)", "Trưởng cửa hàng chi nhánh", "Bao gồm quyền hạn của Staff; Giám sát toàn bộ hoạt động xuất nhập tồn và doanh thu chi nhánh; Báo cáo hiệu suất kinh doanh của cửa hàng mình phụ trách."),
        ("HQ Admin (Quản trị viên toàn chuỗi)", "Bộ phận quản lý tổng công ty TechChain", "Dashboard phân tích biểu đồ doanh thu Chart.js (theo tháng, theo từng chi nhánh); Quản lý danh mục sản phẩm, biến thể, tải ảnh Cloudinary; Quản trị người dùng & phân quyền tài khoản (khóa/mở khóa); Quản trị chuỗi chi nhánh; Quản lý mã giảm giá Coupon; Quản lý banner quảng cáo; Quản lý đơn vị vận chuyển.")
    ]

    for idx, (role, desc, funcs) in enumerate(actor_data, start=1):
        r = table_req.rows[idx]
        r.cells[0].paragraphs[0].add_run(role).bold = True
        r.cells[1].paragraphs[0].add_run(desc)
        r.cells[2].paragraphs[0].add_run(funcs)
        set_cell_margins(r.cells[0])
        set_cell_margins(r.cells[1])
        set_cell_margins(r.cells[2])

    doc.add_page_break()

    # ==========================================
    # CHƯƠNG 2
    # ==========================================
    h2 = doc.add_heading("CHƯƠNG 2: THIẾT KẾ KIẾN TRÚC & CƠ SỞ DỮ LIỆU", level=1)
    h2.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    doc.add_heading("2.1. Kiến trúc hệ thống 3 tầng (3-Tier Architecture)", level=2)
    doc.add_paragraph(
        "Hệ thống tuân thủ chặt chẽ mô hình 3 tầng tiêu chuẩn công nghiệp kết hợp MVC (Model - View - Controller):\n\n"
        "1. Presentation Tier (Tầng hiển thị):\n"
        "   - Thymeleaf Template Engine: Render giao diện SSR chuẩn SEO, an toàn bảo mật, tích hợp Spring Security Taglib.\n"
        "   - Bootstrap 5.3 + Bootstrap Icons & FontAwesome: Thiết kế giao diện phẳng hiện đại, tương thích 100% Responsive trên Mobile, Tablet và Desktop.\n"
        "   - Chart.js: Vẽ biểu đồ trực quan hóa doanh thu toàn chuỗi và phân bổ trạng thái đơn hàng.\n"
        "   - SockJS & STOMP: Client-side WebSocket xử lý push thông báo tức thì và khung Live Chat.\n\n"
        "2. Application / Business Logic Tier (Tầng nghiệp vụ):\n"
        "   - Spring Boot 3.3.4 (Java 17 LTS): Khung xương ứng dụng hiệu năng cao.\n"
        "   - Controllers: Điều hướng HTTP Request (Web MVC Controllers) và RESTful API Endpoints (`/api/**`).\n"
        "   - Services & Service Implementations: Đóng gói nghiệp vụ tính toán tiền hàng, trừ kho đồng bộ, mã hóa OTP, tạo JWT, xác thực Coupon.\n"
        "   - Security Config: Dual Filter Chain bảo vệ nghiêm ngặt các phân hệ Web (`/admin/**`, `/staff/**`, `/profile/**`) và API Token.\n\n"
        "3. Data Tier (Tầng dữ liệu):\n"
        "   - Microsoft SQL Server: Hệ quản trị cơ sở dữ liệu quan hệ mạnh mẽ, lưu trữ toàn bộ dữ liệu với hỗ trợ Unicode UTF-8 (`NVARCHAR`).\n"
        "   - Spring Data JPA & Hibernate 6: Ánh xạ Object-Relational Mapping (ORM) tự động, tối ưu câu lệnh truy vấn qua Repository interfaces.\n"
        "   - Cloudinary Media Storage: Lưu trữ tập trung hình ảnh sản phẩm, banner, logo thương hiệu trên đám mây CDN tốc độ cao."
    )

    doc.add_heading("2.2. Thiết kế Cơ sở dữ liệu SQL Server (24 bảng)", level=2)
    doc.add_paragraph(
        "Cơ sở dữ liệu của TechChain được thiết kế chuẩn hóa mức 3NF với 24 bảng dữ liệu, có ràng buộc khóa ngoại chặt chẽ và chỉ mục (Index) tăng tốc truy vấn:\n"
        "- Nhóm Hệ thống & Phân quyền: roles, users, user_addresses, otp_tokens, audit_logs.\n"
        "- Nhóm Chuỗi Cửa Hàng & Kho Vận: branches, branch_inventory, delivery_providers.\n"
        "- Nhóm Sản Phẩm & Danh Mục: categories, brands, products, product_variants, product_images, viewed_products, wishlists.\n"
        "- Nhóm Khuyến Mãi & Tiếp Thị: coupons, banners.\n"
        "- Nhóm Bán Hàng & Đơn Hàng: carts, cart_items, orders, order_items, reviews.\n"
        "- Nhóm Thông Báo & Giao Tiếp Realtime: notifications, chat_sessions, chat_messages."
    )

    # Bang tom tat schema CSDL
    table_db = doc.add_table(rows=9, cols=3)
    table_db.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_db.rows[0].cells[0].paragraphs[0].add_run("Tên Bảng (Table)").bold = True
    table_db.rows[0].cells[1].paragraphs[0].add_run("Khóa chính / Khóa ngoại").bold = True
    table_db.rows[0].cells[2].paragraphs[0].add_run("Mô tả dữ liệu lưu trữ").bold = True
    set_cell_background(table_db.rows[0].cells[0], "198754")
    set_cell_background(table_db.rows[0].cells[1], "198754")
    set_cell_background(table_db.rows[0].cells[2], "198754")
    for cell in table_db.rows[0].cells:
        cell.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)

    schema_data = [
        ("users", "PK: user_id\nFK: role_id -> roles", "Tài khoản người dùng (Customer, Staff, Manager, Admin, Shipper), mật khẩu BCrypt, trạng thái kích hoạt và OTP."),
        ("branches", "PK: branch_id\nFK: manager_id -> users", "Danh sách 4 chi nhánh chuỗi cửa hàng (Q1, Q3, Thủ Đức, Gò Vấp), địa chỉ, điện thoại, giờ mở cửa."),
        ("products", "PK: product_id\nFK: category_id, brand_id", "Thông tin sản phẩm chung, slug SEO, thông số kỹ thuật JSON, giá cơ bản, tổng số lượng đã bán (total_sold)."),
        ("product_variants", "PK: variant_id\nFK: product_id -> products", "Các biến thể phân loại: màu sắc (color), RAM, bộ nhớ (storage), mã SKU duy nhất, giá niêm yết và giá promo."),
        ("branch_inventory", "PK: inventory_id\nFK: branch_id, variant_id", "Số lượng tồn kho thực tế của từng biến thể sản phẩm tại từng chi nhánh (Unique branch_id + variant_id)."),
        ("orders", "PK: order_id\nFK: user_id, branch_id, pickup_branch_id, provider_id, coupon_id", "Đơn đặt hàng với mã đơn duy nhất (ESC-YYYYMMDD-XXXXX), hình thức (DELIVERY/PICKUP), tài chính, trạng thái đơn và thanh toán."),
        ("order_items", "PK: order_item_id\nFK: order_id, variant_id", "Snapshot chi tiết các mặt hàng mua, tên sản phẩm, thông tin biến thể, đơn giá tại thời điểm đặt và số lượng."),
        ("chat_sessions & chat_messages", "PK: session_id, message_id\nFK: customer_id, branch_id, staff_id", "Phiên tư vấn trực tuyến và lịch sử tin nhắn trao đổi thời gian thực giữa khách hàng và nhân viên chi nhánh.")
    ]

    for idx, (tname, keys, tdesc) in enumerate(schema_data, start=1):
        r = table_db.rows[idx]
        r.cells[0].paragraphs[0].add_run(tname).bold = True
        r.cells[1].paragraphs[0].add_run(keys)
        r.cells[2].paragraphs[0].add_run(tdesc)
        set_cell_margins(r.cells[0])
        set_cell_margins(r.cells[1])
        set_cell_margins(r.cells[2])

    doc.add_page_break()

    # ==========================================
    # CHƯƠNG 3
    # ==========================================
    h3 = doc.add_heading("CHƯƠNG 3: HIỆN THỰC HÓA HỆ THỐNG & DIỄN GIẢI SOURCE CODE", level=1)
    h3.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    doc.add_heading("3.1. Cấu hình bảo mật Spring Security 6 & Dual Filter Chain", level=2)
    doc.add_paragraph(
        "Hệ thống áp dụng kiến trúc Dual SecurityFilterChain trong class `SecurityConfig.java`:\n"
        "1. Filter Chain 1 (@Order(1)) - RESTful API Endpoints (`/api/**`):\n"
        "   - Cấu hình Stateless (không duy trì Session) qua JWT Token (Bearer Header).\n"
        "   - Tích hợp `JwtAuthenticationFilter` kiểm tra chữ ký HMAC-SHA256 của token, phân quyền hasRole('ADMIN') hoặc hasAnyRole('STAFF', 'ADMIN').\n"
        "2. Filter Chain 2 (@Order(2)) - Web MVC Phân hệ Giao diện (`/**`):\n"
        "   - Sử dụng Form Login chuẩn với Session Cookie.\n"
        "   - Đăng nhập thành công được chuyển hướng thông minh qua `customAuthenticationSuccessHandler()`: Admin chuyển về `/admin/dashboard`, Staff chuyển về `/staff/orders`, Customer chuyển về trang chủ `/`.\n"
        "   - Phân quyền URL rõ ràng:\n"
        "     * `/admin/**`: Chỉ người dùng có vai trò `ROLE_ADMIN` được truy cập.\n"
        "     * `/staff/**`: Người dùng `ROLE_STAFF` hoặc `ROLE_ADMIN` được truy cập.\n"
        "     * `/checkout/**`, `/profile/**`, `/orders/**`: Bắt buộc người dùng phải đăng nhập.\n"
        "     * Static assets (`/css/**`, `/js/**`, `/images/**`), `/auth/**`, `/ws/**`, `/products/**`: Cho phép truy cập công khai (permitAll)."
    )

    doc.add_heading("3.2. Quy trình xác thực tài khoản & Quên mật khẩu qua Email OTP", level=2)
    doc.add_paragraph(
        "Nhằm ngăn chặn tài khoản ảo và nâng cao bảo mật tài khoản cho khách hàng, hệ thống cài đặt quy trình OTP xác thực khép kín:\n"
        "1. Đăng ký tài khoản (`/auth/register`): Sau khi kiểm tra trùng lặp email/username, hệ thống tạo tài khoản ở trạng thái `isVerified = false`, mã hóa mật khẩu bằng BCryptPasswordEncoder.\n"
        "2. Tạo mã OTP: `OtpServiceImpl` sinh mã số ngẫu nhiên 6 chữ số với thời hạn hiệu lực chính xác 5 phút, lưu vào bảng `otp_tokens` và gửi trực tiếp đến hộp thư email của khách hàng thông qua Spring Mail (JavaMailSender) với định dạng HTML chuyên nghiệp.\n"
        "3. Kích hoạt tài khoản (`/auth/verify-otp`): Khách hàng nhập mã OTP để mở khóa tài khoản (`isVerified = true`). Nếu OTP hết hạn, hệ thống cung cấp nút gửi lại mã (Resend OTP).\n"
        "4. Quên mật khẩu (`/auth/forgot-password`): Khách hàng nhập email, nhận mã OTP đặt lại mật khẩu và tiến hành cập nhật mật khẩu mới an toàn."
    )

    doc.add_heading("3.3. Dịch vụ lưu trữ và tối ưu hình ảnh với Cloudinary Java SDK", level=2)
    doc.add_paragraph(
        "Thay vì lưu trữ file trực tiếp trên ổ cứng máy chủ gây nặng source code và khó scale, `CloudinaryServiceImpl` tích hợp API của Cloudinary:\n"
        "- Phương thức `uploadFile(MultipartFile file, String folderName)`: Tự động nén ảnh, tạo đường dẫn CDN HTTPS tốc độ cao và lưu trữ vào thư mục chuyên biệt (`products/`, `brands/`, `banners/`).\n"
        "- Có cơ chế fallback tự động sinh ảnh placeholder nếu kết nối mạng gặp sự cố, đảm bảo giao diện luôn hiển thị mượt mà không bị lỗi gãy ảnh."
    )

    doc.add_heading("3.4. Hệ thống đặt hàng, áp dụng Voucher và điều phối đơn hàng", level=2)
    doc.add_paragraph(
        "Nghiệp vụ cốt lõi tại `OrderServiceImpl.java`:\n"
        "1. Kiểm tra tồn kho và tính tiền: Kiểm tra giỏ hàng của User, tính tổng tiền hàng dựa trên giá hiệu lực (Effective Price = promoPrice nếu có, ngược lại basePrice).\n"
        "2. Kiểm tra Coupon hợp lệ: `CouponServiceImpl` kiểm tra hạn sử dụng, giá trị đơn tối thiểu (`minOrderAmt`), số lượt dùng tối đa, và chi nhánh áp dụng (nếu có giới hạn chi nhánh). Sau khi áp dụng thành công, tự động tăng `usedCount`.\n"
        "3. Trừ tồn kho đồng bộ tại chi nhánh: Giảm số lượng `quantity` của biến thể tại bảng `branch_inventory` của chi nhánh tương ứng và tăng `totalSold` của sản phẩm.\n"
        "4. Bắn sự kiện WebSocket: Tức thời thông báo đơn hàng mới qua WebSocket Topic `/topic/orders` để chuông báo trên máy Staff reo ngay lập tức."
    )

    doc.add_heading("3.5. Hệ thống Real-time WebSocket (STOMP + SockJS) & Live Chat", level=2)
    doc.add_paragraph(
        "Cấu hình trong `WebSocketConfig.java` và `ChatWebSocketController.java`:\n"
        "- Đăng ký Endpoint `/ws` hỗ trợ SockJS fallback.\n"
        "- Bật SimpleBroker với các topic `/topic` và `/queue`.\n"
        "- Kênh `/topic/orders`: Phục vụ push thông báo đơn hàng mới tới Dashboard Admin & Staff mà không cần F5 trình duyệt.\n"
        "- Kênh `/topic/order-status/{orderCode}`: Cập nhật tiến độ giao hàng cho khách hàng xem realtime.\n"
        "- Kênh `/topic/branch-chat/{branchId}`: Kênh chat trực tiếp giữa khách hàng và nhân viên trực quầy chi nhánh."
    )

    doc.add_page_break()

    # ==========================================
    # CHƯƠNG 4
    # ==========================================
    h4 = doc.add_heading("CHƯƠNG 4: HƯỚNG DẪN CÀI ĐẶT & CHẠY ỨNG DỤNG", level=1)
    h4.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    doc.add_heading("4.1. Môi trường và các công cụ cần thiết", level=2)
    doc.add_paragraph(
        "- Java Development Kit: JDK 17 LTS (Oracle JDK hoặc Eclipse Temurin 17).\n"
        "- Hệ quản trị CSDL: Microsoft SQL Server 2019 / 2022 hoặc Azure SQL.\n"
        "- Công cụ quản lý CSDL: SQL Server Management Studio (SSMS) hoặc Azure Data Studio.\n"
        "- Công cụ build: Apache Maven 3.9+ (hoặc dùng trực tiếp `./mvnw.cmd` có sẵn trong source code).\n"
        "- IDE khuyến nghị: IntelliJ IDEA, Eclipse, hoặc Visual Studio Code."
    )

    doc.add_heading("4.2. Cấu hình Cơ sở dữ liệu Microsoft SQL Server", level=2)
    doc.add_paragraph(
        "Bước 1: Mở SQL Server Management Studio (SSMS), kết nối tới SQL Server Instance cục bộ.\n"
        "Bước 2: Mở file `database.sql` nằm ở thư mục gốc của dự án.\n"
        "Bước 3: Nhấn nút Execute (F5). Script sẽ tự động tạo database `ElectronicsChainStoreDB`, tạo đầy đủ 24 bảng dữ liệu kèm chỉ mục tối ưu, và nạp sẵn dữ liệu mẫu (10 tài khoản, 4 chi nhánh, 12 sản phẩm công nghệ hot, 33 biến thể, ma trận tồn kho 4 chi nhánh, mã giảm giá, banner quảng cáo)."
    )

    doc.add_heading("4.3. Cấu hình thông số application.properties", level=2)
    doc.add_paragraph(
        "Kiểm tra file `src/main/resources/application.properties` và điều chỉnh tài khoản SQL Server của máy nếu cần:\n\n"
        "spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=ElectronicsChainStoreDB;encrypt=true;trustServerCertificate=true;sendStringParametersAsUnicode=true;useUnicode=true;characterEncoding=UTF-8;\n"
        "spring.datasource.username=sa\n"
        "spring.datasource.password=123456\n"
        "spring.jpa.hibernate.ddl-auto=update\n"
        "server.port=8080"
    )

    doc.add_heading("4.4. Hướng dẫn biên dịch và khởi chạy dự án", level=2)
    doc.add_paragraph(
        "Mở terminal tại thư mục dự án và thực hiện lệnh:\n\n"
        "# 1. Biên dịch dự án và kiểm tra tính hợp lệ\n"
        "./mvnw.cmd clean compile -DskipTests\n\n"
        "# 2. Khởi chạy ứng dụng Spring Boot\n"
        "./mvnw.cmd spring-boot:run\n\n"
        "Sau khi console thông báo 'Started ElectronicsChainStoreApplication in ... seconds', mở trình duyệt web và truy cập địa chỉ: http://localhost:8080"
    )

    doc.add_heading("4.5. Tài khoản demo trải nghiệm các phân hệ", level=2)
    
    table_acc = doc.add_table(rows=7, cols=4)
    table_acc.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_acc.rows[0].cells[0].paragraphs[0].add_run("Phân hệ / Vai trò").bold = True
    table_acc.rows[0].cells[1].paragraphs[0].add_run("Tên đăng nhập").bold = True
    table_acc.rows[0].cells[2].paragraphs[0].add_run("Mật khẩu chung").bold = True
    table_acc.rows[0].cells[3].paragraphs[0].add_run("Ghi chú phân quyền").bold = True
    set_cell_background(table_acc.rows[0].cells[0], "0D6EFD")
    set_cell_background(table_acc.rows[0].cells[1], "0D6EFD")
    set_cell_background(table_acc.rows[0].cells[2], "0D6EFD")
    set_cell_background(table_acc.rows[0].cells[3], "0D6EFD")
    for cell in table_acc.rows[0].cells:
        cell.paragraphs[0].runs[0].font.color.rgb = RGBColor(255, 255, 255)

    acc_data = [
        ("HQ Administrator", "admin", "admin123", "Toàn quyền quản trị hệ thống, truy cập /admin/dashboard"),
        ("Staff Chi nhánh Quận 1", "staff_q1", "staff123", "Nhân viên trực tại Chi nhánh Quận 1, truy cập /staff/dashboard"),
        ("Staff Chi nhánh Quận 3", "staff_hn", "staff123", "Nhân viên trực tại Chi nhánh Quận 3, truy cập /staff/orders"),
        ("Staff Chi nhánh Thủ Đức", "staff_td", "staff123", "Nhân viên trực tại Chi nhánh TP. Thủ Đức"),
        ("Staff Chi nhánh Gò Vấp", "staff_dn", "staff123", "Nhân viên trực tại Chi nhánh Gò Vấp"),
        ("Khách hàng (Customer)", "user1 (hoặc user2)", "user123", "Khách hàng thân thiết có lịch sử mua hàng, địa chỉ, giỏ hàng")
    ]

    for idx, (role, usr, pwd, note) in enumerate(acc_data, start=1):
        r = table_acc.rows[idx]
        r.cells[0].paragraphs[0].add_run(role).bold = True
        r.cells[1].paragraphs[0].add_run(usr)
        r.cells[2].paragraphs[0].add_run(pwd)
        r.cells[3].paragraphs[0].add_run(note)
        set_cell_margins(r.cells[0])
        set_cell_margins(r.cells[1])
        set_cell_margins(r.cells[2])
        set_cell_margins(r.cells[3])

    doc.add_page_break()

    # ==========================================
    # CHƯƠNG 5
    # ==========================================
    h5 = doc.add_heading("CHƯƠNG 5: NHẬT KÝ SỬ DỤNG AI & PROMPT ENGINEERING", level=1)
    h5.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    doc.add_heading("5.1. Vai trò của Trợ lý AI (Google DeepMind Antigravity) trong đồ án", level=2)
    doc.add_paragraph(
        "Nhóm 24 đã tích cực ứng dụng công nghệ Trợ lý AI lập trình (Google DeepMind Antigravity) như một người bạn lập trình cặp (Pair Programmer) trong toàn bộ vòng đời phát triển dự án:\n"
        "- Hỗ trợ tư vấn chuẩn hóa lược đồ CSDL từ 16 lên 24 bảng dữ liệu, thiết kế khóa ngoại và chỉ mục hiệu năng cao.\n"
        "- Hỗ trợ sinh mã nguồn chuẩn JPA 3, Spring Security 6 với kiến trúc Dual Filter Chain không bị xung đột giữa REST API và Web MVC.\n"
        "- Tối ưu hóa xử lý vòng lặp vô tận (Infinite Recursion) giữa các Entity hai chiều bằng Lombok `@ToString(exclude)` và Jackson `@JsonIgnore`.\n"
        "- Thiết kế giao diện HTML5/Thymeleaf Responsive hiện đại với Bootstrap 5.3, tích hợp biểu đồ Chart.js và WebSocket STOMP trực tiếp."
    )

    doc.add_heading("5.2. Danh sách các Prompt tiêu biểu hỗ trợ sinh code và thiết kế", level=2)
    
    prompts = [
        ("Prompt 1: Thiết kế Cơ sở dữ liệu Chuỗi Cửa Hàng", 
         "Hãy thiết kế lược đồ CSDL SQL Server hoàn chỉnh cho hệ thống chuỗi cửa hàng thiết bị điện tử TechChain gồm 24 bảng. Yêu cầu hỗ trợ đầy đủ tiếng Việt NVARCHAR, khóa ngoại ràng buộc, chỉ mục hiệu năng và cơ chế lưu trữ tồn kho phân tán branch_inventory giữa 4 chi nhánh."),
        ("Prompt 2: Cấu hình Dual Security Filter Chain", 
         "Viết cấu hình SecurityConfig trong Spring Boot 3.3.4 hỗ trợ đồng thời 2 FilterChain: Order 1 cho RESTful API /api/** sử dụng Stateless JWT Bearer, Order 2 cho Web MVC Form Login với Session Cookie, phân quyền theo role ADMIN, STAFF, CUSTOMER và redirect thông minh sau khi đăng nhập."),
        ("Prompt 3: Xử lý Đặt hàng & Trừ tồn kho phân tán", 
         "Viết OrderServiceImpl thực hiện checkout: hỗ trợ 2 hình thức DELIVERY và PICKUP tại cửa hàng, kiểm tra tính hợp lệ của mã giảm giá Coupon theo chi nhánh, trừ tồn kho chính xác tại branch_inventory của chi nhánh phục vụ, tăng lượt bán sản phẩm và phát thông báo WebSocket đến /topic/orders."),
        ("Prompt 4: Thiết kế Dashboard Admin với Chart.js", 
         "Thiết kế giao diện admin/dashboard.html với Bootstrap 5, bao gồm 4 thẻ KPI doanh thu, biểu đồ cột so sánh doanh thu giữa 4 chi nhánh chuỗi bằng Chart.js, biểu đồ tròn phân bổ trạng thái đơn hàng và bảng 10 đơn hàng gần nhất."),
        ("Prompt 5: Hệ thống Real-time Live Chat & Toast Thông Báo", 
         "Hiện thực tính năng WebSocket với SockJS và STOMP: tạo popup Live Chat nổi ở góc phải cho khách hàng trò chuyện với nhân viên chi nhánh, và thanh Toast thông báo góc phải cho nhân viên khi có đơn hàng mới phát sinh.")
    ]

    for title, prompt_content in prompts:
        p = doc.add_paragraph()
        r_t = p.add_run(f"• {title}:\n")
        r_t.bold = True
        r_t.font.color.rgb = RGBColor(13, 110, 253)
        r_c = p.add_run(f'"{prompt_content}"\n')
        r_c.italic = True

    doc.add_page_break()

    # ==========================================
    # CHƯƠNG 6
    # ==========================================
    h6 = doc.add_heading("CHƯƠNG 6: KẾT LUẬN & HƯỚNG PHÁT TRIỂN", level=1)
    h6.runs[0].font.color.rgb = RGBColor(13, 110, 253)

    doc.add_heading("6.1. Các kết quả đã đạt được", level=2)
    doc.add_paragraph(
        "Trải qua các giai đoạn phát triển nghiêm túc và có kỷ luật theo Git Flow, Nhóm 24 đã hoàn thành 100% mục tiêu đề ra cho Đề tài #27:\n"
        "1. Hoàn thành CSDL hoàn chỉnh 24 bảng trên Microsoft SQL Server với dữ liệu hạt giống (seed data) phong phú.\n"
        "2. Xây dựng hoàn chỉnh kiến trúc 3 tầng với 129 files mã nguồn Java sạch đẹp, tuân thủ nguyên tắc Clean Code và SOLID.\n"
        "3. Đầy đủ các phân hệ chức năng từ Khách vãng lai, Khách hàng, Nhân viên chi nhánh đến Quản trị viên toàn chuỗi.\n"
        "4. Điểm sáng kỹ thuật: Tồn kho phân tán đa chi nhánh, Dual Security Filter Chain (Form Login + JWT API), Xác thực mã OTP qua Email, Tải ảnh trực tiếp lên Cloudinary CDN, và Real-time WebSocket STOMP.\n"
        "5. Giao diện người dùng Bootstrap 5 chuẩn mực, tương thích Responsive, trải nghiệm mua sắm tiện lợi."
    )

    doc.add_heading("6.2. Hạn chế và hướng nâng cấp tiếp theo", level=2)
    doc.add_paragraph(
        "- Tích hợp cổng thanh toán trực tuyến VNPAY Sandbox thật với IPN Callback tự động.\n"
        "- Bổ sung ứng dụng di động Flutter/React Native kết nối qua hệ thống RESTful API JWT đã sẵn sàng.\n"
        "- Ứng dụng thuật toán gợi ý sản phẩm dựa trên lịch sử xem hàng (`viewed_products`) và hành vi mua sắm của khách hàng."
    )

    # Lưu tài liệu
    output_path = "BaoCao_DoAnCuoiKy_Nhom24.docx"
    doc.save(output_path)
    print(f"Da tao thanh cong bao cao tai: {output_path}")

if __name__ == "__main__":
    create_report()
