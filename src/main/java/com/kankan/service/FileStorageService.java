package com.kankan.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {
    
    String storeFile(MultipartFile file, String fileName);
    
    void deleteFile(String filePath);
}