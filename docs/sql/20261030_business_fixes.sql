-- Chạy toàn bộ trong SSMS khi DB ứng dụng đã tồn tại. Dừng ứng dụng trước khi nâng cấp.
-- Không xóa DB, không nạp demo, không tự hủy đơn cũ hoặc giả lập tiền đã thu.
USE electronics_store_chain;
GO
SET XACT_ABORT ON;
BEGIN TRY
 BEGIN TRANSACTION;
 IF OBJECT_ID('dbo.orders','U') IS NULL OR OBJECT_ID('dbo.product_variants','U') IS NULL
  THROW 51200,N'Hãy chạy database.sql để tạo schema ứng dụng trước.',1;
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

 COMMIT TRANSACTION;
 PRINT N'Đã nâng cấp schema nghiệp vụ ngày 30/10/2026.';
END TRY
BEGIN CATCH
 IF XACT_STATE()<>0 ROLLBACK TRANSACTION;
 THROW;
END CATCH;
GO
