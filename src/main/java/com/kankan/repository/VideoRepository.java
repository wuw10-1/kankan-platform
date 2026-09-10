package com.kankan.repository;

import com.kankan.model.User;
import com.kankan.model.Video;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<Video, Long> {
    
    Page<Video> findByStatus(String status, Pageable pageable);
    
    List<Video> findByUploader(User uploader);
    
    Page<Video> findByTitleContainingIgnoreCase(String title, Pageable pageable);
}