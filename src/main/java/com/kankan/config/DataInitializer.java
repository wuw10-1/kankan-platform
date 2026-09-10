package com.kankan.config;

import com.kankan.model.Role;
import com.kankan.model.User;
import com.kankan.model.Video;
import com.kankan.repository.RoleRepository;
import com.kankan.repository.UserRepository;
import com.kankan.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final VideoRepository videoRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    @Profile("dev")
    public CommandLineRunner initDevData() {
        return args -> {
            log.info("初始化开发环境测试数据...");
            
            // 创建角色
            Role roleAdmin = createRoleIfNotFound(Role.ERole.ROLE_ADMIN);
            Role roleUser = createRoleIfNotFound(Role.ERole.ROLE_USER);
            
            // 创建管理员用户
            if (!userRepository.existsByUsername("admin")) {
                User admin = User.builder()
                        .username("admin")
                        .email("admin@kankan.com")
                        .password(passwordEncoder.encode("password"))
                        .enabled(true)
                        .provider(User.AuthProvider.LOCAL)
                        .createdAt(LocalDateTime.now())
                        .build();
                
                Set<Role> adminRoles = new HashSet<>();
                adminRoles.add(roleAdmin);
                adminRoles.add(roleUser);
                admin.setRoles(adminRoles);
                
                userRepository.save(admin);
                log.info("创建管理员用户: admin / password");
            }
            
            // 创建普通用户
            if (!userRepository.existsByUsername("user")) {
                User user = User.builder()
                        .username("user")
                        .email("user@kankan.com")
                        .password(passwordEncoder.encode("password"))
                        .enabled(true)
                        .provider(User.AuthProvider.LOCAL)
                        .createdAt(LocalDateTime.now())
                        .build();
                
                Set<Role> userRoles = new HashSet<>();
                userRoles.add(roleUser);
                user.setRoles(userRoles);
                
                userRepository.save(user);
                log.info("创建普通用户: user / password");
            }
            
            // 创建测试视频
            if (videoRepository.count() == 0) {
                createTestVideo(
                        "大鱼海棠",
                        "一部唯美的国产动画电影",
                        "https://media.w3.org/2010/05/sintel/trailer.mp4",
                        "https://img.alicdn.com/imgextra/i4/2251979135/O1CN01Ky3Kbe2GdSBQqbR3V_!!2251979135.jpg"
                );
                
                createTestVideo(
                        "千与千寻",
                        "宫崎骏经典动画作品",
                        "https://media.w3.org/2010/05/sintel/trailer.mp4",
                        "https://img9.doubanio.com/view/photo/l/public/p2557573348.webp"
                );
                
                createTestVideo(
                        "你的名字",
                        "新海诚导演的爱情动画电影",
                        "https://media.w3.org/2010/05/sintel/trailer.mp4",
                        "https://img2.doubanio.com/view/photo/l/public/p2395733377.webp"
                );
                
                log.info("创建测试视频数据完成");
            }
            
            log.info("开发环境测试数据初始化完成");
        };
    }
    
    private Role createRoleIfNotFound(Role.ERole name) {
        return roleRepository.findByName(name)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    return roleRepository.save(role);
                });
    }
    
    private void createTestVideo(String title, String description, String url, String coverUrl) {
        Video video = Video.builder()
                .title(title)
                .description(description)
                .url(url)
                .thumbnailUrl(coverUrl)
                .duration(120L)
                .status("READY")
                .format("MP4")
                .viewCount(0)
                .build();
        
        videoRepository.save(video);
    }
}