package com.kankan.service.impl;

import com.kankan.service.FileStorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Slf4j
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final Path fileStorageLocation;

    public LocalFileStorageServiceImpl(@Value("${file.upload-dir:./uploads}") String uploadDir) {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (IOException ex) {
            log.error("无法创建文件上传目录", ex);
        }
    }

    @Override
    public String storeFile(MultipartFile file, String fileName) {
        try {
            if (fileName == null || fileName.isEmpty()) {
                fileName = UUID.randomUUID().toString() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
            }
            
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            
            return targetLocation.toString();
        } catch (IOException ex) {
            log.error("无法存储文件 " + fileName, ex);
            throw new RuntimeException("无法存储文件 " + fileName, ex);
        }
    }

    @Override
    public void deleteFile(String filePath) {
        try {
            Path targetLocation = Paths.get(filePath);
            Files.deleteIfExists(targetLocation);
        } catch (IOException ex) {
            log.error("无法删除文件 " + filePath, ex);
        }
    }
}