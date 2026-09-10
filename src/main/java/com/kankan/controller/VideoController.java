package com.kankan.controller;

import com.kankan.model.User;
import com.kankan.model.Video;
import com.kankan.payload.response.MessageResponse;
import com.kankan.repository.UserRepository;
import com.kankan.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

@RestController
@RequestMapping("/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<?> getAllVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        
        Sort.Direction sortDirection = direction.equalsIgnoreCase("asc") ? 
                Sort.Direction.ASC : Sort.Direction.DESC;
        
        Page<Video> videos = videoService.findAll(
                PageRequest.of(page, size, Sort.by(sortDirection, sortBy)));
        
        return ResponseEntity.ok(videos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getVideoById(@PathVariable Long id) {
        Optional<Video> video = videoService.findById(id);
        
        if (video.isPresent()) {
            // 增加观看次数
            videoService.incrementViewCount(id);
            return ResponseEntity.ok(video.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(new MessageResponse("请选择要上传的视频文件"));
        }
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            Video uploadedVideo = videoService.uploadVideo(file, title, description, user);
            
            return ResponseEntity.status(HttpStatus.CREATED).body(uploadedVideo);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("视频上传失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> deleteVideo(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            boolean deleted = videoService.deleteVideo(id, user);
            
            if (deleted) {
                return ResponseEntity.ok(new MessageResponse("视频已成功删除"));
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("您没有权限删除此视频"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("视频删除失败: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchVideos(
            @RequestParam String title,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        Page<Video> videos = videoService.searchByTitle(
                title, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        
        return ResponseEntity.ok(videos);
    }
}