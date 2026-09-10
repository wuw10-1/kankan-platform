package com.kankan.service;

import com.kankan.model.User;
import com.kankan.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface VideoService {
    
    Video uploadVideo(MultipartFile file, String title, String description, User uploader);
    
    Optional<Video> findById(Long id);
    
    Page<Video> findAll(Pageable pageable);
    
    Page<Video> findByStatus(String status, Pageable pageable);
    
    List<Video> findByUploader(User uploader);
    
    Page<Video> searchByTitle(String title, Pageable pageable);
    
    void incrementViewCount(Long videoId);
    
    boolean deleteVideo(Long videoId, User user);
    
    String generateHlsStream(Video video);
    
    /**
     * 获取推荐视频列表
     * @return 推荐视频列表
     */
    List<Video> getFeaturedVideos();
}