package com.sprotshop.sportstore.service;



import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class CloudinaryService {
    private final Cloudinary cloudinary;

    public CloudinaryService() {
        Map<String, String> valuesMap = new HashMap<>();
        valuesMap.put("cloud_name", "dm1p5ocbz");
        valuesMap.put("api_key", "923887916441132");
        valuesMap.put("api_secret", "pCrHymqBTOlruX0MqVAlF2kdi-8");
        cloudinary = new Cloudinary(valuesMap);
    }

    public Map upload(MultipartFile file) throws IOException {
        File tempFile = convert(file);
        Map result = cloudinary.uploader().upload(tempFile, ObjectUtils.emptyMap());
        tempFile.delete();
        return result;
    }

    public void delete(String imageId) throws IOException {
        cloudinary.uploader().destroy(imageId, ObjectUtils.emptyMap());
    }



    private File convert(MultipartFile file) throws IOException {
        // Sử dụng tên file gốc có thể không an toàn, tạo tên ngẫu nhiên tốt hơn
        // File tempFile = File.createTempFile("upload_", "_" + file.getOriginalFilename());
        File tempFile = File.createTempFile("cloudinary-upload-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        return tempFile;

    }
}