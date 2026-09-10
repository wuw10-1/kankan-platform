package com.kankan.service.impl;

import com.kankan.model.Role;
import com.kankan.model.User;
import com.kankan.model.Video;
import com.kankan.repository.VideoRepository;
import com.kankan.service.FileStorageService;
import com.kankan.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    private final VideoRepository videoRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public Video uploadVideo(MultipartFile file, String title, String description, User uploader) {
        // 生成唯一文件名
        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        
        // 保存文件
        String filePath = fileStorageService.storeFile(file, fileName);
        
        // 创建视频记录
        Video video = Video.builder()
                .title(title)
                .description(description)
                .url(filePath)
                .uploader(uploader)
                .duration(0L) // 初始设置为0，后续处理时更新
                .viewCount(0)
                .status("PROCESSING")
                .format("MP4")
                .build();
        
        Video savedVideo = videoRepository.save(video);
        
        // 异步处理视频（转码、提取封面等）
        // 实际项目中应该使用消息队列或异步任务处理
        processVideo(savedVideo.getId());
        
        return savedVideo;
    }

    @Override
    public Optional<Video> findById(Long id) {
        return videoRepository.findById(id);
    }

    @Override
    public Page<Video> findAll(Pageable pageable) {
        return videoRepository.findAll(pageable);
    }

    @Override
    public Page<Video> findByStatus(String status, Pageable pageable) {
        return videoRepository.findByStatus(status, pageable);
    }

    @Override
    public List<Video> findByUploader(User uploader) {
        return videoRepository.findByUploader(uploader);
    }

    @Override
    public Page<Video> searchByTitle(String title, Pageable pageable) {
        return videoRepository.findByTitleContainingIgnoreCase(title, pageable);
    }

    @Override
    @Transactional
    public void incrementViewCount(Long videoId) {
        videoRepository.findById(videoId).ifPresent(video -> {
            video.setViewCount(video.getViewCount() + 1);
            videoRepository.save(video);
        });
    }

    @Override
    @Transactional
    public boolean deleteVideo(Long videoId, User user) {
        Optional<Video> videoOpt = videoRepository.findById(videoId);
        
        if (videoOpt.isEmpty()) {
            return false;
        }
        
        Video video = videoOpt.get();
        
        // 检查是否是上传者或管理员
        boolean isUploader = video.getUploader() != null && video.getUploader().getId().equals(user.getId());
        boolean isAdmin = user.getRoles().stream().anyMatch(role -> role.getName().equals(Role.ERole.ROLE_ADMIN));
        if (!isUploader && !isAdmin) {
            return false;
        }
        
        // 删除文件
        String filePath = video.getUrl();
        fileStorageService.deleteFile(filePath);
        
        // 删除数据库记录
        videoRepository.delete(video);
        
        return true;
    }

    @Override
    public String generateHlsStream(Video video) {
        // 实际项目中，这里应该调用FFmpeg等工具进行视频转码
        // 简化实现，直接返回原始URL
        return video.getUrl();
    }
    
    @Override
    public List<Video> getFeaturedVideos() {
        // 获取最新的5个活跃视频作为推荐视频
        PageRequest pageRequest = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Video> featuredVideos = videoRepository.findByStatus("READY", pageRequest);
        return featuredVideos.getContent();
    }
    
    private void processVideo(Long videoId) {
        // 实际项目中，这里应该是异步处理
        // 1. 提取视频时长
        // 2. 生成缩略图
        // 3. 转码为不同清晰度
        // 4. 更新视频状态为READY
        
        // 简化实现，直接更新状态
        videoRepository.findById(videoId).ifPresent(video -> {
            video.setStatus("READY");
            video.setDuration(120L); // 假设视频长度为120秒
            videoRepository.save(video);
        });
    }
}