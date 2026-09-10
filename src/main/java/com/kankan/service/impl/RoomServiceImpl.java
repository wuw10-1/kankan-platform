package com.kankan.service.impl;

import com.kankan.model.Room;
import com.kankan.model.User;
import com.kankan.model.Video;
import com.kankan.repository.RoomRepository;
import com.kankan.service.RoomService;
import com.kankan.websocket.message.VideoSyncMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    public RoomServiceImpl(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }
    
    private static final String ROOM_VIDEO_STATE_KEY_PREFIX = "room:video:state:";
    private static final long ROOM_CACHE_TTL = 24; // 房间缓存过期时间（小时）

    @Override
    @Transactional
    public Room createRoom(String name, User owner, Video video, boolean isPrivate, String password, int maxMembers) {
        // 生成唯一房间号
        String roomCode = generateUniqueRoomCode();
        
        Room room = Room.builder()
                .roomCode(roomCode)
                .name(name)
                .owner(owner)
                .currentVideo(video)
                .isPrivate(isPrivate)
                .password(password)
                .maxMembers(maxMembers)
                .status(Room.RoomStatus.ACTIVE)
                .build();
        
        // 添加房主为成员
        room.getMembers().add(owner);
        
        Room savedRoom = roomRepository.save(room);
        
        // 初始化房间视频状态
        VideoSyncMessage initialState = VideoSyncMessage.builder()
                .roomId(roomCode)
                .action(VideoSyncMessage.VideoAction.PAUSE)
                .currentTime(0.0)
                .playbackRate(1.0)
                .timestamp(System.currentTimeMillis())
                .build();
        
        // 缓存房间视频状态
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(
                        ROOM_VIDEO_STATE_KEY_PREFIX + roomCode,
                        initialState,
                        ROOM_CACHE_TTL,
                        TimeUnit.HOURS
                );
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
        
        return savedRoom;
    }

    @Override
    public Optional<Room> findByRoomCode(String roomCode) {
        return roomRepository.findByRoomCode(roomCode);
    }

    @Override
    public List<Room> findPublicRooms() {
        return roomRepository.findByIsPrivateFalseAndStatus(Room.RoomStatus.ACTIVE);
    }

    @Override
    public List<Room> findByOwner(User owner) {
        return roomRepository.findByOwner(owner);
    }

    @Override
    @Transactional
    public boolean joinRoom(String roomCode, User user, String password) {
        Optional<Room> roomOpt = roomRepository.findByRoomCode(roomCode);
        
        if (roomOpt.isEmpty()) {
            return false;
        }
        
        Room room = roomOpt.get();
        
        // 检查房间状态
        if (room.getStatus() != Room.RoomStatus.ACTIVE) {
            return false;
        }
        
        // 检查房间是否已满
        if (room.getMembers().size() >= room.getMaxMembers()) {
            room.setStatus(Room.RoomStatus.FULL);
            roomRepository.save(room);
            return false;
        }
        
        // 检查密码
        if (room.isPrivate() && (password == null || !password.equals(room.getPassword()))) {
            return false;
        }
        
        // 添加用户到房间
        room.getMembers().add(user);
        roomRepository.save(room);
        
        return true;
    }

    @Override
    @Transactional
    public boolean leaveRoom(String roomCode, User user) {
        Optional<Room> roomOpt = roomRepository.findByRoomCode(roomCode);
        
        if (roomOpt.isEmpty()) {
            return false;
        }
        
        Room room = roomOpt.get();
        
        // 移除用户
        boolean removed = room.getMembers().remove(user);
        
        if (removed) {
            // 如果房间之前是满的，现在有空位了
            if (room.getStatus() == Room.RoomStatus.FULL) {
                room.setStatus(Room.RoomStatus.ACTIVE);
            }
            
            // 如果房主离开，转移房主权限或关闭房间
            if (user.getId().equals(room.getOwner().getId())) {
                if (room.getMembers().isEmpty()) {
                    room.setStatus(Room.RoomStatus.INACTIVE);
                } else {
                    // 将房主权限转移给第一个成员
                    room.setOwner(room.getMembers().iterator().next());
                }
            }
            
            roomRepository.save(room);
        }
        
        return removed;
    }

    @Override
    @Transactional
    public boolean deleteRoom(String roomCode, User owner) {
        Optional<Room> roomOpt = roomRepository.findByRoomCode(roomCode);
        
        if (roomOpt.isEmpty()) {
            return false;
        }
        
        Room room = roomOpt.get();
        
        // 检查是否是房主
        if (!owner.getId().equals(room.getOwner().getId())) {
            return false;
        }
        
        // 设置房间为不活跃
        room.setStatus(Room.RoomStatus.INACTIVE);
        roomRepository.save(room);
        
        // 删除房间视频状态缓存
        try {
            if (redisTemplate != null) {
                redisTemplate.delete(ROOM_VIDEO_STATE_KEY_PREFIX + roomCode);
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
        
        return true;
    }

    @Override
    @Transactional
    public boolean changeVideo(String roomCode, Video video, User owner) {
        Optional<Room> roomOpt = roomRepository.findByRoomCode(roomCode);
        
        if (roomOpt.isEmpty()) {
            return false;
        }
        
        Room room = roomOpt.get();
        
        // 检查是否是房主
        if (!owner.getId().equals(room.getOwner().getId())) {
            return false;
        }
        
        // 更新视频
        room.setCurrentVideo(video);
        roomRepository.save(room);
        
        // 重置房间视频状态
        VideoSyncMessage initialState = VideoSyncMessage.builder()
                .roomId(roomCode)
                .action(VideoSyncMessage.VideoAction.PAUSE)
                .currentTime(0.0)
                .playbackRate(1.0)
                .timestamp(System.currentTimeMillis())
                .build();
        
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(
                        ROOM_VIDEO_STATE_KEY_PREFIX + roomCode,
                        initialState,
                        ROOM_CACHE_TTL,
                        TimeUnit.HOURS
                );
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
        
        return true;
    }

    @Override
    public boolean roomExists(String roomId) {
        return roomRepository.existsByRoomCode(roomId);
    }

    @Override
    public void updateRoomVideoState(String roomId, Double currentTime, boolean isPlaying) {
        VideoSyncMessage state = VideoSyncMessage.builder()
                .roomId(roomId)
                .action(isPlaying ? VideoSyncMessage.VideoAction.PLAY : VideoSyncMessage.VideoAction.PAUSE)
                .currentTime(currentTime)
                .playbackRate(1.0) // 默认播放速率
                .timestamp(System.currentTimeMillis())
                .build();
        
        try {
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(
                        ROOM_VIDEO_STATE_KEY_PREFIX + roomId,
                        state,
                        ROOM_CACHE_TTL,
                        TimeUnit.HOURS
                );
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
    }

    @Override
    public VideoSyncMessage getRoomVideoState(String roomId) {
        try {
            if (redisTemplate != null) {
                return (VideoSyncMessage) redisTemplate.opsForValue().get(ROOM_VIDEO_STATE_KEY_PREFIX + roomId);
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
        return null;
    }
    
    private String generateUniqueRoomCode() {
        String roomCode;
        do {
            // 生成6位随机数字
            roomCode = String.format("%06d", new Random().nextInt(1000000));
        } while (roomRepository.existsByRoomCode(roomCode));
        
        return roomCode;
    }
}