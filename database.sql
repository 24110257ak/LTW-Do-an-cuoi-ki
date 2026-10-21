-- ==================================================================================
-- HỆ THỐNG WEBSITE BÁN HÀNG THEO MÔ HÌNH CHUỖI CỬA HÀNG THIẾT BỊ ĐIỆN TỬ
-- Đề tài 27 - Nhóm 24 - Môn Lập Trình WEB - HCMUTE 2026
-- Thành viên:
--   24110257 - Trần Vũ Anh Khoa
--   24110292 - Mai Huy Nhật (Nhóm trưởng)
--   24110304 - Đặng Nhật Phúc
-- DB: Microsoft SQL Server 2019/2022
-- Charset: NVARCHAR cho mọi trường tiếng Việt
-- ==================================================================================

USE master;
GO

-- Drop and recreate database
IF EXISTS (SELECT name FROM sys.databases WHERE name = N'electronics_store_chain')
BEGIN
    ALTER DATABASE electronics_store_chain SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE electronics_store_chain;
END
GO

CREATE DATABASE electronics_store_chain
    COLLATE Vietnamese_CI_AS;
GO

USE electronics_store_chain;
GO

-- ==================================================================================
-- BẢNG 1: ROLES - Vai trò người dùng
-- ==================================================================================
CREATE TABLE roles (
    role_id   INT IDENTITY(1,1) PRIMARY KEY,
    role_name NVARCHAR(50) NOT NULL UNIQUE -- CUSTOMER, STAFF, SHIPPER, ADMIN
);
GO

-- ==================================================================================
-- BẢNG 2: USERS - Người dùng hệ thống
-- ==================================================================================
CREATE TABLE users (
    user_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    username      NVARCHAR(100) NOT NULL UNIQUE,
    email         NVARCHAR(255) NOT NULL UNIQUE,
    password      NVARCHAR(255) NOT NULL,                -- BCrypt hashed
    full_name     NVARCHAR(150) NOT NULL,
    phone         NVARCHAR(20)  NULL UNIQUE,
    avatar_url    NVARCHAR(500) NULL,                   -- Cloudinary URL
    is_active     BIT NOT NULL DEFAULT 1,               -- 0 = bị khóa
    is_verified   BIT NOT NULL DEFAULT 0,               -- OTP email xác thực
    otp_code      NVARCHAR(10)  NULL,                   -- Mã OTP 6 chữ số
    otp_expired_at DATETIME     NULL,
    role_id       INT NOT NULL DEFAULT 1,
    created_at    DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles(role_id)
);
GO

-- ==================================================================================
-- BẢNG 3: USER_ADDRESSES - Sổ địa chỉ nhận hàng đa điểm
-- ==================================================================================
CREATE TABLE user_addresses (
    address_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    receiver_name NVARCHAR(150) NOT NULL,
    phone         NVARCHAR(20)  NOT NULL,
    province      NVARCHAR(100) NOT NULL,
    district      NVARCHAR(100) NOT NULL,
    ward          NVARCHAR(100) NOT NULL,
    street        NVARCHAR(255) NOT NULL,
    address_note  NVARCHAR(255) NULL,
    is_default    BIT NOT NULL DEFAULT 0,
    created_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
GO

-- ==================================================================================
-- BẢNG 4: BRANCHES - Chi nhánh chuỗi cửa hàng
-- ==================================================================================
CREATE TABLE branches (
    branch_id    INT IDENTITY(1,1) PRIMARY KEY,
    branch_name  NVARCHAR(200) NOT NULL UNIQUE,
    address      NVARCHAR(500) NOT NULL,
    district     NVARCHAR(100) NOT NULL,
    province     NVARCHAR(100) NOT NULL DEFAULT N'TP. Hồ Chí Minh',
    phone        NVARCHAR(20)  NOT NULL,
    email        NVARCHAR(255) NULL,
    avatar_url   NVARCHAR(500) NULL,                    -- Cloudinary URL
    latitude     DECIMAL(10,8) NULL,
    longitude    DECIMAL(11,8) NULL,
    open_time    TIME NULL DEFAULT '08:00:00',
    close_time   TIME NULL DEFAULT '21:30:00',
    is_active    BIT NOT NULL DEFAULT 1,
    manager_id   BIGINT NULL,                           -- FK tới users (STAFF)
    created_at   DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at   DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_branch_manager FOREIGN KEY (manager_id) REFERENCES users(user_id)
);
GO

-- ==================================================================================
-- BẢNG 5: CATEGORIES - Danh mục sản phẩm (hỗ trợ phân cấp)
-- ==================================================================================
CREATE TABLE categories (
    category_id   INT IDENTITY(1,1) PRIMARY KEY,
    category_name NVARCHAR(150) NOT NULL,
    slug          NVARCHAR(200) NOT NULL UNIQUE,
    image_url     NVARCHAR(500) NULL,
    parent_id     INT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active     BIT NOT NULL DEFAULT 1,
    created_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES categories(category_id)
);
GO

-- ==================================================================================
-- BẢNG 6: BRANDS - Thương hiệu / Hãng sản xuất
-- ==================================================================================
CREATE TABLE brands (
    brand_id     INT IDENTITY(1,1) PRIMARY KEY,
    brand_name   NVARCHAR(100) NOT NULL UNIQUE,
    logo_url     NVARCHAR(500) NULL,
    description  NVARCHAR(1000) NULL,
    is_active    BIT NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT GETDATE()
);
GO

-- ==================================================================================
-- BẢNG 7: PRODUCTS - Sản phẩm
-- ==================================================================================
CREATE TABLE products (
    product_id       BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_name     NVARCHAR(300) NOT NULL,
    slug             NVARCHAR(350) NOT NULL UNIQUE,
    description      NVARCHAR(MAX) NULL,
    specifications   NVARCHAR(MAX) NULL,                -- JSON: thông số kỹ thuật
    category_id      INT NOT NULL,
    brand_id         INT NOT NULL,
    thumbnail_url    NVARCHAR(500) NULL,                -- Cloudinary
    base_price       DECIMAL(18,0) NOT NULL DEFAULT 0,
    is_active        BIT NOT NULL DEFAULT 1,
    is_featured      BIT NOT NULL DEFAULT 0,
    total_sold       INT NOT NULL DEFAULT 0,
    avg_rating       DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    review_count     INT NOT NULL DEFAULT 0,
    created_at       DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at       DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES brands(brand_id)
);
GO

-- ==================================================================================
-- BẢNG 8: PRODUCT_IMAGES - Album ảnh sản phẩm (lưu URL Cloudinary)
-- ==================================================================================
CREATE TABLE product_images (
    image_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    image_url     NVARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pimg_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);
GO

-- ==================================================================================
-- BẢNG 9: PRODUCT_VARIANTS - Biến thể sản phẩm (Màu, RAM, ROM, v.v.)
-- ==================================================================================
CREATE TABLE product_variants (
    variant_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    color         NVARCHAR(100) NULL,           -- Màu sắc: Titan Đen, Trắng Ngà...
    storage       NVARCHAR(50)  NULL,           -- Dung lượng: 128GB, 256GB...
    ram           NVARCHAR(50)  NULL,           -- RAM: 8GB, 16GB, 32GB...
    price         DECIMAL(18,0) NOT NULL,       -- Giá niêm yết biến thể
    promo_price   DECIMAL(18,0) NULL,           -- Giá khuyến mãi
    sku           NVARCHAR(100) NOT NULL UNIQUE,
    image_url     NVARCHAR(500) NULL,           -- Ảnh riêng biến thể (Cloudinary)
    is_active     BIT NOT NULL DEFAULT 1,
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES products(product_id) ON DELETE CASCADE
);
GO

-- ==================================================================================
-- BẢNG 10: BRANCH_INVENTORY - Tồn kho phân tán theo chi nhánh (NGHIỆP VỤ CỐT LÕI)
-- ==================================================================================
CREATE TABLE branch_inventory (
    inventory_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id     INT NOT NULL,
    variant_id    BIGINT NOT NULL,
    quantity      INT NOT NULL DEFAULT 0,
    updated_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_inv_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    CONSTRAINT fk_inv_variant FOREIGN KEY (variant_id) REFERENCES product_variants(variant_id),
    CONSTRAINT uq_inv_branch_variant UNIQUE (branch_id, variant_id)
);
GO

-- ==================================================================================
-- BẢNG 11: CARTS - Giỏ hàng lưu database
-- ==================================================================================
CREATE TABLE carts (
    cart_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);
GO

-- ==================================================================================
-- BẢNG 12: CART_ITEMS - Chi tiết giỏ hàng
-- ==================================================================================
CREATE TABLE cart_items (
    cart_item_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cart_id      BIGINT NOT NULL,
    variant_id   BIGINT NOT NULL,
    quantity     INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    added_at     DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_ci_cart FOREIGN KEY (cart_id) REFERENCES carts(cart_id) ON DELETE CASCADE,
    CONSTRAINT fk_ci_variant FOREIGN KEY (variant_id) REFERENCES product_variants(variant_id),
    CONSTRAINT uq_cart_variant UNIQUE (cart_id, variant_id)
);
GO

-- ==================================================================================
-- BẢNG 13: DELIVERY_PROVIDERS - Nhà vận chuyển
-- ==================================================================================
CREATE TABLE delivery_providers (
    provider_id   INT IDENTITY(1,1) PRIMARY KEY,
    provider_name NVARCHAR(150) NOT NULL UNIQUE,
    base_fee      DECIMAL(18,0) NOT NULL DEFAULT 0,
    logo_url      NVARCHAR(500) NULL,
    is_active     BIT NOT NULL DEFAULT 1
);
GO

-- ==================================================================================
-- BẢNG 14: COUPONS - Voucher / Mã giảm giá
-- ==================================================================================
CREATE TABLE coupons (
    coupon_id      INT IDENTITY(1,1) PRIMARY KEY,
    coupon_code    NVARCHAR(50) NOT NULL UNIQUE,
    description    NVARCHAR(300) NULL,
    discount_type  NVARCHAR(20) NOT NULL DEFAULT 'PERCENT', -- PERCENT | AMOUNT
    discount_value DECIMAL(18,0) NOT NULL,
    min_order_amt  DECIMAL(18,0) NOT NULL DEFAULT 0,
    max_discount   DECIMAL(18,0) NULL,                     -- Giới hạn giảm tối đa
    usage_limit    INT NOT NULL DEFAULT 1,
    used_count     INT NOT NULL DEFAULT 0,
    branch_id      INT NULL,                               -- NULL = toàn chuỗi
    valid_from     DATETIME NOT NULL DEFAULT GETDATE(),
    valid_until    DATETIME NOT NULL,
    is_active      BIT NOT NULL DEFAULT 1,
    created_at     DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_coupon_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);
GO

-- ==================================================================================
-- BẢNG 15: ORDERS - Đơn hàng
-- ==================================================================================
CREATE TABLE orders (
    order_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_code         NVARCHAR(30) NOT NULL UNIQUE,   -- ESC-20261021-00001
    user_id            BIGINT NOT NULL,
    branch_id          INT NULL,                       -- Chi nhánh xử lý (NULL = auto)
    order_type         NVARCHAR(20) NOT NULL DEFAULT 'DELIVERY', -- DELIVERY | PICKUP
    -- Thông tin giao hàng (DELIVERY)
    receiver_name      NVARCHAR(150) NULL,
    receiver_phone     NVARCHAR(20)  NULL,
    shipping_address   NVARCHAR(500) NULL,
    provider_id        INT NULL,
    shipping_fee       DECIMAL(18,0) NOT NULL DEFAULT 0,
    -- Thông tin nhận tại cửa hàng (PICKUP)
    pickup_branch_id   INT NULL,
    -- Tài chính
    subtotal           DECIMAL(18,0) NOT NULL DEFAULT 0,
    discount_amount    DECIMAL(18,0) NOT NULL DEFAULT 0,
    total_amount       DECIMAL(18,0) NOT NULL DEFAULT 0,
    coupon_id          INT NULL,
    -- Thanh toán
    payment_method     NVARCHAR(20) NOT NULL DEFAULT 'COD', -- COD | VNPAY
    payment_status     NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | REFUNDED
    vnpay_txn_ref      NVARCHAR(100) NULL,
    -- Trạng thái đơn hàng (6 trạng thái)
    status             NVARCHAR(30) NOT NULL DEFAULT 'NEW',
        -- NEW | CONFIRMED | SHIPPING | DELIVERED | CANCELLED | RETURNED_REFUND
    shipper_id         BIGINT NULL,
    note               NVARCHAR(500) NULL,
    cancelled_reason   NVARCHAR(300) NULL,
    created_at         DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at         DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_order_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    CONSTRAINT fk_order_pickup_branch FOREIGN KEY (pickup_branch_id) REFERENCES branches(branch_id),
    CONSTRAINT fk_order_provider FOREIGN KEY (provider_id) REFERENCES delivery_providers(provider_id),
    CONSTRAINT fk_order_coupon FOREIGN KEY (coupon_id) REFERENCES coupons(coupon_id),
    CONSTRAINT fk_order_shipper FOREIGN KEY (shipper_id) REFERENCES users(user_id),
    CONSTRAINT chk_order_status CHECK (status IN ('NEW','CONFIRMED','SHIPPING','DELIVERED','CANCELLED','RETURNED_REFUND')),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('COD','VNPAY')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('DELIVERY','PICKUP'))
);
GO

-- ==================================================================================
-- BẢNG 16: ORDER_ITEMS - Chi tiết đơn hàng
-- ==================================================================================
CREATE TABLE order_items (
    order_item_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    variant_id     BIGINT NOT NULL,
    product_name   NVARCHAR(300) NOT NULL, -- Snapshot tên sản phẩm lúc mua
    variant_info   NVARCHAR(200) NULL,     -- Snapshot màu/ROM/RAM lúc mua
    unit_price     DECIMAL(18,0) NOT NULL,
    quantity       INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    line_total     AS (unit_price * quantity) PERSISTED,
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_oi_variant FOREIGN KEY (variant_id) REFERENCES product_variants(variant_id)
);
GO

-- ==================================================================================
-- BẢNG 17: REVIEWS - Đánh giá sản phẩm (chỉ DELIVERED mới được review)
-- ==================================================================================
CREATE TABLE reviews (
    review_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id     BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    product_id   BIGINT NOT NULL,
    stars        TINYINT NOT NULL CHECK (stars BETWEEN 1 AND 5),
    content      NVARCHAR(2000) NOT NULL, -- Tối thiểu 50 ký tự (validate tầng Service)
    image_urls   NVARCHAR(MAX) NULL,      -- JSON array các URL Cloudinary
    is_active    BIT NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_rv_order FOREIGN KEY (order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_rv_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(order_item_id),
    CONSTRAINT fk_rv_user FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_rv_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT uq_review_order_item UNIQUE (order_item_id) -- 1 item = 1 review
);
GO

-- ==================================================================================
-- BẢNG 18: WISHLISTS - Sản phẩm yêu thích
-- ==================================================================================
CREATE TABLE wishlists (
    wishlist_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    added_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_wl_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT uq_wishlist UNIQUE (user_id, product_id)
);
GO

-- ==================================================================================
-- BẢNG 19: VIEWED_PRODUCTS - Sản phẩm đã xem gần đây
-- ==================================================================================
CREATE TABLE viewed_products (
    view_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    viewed_at  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_vp_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vp_product FOREIGN KEY (product_id) REFERENCES products(product_id),
    CONSTRAINT uq_viewed UNIQUE (user_id, product_id)
);
GO

-- ==================================================================================
-- BẢNG 20: NOTIFICATIONS - Thông báo hệ thống (WebSocket push)
-- ==================================================================================
CREATE TABLE notifications (
    notif_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     BIGINT NOT NULL,                        -- NULL = broadcast
    branch_id   INT NULL,                              -- Thông báo tới chi nhánh
    type        NVARCHAR(50) NOT NULL,                 -- NEW_ORDER | STATUS_CHANGED | SYSTEM
    title       NVARCHAR(300) NOT NULL,
    message     NVARCHAR(1000) NOT NULL,
    ref_id      BIGINT NULL,                           -- order_id hoặc id đối tượng liên quan
    is_read     BIT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id)
);
GO

-- ==================================================================================
-- BẢNG 21: CHAT_SESSIONS - Phiên Live Chat
-- ==================================================================================
CREATE TABLE chat_sessions (
    session_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    branch_id   INT NOT NULL,
    staff_id    BIGINT NULL,                           -- Staff tiếp nhận
    status      NVARCHAR(20) NOT NULL DEFAULT 'OPEN', -- OPEN | CLOSED
    created_at  DATETIME NOT NULL DEFAULT GETDATE(),
    closed_at   DATETIME NULL,
    CONSTRAINT fk_cs_customer FOREIGN KEY (customer_id) REFERENCES users(user_id),
    CONSTRAINT fk_cs_branch FOREIGN KEY (branch_id) REFERENCES branches(branch_id),
    CONSTRAINT fk_cs_staff FOREIGN KEY (staff_id) REFERENCES users(user_id)
);
GO

-- ==================================================================================
-- BẢNG 22: CHAT_MESSAGES - Tin nhắn Live Chat (WebSocket STOMP)
-- ==================================================================================
CREATE TABLE chat_messages (
    message_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    session_id  BIGINT NOT NULL,
    sender_id   BIGINT NOT NULL,
    content     NVARCHAR(2000) NOT NULL,
    sent_at     DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES chat_sessions(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_sender FOREIGN KEY (sender_id) REFERENCES users(user_id)
);
GO

-- ==================================================================================
-- BẢNG 23: OTP_TOKENS - Lưu mã OTP gửi qua Email (đăng ký & quên mật khẩu)
-- ==================================================================================
CREATE TABLE otp_tokens (
    token_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     BIGINT NULL,                         -- NULL nếu chưa có user (chưa đăng ký)
    email       NVARCHAR(255) NOT NULL,
    otp_code    NVARCHAR(10) NOT NULL,
    purpose     NVARCHAR(30) NOT NULL,               -- REGISTER | FORGOT_PASSWORD
    is_used     BIT NOT NULL DEFAULT 0,
    expired_at  DATETIME NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
);
GO

-- ==================================================================================
-- BẢNG 24: BANNERS - Banner quảng cáo trang chủ
-- ==================================================================================
CREATE TABLE banners (
    banner_id    INT IDENTITY(1,1) PRIMARY KEY,
    title        NVARCHAR(300) NOT NULL,
    image_url    NVARCHAR(500) NOT NULL,
    link_url     NVARCHAR(500) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active    BIT NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT GETDATE()
);
GO

-- ==================================================================================
-- INDEX TỐI ƯU HIỆU NĂNG TRUY VẤN
-- ==================================================================================
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_brand ON products(brand_id);
CREATE INDEX idx_products_slug ON products(slug);
CREATE INDEX idx_products_featured ON products(is_featured, is_active);
CREATE INDEX idx_products_rating ON products(avg_rating DESC);
CREATE INDEX idx_products_sold ON products(total_sold DESC);
CREATE INDEX idx_variants_product ON product_variants(product_id);
CREATE INDEX idx_inventory_branch ON branch_inventory(branch_id);
CREATE INDEX idx_inventory_variant ON branch_inventory(variant_id);
CREATE INDEX idx_orders_user ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_branch ON orders(branch_id, status);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_code ON orders(order_code);
CREATE INDEX idx_reviews_product ON reviews(product_id);
CREATE INDEX idx_notif_user ON notifications(user_id, is_read);
CREATE INDEX idx_notif_branch ON notifications(branch_id, is_read);
GO

-- ==================================================================================
-- ==================================================================================
-- SEED DATA - DỮ LIỆU MẪU ĐẦY ĐỦ, THỰC TẾ
-- ==================================================================================
-- ==================================================================================

-- -------------------------------------------------------------------------------
-- ROLES
-- -------------------------------------------------------------------------------
SET IDENTITY_INSERT roles ON;
INSERT INTO roles (role_id, role_name) VALUES
(1, 'CUSTOMER'),
(2, 'STAFF'),
(3, 'SHIPPER'),
(4, 'ADMIN');
SET IDENTITY_INSERT roles OFF;
GO

-- -------------------------------------------------------------------------------
-- USERS
-- Mật khẩu tất cả đều là: Admin@123 / Staff@123 / Customer@123 / Shipper@123
-- BCrypt hash ($2a$12$...) - đã generate sẵn để dùng ngay
-- -------------------------------------------------------------------------------
-- admin       -> Admin@123
-- staff_q1    -> Staff@123
-- staff_q3    -> Staff@123
-- staff_thuduc -> Staff@123
-- staff_govap -> Staff@123
-- shipper1    -> Shipper@123
-- customer1   -> Customer@123
-- customer2   -> Customer@123
INSERT INTO users (username, email, password, full_name, phone, is_active, is_verified, role_id) VALUES
(N'admin',        N'admin@esc.vn',                N'$2a$12$kUHSq8RRqOhGGLGP0XmgXeVZ8YCY25S2J5LjFGBN/ZNMGwAXGVxOq', N'Quản trị viên Hệ thống',  N'0901234567', 1, 1, 4),
(N'staff_q1',     N'staff.q1@esc.vn',             N'$2a$12$xfJSh74sIQR1N8V00cVSb.cqUHV7v4W.xPGnBHRmAH4W8xT0s9Wha', N'Nguyễn Văn An',            N'0902345678', 1, 1, 2),
(N'staff_q3',     N'staff.q3@esc.vn',             N'$2a$12$xfJSh74sIQR1N8V00cVSb.cqUHV7v4W.xPGnBHRmAH4W8xT0s9Wha', N'Trần Thị Bích',            N'0903456789', 1, 1, 2),
(N'staff_thuduc', N'staff.thuduc@esc.vn',         N'$2a$12$xfJSh74sIQR1N8V00cVSb.cqUHV7v4W.xPGnBHRmAH4W8xT0s9Wha', N'Lê Văn Cường',             N'0904567890', 1, 1, 2),
(N'staff_govap',  N'staff.govap@esc.vn',          N'$2a$12$xfJSh74sIQR1N8V00cVSb.cqUHV7v4W.xPGnBHRmAH4W8xT0s9Wha', N'Phạm Thị Dung',            N'0905678901', 1, 1, 2),
(N'shipper1',     N'shipper1@esc.vn',             N'$2a$12$j7OEiXUV1V7/MbI5G5P7q.KOEp8oTD7ZeGMmDCcImr3BoMOZlL8im', N'Hoàng Minh Đức',           N'0906789012', 1, 1, 3),
(N'shipper2',     N'shipper2@esc.vn',             N'$2a$12$j7OEiXUV1V7/MbI5G5P7q.KOEp8oTD7ZeGMmDCcImr3BoMOZlL8im', N'Vũ Thị Hoa',               N'0907890123', 1, 1, 3),
(N'customer1',    N'customer1@gmail.com',         N'$2a$12$9fPGBbO3mPxHCPBTeyF6QecfXF0m1cVgC.YhkCtXF0lqhOaX.OERW', N'Trần Vũ Anh Khoa',         N'0908901234', 1, 1, 1),
(N'customer2',    N'customer2@gmail.com',         N'$2a$12$9fPGBbO3mPxHCPBTeyF6QecfXF0m1cVgC.YhkCtXF0lqhOaX.OERW', N'Mai Huy Nhật',              N'0909012345', 1, 1, 1),
(N'customer3',    N'customer3@gmail.com',         N'$2a$12$9fPGBbO3mPxHCPBTeyF6QecfXF0m1cVgC.YhkCtXF0lqhOaX.OERW', N'Đặng Nhật Phúc',           N'0910123456', 1, 1, 1);
GO

-- -------------------------------------------------------------------------------
-- USER_ADDRESSES
-- -------------------------------------------------------------------------------
INSERT INTO user_addresses (user_id, receiver_name, phone, province, district, ward, street, is_default) VALUES
(8, N'Trần Vũ Anh Khoa',  N'0908901234', N'TP. Hồ Chí Minh', N'Quận 1',      N'Phường Bến Nghé', N'12 Lê Lợi',        1),
(8, N'Trần Vũ Anh Khoa',  N'0908901234', N'TP. Hồ Chí Minh', N'Quận 3',      N'Phường Võ Thị Sáu', N'45 Điện Biên Phủ', 0),
(9, N'Mai Huy Nhật',       N'0909012345', N'TP. Hồ Chí Minh', N'TP. Thủ Đức', N'Phường Linh Trung', N'1 Võ Văn Ngân',    1),
(10, N'Đặng Nhật Phúc',    N'0910123456', N'TP. Hồ Chí Minh', N'Quận Gò Vấp', N'Phường 12',         N'100 Quang Trung',  1);
GO

-- -------------------------------------------------------------------------------
-- BRANCHES - 4 chi nhánh chuỗi ESC (Electronics Store Chain)
-- -------------------------------------------------------------------------------
INSERT INTO branches (branch_name, address, district, province, phone, email, latitude, longitude, manager_id) VALUES
(N'ESC Chi nhánh Quận 1',     N'123 Lê Lợi, Phường Bến Nghé',      N'Quận 1',      N'TP. Hồ Chí Minh', N'(028) 3822 1234', N'q1@esc.vn',     10.7769, 106.7009, 2),
(N'ESC Chi nhánh Quận 3',     N'45 Điện Biên Phủ, Phường Đakao',   N'Quận 3',      N'TP. Hồ Chí Minh', N'(028) 3930 5678', N'q3@esc.vn',     10.7908, 106.6960, 3),
(N'ESC Chi nhánh TP. Thủ Đức',N'2 Võ Văn Ngân, Phường Bình Thọ',  N'TP. Thủ Đức', N'TP. Hồ Chí Minh', N'(028) 3896 9012', N'thuduc@esc.vn', 10.8503, 106.7747, 4),
(N'ESC Chi nhánh Gò Vấp',     N'200 Quang Trung, Phường 10',       N'Quận Gò Vấp', N'TP. Hồ Chí Minh', N'(028) 3894 3456', N'govap@esc.vn',  10.8380, 106.6709, 5);
GO

-- -------------------------------------------------------------------------------
-- CATEGORIES - Danh mục cha
-- -------------------------------------------------------------------------------
SET IDENTITY_INSERT categories ON;
INSERT INTO categories (category_id, category_name, slug, display_order, is_active) VALUES
(1,  N'Điện thoại',          N'dien-thoai',          1, 1),
(2,  N'Laptop',               N'laptop',              2, 1),
(3,  N'Máy tính bảng',        N'may-tinh-bang',       3, 1),
(4,  N'Phụ kiện',             N'phu-kien',            4, 1),
(5,  N'Smartwatch',           N'smartwatch',          5, 1),
(6,  N'Tai nghe',             N'tai-nghe',            6, 1),
(7,  N'Máy tính để bàn & Màn hình', N'may-tinh-de-ban', 7, 1),
-- Danh mục con: Điện thoại
(11, N'iPhone',               N'iphone',              1, 1),
(12, N'Samsung Galaxy',       N'samsung-galaxy',      2, 1),
(13, N'Xiaomi',               N'xiaomi',              3, 1),
(14, N'OPPO',                 N'oppo',                4, 1),
-- Danh mục con: Laptop
(21, N'Laptop Gaming',        N'laptop-gaming',       1, 1),
(22, N'MacBook',              N'macbook',             2, 1),
(23, N'Laptop Văn phòng',     N'laptop-van-phong',    3, 1),
-- Danh mục con: Phụ kiện
(41, N'Cáp & Sạc',            N'cap-sac',             1, 1),
(42, N'Ốp lưng & Bao da',     N'op-lung-bao-da',      2, 1),
(43, N'Bàn phím & Chuột',     N'ban-phim-chuot',      3, 1),
(44, N'Túi & Balo laptop',    N'tui-balo-laptop',     4, 1);
SET IDENTITY_INSERT categories OFF;
GO

-- Cập nhật parent_id
UPDATE categories SET parent_id = 1  WHERE category_id IN (11, 12, 13, 14);
UPDATE categories SET parent_id = 2  WHERE category_id IN (21, 22, 23);
UPDATE categories SET parent_id = 4  WHERE category_id IN (41, 42, 43, 44);
GO

-- -------------------------------------------------------------------------------
-- BRANDS
-- -------------------------------------------------------------------------------
INSERT INTO brands (brand_name, description) VALUES
(N'Apple',      N'Thương hiệu công nghệ hàng đầu thế giới của Mỹ'),
(N'Samsung',    N'Tập đoàn công nghệ đa quốc gia của Hàn Quốc'),
(N'Asus',       N'Thương hiệu máy tính và thiết bị điện tử của Đài Loan'),
(N'Dell',       N'Tập đoàn công nghệ của Mỹ, chuyên về máy tính cá nhân'),
(N'Lenovo',     N'Tập đoàn công nghệ đa quốc gia của Trung Quốc'),
(N'Sony',       N'Tập đoàn công nghệ và giải trí của Nhật Bản'),
(N'Xiaomi',     N'Thương hiệu công nghệ của Trung Quốc'),
(N'OPPO',       N'Thương hiệu điện thoại thông minh của Trung Quốc'),
(N'MSI',        N'Thương hiệu laptop gaming hàng đầu của Đài Loan'),
(N'LG',         N'Tập đoàn công nghệ và điện tử của Hàn Quốc');
GO

-- -------------------------------------------------------------------------------
-- PRODUCTS (12 sản phẩm thực tế)
-- -------------------------------------------------------------------------------
SET IDENTITY_INSERT products ON;
INSERT INTO products (product_id, product_name, slug, description, category_id, brand_id, base_price, is_active, is_featured) VALUES
-- ĐIỆN THOẠI
(1,  N'iPhone 16 Pro Max',
     N'iphone-16-pro-max',
     N'iPhone 16 Pro Max với chip A18 Pro, màn hình 6.9" Super Retina XDR, camera 48MP Camera Control, pin 33 giờ. Đỉnh cao của công nghệ smartphone 2024.',
     11, 1, 34990000, 1, 1),
(2,  N'Samsung Galaxy S25 Ultra',
     N'samsung-galaxy-s25-ultra',
     N'Galaxy S25 Ultra mang đến trải nghiệm AI tích hợp Galaxy AI, Snapdragon 8 Elite, bút S Pen, camera 200MP, màn hình 6.9" Dynamic AMOLED 2X.',
     12, 2, 31990000, 1, 1),
(3,  N'iPhone 15',
     N'iphone-15',
     N'iPhone 15 với Dynamic Island, chip A16 Bionic, cổng USB-C, camera 48MP chính, màu sắc tươi sáng mới.',
     11, 1, 22990000, 1, 0),
(4,  N'Xiaomi 14 Ultra',
     N'xiaomi-14-ultra',
     N'Xiaomi 14 Ultra hợp tác Leica, Snapdragon 8 Gen 3, camera 1 inch 50MP, màn hình AMOLED 6.73".',
     13, 7, 19990000, 1, 0),
-- LAPTOP
(5,  N'MacBook Pro 14 M4 Pro',
     N'macbook-pro-14-m4-pro',
     N'MacBook Pro 14 inch trang bị chip M4 Pro mạnh mẽ, màn hình Liquid Retina XDR 3024x1964, thời lượng pin lên đến 22 giờ.',
     22, 1, 62990000, 1, 1),
(6,  N'Asus ROG Zephyrus G16 2024',
     N'asus-rog-zephyrus-g16-2024',
     N'ROG Zephyrus G16 2024 với Intel Core Ultra 9 185H, NVIDIA GeForce RTX 4090, màn hình OLED 2.5K 240Hz mỏng nhẹ đẳng cấp gaming.',
     21, 3, 74990000, 1, 1),
(7,  N'Dell XPS 15 9530',
     N'dell-xps-15-9530',
     N'Dell XPS 15 với Intel Core i9-13900H, NVIDIA RTX 4060, màn hình OLED 3.5K cảm ứng, thiết kế nhôm sang trọng.',
     23, 4, 55990000, 1, 0),
(8,  N'Lenovo ThinkPad X1 Carbon Gen 11',
     N'lenovo-thinkpad-x1-carbon-gen11',
     N'ThinkPad X1 Carbon siêu nhẹ 1.12kg, Intel Core i7-1365U, màn hình IPS 2.8K OLED, bảo mật doanh nghiệp hàng đầu.',
     23, 5, 42990000, 1, 0),
-- MÁY TÍNH BẢNG
(9,  N'iPad Pro M4 11 inch',
     N'ipad-pro-m4-11-inch',
     N'iPad Pro với chip M4, màn hình Ultra Retina XDR OLED 11", mỏng nhất từ trước đến nay chỉ 5.1mm, hỗ trợ Apple Pencil Pro.',
     3, 1, 23990000, 1, 1),
(10, N'Samsung Galaxy Tab S9 Ultra',
     N'samsung-galaxy-tab-s9-ultra',
     N'Galaxy Tab S9 Ultra màn hình Super AMOLED 14.6", Snapdragon 8 Gen 2, kháng nước IP68, kèm S Pen.',
     3, 2, 28990000, 1, 0),
-- SMARTWATCH
(11, N'Apple Watch Ultra 2',
     N'apple-watch-ultra-2',
     N'Apple Watch Ultra 2 với vỏ Titan, màn hình MicroLED sáng nhất 3000 nit, pin 60 giờ, đồng hồ thể thao chuyên nghiệp.',
     5, 1, 21990000, 1, 1),
(12, N'Samsung Galaxy Watch 7',
     N'samsung-galaxy-watch-7',
     N'Galaxy Watch 7 với BioActive Sensor thế hệ mới, Galaxy AI, đo đường huyết, pin 40 giờ chế độ tiết kiệm.',
     5, 2, 8990000, 1, 0);
SET IDENTITY_INSERT products OFF;
GO

-- -------------------------------------------------------------------------------
-- PRODUCT_VARIANTS - Biến thể chi tiết
-- -------------------------------------------------------------------------------
-- iPhone 16 Pro Max
INSERT INTO product_variants (product_id, color, storage, sku, price, promo_price) VALUES
(1, N'Titan Đen',   N'256GB', N'IP16PM-BLK-256',  34990000, 33490000),
(1, N'Titan Trắng', N'256GB', N'IP16PM-WHT-256',  34990000, 33490000),
(1, N'Titan Sa mạc',N'256GB', N'IP16PM-DST-256',  34990000, 33490000),
(1, N'Titan Đen',   N'512GB', N'IP16PM-BLK-512',  39990000, 38490000),
(1, N'Titan Trắng', N'512GB', N'IP16PM-WHT-512',  39990000, 38490000),
(1, N'Titan Đen',   N'1TB',   N'IP16PM-BLK-1T',   49990000, NULL),
-- Samsung Galaxy S25 Ultra
(2, N'Titanium Đen',  N'256GB', N'GS25U-BLK-256', 31990000, 30490000),
(2, N'Titanium Trắng',N'256GB', N'GS25U-WHT-256', 31990000, 30490000),
(2, N'Titanium Xanh', N'512GB', N'GS25U-BLU-512', 37990000, 36490000),
-- iPhone 15
(3, N'Hồng',   N'128GB', N'IP15-PNK-128', 22990000, 21490000),
(3, N'Vàng',   N'128GB', N'IP15-YLW-128', 22990000, 21490000),
(3, N'Xanh',   N'256GB', N'IP15-BLU-256', 26990000, 25490000),
-- Xiaomi 14 Ultra
(4, N'Đen',    N'256GB/12GB', N'X14U-BLK-256', 19990000, 18990000),
(4, N'Trắng',  N'512GB/16GB', N'X14U-WHT-512', 23990000, 22490000),
-- MacBook Pro 14 M4 Pro
(5, N'Silver',  N'18GB/512GB SSD', N'MBP14-M4-SLV-512', 62990000, 60490000),
(5, N'Space Black', N'24GB/1TB SSD', N'MBP14-M4-BLK-1T', 78990000, NULL),
-- Asus ROG Zephyrus G16
(6, N'Eclipse Gray',  N'32GB/1TB SSD', N'ROGZ16-EG-1T',  74990000, 72490000),
(6, N'Platinum White',N'32GB/2TB SSD', N'ROGZ16-PW-2T',  89990000, NULL),
-- Dell XPS 15
(7, N'Platinum Silver',N'16GB/512GB SSD', N'XPS15-SLV-512', 55990000, 53490000),
(7, N'Platinum Silver',N'32GB/1TB SSD',   N'XPS15-SLV-1T',  67990000, NULL),
-- Lenovo ThinkPad X1
(8, N'Đen Carbon', N'16GB/512GB SSD', N'TPX1-BLK-512', 42990000, 40490000),
(8, N'Đen Carbon', N'32GB/1TB SSD',   N'TPX1-BLK-1T',  55990000, NULL),
-- iPad Pro M4 11"
(9, N'Silver', N'256GB WiFi',     N'IPAD-PRO-M4-SLV-256W', 23990000, 22490000),
(9, N'Space Gray', N'512GB WiFi', N'IPAD-PRO-M4-GRY-512W', 29990000, NULL),
(9, N'Silver', N'1TB WiFi+Cell',  N'IPAD-PRO-M4-SLV-1TC',  42990000, NULL),
-- Samsung Galaxy Tab S9 Ultra
(10, N'Graphite', N'256GB WiFi', N'TABS9U-GRP-256W', 28990000, 27490000),
(10, N'Beige',    N'512GB WiFi', N'TABS9U-BEI-512W', 34990000, NULL),
-- Apple Watch Ultra 2
(11, N'Natural Titan', N'49mm', N'AWU2-NAT-49', 21990000, 20490000),
(11, N'Black Titan',   N'49mm', N'AWU2-BLK-49', 21990000, 20490000),
-- Samsung Galaxy Watch 7
(12, N'Cream',  N'40mm', N'GW7-CRM-40', 8990000, 7990000),
(12, N'Green',  N'44mm', N'GW7-GRN-44', 9990000, 8990000);
GO

-- -------------------------------------------------------------------------------
-- BRANCH_INVENTORY - Tồn kho phân tán theo 4 chi nhánh
-- variant_id: 1-33 tương ứng theo thứ tự insert ở trên
-- -------------------------------------------------------------------------------
-- Chi nhánh Quận 1 (branch_id=1)
INSERT INTO branch_inventory (branch_id, variant_id, quantity) VALUES
(1, 1, 15), (1, 2, 10), (1, 3, 8),  (1, 4, 5),  (1, 5, 4),  -- iPhone 16 Pro Max
(1, 7, 12), (1, 8, 8),  (1, 9, 6),                            -- Galaxy S25 Ultra
(1, 10, 20),(1, 11, 18),(1, 12, 10),                          -- iPhone 15
(1, 15, 3), (1, 16, 2),                                        -- MacBook Pro
(1, 17, 5), (1, 18, 3),                                        -- ROG Zephyrus
(1, 23, 8), (1, 24, 4),                                        -- iPad Pro
(1, 29, 10),(1, 30, 8),                                        -- Apple Watch Ultra 2
(1, 31, 15),(1, 32, 10);                                       -- Galaxy Watch 7
GO
-- Chi nhánh Quận 3 (branch_id=2)
INSERT INTO branch_inventory (branch_id, variant_id, quantity) VALUES
(2, 1, 8),  (2, 2, 6),  (2, 3, 0),  (2, 4, 3),               -- iPhone 16 Pro Max (hết màu Titan Sa mạc 256)
(2, 7, 10), (2, 8, 0),  (2, 9, 4),                            -- Galaxy S25 Ultra
(2, 10, 15),(2, 11, 0), (2, 12, 8),                            -- iPhone 15
(2, 13, 5), (2, 14, 3),                                        -- Xiaomi 14 Ultra
(2, 15, 2), (2, 16, 1),                                        -- MacBook Pro
(2, 19, 4), (2, 20, 2),                                        -- Dell XPS 15
(2, 21, 3), (2, 22, 1),                                        -- ThinkPad X1
(2, 23, 6), (2, 25, 2),                                        -- iPad Pro
(2, 26, 5), (2, 27, 2);                                        -- Galaxy Tab S9 Ultra
GO
-- Chi nhánh TP. Thủ Đức (branch_id=3)
INSERT INTO branch_inventory (branch_id, variant_id, quantity) VALUES
(3, 1, 20), (3, 2, 15), (3, 3, 12), (3, 4, 8),  (3, 5, 6),   -- iPhone 16 Pro Max
(3, 7, 18), (3, 8, 12), (3, 9, 8),                             -- Galaxy S25 Ultra
(3, 10, 25),(3, 11, 20),(3, 12, 15),                           -- iPhone 15
(3, 13, 8), (3, 14, 5),                                        -- Xiaomi 14 Ultra
(3, 15, 5), (3, 16, 3),                                        -- MacBook Pro
(3, 17, 8), (3, 18, 4),                                        -- ROG Zephyrus
(3, 19, 6), (3, 20, 3),                                        -- Dell XPS 15
(3, 23, 12),(3, 24, 6), (3, 25, 3),                            -- iPad Pro
(3, 26, 8), (3, 27, 4),                                        -- Galaxy Tab S9 Ultra
(3, 29, 15),(3, 30, 10),                                       -- Apple Watch Ultra 2
(3, 31, 20),(3, 32, 15);                                       -- Galaxy Watch 7
GO
-- Chi nhánh Gò Vấp (branch_id=4)
INSERT INTO branch_inventory (branch_id, variant_id, quantity) VALUES
(4, 2, 10), (4, 3, 8),  (4, 4, 5),                            -- iPhone 16 Pro Max
(4, 7, 8),  (4, 8, 0),  (4, 9, 5),                            -- Galaxy S25 Ultra
(4, 10, 12),(4, 11, 10),(4, 12, 6),                            -- iPhone 15
(4, 15, 2),                                                    -- MacBook Pro
(4, 21, 2), (4, 22, 0),                                        -- ThinkPad X1 (hết 2TB)
(4, 23, 5), (4, 24, 2),                                        -- iPad Pro
(4, 29, 8), (4, 30, 5),                                        -- Apple Watch Ultra 2
(4, 31, 12),(4, 32, 8);                                        -- Galaxy Watch 7
GO

-- -------------------------------------------------------------------------------
-- DELIVERY_PROVIDERS
-- -------------------------------------------------------------------------------
INSERT INTO delivery_providers (provider_name, base_fee) VALUES
(N'Giao Hàng Nhanh (GHN)',        25000),
(N'Giao Hàng Tiết Kiệm (GHTK)',   20000),
(N'Viettel Post',                  22000),
(N'Hỏa tốc ESC (Nội thành HCM)', 35000);
GO

-- -------------------------------------------------------------------------------
-- COUPONS - Mã giảm giá mẫu
-- -------------------------------------------------------------------------------
INSERT INTO coupons (coupon_code, description, discount_type, discount_value, min_order_amt, max_discount, usage_limit, valid_until) VALUES
(N'WELCOME100K',  N'Chào mừng khách hàng mới - Giảm 100K',          N'AMOUNT',  100000,   500000,  NULL,    500,  '2026-12-31'),
(N'SALE10PCT',    N'Giảm 10% toàn bộ đơn hàng',                     N'PERCENT', 10,       2000000, 500000,  200,  '2026-12-31'),
(N'PHONE5PCT',    N'Giảm 5% khi mua điện thoại từ 10 triệu',        N'PERCENT', 5,        10000000, 1000000, 100,  '2026-12-31'),
(N'LAPTOP15PCT',  N'Giảm 15% cho laptop từ 30 triệu',               N'PERCENT', 15,       30000000, 3000000, 50,   '2026-12-31'),
(N'FREESHIP',     N'Miễn phí vận chuyển cho mọi đơn hàng',          N'AMOUNT',  35000,    0,        35000,   1000, '2026-12-31'),
(N'ESC24BIRTHDAY',N'Sinh nhật ESC - Giảm 500K cho đơn từ 20 triệu', N'AMOUNT',  500000,   20000000, NULL,    100,  '2026-10-31');
GO

-- -------------------------------------------------------------------------------
-- CARTS (tạo sẵn cho customer)
-- -------------------------------------------------------------------------------
INSERT INTO carts (user_id) VALUES (8), (9), (10);
GO

-- -------------------------------------------------------------------------------
-- CART_ITEMS - Giỏ hàng mẫu của customer1
-- -------------------------------------------------------------------------------
INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES
(1, 1,  1),   -- iPhone 16 Pro Max Titan Đen 256GB
(1, 15, 1);   -- MacBook Pro 14 M4 Pro Silver
GO

-- -------------------------------------------------------------------------------
-- ORDERS - Đơn hàng mẫu với đầy đủ trạng thái
-- -------------------------------------------------------------------------------
INSERT INTO orders (order_code, user_id, branch_id, order_type, receiver_name, receiver_phone, shipping_address, provider_id, shipping_fee, subtotal, discount_amount, total_amount, payment_method, payment_status, status, shipper_id) VALUES
(N'ESC-20261019-00001', 8, 1, N'DELIVERY', N'Trần Vũ Anh Khoa', N'0908901234', N'12 Lê Lợi, Phường Bến Nghé, Quận 1, TP. HCM', 1, 25000, 33490000, 0, 33515000, N'COD',   N'PENDING', N'DELIVERED',  6),
(N'ESC-20261019-00002', 9, 3, N'PICKUP',   NULL,                 NULL,          NULL, NULL, 0, 21490000, 100000, 21390000, N'VNPAY', N'PAID',    N'CONFIRMED',  NULL),
(N'ESC-20261020-00003', 8, 1, N'DELIVERY', N'Trần Vũ Anh Khoa', N'0908901234', N'45 Điện Biên Phủ, Phường Đakao, Quận 3, TP. HCM', 4, 35000, 60490000, 500000, 60025000, N'VNPAY', N'PAID',    N'SHIPPING',   6),
(N'ESC-20261021-00004', 10, 2, N'DELIVERY', N'Đặng Nhật Phúc',  N'0910123456', N'100 Quang Trung, Phường 12, Gò Vấp, TP. HCM', 2, 20000, 7990000, 0, 8010000, N'COD',   N'PENDING', N'NEW',        NULL),
(N'ESC-20261021-00005', 9, 3, N'PICKUP',   NULL,                 NULL,          NULL, NULL, 0, 8990000, 0, 8990000, N'COD',   N'PENDING', N'CANCELLED',  NULL);
GO

-- -------------------------------------------------------------------------------
-- ORDER_ITEMS
-- -------------------------------------------------------------------------------
INSERT INTO order_items (order_id, variant_id, product_name, variant_info, unit_price, quantity) VALUES
(1, 1,  N'iPhone 16 Pro Max', N'Titan Đen / 256GB',   33490000, 1),
(2, 10, N'iPhone 15',         N'Hồng / 128GB',        21490000, 1),
(3, 15, N'MacBook Pro 14 M4 Pro', N'Silver / 18GB / 512GB SSD', 60490000, 1),
(4, 31, N'Apple Watch Ultra 2',   N'Natural Titan / 49mm',      7990000, 1),
(5, 32, N'Samsung Galaxy Watch 7',N'Cream / 40mm',              8990000, 1);
GO

-- -------------------------------------------------------------------------------
-- REVIEWS - Đánh giá mẫu cho đơn hàng DELIVERED
-- -------------------------------------------------------------------------------
INSERT INTO reviews (order_id, order_item_id, user_id, product_id, stars, content) VALUES
(1, 1, 8, 1, 5, N'Sản phẩm chính hãng, đóng gói cẩn thận, giao hàng nhanh. iPhone 16 Pro Max dùng rất mượt, camera cực đẹp. Nhân viên chi nhánh tư vấn nhiệt tình. Sẽ ủng hộ ESC lần sau!');
GO

-- -------------------------------------------------------------------------------
-- WISHLISTS
-- -------------------------------------------------------------------------------
INSERT INTO wishlists (user_id, product_id) VALUES
(8, 2), (8, 5), (8, 9),
(9, 1), (9, 6),
(10, 1), (10, 11);
GO

-- -------------------------------------------------------------------------------
-- VIEWED_PRODUCTS
-- -------------------------------------------------------------------------------
INSERT INTO viewed_products (user_id, product_id) VALUES
(8, 1), (8, 2), (8, 5), (8, 9), (8, 11),
(9, 1), (9, 3), (9, 10),
(10, 1), (10, 12);
GO

-- -------------------------------------------------------------------------------
-- NOTIFICATIONS
-- -------------------------------------------------------------------------------
INSERT INTO notifications (user_id, branch_id, type, title, message, ref_id) VALUES
(2, 1, N'NEW_ORDER',      N'Đơn hàng mới #ESC-20261021-00004', N'Khách hàng Đặng Nhật Phúc vừa đặt đơn hàng mới cần xác nhận.', 4),
(8, NULL, N'STATUS_CHANGED', N'Đơn hàng của bạn đang giao', N'Đơn hàng #ESC-20261020-00003 của bạn đang được giao. Vui lòng chú ý điện thoại!', 3),
(9, NULL, N'STATUS_CHANGED', N'Đơn hàng đã được xác nhận', N'Đơn hàng #ESC-20261019-00002 đã được xác nhận. Vui lòng đến chi nhánh TP. Thủ Đức để nhận hàng.', 2);
GO

-- -------------------------------------------------------------------------------
-- BANNERS
-- -------------------------------------------------------------------------------
INSERT INTO banners (title, image_url, link_url, display_order) VALUES
(N'iPhone 16 Pro Max - Đỉnh cao mới', N'https://res.cloudinary.com/demo/image/upload/sample.jpg', N'/products/iphone-16-pro-max', 1),
(N'Khuyến mãi Laptop tháng 10 - Giảm đến 15%', N'https://res.cloudinary.com/demo/image/upload/sample.jpg', N'/collections/laptop', 2),
(N'MacBook Pro M4 - Sức mạnh vượt trội', N'https://res.cloudinary.com/demo/image/upload/sample.jpg', N'/products/macbook-pro-14-m4-pro', 3);
GO

PRINT N'==========================================';
PRINT N'DATABASE electronics_store_chain đã được';
PRINT N'khởi tạo và nạp dữ liệu mẫu thành công!';
PRINT N'==========================================';
PRINT N'';
PRINT N'Tài khoản thử nghiệm:';
PRINT N'  admin / Admin@123       -> ROLE_ADMIN';
PRINT N'  staff_q1 / Staff@123    -> ROLE_STAFF  (Chi nhánh Q.1)';
PRINT N'  staff_q3 / Staff@123    -> ROLE_STAFF  (Chi nhánh Q.3)';
PRINT N'  staff_thuduc / Staff@123 -> ROLE_STAFF (Chi nhánh Thủ Đức)';
PRINT N'  staff_govap / Staff@123 -> ROLE_STAFF  (Chi nhánh Gò Vấp)';
PRINT N'  shipper1 / Shipper@123  -> ROLE_SHIPPER';
PRINT N'  customer1 / Customer@123 -> ROLE_CUSTOMER';
GO
