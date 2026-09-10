package com.kankan.controller;

import com.kankan.model.Room;
import com.kankan.model.User;
import com.kankan.model.Video;
import com.kankan.payload.request.CreateRoomRequest;
import com.kankan.payload.request.JoinRoomRequest;
import com.kankan.payload.response.MessageResponse;
import com.kankan.repository.UserRepository;
import com.kankan.service.RoomService;
import com.kankan.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final VideoService videoService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> createRoom(
            @Valid @RequestBody CreateRoomRequest createRoomRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            Optional<Video> videoOpt = videoService.findById(createRoomRequest.getVideoId());
            if (videoOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponse("视频不存在"));
            }
            
            Room room = roomService.createRoom(
                    createRoomRequest.getName(),
                    user,
                    videoOpt.get(),
                    createRoomRequest.isPrivate(),
                    createRoomRequest.getPassword(),
                    createRoomRequest.getMaxMembers()
            );
            
            return ResponseEntity.status(HttpStatus.CREATED).body(room);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("创建房间失败: " + e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> getPublicRooms() {
        List<Room> rooms = roomService.findPublicRooms();
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{roomCode}")
    public ResponseEntity<?> getRoomByCode(@PathVariable String roomCode) {
        Optional<Room> room = roomService.findByRoomCode(roomCode);
        
        if (room.isPresent()) {
            return ResponseEntity.ok(room.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/join")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> joinRoom(
            @Valid @RequestBody JoinRoomRequest joinRoomRequest,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            boolean joined = roomService.joinRoom(
                    joinRoomRequest.getRoomCode(),
                    user,
                    joinRoomRequest.getPassword()
            );
            
            if (joined) {
                return ResponseEntity.ok(new MessageResponse("成功加入房间"));
            } else {
                return ResponseEntity.badRequest().body(new MessageResponse("加入房间失败，请检查房间号和密码"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("加入房间失败: " + e.getMessage()));
        }
    }

    @PostMapping("/{roomCode}/leave")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> leaveRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            boolean left = roomService.leaveRoom(roomCode, user);
            
            if (left) {
                return ResponseEntity.ok(new MessageResponse("成功离开房间"));
            } else {
                return ResponseEntity.badRequest().body(new MessageResponse("离开房间失败"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("离开房间失败: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{roomCode}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> deleteRoom(
            @PathVariable String roomCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            boolean deleted = roomService.deleteRoom(roomCode, user);
            
            if (deleted) {
                return ResponseEntity.ok(new MessageResponse("成功删除房间"));
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("您没有权限删除此房间"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("删除房间失败: " + e.getMessage()));
        }
    }

    @PutMapping("/{roomCode}/video/{videoId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> changeVideo(
            @PathVariable String roomCode,
            @PathVariable Long videoId,
            @AuthenticationPrincipal UserDetails userDetails) {
        
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            Optional<Video> videoOpt = videoService.findById(videoId);
            if (videoOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(new MessageResponse("视频不存在"));
            }
            
            boolean changed = roomService.changeVideo(roomCode, videoOpt.get(), user);
            
            if (changed) {
                return ResponseEntity.ok(new MessageResponse("成功更换视频"));
            } else {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new MessageResponse("您没有权限更换此房间的视频"));
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("更换视频失败: " + e.getMessage()));
        }
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<?> getMyRooms(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
            
            List<Room> rooms = roomService.findByOwner(user);
            
            return ResponseEntity.ok(rooms);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new MessageResponse("获取房间列表失败: " + e.getMessage()));
        }
    }
}