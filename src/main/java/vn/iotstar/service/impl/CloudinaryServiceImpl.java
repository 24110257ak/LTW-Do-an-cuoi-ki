package vn.iotstar.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.iotstar.exception.BadRequestException;
import vn.iotstar.service.CloudinaryService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file, String folderName) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("File tải lên không được để trống!");
        }

        // 1. Kiểm tra cấu hình Cloudinary hợp lệ (khác dummy 'your_cloud_name')
        boolean hasValidCloudinary = cloudinary != null
                && cloudinary.config.cloudName != null
                && !cloudinary.config.cloudName.isBlank()
                && !"your_cloud_name".equalsIgnoreCase(cloudinary.config.cloudName);

        if (hasValidCloudinary) {
            try {
                File uploadedFile = convertMultiPartToFile(file);
                Map<?, ?> uploadResult = cloudinary.uploader().upload(uploadedFile, ObjectUtils.asMap(
                        "folder", "electronics_store/" + folderName,
                        "resource_type", "auto"
                ));

                if (uploadedFile.exists()) {
                    uploadedFile.delete();
                }

                return (String) uploadResult.get("secure_url");
            } catch (Exception e) {
                log.warn("Không thể tải lên Cloudinary ({}), tự động lưu vào thư mục dự án uploads/: {}", folderName, e.getMessage());
            }
        }

        // 2. Lưu trữ cục bộ trong uploads/{folderName} theo chuẩn bài giảng Spring Boot
        return saveLocalFile(file, folderName);
    }

    private String saveLocalFile(MultipartFile file, String folderName) {
        try {
            Path targetDir = Paths.get("uploads", folderName);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = UUID.randomUUID().toString() + extension;
            Path targetPath = targetDir.resolve(fileName);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Đã lưu file upload cục bộ: uploads/{}/{}", folderName, fileName);
            return "/uploads/" + folderName + "/" + fileName;
        } catch (IOException e) {
            log.error("Lỗi khi lưu file upload cục bộ: {}", e.getMessage());
            throw new BadRequestException("Không thể lưu file tải lên: " + e.getMessage());
        }
    }

    @Override
    public void deleteFile(String pathOrPublicId) {
        if (pathOrPublicId == null || pathOrPublicId.isBlank()) {
            return;
        }

        if (pathOrPublicId.startsWith("/uploads/")) {
            try {
                String relativePath = pathOrPublicId.substring(1); // bỏ dấu gạch chéo đầu
                Path filePath = Paths.get(relativePath);
                Files.deleteIfExists(filePath);
                log.info("Đã xóa file upload cục bộ: {}", relativePath);
            } catch (IOException e) {
                log.warn("Không thể xóa file cục bộ: {}", e.getMessage());
            }
            return;
        }

        try {
            cloudinary.uploader().destroy(pathOrPublicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Không thể xóa file trên Cloudinary: {}", e.getMessage());
        }
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.tmp";
        File convFile = File.createTempFile("cloudinary_", "_" + originalFilename);
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        return convFile;
    }
}
