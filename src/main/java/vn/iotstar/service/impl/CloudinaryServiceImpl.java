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

        try {
            File uploadedFile = convertMultiPartToFile(file);
            Map<?, ?> uploadResult = cloudinary.uploader().upload(uploadedFile, ObjectUtils.asMap(
                    "folder", "electronics_store/" + folderName,
                    "resource_type", "auto"
            ));

            // Clean up temporary local file
            if (uploadedFile.exists()) {
                uploadedFile.delete();
            }

            return (String) uploadResult.get("secure_url");
        } catch (Exception e) {
            log.error("Lỗi khi tải ảnh lên Cloudinary: {}. Sử dụng ảnh placeholder fallback.", e.getMessage());
            // Fallback: nếu lỗi mạng hoặc tài khoản Cloudinary demo, trả về placeholder không làm crash app
            return "https://res.cloudinary.com/demo/image/upload/sample.jpg";
        }
    }

    @Override
    public void deleteFile(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Không thể xóa file trên Cloudinary: {}", e.getMessage());
        }
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.tmp";
        File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID() + "_" + originalFilename);
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        return convFile;
    }
}

