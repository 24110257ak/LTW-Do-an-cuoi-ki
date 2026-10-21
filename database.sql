-- ==================================================================================
-- HỆ THỐNG QUẢN LÝ WEBSITE BÁN HÀNG THEO MÔ HÌNH CHUỖI CỬA HÀNG THIẾT BỊ ĐIỆN TỬ
-- ĐỒ ÁN CUỐI KỲ MÔN LẬP TRÌNH WEB - NHÓM 24 (ĐỀ TÀI 27)
-- Thành viên:
--   1. 24110257 - Trần Vũ Anh Khoa
--   2. 24110292 - Mai Huy Nhật
--   3. 24110304 - Đặng Nhật Phúc
-- Hệ quản trị CSDL: Microsoft SQL Server 2019 / 2022
-- ==================================================================================

USE master;
GO

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'electronics_store_chain')
BEGIN
    CREATE DATABASE electronics_store_chain COLLATE Latin1_General_100_CI_AI_SC_UTF8;
END
GO

USE electronics_store_chain;
GO

-- Thông báo khởi tạo thành công
PRINT N'Cơ sở dữ liệu electronics_store_chain đã sẵn sàng cho hệ thống chuỗi cửa hàng!';
GO
