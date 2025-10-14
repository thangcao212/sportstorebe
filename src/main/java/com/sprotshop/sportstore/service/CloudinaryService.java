package com.sprotshop.sportstore.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
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

    public Map uploadUrl(String imageUrl) throws IOException {
        log.debug("Try direct upload URL: {}", imageUrl);
        Map<String, Object> options = new HashMap<>();
        options.put("resource_type", "image");
        options.put("public_id", "import_" + UUID.randomUUID().toString().replace("-", ""));
        try {
            Map result = cloudinary.uploader().upload(imageUrl, options);
            if (result != null && result.get("secure_url") != null) {
                log.info("Direct upload success: {}", result.get("secure_url"));
                return result;
            } else {
                log.warn("Direct URL upload returned null for {}", imageUrl);
            }
        } catch (Exception e) {
            log.warn("Direct URL upload exception: {}", e.getMessage());
        }
        return null;  // Trigger fallback
    }

    private File convert(MultipartFile file) throws IOException {
        File tempFile = File.createTempFile("cloudinary-upload-", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        }
        return tempFile;
    }
}