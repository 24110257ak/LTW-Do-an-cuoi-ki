-- ==================================================================================
-- DATABASE DUY NHẤT - ELECTRONICS STORE CHAIN / NHÓM 24
-- Thành viên: 24110257 Trần Vũ Anh Khoa; 24110292 Mai Huy Nhật; 24110304 Đặng Nhật Phúc
-- Microsoft SQL Server 2019/2022; NVARCHAR cho nội dung tiếng Việt.
--
-- CÁCH CHẠY: dừng ứng dụng, mở file này trong SSMS, chọn đúng server, bấm F5 TOÀN BỘ.
--   Chưa có database: tự tạo electronics_store_chain, 34 bảng và dữ liệu demo.
--   Đã có database: giữ dữ liệu, tạo bảng còn thiếu và nâng cấp schema chat.
--   Không DROP database; không xóa lịch sử; không nạp lại demo khi đã có bảng ứng dụng.
--   Có transaction/rollback cho schema, nâng cấp và seed; chạy lại được.
--   Kết quả cuối phải có client_message_id_bytes = 128 (NVARCHAR(64)).
--
-- NỘI DUNG: 1. Tạo DB nếu thiếu  2. Schema 34 bảng  3. Nâng cấp chat
--           4. Index  5. Demo cho DB mới  6. Kiểm tra kết quả
-- ==================================================================================
USE master;
GO
SET NOCOUNT ON;
SET IMPLICIT_TRANSACTIONS OFF;
IF DB_ID(N'electronics_store_chain') IS NULL
    EXEC sys.sp_executesql N'CREATE DATABASE electronics_store_chain COLLATE Vietnamese_CI_AS;';
GO
USE electronics_store_chain;
GO
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_PADDING ON;
SET ANSI_WARNINGS ON;
SET ARITHABORT ON;
SET CONCAT_NULL_YIELDS_NULL ON;
SET NUMERIC_ROUNDABORT OFF;

IF DB_NAME() <> N'electronics_store_chain'
    THROW 51100, N'Không ở đúng database electronics_store_chain.', 1;
IF @@TRANCOUNT <> 0
    THROW 51101, N'Hãy hoàn tất transaction đang mở trước khi chạy database.sql.', 1;

BEGIN TRY
    BEGIN TRANSACTION;
    DECLARE @required_tables TABLE (table_name SYSNAME PRIMARY KEY);
    INSERT INTO @required_tables (table_name) VALUES
        (N'roles'),
        (N'users'),
        (N'user_addresses'),
        (N'branches'),
        (N'categories'),
        (N'brands'),
        (N'products'),
        (N'product_images'),
        (N'product_variants'),
        (N'branch_inventory'),
        (N'carts'),
        (N'cart_items'),
        (N'delivery_providers'),
        (N'coupons'),
        (N'orders'),
        (N'simulated_payments'),
        (N'order_items'),
        (N'reviews'),
        (N'wishlists'),
        (N'viewed_products'),
        (N'notifications'),
        (N'chat_sessions'),
        (N'chat_messages'),
        (N'otp_tokens'),
        (N'banners'),
        (N'stock_transfers'),
        (N'stock_transfer_items'),
        (N'stock_logs'),
        (N'otp_rate_limits'),
        (N'order_return_receipts'),
        (N'order_return_lines'),
        (N'delivery_attempts'),
        (N'cost_price_audits'),
        (N'payment_refunds');
    -- Chỉ nạp dữ liệu demo nếu trước khi chạy chưa có bất kỳ bảng ứng dụng nào.
    DECLARE @seed_demo BIT = CASE WHEN EXISTS (
        SELECT 1 FROM sys.tables t JOIN @required_tables r ON r.table_name = t.name
        WHERE t.schema_id = SCHEMA_ID(N'dbo')
    ) THEN 0 ELSE 1 END;

    -- 2. SCHEMA: tạo bảng thiếu theo đúng thứ tự khóa ngoại.

    -- BẢNG 1: ROLES
    IF OBJECT_ID(N'dbo.roles', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.roles (
    role_id   INT IDENTITY(1,1) PRIMARY KEY,
    role_name NVARCHAR(50) NOT NULL UNIQUE -- CUSTOMER, STAFF, SHIPPER, ADMIN

        );
    END;

    -- BẢNG 2: USERS
    IF OBJECT_ID(N'dbo.users', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.users (
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
    branch_id     INT NULL,                               -- FK được thêm sau khi có branches
    created_at    DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES dbo.roles(role_id)

        );
    END;

    -- BẢNG 3: USER_ADDRESSES
    IF OBJECT_ID(N'dbo.user_addresses', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.user_addresses (
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
    CONSTRAINT fk_addr_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id) ON DELETE CASCADE

        );
    END;

    -- BẢNG 4: BRANCHES
    IF OBJECT_ID(N'dbo.branches', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.branches (
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
    CONSTRAINT fk_branch_manager FOREIGN KEY (manager_id) REFERENCES dbo.users(user_id)

        );
    END;

    -- BẢNG 5: CATEGORIES
    IF OBJECT_ID(N'dbo.categories', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.categories (
    category_id   INT IDENTITY(1,1) PRIMARY KEY,
    category_name NVARCHAR(150) NOT NULL,
    slug          NVARCHAR(200) NOT NULL UNIQUE,
    image_url     NVARCHAR(500) NULL,
    parent_id     INT NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active     BIT NOT NULL DEFAULT 1,
    created_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_category_parent FOREIGN KEY (parent_id) REFERENCES dbo.categories(category_id)

        );
    END;

    -- BẢNG 6: BRANDS
    IF OBJECT_ID(N'dbo.brands', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.brands (
    brand_id     INT IDENTITY(1,1) PRIMARY KEY,
    brand_name   NVARCHAR(100) NOT NULL UNIQUE,
    logo_url     NVARCHAR(500) NULL,
    description  NVARCHAR(1000) NULL,
    is_active    BIT NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT GETDATE()

        );
    END;

    -- BẢNG 7: PRODUCTS
    IF OBJECT_ID(N'dbo.products', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.products (
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
    CONSTRAINT fk_product_category FOREIGN KEY (category_id) REFERENCES dbo.categories(category_id),
    CONSTRAINT fk_product_brand FOREIGN KEY (brand_id) REFERENCES dbo.brands(brand_id)

        );
    END;

    -- BẢNG 8: PRODUCT_IMAGES
    IF OBJECT_ID(N'dbo.product_images', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.product_images (
    image_id      BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    image_url     NVARCHAR(500) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_pimg_product FOREIGN KEY (product_id) REFERENCES dbo.products(product_id) ON DELETE CASCADE

        );
    END;

    -- BẢNG 9: PRODUCT_VARIANTS
    IF OBJECT_ID(N'dbo.product_variants', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.product_variants (
    variant_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    product_id    BIGINT NOT NULL,
    color         NVARCHAR(100) NULL,           -- Màu sắc: Titan Đen, Trắng Ngà...
    storage       NVARCHAR(50)  NULL,           -- Dung lượng: 128GB, 256GB...
    ram           NVARCHAR(50)  NULL,           -- RAM: 8GB, 16GB, 32GB...
    cost_price    DECIMAL(18,0) NOT NULL DEFAULT 0, -- Giá vốn biến thể (COGS)
    price         DECIMAL(18,0) NOT NULL,       -- Giá niêm yết biến thể
    promo_price   DECIMAL(18,0) NULL,           -- Giá khuyến mãi
    sku           NVARCHAR(100) NOT NULL UNIQUE,
    image_url     NVARCHAR(500) NULL,           -- Ảnh riêng biến thể (Cloudinary)
    is_active     BIT NOT NULL DEFAULT 1,
    CONSTRAINT fk_variant_product FOREIGN KEY (product_id) REFERENCES dbo.products(product_id) ON DELETE CASCADE

        );
    END;

    -- BẢNG 10: BRANCH_INVENTORY
    IF OBJECT_ID(N'dbo.branch_inventory', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.branch_inventory (
    inventory_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id     INT NOT NULL,
    variant_id    BIGINT NOT NULL,
    quantity      INT NOT NULL DEFAULT 0,
    safety_stock  INT NOT NULL DEFAULT 3,       -- Ngưỡng an toàn cảnh báo sắp hết hàng
    updated_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_inv_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_inv_variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT uq_inv_branch_variant UNIQUE (branch_id, variant_id)

        );
    END;

    -- BẢNG 11: CARTS
    IF OBJECT_ID(N'dbo.carts', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.carts (
    cart_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id    BIGINT NOT NULL UNIQUE,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_cart_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id) ON DELETE CASCADE

        );
    END;

    -- BẢNG 12: CART_ITEMS
    IF OBJECT_ID(N'dbo.cart_items', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.cart_items (
    cart_item_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    cart_id      BIGINT NOT NULL,
    variant_id   BIGINT NOT NULL,
    quantity     INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    added_at     DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_ci_cart FOREIGN KEY (cart_id) REFERENCES dbo.carts(cart_id) ON DELETE CASCADE,
    CONSTRAINT fk_ci_variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT uq_cart_variant UNIQUE (cart_id, variant_id)

        );
    END;

    -- BẢNG 13: DELIVERY_PROVIDERS
    IF OBJECT_ID(N'dbo.delivery_providers', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.delivery_providers (
    provider_id   INT IDENTITY(1,1) PRIMARY KEY,
    provider_name NVARCHAR(150) NOT NULL UNIQUE,
    base_fee      DECIMAL(18,0) NOT NULL DEFAULT 0,
    logo_url      NVARCHAR(500) NULL,
    is_active     BIT NOT NULL DEFAULT 1

        );
    END;

    -- BẢNG 14: COUPONS
    IF OBJECT_ID(N'dbo.coupons', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.coupons (
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
    CONSTRAINT fk_coupon_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id)

        );
    END;

    -- BẢNG 15: ORDERS
    IF OBJECT_ID(N'dbo.orders', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.orders (
    order_id           BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_code         NVARCHAR(30) NOT NULL UNIQUE,   -- ESC-20261021-00001
    checkout_token     NVARCHAR(64) NULL,       -- Chống trùng lặp tạo đơn (Idempotency)
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
    -- Tài chính & Quản trị Lợi nhuận (P&L)
    subtotal           DECIMAL(18,0) NOT NULL DEFAULT 0,
    discount_amount    DECIMAL(18,0) NOT NULL DEFAULT 0,
    total_amount       DECIMAL(18,0) NOT NULL DEFAULT 0,
    total_cost         DECIMAL(18,0) NOT NULL DEFAULT 0,       -- Tổng giá vốn đơn hàng (COGS)
    gross_profit       DECIMAL(18,0) NOT NULL DEFAULT 0,       -- Lợi nhuận gộp (Gross Profit)
    coupon_id          INT NULL,
    -- Thanh toán & Quản trị đối soát tiền mặt COD
    payment_method     NVARCHAR(20) NOT NULL DEFAULT 'COD', -- COD | VNPAY
    payment_status     NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | PAID | REFUNDED
    vnpay_txn_ref      NVARCHAR(100) NULL,
    cod_collected      BIT NOT NULL DEFAULT 0,                 -- Shipper đã thu tiền mặt COD
    cod_reconciled     BIT NOT NULL DEFAULT 0,                 -- Kế toán đã đối soát nộp quỹ
    cod_reconciled_at  DATETIME NULL,
    cod_transaction_id NVARCHAR(100) NULL,
    -- Trạng thái đơn hàng (10 trạng thái)
    status             NVARCHAR(30) NOT NULL DEFAULT 'NEW',
        -- NEW | CONFIRMED | READY_FOR_PICKUP | SHIPPING | DELIVERY_FAILED | RETURNING | RETURNED | DELIVERED | CANCELLED | RETURNED_REFUND
    shipper_id         BIGINT NULL,
    note               NVARCHAR(500) NULL,
    cancelled_reason   NVARCHAR(300) NULL,
    created_at         DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at         DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id),
    CONSTRAINT fk_order_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_order_pickup_branch FOREIGN KEY (pickup_branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_order_provider FOREIGN KEY (provider_id) REFERENCES dbo.delivery_providers(provider_id),
    CONSTRAINT fk_order_coupon FOREIGN KEY (coupon_id) REFERENCES dbo.coupons(coupon_id),
    CONSTRAINT fk_order_shipper FOREIGN KEY (shipper_id) REFERENCES dbo.users(user_id),
    CONSTRAINT chk_order_status CHECK (status IN ('NEW','CONFIRMED','READY_FOR_PICKUP','SHIPPING','DELIVERY_FAILED','RETURNING','RETURNED','DELIVERED','CANCELLED','RETURNED_REFUND')),
    CONSTRAINT chk_payment_method CHECK (payment_method IN ('COD','VNPAY')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('DELIVERY','PICKUP'))

        );
    END;

    -- BẢNG 16: ORDER_ITEMS
    IF OBJECT_ID(N'dbo.order_items', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.order_items (
    order_item_id  BIGINT IDENTITY(1,1) PRIMARY KEY,
    order_id       BIGINT NOT NULL,
    variant_id     BIGINT NOT NULL,
    product_name   NVARCHAR(300) NOT NULL, -- Snapshot tên sản phẩm lúc mua
    variant_info   NVARCHAR(200) NULL,     -- Snapshot màu/ROM/RAM lúc mua
    unit_price     DECIMAL(18,0) NOT NULL,
    cost_price     DECIMAL(18,0) NOT NULL DEFAULT 0, -- Snapshot giá vốn lúc mua (COGS)
    quantity       INT NOT NULL DEFAULT 1 CHECK (quantity > 0),
    line_total     AS (unit_price * quantity) PERSISTED,
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_oi_variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(variant_id)

        );
    END;

    -- BẢNG 17: REVIEWS
    IF OBJECT_ID(N'dbo.reviews', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.reviews (
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
    CONSTRAINT fk_rv_order FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id),
    CONSTRAINT fk_rv_order_item FOREIGN KEY (order_item_id) REFERENCES dbo.order_items(order_item_id),
    CONSTRAINT fk_rv_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id),
    CONSTRAINT fk_rv_product FOREIGN KEY (product_id) REFERENCES dbo.products(product_id),
    CONSTRAINT uq_review_order_item UNIQUE (order_item_id) -- 1 item = 1 review

        );
    END;

    -- BẢNG 18: WISHLISTS
    IF OBJECT_ID(N'dbo.wishlists', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.wishlists (
    wishlist_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    product_id  BIGINT NOT NULL,
    added_at    DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_wl_product FOREIGN KEY (product_id) REFERENCES dbo.products(product_id),
    CONSTRAINT uq_wishlist UNIQUE (user_id, product_id)

        );
    END;

    -- BẢNG 19: VIEWED_PRODUCTS
    IF OBJECT_ID(N'dbo.viewed_products', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.viewed_products (
    view_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    viewed_at  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_vp_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_vp_product FOREIGN KEY (product_id) REFERENCES dbo.products(product_id),
    CONSTRAINT uq_viewed UNIQUE (user_id, product_id)

        );
    END;

    -- BẢNG 20: NOTIFICATIONS
    IF OBJECT_ID(N'dbo.notifications', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.notifications (
    notif_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     BIGINT NOT NULL,                        -- NULL = broadcast
    branch_id   INT NULL,                              -- Thông báo tới chi nhánh
    type        NVARCHAR(50) NOT NULL,                 -- NEW_ORDER | STATUS_CHANGED | SYSTEM
    title       NVARCHAR(300) NOT NULL,
    message     NVARCHAR(1000) NOT NULL,
    ref_id      BIGINT NULL,                           -- order_id hoặc id đối tượng liên quan
    is_read     BIT NOT NULL DEFAULT 0,
    created_at  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_notif_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_notif_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id)

        );
    END;

    -- BẢNG 21: CHAT_SESSIONS
    IF OBJECT_ID(N'dbo.chat_sessions', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.chat_sessions (
    session_id             BIGINT IDENTITY(1,1) PRIMARY KEY,
    customer_id            BIGINT NOT NULL,                  -- Khách sở hữu cuộc trò chuyện
    branch_id              INT NOT NULL,                     -- Chi nhánh tư vấn
    staff_id               BIGINT NULL,                      -- Nhân viên tiếp nhận; NULL = đang chờ
    status                 NVARCHAR(20) NOT NULL CONSTRAINT df_chat_sessions_status DEFAULT N'OPEN',
    created_at             DATETIME2(6) NOT NULL CONSTRAINT df_chat_sessions_created DEFAULT SYSDATETIME(),
    closed_at              DATETIME2(6) NULL,
    last_message_at        DATETIME2(6) NOT NULL CONSTRAINT df_chat_sessions_last_message DEFAULT SYSDATETIME(),
    customer_last_read_id  BIGINT NOT NULL CONSTRAINT df_chat_sessions_customer_read DEFAULT 0,
    staff_last_read_id     BIGINT NOT NULL CONSTRAINT df_chat_sessions_staff_read DEFAULT 0,
    CONSTRAINT fk_cs_customer FOREIGN KEY (customer_id) REFERENCES dbo.users(user_id),
    CONSTRAINT fk_cs_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_cs_staff FOREIGN KEY (staff_id) REFERENCES dbo.users(user_id)

        );
    END;

    -- BẢNG 22: CHAT_MESSAGES
    IF OBJECT_ID(N'dbo.chat_messages', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.chat_messages (
    message_id         BIGINT IDENTITY(1,1) PRIMARY KEY,      -- ID do database cấp
    session_id         BIGINT NOT NULL,
    sender_id          BIGINT NOT NULL,                       -- Backend lấy từ tài khoản đăng nhập
    content            NVARCHAR(2000) NOT NULL,
    sent_at            DATETIME2(6) NOT NULL CONSTRAINT df_chat_messages_sent DEFAULT SYSDATETIME(),
    client_message_id  NVARCHAR(64) NOT NULL,                 -- Mã gửi từ client; gửi lại không tạo tin trùng
    CONSTRAINT uq_chat_message_client UNIQUE (session_id, client_message_id),
    CONSTRAINT fk_cm_session FOREIGN KEY (session_id) REFERENCES dbo.chat_sessions(session_id) ON DELETE CASCADE,
    CONSTRAINT fk_cm_sender FOREIGN KEY (sender_id) REFERENCES dbo.users(user_id)

        );
    END;

    -- BẢNG 23: OTP_TOKENS
    IF OBJECT_ID(N'dbo.otp_tokens', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.otp_tokens (
    token_id    BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id     BIGINT NULL,                         -- NULL nếu chưa có user (chưa đăng ký)
    email       NVARCHAR(255) NOT NULL,
    otp_code    NVARCHAR(10) NOT NULL,
    purpose     NVARCHAR(30) NOT NULL,               -- REGISTER | FORGOT_PASSWORD
    is_used     BIT NOT NULL DEFAULT 0,
    expired_at  DATETIME NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES dbo.users(user_id) ON DELETE SET NULL

        );
    END;

    -- BẢNG 24: BANNERS
    IF OBJECT_ID(N'dbo.banners', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.banners (
    banner_id    INT IDENTITY(1,1) PRIMARY KEY,
    title        NVARCHAR(300) NOT NULL,
    image_url    NVARCHAR(500) NOT NULL,
    link_url     NVARCHAR(500) NULL,
    display_order INT NOT NULL DEFAULT 0,
    is_active    BIT NOT NULL DEFAULT 1,
    created_at   DATETIME NOT NULL DEFAULT GETDATE()

        );
    END;

    -- BẢNG 25: STOCK_TRANSFERS
    IF OBJECT_ID(N'dbo.stock_transfers', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.stock_transfers (
    transfer_id            BIGINT IDENTITY(1,1) PRIMARY KEY,
    transfer_code          NVARCHAR(50) NOT NULL UNIQUE,
    source_branch_id       INT NOT NULL,
    destination_branch_id  INT NOT NULL,
    status                 NVARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_by_id          BIGINT NOT NULL,
    approved_by_id         BIGINT NULL,
    note                   NVARCHAR(500) NULL,
    created_at             DATETIME NOT NULL DEFAULT GETDATE(),
    updated_at             DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_st_src_branch FOREIGN KEY (source_branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_st_dest_branch FOREIGN KEY (destination_branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_st_created_by FOREIGN KEY (created_by_id) REFERENCES dbo.users(user_id),
    CONSTRAINT fk_st_approved_by FOREIGN KEY (approved_by_id) REFERENCES dbo.users(user_id),
    CONSTRAINT chk_st_status CHECK (status IN ('PENDING', 'APPROVED', 'IN_TRANSIT', 'RETURNING', 'RETURNED', 'COMPLETED', 'CANCELLED'))

        );
    END;

    -- BẢNG 26: STOCK_TRANSFER_ITEMS
    IF OBJECT_ID(N'dbo.stock_transfer_items', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.stock_transfer_items (
    item_id     BIGINT IDENTITY(1,1) PRIMARY KEY,
    transfer_id BIGINT NOT NULL,
    variant_id  BIGINT NOT NULL,
    quantity    INT NOT NULL CHECK (quantity > 0),
    CONSTRAINT fk_sti_transfer FOREIGN KEY (transfer_id) REFERENCES dbo.stock_transfers(transfer_id) ON DELETE CASCADE,
    CONSTRAINT fk_sti_variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(variant_id)

        );
    END;

    -- BẢNG 27: STOCK_LOGS
    IF OBJECT_ID(N'dbo.stock_logs', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.stock_logs (
    log_id          BIGINT IDENTITY(1,1) PRIMARY KEY,
    branch_id       INT NOT NULL,
    variant_id      BIGINT NOT NULL,
    movement_type   NVARCHAR(30) NOT NULL,
    quantity_change INT NOT NULL,
    quantity_after  INT NOT NULL,
    performed_by_id BIGINT NULL,
    reference_code  NVARCHAR(100) NULL,
    note            NVARCHAR(500) NULL,
    created_at      DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT fk_sl_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id),
    CONSTRAINT fk_sl_variant FOREIGN KEY (variant_id) REFERENCES dbo.product_variants(variant_id),
    CONSTRAINT fk_sl_user FOREIGN KEY (performed_by_id) REFERENCES dbo.users(user_id),
    CONSTRAINT chk_sl_type CHECK (movement_type IN ('SALE', 'RETURN', 'TRANSFER_OUT', 'TRANSFER_IN', 'ADJUSTMENT'))

        );
    END;

    -- Quan hệ vòng users.branch_id -> branches và branches.manager_id -> users.
    IF COL_LENGTH('dbo.users', 'branch_id') IS NULL
        ALTER TABLE dbo.users ADD branch_id INT NULL;
    IF NOT EXISTS (
        SELECT 1 FROM sys.foreign_key_columns f
        WHERE f.parent_object_id = OBJECT_ID('dbo.users')
          AND COL_NAME(f.parent_object_id, f.parent_column_id) = N'branch_id'
          AND f.referenced_object_id = OBJECT_ID('dbo.branches')
    )
        EXEC sys.sp_executesql N'ALTER TABLE dbo.users ADD CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(branch_id);';

    -- 3. NÂNG CẤP CHAT: sửa client_message_id và tương thích phiên chat cũ.
    -- Ghi nhận trước khi thêm cột để chỉ sao chép mốc đọc cũ một lần.
    DECLARE @copy_legacy_read BIT = CASE WHEN COL_LENGTH('dbo.chat_sessions', 'customer_last_read_id') IS NULL THEN 1 ELSE 0 END;

    -- Thêm cột dạng nullable trước để không làm lỗi các hàng đã có dữ liệu.
    IF COL_LENGTH('dbo.chat_sessions', 'last_message_at') IS NULL
        ALTER TABLE dbo.chat_sessions ADD last_message_at DATETIME2(6) NULL;
    IF COL_LENGTH('dbo.chat_sessions', 'customer_last_read_id') IS NULL
        ALTER TABLE dbo.chat_sessions ADD customer_last_read_id BIGINT NULL;
    IF COL_LENGTH('dbo.chat_sessions', 'staff_last_read_id') IS NULL
        ALTER TABLE dbo.chat_sessions ADD staff_last_read_id BIGINT NULL;
    IF COL_LENGTH('dbo.chat_messages', 'client_message_id') IS NULL
        ALTER TABLE dbo.chat_messages ADD client_message_id NVARCHAR(64) NULL;

    -- Bỏ ràng buộc cùng tên trước khi chuẩn hóa kiểu cột.
    -- Bản cũ có thể đặt nhầm unique trên (session_id, message_id).
    IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE parent_object_id = OBJECT_ID('dbo.chat_messages') AND name = N'uq_chat_message_client')
        ALTER TABLE dbo.chat_messages DROP CONSTRAINT uq_chat_message_client;
    ELSE IF EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.chat_messages') AND name = N'uq_chat_message_client')
        DROP INDEX uq_chat_message_client ON dbo.chat_messages;

    -- Các câu tham chiếu cột vừa thêm được biên dịch ở batch động sau ALTER TABLE.
    EXEC sys.sp_executesql N'
        IF EXISTS (SELECT 1 FROM dbo.chat_messages WHERE DATALENGTH(CONVERT(nvarchar(max), client_message_id)) > 128)
            THROW 51103, N''Có client_message_id dài hơn 64 ký tự. Chưa thay đổi dữ liệu; cần kiểm tra các hàng đó.'', 1;
        UPDATE dbo.chat_sessions SET customer_last_read_id = COALESCE(customer_last_read_id, 0),
                                     staff_last_read_id = COALESCE(staff_last_read_id, 0);
        -- Chỉ điền mã thiếu; nội dung, người gửi, thời điểm và ID tin cũ được giữ nguyên.
        UPDATE dbo.chat_messages SET client_message_id = CONCAT(''legacy-'', message_id)
        WHERE client_message_id IS NULL OR LTRIM(RTRIM(client_message_id)) = '''';
        -- Nếu dữ liệu cũ lặp mã, giữ mã của tin đầu, cấp mã legacy mới cho các tin còn lại.
        ;WITH duplicate_ids AS (
            SELECT client_message_id,
                   ROW_NUMBER() OVER (PARTITION BY session_id, client_message_id ORDER BY message_id) AS rn
            FROM dbo.chat_messages
        ) UPDATE duplicate_ids SET client_message_id = CONCAT(''legacy-'', CONVERT(nvarchar(36), NEWID())) WHERE rn > 1;
        UPDATE s SET last_message_at = CASE
            WHEN s.last_message_at IS NULL THEN COALESCE(m.latest, s.created_at, SYSDATETIME())
            WHEN m.latest > s.last_message_at THEN m.latest ELSE s.last_message_at END
        FROM dbo.chat_sessions s
        OUTER APPLY (SELECT MAX(cm.sent_at) AS latest FROM dbo.chat_messages cm WHERE cm.session_id = s.session_id) m;
    ';

    -- Tương thích bản entity từng đặt nhầm Long là customer_last_read_at.
    -- Giữ nguyên cột/dữ liệu cũ; thêm default nếu cột cũ NOT NULL để INSERT phiên mới không bị chặn.
    DECLARE @legacy_type SYSNAME;
    SELECT @legacy_type = t.name FROM sys.columns c
    JOIN sys.types t ON c.user_type_id = t.user_type_id
    WHERE c.object_id = OBJECT_ID('dbo.chat_sessions') AND c.name = N'customer_last_read_at';
    IF @copy_legacy_read = 1 AND @legacy_type IN (N'bigint', N'int', N'smallint', N'tinyint')
        EXEC sys.sp_executesql N'
            UPDATE s SET customer_last_read_id = m.message_id
            FROM dbo.chat_sessions s JOIN dbo.chat_messages m
              ON m.session_id = s.session_id AND m.message_id = s.customer_last_read_at
            WHERE s.customer_last_read_id = 0;
        ';
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.chat_sessions')
               AND name = N'customer_last_read_at' AND is_nullable = 0 AND default_object_id = 0)
    BEGIN
        IF @legacy_type IN (N'bigint', N'int', N'smallint', N'tinyint')
            EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_sessions ADD CONSTRAINT df_chat_sessions_legacy_read DEFAULT (0) FOR customer_last_read_at;';
        ELSE IF @legacy_type IN (N'datetime', N'datetime2', N'smalldatetime', N'date')
            EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_sessions ADD CONSTRAINT df_chat_sessions_legacy_read DEFAULT (SYSDATETIME()) FOR customer_last_read_at;';
        ELSE
            THROW 51104, N'Kiểu cột customer_last_read_at cũ không được hỗ trợ tự động. Migration đã dừng.', 1;
    END;

    -- Chuyển các cột đã điền đầy đủ sang NOT NULL và tạo default cho INSERT mới.
    EXEC sys.sp_executesql N'
        ALTER TABLE dbo.chat_messages ALTER COLUMN client_message_id NVARCHAR(64) NOT NULL;
        ALTER TABLE dbo.chat_sessions ALTER COLUMN customer_last_read_id BIGINT NOT NULL;
        ALTER TABLE dbo.chat_sessions ALTER COLUMN staff_last_read_id BIGINT NOT NULL;
    ';
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'last_message_at' AND default_object_id <> 0)
        EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_sessions ADD CONSTRAINT df_chat_sessions_last_message DEFAULT (SYSDATETIME()) FOR last_message_at;';
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'customer_last_read_id' AND default_object_id <> 0)
        EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_sessions ADD CONSTRAINT df_chat_sessions_customer_read DEFAULT (0) FOR customer_last_read_id;';
    IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'staff_last_read_id' AND default_object_id <> 0)
        EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_sessions ADD CONSTRAINT df_chat_sessions_staff_read DEFAULT (0) FOR staff_last_read_id;';
    -- Giữ kiểu ngày giờ cũ nếu đã có, chỉ bổ sung NOT NULL khi cần.
    DECLARE @last_message_type SYSNAME;
    SELECT @last_message_type = t.name FROM sys.columns c JOIN sys.types t ON c.user_type_id = t.user_type_id
    WHERE c.object_id = OBJECT_ID('dbo.chat_sessions') AND c.name = N'last_message_at';
    IF EXISTS (SELECT 1 FROM sys.columns WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'last_message_at' AND is_nullable = 1)
    BEGIN
        IF @last_message_type = N'datetime'
            EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_sessions ALTER COLUMN last_message_at DATETIME NOT NULL;';
        ELSE IF @last_message_type = N'datetime2'
        BEGIN
            DECLARE @last_message_scale TINYINT = (SELECT scale FROM sys.columns WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'last_message_at');
            DECLARE @alter_last_message NVARCHAR(300) = CONCAT(N'ALTER TABLE dbo.chat_sessions ALTER COLUMN last_message_at DATETIME2(', @last_message_scale, N') NOT NULL;');
            EXEC sys.sp_executesql @alter_last_message;
        END
        ELSE
            THROW 51105, N'last_message_at phải có kiểu DATETIME hoặc DATETIME2.', 1;
    END;
    EXEC sys.sp_executesql N'ALTER TABLE dbo.chat_messages ADD CONSTRAINT uq_chat_message_client UNIQUE (session_id, client_message_id);';

    -- Kiểm tra trước khi COMMIT. Nếu thiếu cột thì rollback, không báo thành công giả.
    IF COL_LENGTH('dbo.chat_messages', 'client_message_id') <> 128 OR COL_LENGTH('dbo.chat_messages', 'client_message_id') IS NULL
        THROW 51106, N'client_message_id chưa đúng NVARCHAR(64).', 1;
    IF ISNULL(COL_LENGTH('dbo.chat_sessions', 'customer_last_read_id'), -1) <> 8 OR ISNULL(COL_LENGTH('dbo.chat_sessions', 'staff_last_read_id'), -1) <> 8
        THROW 51107, N'Mốc đọc phải có kiểu BIGINT.', 1;
    IF EXISTS (SELECT 1 FROM sys.columns WHERE
        (object_id = OBJECT_ID('dbo.chat_messages') AND name = N'client_message_id' AND is_nullable = 1)
        OR (object_id = OBJECT_ID('dbo.chat_sessions') AND name IN (N'last_message_at', N'customer_last_read_id', N'staff_last_read_id') AND is_nullable = 1))
        THROW 51108, N'Còn cột chat chưa được chuyển sang NOT NULL.', 1;

    -- 3A. VNPAY MÔ PHỎNG: không tạo giao dịch ngân hàng thật.
    IF COL_LENGTH('dbo.orders', 'payment_simulated') IS NULL
        ALTER TABLE dbo.orders ADD payment_simulated BIT NOT NULL CONSTRAINT df_orders_payment_simulated DEFAULT 0;
    IF OBJECT_ID(N'dbo.simulated_payments', N'U') IS NULL
    BEGIN
        CREATE TABLE dbo.simulated_payments (
            payment_id BIGINT IDENTITY(1,1) PRIMARY KEY,
            order_id BIGINT NOT NULL UNIQUE,
            reference NVARCHAR(64) NOT NULL UNIQUE,
            amount DECIMAL(18,0) NOT NULL,
            status NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
            created_at DATETIME2(6) NOT NULL DEFAULT SYSDATETIME(),
            expires_at DATETIME2(6) NOT NULL,
            settled_at DATETIME2(6) NULL,
            CONSTRAINT fk_sim_payment_order FOREIGN KEY (order_id) REFERENCES dbo.orders(order_id),
            CONSTRAINT chk_sim_payment CHECK (amount >= 0 AND expires_at > created_at AND status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED'))
        );
    END;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.simulated_payments') AND name = N'ix_sim_payment_expiry')
        CREATE INDEX ix_sim_payment_expiry ON dbo.simulated_payments(status,expires_at);
    -- 3B. NHẤT QUÁN CHECKOUT, COUPON VÀ ĐIỀU CHUYỂN (28/10/2026).
    -- Chỉ thêm cột; không tự sửa coupon sai hoặc tự cộng/trừ tồn kho cũ.
    IF COL_LENGTH('dbo.orders', 'completed_at') IS NULL
        ALTER TABLE dbo.orders ADD completed_at DATETIME2(6) NULL;
    IF COL_LENGTH('dbo.orders', 'stock_released') IS NULL
        ALTER TABLE dbo.orders ADD stock_released BIT NOT NULL CONSTRAINT df_orders_stock_released DEFAULT 0;
    IF COL_LENGTH('dbo.orders', 'coupon_released') IS NULL
        ALTER TABLE dbo.orders ADD coupon_released BIT NOT NULL CONSTRAINT df_orders_coupon_released DEFAULT 0;
    IF COL_LENGTH('dbo.stock_transfers', 'returned_at') IS NULL
        ALTER TABLE dbo.stock_transfers ADD returned_at DATETIME2(6) NULL;
    IF COL_LENGTH('dbo.stock_transfer_items', 'returned_quantity') IS NULL
        ALTER TABLE dbo.stock_transfer_items ADD returned_quantity INT NOT NULL CONSTRAINT df_sti_returned_quantity DEFAULT 0;

    DECLARE @drop_transfer_checks NVARCHAR(MAX) = N'';
    SELECT @drop_transfer_checks = @drop_transfer_checks + N'ALTER TABLE dbo.stock_transfers DROP CONSTRAINT ' + QUOTENAME(name) + N';'
    FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.stock_transfers') AND definition LIKE N'%status%';
    IF LEN(@drop_transfer_checks) > 0 EXEC sys.sp_executesql @drop_transfer_checks;
    ALTER TABLE dbo.stock_transfers WITH CHECK ADD CONSTRAINT chk_st_status
        CHECK (status IN ('PENDING','APPROVED','IN_TRANSIT','RETURNING','RETURNED','COMPLETED','CANCELLED'));
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.stock_transfer_items') AND name = N'chk_sti_returned_quantity')
        EXEC sys.sp_executesql N'ALTER TABLE dbo.stock_transfer_items WITH CHECK ADD CONSTRAINT chk_sti_returned_quantity CHECK (returned_quantity >= 0 AND returned_quantity <= quantity);';

    IF EXISTS (SELECT 1 FROM dbo.coupons WHERE
        LEN(LTRIM(RTRIM(coupon_code))) = 0 OR discount_type NOT IN ('PERCENT','AMOUNT') OR discount_value <= 0
        OR (discount_type = 'PERCENT' AND discount_value > 100) OR min_order_amt < 0
        OR (max_discount IS NOT NULL AND max_discount <= 0) OR valid_from >= valid_until
        OR usage_limit <= 0 OR used_count < 0 OR used_count > usage_limit)
    BEGIN
        SELECT coupon_id, coupon_code, discount_type, discount_value, min_order_amt, max_discount,
               valid_from, valid_until, usage_limit, used_count FROM dbo.coupons
        WHERE LEN(LTRIM(RTRIM(coupon_code))) = 0 OR discount_type NOT IN ('PERCENT','AMOUNT') OR discount_value <= 0
        OR (discount_type = 'PERCENT' AND discount_value > 100) OR min_order_amt < 0
        OR (max_discount IS NOT NULL AND max_discount <= 0) OR valid_from >= valid_until
        OR usage_limit <= 0 OR used_count < 0 OR used_count > usage_limit;
        THROW 51120, N'Có coupon không hợp lệ. Kiểm tra các dòng được liệt kê; migration đã rollback, chưa tự thay đổi giá trị.', 1;
    END;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.coupons') AND name = N'chk_coupon_rules')
        ALTER TABLE dbo.coupons WITH CHECK ADD CONSTRAINT chk_coupon_rules CHECK (
            LEN(LTRIM(RTRIM(coupon_code))) > 0 AND discount_type IN ('PERCENT','AMOUNT') AND discount_value > 0
            AND (discount_type <> 'PERCENT' OR discount_value <= 100) AND min_order_amt >= 0
            AND (max_discount IS NULL OR max_discount > 0) AND valid_from < valid_until
            AND usage_limit > 0 AND used_count >= 0 AND used_count <= usage_limit);
    ALTER TABLE dbo.coupons WITH CHECK CHECK CONSTRAINT chk_coupon_rules;

    -- 4. INDEX. Bỏ UNIQUE cũ trên checkout_token để SQL Server cho phép nhiều NULL.
    -- Duy nhất các token đã điền được bảo vệ bằng filtered unique index bên dưới.
    DECLARE @drop_checkout_uq NVARCHAR(MAX) = N'';
    SELECT @drop_checkout_uq = @drop_checkout_uq + N'ALTER TABLE dbo.orders DROP CONSTRAINT ' + QUOTENAME(k.name) + N';'
    FROM sys.key_constraints k
    WHERE k.parent_object_id = OBJECT_ID('dbo.orders') AND k.type = N'UQ'
      AND (SELECT COUNT(*) FROM sys.index_columns i WHERE i.object_id = k.parent_object_id AND i.index_id = k.unique_index_id AND i.key_ordinal > 0) = 1
      AND EXISTS (SELECT 1 FROM sys.index_columns i JOIN sys.columns c ON c.object_id = i.object_id AND c.column_id = i.column_id
                  WHERE i.object_id = k.parent_object_id AND i.index_id = k.unique_index_id AND c.name = N'checkout_token');
    IF LEN(@drop_checkout_uq) > 0 EXEC sys.sp_executesql @drop_checkout_uq;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'ix_chat_sessions_branch_activity')
        EXEC sys.sp_executesql N'CREATE INDEX ix_chat_sessions_branch_activity ON dbo.chat_sessions(branch_id, last_message_at DESC, session_id DESC);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.chat_sessions') AND name = N'ix_chat_sessions_customer_activity')
        EXEC sys.sp_executesql N'CREATE INDEX ix_chat_sessions_customer_activity ON dbo.chat_sessions(customer_id, last_message_at DESC, session_id DESC);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.chat_messages') AND name = N'ix_chat_messages_history')
        EXEC sys.sp_executesql N'CREATE INDEX ix_chat_messages_history ON dbo.chat_messages(session_id, message_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.products') AND name = N'idx_products_category')
        EXEC sys.sp_executesql N'CREATE INDEX idx_products_category ON dbo.products(category_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.products') AND name = N'idx_products_brand')
        EXEC sys.sp_executesql N'CREATE INDEX idx_products_brand ON dbo.products(brand_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.products') AND name = N'idx_products_slug')
        EXEC sys.sp_executesql N'CREATE INDEX idx_products_slug ON dbo.products(slug);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.products') AND name = N'idx_products_featured')
        EXEC sys.sp_executesql N'CREATE INDEX idx_products_featured ON dbo.products(is_featured, is_active);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.products') AND name = N'idx_products_rating')
        EXEC sys.sp_executesql N'CREATE INDEX idx_products_rating ON dbo.products(avg_rating DESC);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.products') AND name = N'idx_products_sold')
        EXEC sys.sp_executesql N'CREATE INDEX idx_products_sold ON dbo.products(total_sold DESC);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.product_variants') AND name = N'idx_variants_product')
        EXEC sys.sp_executesql N'CREATE INDEX idx_variants_product ON dbo.product_variants(product_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.branch_inventory') AND name = N'idx_inventory_branch')
        EXEC sys.sp_executesql N'CREATE INDEX idx_inventory_branch ON dbo.branch_inventory(branch_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.branch_inventory') AND name = N'idx_inventory_variant')
        EXEC sys.sp_executesql N'CREATE INDEX idx_inventory_variant ON dbo.branch_inventory(variant_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.orders') AND name = N'ux_orders_checkout_token')
        EXEC sys.sp_executesql N'CREATE UNIQUE INDEX ux_orders_checkout_token ON dbo.orders(checkout_token) WHERE checkout_token IS NOT NULL;';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.orders') AND name = N'idx_orders_user')
        EXEC sys.sp_executesql N'CREATE INDEX idx_orders_user ON dbo.orders(user_id, created_at DESC);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.orders') AND name = N'idx_orders_branch')
        EXEC sys.sp_executesql N'CREATE INDEX idx_orders_branch ON dbo.orders(branch_id, status);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.orders') AND name = N'idx_orders_status')
        EXEC sys.sp_executesql N'CREATE INDEX idx_orders_status ON dbo.orders(status);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.orders') AND name = N'idx_orders_code')
        EXEC sys.sp_executesql N'CREATE INDEX idx_orders_code ON dbo.orders(order_code);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.reviews') AND name = N'idx_reviews_product')
        EXEC sys.sp_executesql N'CREATE INDEX idx_reviews_product ON dbo.reviews(product_id);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.notifications') AND name = N'idx_notif_user')
        EXEC sys.sp_executesql N'CREATE INDEX idx_notif_user ON dbo.notifications(user_id, is_read);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.notifications') AND name = N'idx_notif_branch')
        EXEC sys.sp_executesql N'CREATE INDEX idx_notif_branch ON dbo.notifications(branch_id, is_read);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.stock_transfers') AND name = N'idx_transfers_source')
        EXEC sys.sp_executesql N'CREATE INDEX idx_transfers_source ON dbo.stock_transfers(source_branch_id, status);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.stock_transfers') AND name = N'idx_transfers_dest')
        EXEC sys.sp_executesql N'CREATE INDEX idx_transfers_dest ON dbo.stock_transfers(destination_branch_id, status);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.stock_logs') AND name = N'idx_stock_logs_branch')
        EXEC sys.sp_executesql N'CREATE INDEX idx_stock_logs_branch ON dbo.stock_logs(branch_id, created_at DESC);';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.stock_logs') AND name = N'idx_stock_logs_variant')
        EXEC sys.sp_executesql N'CREATE INDEX idx_stock_logs_variant ON dbo.stock_logs(variant_id, created_at DESC);';

    -- 5. DỮ LIỆU DEMO CHỈ CHO DB CHƯA CÓ BẢNG ỨNG DỤNG.
    -- Không cập nhật tài khoản/đơn hàng/tồn kho hiện có khi chạy lại file.
    IF @seed_demo = 1
    BEGIN
        EXEC sys.sp_executesql N'SET IDENTITY_INSERT roles ON;
INSERT INTO roles (role_id, role_name) VALUES
(1, ''CUSTOMER''),
(2, ''STAFF''),
(3, ''SHIPPER''),
(4, ''ADMIN'');
SET IDENTITY_INSERT roles OFF;

-- -------------------------------------------------------------------------------
-- USERS
-- Staff được tạo với branch_id=NULL; gán nhánh sau khi INSERT branches thành công.
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
INSERT INTO users (username, email, password, full_name, phone, is_active, is_verified, role_id, branch_id) VALUES
(N''admin'',        N''admin@esc.vn'',                N''$2a$10$Ceeo6FmYhUKF/aYHBmw2eOCUCeVdqFGw0Xg0D4jbASog0rj2.xNq2'', N''Quản trị viên Hệ thống'',  N''0901234567'', 1, 1, 4, NULL),
(N''staff_q1'',     N''staff.q1@esc.vn'',             N''$2a$10$p1DQk4zx/lmyVmqwCT/eauk6APbG4a4DH4CoFovQDJrU/l0GiKdT6'', N''Nguyễn Văn An'',            N''0902345678'', 1, 1, 2, NULL),
(N''staff_q3'',     N''staff.q3@esc.vn'',             N''$2a$10$p1DQk4zx/lmyVmqwCT/eauk6APbG4a4DH4CoFovQDJrU/l0GiKdT6'', N''Trần Thị Bích'',            N''0903456789'', 1, 1, 2, NULL),
(N''staff_thuduc'', N''staff.thuduc@esc.vn'',         N''$2a$10$p1DQk4zx/lmyVmqwCT/eauk6APbG4a4DH4CoFovQDJrU/l0GiKdT6'', N''Lê Văn Cường'',             N''0904567890'', 1, 1, 2, NULL),
(N''staff_govap'',  N''staff.govap@esc.vn'',          N''$2a$10$p1DQk4zx/lmyVmqwCT/eauk6APbG4a4DH4CoFovQDJrU/l0GiKdT6'', N''Phạm Thị Dung'',            N''0905678901'', 1, 1, 2, NULL),
(N''shipper1'',     N''shipper1@esc.vn'',             N''$2a$10$ZqrMnOVPKXWNWg6EGL3R5OnqKW2BbJNK5jExgXaeAS/7CtJuk3fvO'', N''Hoàng Minh Đức'',           N''0906789012'', 1, 1, 3, NULL),
(N''shipper2'',     N''shipper2@esc.vn'',             N''$2a$10$ZqrMnOVPKXWNWg6EGL3R5OnqKW2BbJNK5jExgXaeAS/7CtJuk3fvO'', N''Vũ Thị Hoa'',               N''0907890123'', 1, 1, 3, NULL),
(N''customer1'',    N''customer1@gmail.com'',         N''$2a$10$9fYEMORVZrxvW7K47zuX3uOaAoe3sBpXtdkwRKQldQ/80BcaBr1CW'', N''Trần Vũ Anh Khoa'',         N''0908901234'', 1, 1, 1, NULL),
(N''customer2'',    N''customer2@gmail.com'',         N''$2a$10$9fYEMORVZrxvW7K47zuX3uOaAoe3sBpXtdkwRKQldQ/80BcaBr1CW'', N''Mai Huy Nhật'',              N''0909012345'', 1, 1, 1, NULL),
(N''customer3'',    N''customer3@gmail.com'',         N''$2a$10$9fYEMORVZrxvW7K47zuX3uOaAoe3sBpXtdkwRKQldQ/80BcaBr1CW'', N''Đặng Nhật Phúc'',           N''0910123456'', 1, 1, 1, NULL);

-- -------------------------------------------------------------------------------
-- USER_ADDRESSES
-- -------------------------------------------------------------------------------
INSERT INTO user_addresses (user_id, receiver_name, phone, province, district, ward, street, is_default) VALUES
(8, N''Trần Vũ Anh Khoa'',  N''0908901234'', N''TP. Hồ Chí Minh'', N''Quận 1'',      N''Phường Bến Nghé'', N''12 Lê Lợi'',        1),
(8, N''Trần Vũ Anh Khoa'',  N''0908901234'', N''TP. Hồ Chí Minh'', N''Quận 3'',      N''Phường Võ Thị Sáu'', N''45 Điện Biên Phủ'', 0),
(9, N''Mai Huy Nhật'',       N''0909012345'', N''TP. Hồ Chí Minh'', N''TP. Thủ Đức'', N''Phường Linh Trung'', N''1 Võ Văn Ngân'',    1),
(10, N''Đặng Nhật Phúc'',    N''0910123456'', N''TP. Hồ Chí Minh'', N''Quận Gò Vấp'', N''Phường 12'',         N''100 Quang Trung'',  1);

-- -------------------------------------------------------------------------------
-- BRANCHES - 4 chi nhánh chuỗi ESC (Electronics Store Chain)
-- -------------------------------------------------------------------------------
INSERT INTO branches (branch_name, address, district, province, phone, email, latitude, longitude, manager_id) VALUES
(N''ESC Chi nhánh Quận 1'',     N''123 Lê Lợi, Phường Bến Nghé'',      N''Quận 1'',      N''TP. Hồ Chí Minh'', N''(028) 3822 1234'', N''q1@esc.vn'',     10.7769, 106.7009, 2),
(N''ESC Chi nhánh Quận 3'',     N''45 Điện Biên Phủ, Phường Đakao'',   N''Quận 3'',      N''TP. Hồ Chí Minh'', N''(028) 3930 5678'', N''q3@esc.vn'',     10.7908, 106.6960, 3),
(N''ESC Chi nhánh TP. Thủ Đức'',N''2 Võ Văn Ngân, Phường Bình Thọ'',  N''TP. Thủ Đức'', N''TP. Hồ Chí Minh'', N''(028) 3896 9012'', N''thuduc@esc.vn'', 10.8503, 106.7747, 4),
(N''ESC Chi nhánh Gò Vấp'',     N''200 Quang Trung, Phường 10'',       N''Quận Gò Vấp'', N''TP. Hồ Chí Minh'', N''(028) 3894 3456'', N''govap@esc.vn'',  10.8380, 106.6709, 5);

-- Đã có users và branches: lúc này mới gán chi nhánh cho nhân viên.
UPDATE u SET u.branch_id = b.branch_id
FROM users u
JOIN (VALUES
    (N''staff_q1'', 1),
    (N''staff_q3'', 2),
    (N''staff_thuduc'', 3),
    (N''staff_govap'', 4)
) assignment(username, branch_id) ON assignment.username = u.username
JOIN branches b ON b.branch_id = assignment.branch_id;

-- -------------------------------------------------------------------------------
-- CATEGORIES - Danh mục cha
-- -------------------------------------------------------------------------------
SET IDENTITY_INSERT categories ON;
INSERT INTO categories (category_id, category_name, slug, display_order, is_active) VALUES
(1,  N''Điện thoại'',          N''dien-thoai'',          1, 1),
(2,  N''Laptop'',               N''laptop'',              2, 1),
(3,  N''Máy tính bảng'',        N''may-tinh-bang'',       3, 1),
(4,  N''Phụ kiện'',             N''phu-kien'',            4, 1),
(5,  N''Smartwatch'',           N''smartwatch'',          5, 1),
(6,  N''Tai nghe'',             N''tai-nghe'',            6, 1),
(7,  N''Máy tính để bàn & Màn hình'', N''may-tinh-de-ban'', 7, 1),
-- Danh mục con: Điện thoại
(11, N''iPhone'',               N''iphone'',              1, 1),
(12, N''Samsung Galaxy'',       N''samsung-galaxy'',      2, 1),
(13, N''Xiaomi'',               N''xiaomi'',              3, 1),
(14, N''OPPO'',                 N''oppo'',                4, 1),
-- Danh mục con: Laptop
(21, N''Laptop Gaming'',        N''laptop-gaming'',       1, 1),
(22, N''MacBook'',              N''macbook'',             2, 1),
(23, N''Laptop Văn phòng'',     N''laptop-van-phong'',    3, 1),
-- Danh mục con: Phụ kiện
(41, N''Cáp & Sạc'',            N''cap-sac'',             1, 1),
(42, N''Ốp lưng & Bao da'',     N''op-lung-bao-da'',      2, 1),
(43, N''Bàn phím & Chuột'',     N''ban-phim-chuot'',      3, 1),
(44, N''Túi & Balo laptop'',    N''tui-balo-laptop'',     4, 1);
SET IDENTITY_INSERT categories OFF;

-- Cập nhật parent_id
UPDATE categories SET parent_id = 1  WHERE category_id IN (11, 12, 13, 14);
UPDATE categories SET parent_id = 2  WHERE category_id IN (21, 22, 23);
UPDATE categories SET parent_id = 4  WHERE category_id IN (41, 42, 43, 44);

-- -------------------------------------------------------------------------------
-- BRANDS
-- -------------------------------------------------------------------------------
INSERT INTO brands (brand_name, logo_url, description) VALUES
(N''Apple'',      N''/uploads/brands/apple.png'',   N''Thương hiệu công nghệ hàng đầu thế giới của Mỹ''),
(N''Samsung'',    N''/uploads/brands/samsung.png'', N''Tập đoàn công nghệ đa quốc gia của Hàn Quốc''),
(N''Asus'',       N''/uploads/brands/asus.png'',    N''Thương hiệu máy tính và thiết bị điện tử của Đài Loan''),
(N''Dell'',       N''/uploads/brands/dell.png'',    N''Tập đoàn công nghệ của Mỹ, chuyên về máy tính cá nhân''),
(N''Lenovo'',     N''/uploads/brands/lenovo.png'',  N''Thương hiệu ThinkPad và Legion đẳng cấp toàn cầu''),
(N''Sony'',       N''/uploads/brands/sony.png'',    N''Tập đoàn công nghệ và giải trí của Nhật Bản''),
(N''Xiaomi'',     N''/uploads/brands/xiaomi.png'',  N''Thương hiệu công nghệ của Trung Quốc''),
(N''OPPO'',       NULL,                           N''Thương hiệu điện thoại thông minh của Trung Quốc''),
(N''MSI'',        NULL,                           N''Thương hiệu laptop gaming hàng đầu của Đài Loan''),
(N''LG'',         NULL,                           N''Tập đoàn công nghệ và điện tử của Hàn Quốc'');

-- -------------------------------------------------------------------------------
-- PRODUCTS (12 sản phẩm thực tế)
-- -------------------------------------------------------------------------------
SET IDENTITY_INSERT products ON;
INSERT INTO products (product_id, product_name, slug, description, category_id, brand_id, thumbnail_url, base_price, is_active, is_featured) VALUES
-- ĐIỆN THOẠI
(1,  N''iPhone 16 Pro Max'',
     N''iphone-16-pro-max'',
     N''iPhone 16 Pro Max với chip A18 Pro, màn hình 6.9" Super Retina XDR, camera 48MP Camera Control, pin 33 giờ. Đỉnh cao của công nghệ smartphone 2024.'',
     11, 1, N''/uploads/products/iphone-16-pro-max.png'', 34990000, 1, 1),
(2,  N''Samsung Galaxy S25 Ultra'',
     N''samsung-galaxy-s25-ultra'',
     N''Galaxy S25 Ultra mang đến trải nghiệm AI tích hợp Galaxy AI, Snapdragon 8 Elite, bút S Pen, camera 200MP, màn hình 6.9" Dynamic AMOLED 2X.'',
     12, 2, N''/uploads/products/samsung-galaxy-s25-ultra.png'', 31990000, 1, 1),
(3,  N''iPhone 15'',
     N''iphone-15'',
     N''iPhone 15 với Dynamic Island, chip A16 Bionic, cổng USB-C, camera 48MP chính, màu sắc tươi sáng mới.'',
     11, 1, N''/uploads/products/iphone-15.png'', 22990000, 1, 0),
(4,  N''Xiaomi 14 Ultra'',
     N''xiaomi-14-ultra'',
     N''Xiaomi 14 Ultra hợp tác Leica, Snapdragon 8 Gen 3, camera 1 inch 50MP, màn hình AMOLED 6.73".'',
     13, 7, N''/uploads/products/xiaomi-14-ultra.png'', 19990000, 1, 0),
-- LAPTOP
(5,  N''MacBook Pro 14 M4 Pro'',
     N''macbook-pro-14-m4-pro'',
     N''MacBook Pro 14 inch trang bị chip M4 Pro mạnh mẽ, màn hình Liquid Retina XDR 3024x1964, thời lượng pin lên đến 22 giờ.'',
     22, 1, N''/uploads/products/macbook-pro-14-m4-pro.png'', 62990000, 1, 1),
(6,  N''Asus ROG Zephyrus G16 2024'',
     N''asus-rog-zephyrus-g16-2024'',
     N''ROG Zephyrus G16 2024 với Intel Core Ultra 9 185H, NVIDIA GeForce RTX 4090, màn hình OLED 2.5K 240Hz mỏng nhẹ đẳng cấp gaming.'',
     21, 3, N''/uploads/products/asus-rog-zephyrus-g16.jpg'', 74990000, 1, 1),
(7,  N''Dell XPS 15 9530'',
     N''dell-xps-15-9530'',
     N''Dell XPS 15 với Intel Core i9-13900H, NVIDIA RTX 4060, màn hình OLED 3.5K cảm ứng, thiết kế nhôm sang trọng.'',
     23, 4, N''/uploads/products/dell-xps-15-9530.jpg'', 55990000, 1, 0),
(8,  N''Lenovo ThinkPad X1 Carbon Gen 11'',
     N''lenovo-thinkpad-x1-carbon-gen11'',
     N''ThinkPad X1 Carbon siêu nhẹ 1.12kg, Intel Core i7-1365U, màn hình IPS 2.8K OLED, bảo mật doanh nghiệp hàng đầu.'',
     23, 5, N''/uploads/products/lenovo-thinkpad-x1-carbon.jpg'', 42990000, 1, 0),
-- MÁY TÍNH BẢNG
(9,  N''iPad Pro M4 11 inch'',
     N''ipad-pro-m4-11-inch'',
     N''iPad Pro với chip M4, màn hình Ultra Retina XDR OLED 11", mỏng nhất từ trước đến nay chỉ 5.1mm, hỗ trợ Apple Pencil Pro.'',
     3, 1, N''/uploads/products/ipad-pro-m4-11-inch.jpg'', 23990000, 1, 1),
(10, N''Samsung Galaxy Tab S9 Ultra'',
     N''samsung-galaxy-tab-s9-ultra'',
     N''Galaxy Tab S9 Ultra màn hình Super AMOLED 14.6", Snapdragon 8 Gen 2, kháng nước IP68, kèm S Pen.'',
     3, 2, N''/uploads/products/samsung-galaxy-tab-s9-ultra.jpg'', 28990000, 1, 0),
-- SMARTWATCH
(11, N''Apple Watch Ultra 2'',
     N''apple-watch-ultra-2'',
     N''Apple Watch Ultra 2 với vỏ Titan, màn hình MicroLED sáng nhất 3000 nit, pin 60 giờ, đồng hồ thể thao chuyên nghiệp.'',
     5, 1, N''/uploads/products/apple-watch-ultra-2.jpg'', 21990000, 1, 1),
(12, N''Samsung Galaxy Watch 7'',
     N''samsung-galaxy-watch-7'',
     N''Galaxy Watch 7 với BioActive Sensor thế hệ mới, Galaxy AI, đo đường huyết, pin 40 giờ chế độ tiết kiệm.'',
     5, 2, N''/uploads/products/samsung-galaxy-watch-7.jpg'', 8990000, 1, 0);
SET IDENTITY_INSERT products OFF;

-- -------------------------------------------------------------------------------
-- PRODUCT_VARIANTS - Biến thể chi tiết
-- -------------------------------------------------------------------------------
-- iPhone 16 Pro Max
INSERT INTO product_variants (product_id, color, storage, sku, price, promo_price) VALUES
(1, N''Titan Đen'',   N''256GB'', N''IP16PM-BLK-256'',  34990000, 33490000),
(1, N''Titan Trắng'', N''256GB'', N''IP16PM-WHT-256'',  34990000, 33490000),
(1, N''Titan Sa mạc'',N''256GB'', N''IP16PM-DST-256'',  34990000, 33490000),
(1, N''Titan Đen'',   N''512GB'', N''IP16PM-BLK-512'',  39990000, 38490000),
(1, N''Titan Trắng'', N''512GB'', N''IP16PM-WHT-512'',  39990000, 38490000),
(1, N''Titan Đen'',   N''1TB'',   N''IP16PM-BLK-1T'',   49990000, NULL),
-- Samsung Galaxy S25 Ultra
(2, N''Titanium Đen'',  N''256GB'', N''GS25U-BLK-256'', 31990000, 30490000),
(2, N''Titanium Trắng'',N''256GB'', N''GS25U-WHT-256'', 31990000, 30490000),
(2, N''Titanium Xanh'', N''512GB'', N''GS25U-BLU-512'', 37990000, 36490000),
-- iPhone 15
(3, N''Hồng'',   N''128GB'', N''IP15-PNK-128'', 22990000, 21490000),
(3, N''Vàng'',   N''128GB'', N''IP15-YLW-128'', 22990000, 21490000),
(3, N''Xanh'',   N''256GB'', N''IP15-BLU-256'', 26990000, 25490000),
-- Xiaomi 14 Ultra
(4, N''Đen'',    N''256GB/12GB'', N''X14U-BLK-256'', 19990000, 18990000),
(4, N''Trắng'',  N''512GB/16GB'', N''X14U-WHT-512'', 23990000, 22490000),
-- MacBook Pro 14 M4 Pro
(5, N''Silver'',  N''18GB/512GB SSD'', N''MBP14-M4-SLV-512'', 62990000, 60490000),
(5, N''Space Black'', N''24GB/1TB SSD'', N''MBP14-M4-BLK-1T'', 78990000, NULL),
-- Asus ROG Zephyrus G16
(6, N''Eclipse Gray'',  N''32GB/1TB SSD'', N''ROGZ16-EG-1T'',  74990000, 72490000),
(6, N''Platinum White'',N''32GB/2TB SSD'', N''ROGZ16-PW-2T'',  89990000, NULL),
-- Dell XPS 15
(7, N''Platinum Silver'',N''16GB/512GB SSD'', N''XPS15-SLV-512'', 55990000, 53490000),
(7, N''Platinum Silver'',N''32GB/1TB SSD'',   N''XPS15-SLV-1T'',  67990000, NULL),
-- Lenovo ThinkPad X1
(8, N''Đen Carbon'', N''16GB/512GB SSD'', N''TPX1-BLK-512'', 42990000, 40490000),
(8, N''Đen Carbon'', N''32GB/1TB SSD'',   N''TPX1-BLK-1T'',  55990000, NULL),
-- iPad Pro M4 11"
(9, N''Silver'', N''256GB WiFi'',     N''IPAD-PRO-M4-SLV-256W'', 23990000, 22490000),
(9, N''Space Gray'', N''512GB WiFi'', N''IPAD-PRO-M4-GRY-512W'', 29990000, NULL),
(9, N''Silver'', N''1TB WiFi+Cell'',  N''IPAD-PRO-M4-SLV-1TC'',  42990000, NULL),
-- Samsung Galaxy Tab S9 Ultra
(10, N''Graphite'', N''256GB WiFi'', N''TABS9U-GRP-256W'', 28990000, 27490000),
(10, N''Beige'',    N''512GB WiFi'', N''TABS9U-BEI-512W'', 34990000, NULL),
-- Apple Watch Ultra 2
(11, N''Natural Titan'', N''49mm'', N''AWU2-NAT-49'', 21990000, 20490000),
(11, N''Black Titan'',   N''49mm'', N''AWU2-BLK-49'', 21990000, 20490000),
-- Samsung Galaxy Watch 7
(12, N''Cream'',  N''40mm'', N''GW7-CRM-40'', 8990000, 7990000),
(12, N''Green'',  N''44mm'', N''GW7-GRN-44'', 9990000, 8990000);

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
(1, 28, 10),(1, 29, 8),                                        -- Apple Watch Ultra 2
(1, 30, 15),(1, 31, 10);                                       -- Galaxy Watch 7

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
(2, 26, 5), (2, 27, 2),                                        -- Galaxy Tab S9 Ultra
(2, 28, 6), (2, 29, 3),                                        -- Apple Watch Ultra 2
(2, 30, 8), (2, 31, 4);                                        -- Galaxy Watch 7

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
(3, 28, 15),(3, 29, 10),                                       -- Apple Watch Ultra 2
(3, 30, 20),(3, 31, 15);                                       -- Galaxy Watch 7

-- Chi nhánh Gò Vấp (branch_id=4)
INSERT INTO branch_inventory (branch_id, variant_id, quantity) VALUES
(4, 2, 10), (4, 3, 8),  (4, 4, 5),                            -- iPhone 16 Pro Max
(4, 7, 8),  (4, 8, 0),  (4, 9, 5),                            -- Galaxy S25 Ultra
(4, 10, 12),(4, 11, 10),(4, 12, 6),                            -- iPhone 15
(4, 15, 2),                                                    -- MacBook Pro
(4, 21, 2), (4, 22, 0),                                        -- ThinkPad X1 (hết 2TB)
(4, 23, 5), (4, 24, 2),                                        -- iPad Pro
(4, 28, 8), (4, 29, 5),                                        -- Apple Watch Ultra 2
(4, 30, 12),(4, 31, 8);                                        -- Galaxy Watch 7

-- -------------------------------------------------------------------------------
-- DELIVERY_PROVIDERS
-- -------------------------------------------------------------------------------
INSERT INTO delivery_providers (provider_name, base_fee) VALUES
(N''Giao Hàng Nhanh (GHN)'',        25000),
(N''Giao Hàng Tiết Kiệm (GHTK)'',   20000),
(N''Viettel Post'',                  22000),
(N''Hỏa tốc ESC (Nội thành HCM)'', 35000);

-- -------------------------------------------------------------------------------
-- COUPONS - Mã giảm giá mẫu
-- -------------------------------------------------------------------------------
INSERT INTO coupons (coupon_code, description, discount_type, discount_value, min_order_amt, max_discount, usage_limit, valid_until) VALUES
(N''WELCOME100K'',  N''Chào mừng khách hàng mới - Giảm 100K'',          N''AMOUNT'',  100000,   500000,  NULL,    500,  ''2026-12-31''),
(N''SALE10PCT'',    N''Giảm 10% toàn bộ đơn hàng'',                     N''PERCENT'', 10,       2000000, 500000,  200,  ''2026-12-31''),
(N''PHONE5PCT'',    N''Giảm 5% khi mua điện thoại từ 10 triệu'',        N''PERCENT'', 5,        10000000, 1000000, 100,  ''2026-12-31''),
(N''LAPTOP15PCT'',  N''Giảm 15% cho laptop từ 30 triệu'',               N''PERCENT'', 15,       30000000, 3000000, 50,   ''2026-12-31''),
(N''FREESHIP'',     N''Miễn phí vận chuyển cho mọi đơn hàng'',          N''AMOUNT'',  35000,    0,        35000,   1000, ''2026-12-31''),
(N''ESC24BIRTHDAY'',N''Sinh nhật ESC - Giảm 500K cho đơn từ 20 triệu'', N''AMOUNT'',  500000,   20000000, NULL,    100,  ''2026-10-31'');

-- -------------------------------------------------------------------------------
-- CARTS (tạo sẵn cho customer)
-- -------------------------------------------------------------------------------
INSERT INTO carts (user_id) VALUES (8), (9), (10);

-- -------------------------------------------------------------------------------
-- CART_ITEMS - Giỏ hàng mẫu của customer1
-- -------------------------------------------------------------------------------
INSERT INTO cart_items (cart_id, variant_id, quantity) VALUES
(1, 1,  1),   -- iPhone 16 Pro Max Titan Đen 256GB
(1, 15, 1);   -- MacBook Pro 14 M4 Pro Silver

-- -------------------------------------------------------------------------------
-- ORDERS - Đơn hàng mẫu với đầy đủ trạng thái
-- -------------------------------------------------------------------------------
INSERT INTO orders (order_code, user_id, branch_id, order_type, receiver_name, receiver_phone, shipping_address, provider_id, shipping_fee, subtotal, discount_amount, total_amount, payment_method, payment_status, status, shipper_id) VALUES
(N''ESC-20261019-00001'', 8, 1, N''DELIVERY'', N''Trần Vũ Anh Khoa'', N''0908901234'', N''12 Lê Lợi, Phường Bến Nghé, Quận 1, TP. HCM'', 1, 25000, 33490000, 0, 33515000, N''COD'',   N''PENDING'', N''DELIVERED'',  6),
(N''ESC-20261019-00002'', 9, 3, N''PICKUP'',   NULL,                 NULL,          NULL, NULL, 0, 21490000, 100000, 21390000, N''VNPAY'', N''PAID'',    N''CONFIRMED'',  NULL),
(N''ESC-20261020-00003'', 8, 1, N''DELIVERY'', N''Trần Vũ Anh Khoa'', N''0908901234'', N''45 Điện Biên Phủ, Phường Đakao, Quận 3, TP. HCM'', 4, 35000, 60490000, 500000, 60025000, N''VNPAY'', N''PAID'',    N''SHIPPING'',   6),
(N''ESC-20261021-00004'', 10, 2, N''DELIVERY'', N''Đặng Nhật Phúc'',  N''0910123456'', N''100 Quang Trung, Phường 12, Gò Vấp, TP. HCM'', 2, 20000, 7990000, 0, 8010000, N''COD'',   N''PENDING'', N''NEW'',        NULL),
(N''ESC-20261021-00005'', 9, 3, N''PICKUP'',   NULL,                 NULL,          NULL, NULL, 0, 8990000, 0, 8990000, N''COD'',   N''PENDING'', N''CANCELLED'',  NULL);

-- -------------------------------------------------------------------------------
-- ORDER_ITEMS
-- -------------------------------------------------------------------------------
INSERT INTO order_items (order_id, variant_id, product_name, variant_info, unit_price, quantity) VALUES
(1, 1,  N''iPhone 16 Pro Max'', N''Titan Đen / 256GB'',   33490000, 1),
(2, 10, N''iPhone 15'',         N''Hồng / 128GB'',        21490000, 1),
(3, 15, N''MacBook Pro 14 M4 Pro'', N''Silver / 18GB / 512GB SSD'', 60490000, 1),
(4, 30, N''Samsung Galaxy Watch 7'',N''Cream / 40mm'',              7990000, 1),
(5, 31, N''Samsung Galaxy Watch 7'',N''Green / 44mm'',              8990000, 1);

-- -------------------------------------------------------------------------------
-- REVIEWS - Đánh giá mẫu cho đơn hàng DELIVERED
-- -------------------------------------------------------------------------------
INSERT INTO reviews (order_id, order_item_id, user_id, product_id, stars, content) VALUES
(1, 1, 8, 1, 5, N''Sản phẩm chính hãng, đóng gói cẩn thận, giao hàng nhanh. iPhone 16 Pro Max dùng rất mượt, camera cực đẹp. Nhân viên chi nhánh tư vấn nhiệt tình. Sẽ ủng hộ ESC lần sau!'');

-- -------------------------------------------------------------------------------
-- WISHLISTS
-- -------------------------------------------------------------------------------
INSERT INTO wishlists (user_id, product_id) VALUES
(8, 2), (8, 5), (8, 9),
(9, 1), (9, 6),
(10, 1), (10, 11);

-- -------------------------------------------------------------------------------
-- VIEWED_PRODUCTS
-- -------------------------------------------------------------------------------
INSERT INTO viewed_products (user_id, product_id) VALUES
(8, 1), (8, 2), (8, 5), (8, 9), (8, 11),
(9, 1), (9, 3), (9, 10),
(10, 1), (10, 12);

-- -------------------------------------------------------------------------------
-- NOTIFICATIONS
-- -------------------------------------------------------------------------------
INSERT INTO notifications (user_id, branch_id, type, title, message, ref_id) VALUES
(2, 1, N''NEW_ORDER'',      N''Đơn hàng mới #ESC-20261021-00004'', N''Khách hàng Đặng Nhật Phúc vừa đặt đơn hàng mới cần xác nhận.'', 4),
(8, NULL, N''STATUS_CHANGED'', N''Đơn hàng của bạn đang giao'', N''Đơn hàng #ESC-20261020-00003 của bạn đang được giao. Vui lòng chú ý điện thoại!'', 3),
(9, NULL, N''STATUS_CHANGED'', N''Đơn hàng đã được xác nhận'', N''Đơn hàng #ESC-20261019-00002 đã được xác nhận. Vui lòng đến chi nhánh TP. Thủ Đức để nhận hàng.'', 2);

-- -------------------------------------------------------------------------------
-- BANNERS
-- -------------------------------------------------------------------------------
INSERT INTO banners (title, image_url, link_url, display_order) VALUES
(N''iPhone 16 Pro Max - Đỉnh cao mới'', N''/uploads/banners/banner-iphone-16-pro-max.jpg'', N''/products/iphone-16-pro-max'', 1),
(N''Khuyến mãi Laptop tháng 10 - Giảm đến 15%'', N''/uploads/banners/banner-laptop-sale.jpg'', N''/products?categoryId=2'', 2),
(N''MacBook Pro M4 - Sức mạnh vượt trội'', N''/uploads/banners/banner-macbook-pro-m4.jpg'', N''/products/macbook-pro-14-m4-pro'', 3);

-- -------------------------------------------------------------------------------
-- PRODUCT_IMAGES - Thư viện ảnh sản phẩm
-- -------------------------------------------------------------------------------
INSERT INTO product_images (product_id, image_url, display_order) VALUES
(1,  N''/uploads/products/iphone-16-pro-max.png'', 1),
(2,  N''/uploads/products/samsung-galaxy-s25-ultra.png'', 1),
(3,  N''/uploads/products/iphone-15.png'', 1),
(4,  N''/uploads/products/xiaomi-14-ultra.png'', 1),
(5,  N''/uploads/products/macbook-pro-14-m4-pro.png'', 1),
(6,  N''/uploads/products/asus-rog-zephyrus-g16.jpg'', 1),
(7,  N''/uploads/products/dell-xps-15-9530.jpg'', 1),
(8,  N''/uploads/products/lenovo-thinkpad-x1-carbon.jpg'', 1),
(9,  N''/uploads/products/ipad-pro-m4-11-inch.jpg'', 1),
(10, N''/uploads/products/samsung-galaxy-tab-s9-ultra.jpg'', 1),
(11, N''/uploads/products/apple-watch-ultra-2.jpg'', 1),
(12, N''/uploads/products/samsung-galaxy-watch-7.jpg'', 1);

-- -------------------------------------------------------------------------------
-- CATEGORY_IMAGES - Cập nhật ảnh đại diện ngành hàng
-- -------------------------------------------------------------------------------
UPDATE categories SET image_url = N''/uploads/categories/dien-thoai.png'' WHERE category_id = 1;
UPDATE categories SET image_url = N''/uploads/categories/laptop.png'' WHERE category_id = 2;
UPDATE categories SET image_url = N''/uploads/categories/may-tinh-bang.png'' WHERE category_id = 3;
UPDATE categories SET image_url = N''/uploads/categories/phu-kien.png'' WHERE category_id = 4;
UPDATE categories SET image_url = N''/uploads/categories/smartwatch.png'' WHERE category_id = 5;
UPDATE categories SET image_url = N''/uploads/categories/tai-nghe.png'' WHERE category_id = 6;
UPDATE categories SET image_url = N''/uploads/categories/may-tinh-de-ban.png'' WHERE category_id = 7;

-- -------------------------------------------------------------------------------
-- STOCK_TRANSFERS & ITEMS (Dữ liệu mẫu điều chuyển kho giữa các chi nhánh)
-- -------------------------------------------------------------------------------
INSERT INTO stock_transfers (transfer_code, source_branch_id, destination_branch_id, status, created_by_id, approved_by_id, note) VALUES
(N''TRF-20261021-00001'', 1, 2, ''COMPLETED'', 2, 1, N''Điều chuyển iPhone 16 Pro Max sang chi nhánh Q.3 để phục vụ khách đặt hàng'');

INSERT INTO stock_transfer_items (transfer_id, variant_id, quantity) VALUES
(1, 1, 5);

-- -------------------------------------------------------------------------------
-- STOCK_LOGS (Dữ liệu mẫu sổ nhật ký kiểm toán kho)
-- -------------------------------------------------------------------------------
INSERT INTO stock_logs (branch_id, variant_id, movement_type, quantity_change, quantity_after, performed_by_id, reference_code, note) VALUES
(1, 1, ''TRANSFER_OUT'', -5, 20, 1, N''TRF-20261021-00001'', N''Xuất kho chuyển sang chi nhánh Q.3''),
(2, 1, ''TRANSFER_IN'',   5, 15, 3, N''TRF-20261021-00001'', N''Nhập kho nhận từ chi nhánh Q.1 chuyển đến''),
(1, 1, ''SALE'',         -1, 19, 2, N''ESC-20261021-00001'', N''Bán hàng đơn #ESC-20261021-00001'');

        IF (SELECT COUNT(*) FROM dbo.roles) <> 4 OR (SELECT COUNT(*) FROM dbo.users) <> 10
            OR (SELECT COUNT(*) FROM dbo.branches) <> 4 OR (SELECT COUNT(*) FROM dbo.orders) <> 5
            THROW 51012, N''Dữ liệu mẫu chưa được nạp đủ.'', 1;
        IF EXISTS (SELECT 1 FROM dbo.users u JOIN dbo.roles r ON r.role_id = u.role_id WHERE r.role_name = N''STAFF'' AND u.branch_id IS NULL)
            THROW 51013, N''Có nhân viên mẫu chưa được gán chi nhánh.'', 1;';

    END;

    -- Dữ liệu cũ không có thời điểm giao chính xác: dùng updated_at làm mốc xấp xỉ một lần.
    -- Chạy sau seed để cả DB mới và DB nâng cấp đều có completed_at cho đơn DELIVERED.
    EXEC sys.sp_executesql N'
        UPDATE dbo.orders SET completed_at = COALESCE(updated_at,created_at)
        WHERE status = ''DELIVERED'' AND completed_at IS NULL;
        UPDATE dbo.orders SET stock_released = 1, coupon_released = 1
        WHERE status IN (''CANCELLED'',''RETURNED'',''RETURNED_REFUND'');
    ';
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.orders') AND name = N'ix_orders_completed_at')
        EXEC sys.sp_executesql N'CREATE INDEX ix_orders_completed_at ON dbo.orders(status,completed_at) INCLUDE (subtotal,discount_amount,total_cost,branch_id);';

    IF COL_LENGTH('dbo.orders','completed_at') IS NULL OR COL_LENGTH('dbo.orders','stock_released') IS NULL
       OR COL_LENGTH('dbo.orders','coupon_released') IS NULL OR COL_LENGTH('dbo.stock_transfers','returned_at') IS NULL
       OR COL_LENGTH('dbo.stock_transfer_items','returned_quantity') IS NULL OR COL_LENGTH('dbo.orders','payment_simulated') IS NULL
        THROW 51121, N'Thiếu cột nghiệp vụ mới. Migration đã rollback.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.check_constraints WHERE parent_object_id = OBJECT_ID('dbo.coupons')
                   AND name = N'chk_coupon_rules' AND is_disabled = 0 AND is_not_trusted = 0)
        THROW 51122, N'Ràng buộc coupon chưa được kiểm tra đầy đủ.', 1;

-- G01 C03 S02 H01 H03 A02 - schema additions; no DROP, no stock/payment rewrite.
IF COL_LENGTH('dbo.product_variants','cost_price_confirmed') IS NULL
BEGIN
 ALTER TABLE dbo.product_variants ADD cost_price_confirmed BIT NOT NULL CONSTRAINT df_variant_cost_confirmed DEFAULT 0 WITH VALUES;
 EXEC sys.sp_executesql N'UPDATE dbo.product_variants SET cost_price_confirmed=1 WHERE cost_price>0;';
END;
IF COL_LENGTH('dbo.order_items','cost_price_confirmed') IS NULL
BEGIN
 ALTER TABLE dbo.order_items ADD cost_price_confirmed BIT NOT NULL CONSTRAINT df_item_cost_confirmed DEFAULT 0 WITH VALUES;
 EXEC sys.sp_executesql N'UPDATE dbo.order_items SET cost_price_confirmed=1 WHERE cost_price>0;';
END;
IF COL_LENGTH('dbo.orders','hold_expires_at') IS NULL ALTER TABLE dbo.orders ADD hold_expires_at DATETIME2(6) NULL;
IF COL_LENGTH('dbo.orders','hold_reminder_sent_at') IS NULL ALTER TABLE dbo.orders ADD hold_reminder_sent_at DATETIME2(6) NULL;
IF COL_LENGTH('dbo.orders','no_show_at') IS NULL ALTER TABLE dbo.orders ADD no_show_at DATETIME2(6) NULL;
IF COL_LENGTH('dbo.orders','cancellation_requested_at') IS NULL ALTER TABLE dbo.orders ADD cancellation_requested_at DATETIME2(6) NULL;
IF COL_LENGTH('dbo.orders','cancellation_request_reason') IS NULL ALTER TABLE dbo.orders ADD cancellation_request_reason NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.orders','manual_review_required') IS NULL ALTER TABLE dbo.orders ADD manual_review_required BIT NOT NULL CONSTRAINT df_order_manual_review DEFAULT 0 WITH VALUES;
IF COL_LENGTH('dbo.orders','actual_collected_amount') IS NULL ALTER TABLE dbo.orders ADD actual_collected_amount NUMERIC(18,0) NULL;
-- Legacy orders deliberately have no automatic deadline. Set pickup deadlines after contacting the customer.
IF OBJECT_ID('dbo.otp_rate_limits','U') IS NULL CREATE TABLE dbo.otp_rate_limits (
 limit_key VARCHAR(64) NOT NULL PRIMARY KEY,
 window_started_at DATETIME2(6) NOT NULL,
 send_count INT NOT NULL DEFAULT 0 CHECK(send_count>=0),
 next_send_at DATETIME2(6) NULL,
 verification_attempts INT NOT NULL DEFAULT 0 CHECK(verification_attempts>=0),
 blocked_until DATETIME2(6) NULL
);
IF OBJECT_ID('dbo.order_return_receipts','U') IS NULL CREATE TABLE dbo.order_return_receipts (
 receipt_id BIGINT IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES dbo.orders(order_id),
 inspector_id BIGINT NOT NULL REFERENCES dbo.users(user_id),
 inspected_at DATETIME2(6) NOT NULL,
 note NVARCHAR(500) NULL
);
IF OBJECT_ID('dbo.order_return_lines','U') IS NULL CREATE TABLE dbo.order_return_lines (
 line_id BIGINT IDENTITY PRIMARY KEY,
 receipt_id BIGINT NOT NULL REFERENCES dbo.order_return_receipts(receipt_id),
 order_item_id BIGINT NOT NULL REFERENCES dbo.order_items(order_item_id),
 good_quantity INT NOT NULL CHECK(good_quantity>=0),
 damaged_quantity INT NOT NULL CHECK(damaged_quantity>=0),
 missing_quantity INT NOT NULL CHECK(missing_quantity>=0),
 discrepancy_note NVARCHAR(500) NULL,
 CONSTRAINT uq_order_return_line UNIQUE(receipt_id,order_item_id)
);
IF OBJECT_ID('dbo.delivery_attempts','U') IS NULL CREATE TABLE dbo.delivery_attempts (
 attempt_id BIGINT IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL REFERENCES dbo.orders(order_id),
 actor_id BIGINT NOT NULL REFERENCES dbo.users(user_id),
 attempted_at DATETIME2(6) NOT NULL,
 outcome VARCHAR(30) NOT NULL CHECK(outcome IN ('DELIVERED','DELIVERY_FAILED')),
 collected_amount NUMERIC(18,0) NULL CHECK(collected_amount>=0),
 recipient_name NVARCHAR(150) NULL,
 reason NVARCHAR(500) NULL
);
IF OBJECT_ID('dbo.cost_price_audits','U') IS NULL CREATE TABLE dbo.cost_price_audits (
 audit_id BIGINT IDENTITY PRIMARY KEY,
 variant_id BIGINT NOT NULL REFERENCES dbo.product_variants(variant_id),
 actor_id BIGINT NOT NULL REFERENCES dbo.users(user_id),
 old_cost NUMERIC(18,0) NOT NULL,
 new_cost NUMERIC(18,0) NOT NULL CHECK(new_cost>=0),
 previously_confirmed BIT NOT NULL,
 changed_at DATETIME2(6) NOT NULL,
 reason NVARCHAR(500) NOT NULL
);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.orders') AND name='ix_orders_hold_expiry')
 EXEC sys.sp_executesql N'CREATE INDEX ix_orders_hold_expiry ON dbo.orders(hold_expires_at,status) WHERE hold_expires_at IS NOT NULL;';
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.delivery_attempts') AND name='ix_delivery_attempt_order')
 CREATE INDEX ix_delivery_attempt_order ON dbo.delivery_attempts(order_id,attempt_id DESC);
IF NOT EXISTS(SELECT 1 FROM sys.indexes WHERE object_id=OBJECT_ID('dbo.cost_price_audits') AND name='ix_cost_audit_variant')
 CREATE INDEX ix_cost_audit_variant ON dbo.cost_price_audits(variant_id,audit_id DESC);


IF OBJECT_ID('dbo.payment_refunds','U') IS NULL CREATE TABLE dbo.payment_refunds (
 refund_id BIGINT IDENTITY PRIMARY KEY,
 order_id BIGINT NOT NULL UNIQUE REFERENCES dbo.orders(order_id),
 actor_id BIGINT NOT NULL REFERENCES dbo.users(user_id),
 amount NUMERIC(18,0) NOT NULL CHECK(amount>=0),
 reference VARCHAR(100) NOT NULL UNIQUE,
 refunded_at DATETIME2(6) NOT NULL,
 reason NVARCHAR(500) NOT NULL
);
DECLARE @refund_status_checks NVARCHAR(MAX)=N'';
SELECT @refund_status_checks=@refund_status_checks+N'ALTER TABLE dbo.simulated_payments DROP CONSTRAINT '+QUOTENAME(name)+N';'
 FROM sys.check_constraints WHERE parent_object_id=OBJECT_ID('dbo.simulated_payments') AND definition LIKE N'%status%';
IF LEN(@refund_status_checks)>0 EXEC sys.sp_executesql @refund_status_checks;
ALTER TABLE dbo.simulated_payments WITH CHECK ADD CONSTRAINT chk_sim_payment
 CHECK(amount>=0 AND expires_at>created_at AND status IN ('PENDING','SUCCESS','FAILED','CANCELLED','EXPIRED','REFUNDED'));

    -- 6. KIỂM TRA CUỐI: chỉ COMMIT khi đã có đủ bảng, cột chat và index chống trùng.
    IF EXISTS (SELECT 1 FROM @required_tables r WHERE OBJECT_ID(N'dbo.' + r.table_name, N'U') IS NULL)
        THROW 51014, N'Chưa tạo đủ 34 bảng ứng dụng.', 1;
    IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE object_id = OBJECT_ID('dbo.chat_messages') AND name = N'uq_chat_message_client' AND is_unique = 1)
        THROW 51015, N'Thiếu ràng buộc chống trùng tin nhắn.', 1;
    COMMIT TRANSACTION;

    PRINT N'Hoàn tất database.sql. Có thể khởi động lại ứng dụng.';
    IF @seed_demo = 1
    BEGIN
        PRINT N'Đã tạo schema và dữ liệu mẫu mới.';
        PRINT N'admin / Admin@123; staff_q1, staff_q3, staff_thuduc, staff_govap / Staff@123';
        PRINT N'shipper1 / Shipper@123; customer1 / Customer@123';
    END
    ELSE PRINT N'Đã nâng cấp schema; giữ dữ liệu và không nạp lại demo.';
    SELECT @@SERVERNAME AS server_name, DB_NAME() AS database_name,
           @seed_demo AS seeded_demo,
           (SELECT COUNT(*) FROM @required_tables r WHERE OBJECT_ID(N'dbo.' + r.table_name, N'U') IS NOT NULL) AS app_tables,
           COL_LENGTH('dbo.chat_messages', 'client_message_id') AS client_message_id_bytes;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0 ROLLBACK TRANSACTION;
    THROW;
END CATCH;
GO
