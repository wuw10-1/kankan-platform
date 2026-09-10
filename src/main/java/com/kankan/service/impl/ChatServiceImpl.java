package com.kankan.service.impl;

import com.kankan.model.ChatMessage;
import com.kankan.model.Room;
import com.kankan.model.User;
import com.kankan.repository.ChatMessageRepository;
import com.kankan.repository.RoomRepository;
import com.kankan.repository.UserRepository;
import com.kankan.service.ChatService;
import com.kankan.websocket.message.ChatMessageDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    
    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;
    
    public ChatServiceImpl(ChatMessageRepository chatMessageRepository, RoomRepository roomRepository, UserRepository userRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }
    
    private static final String ROOM_CHAT_CACHE_KEY_PREFIX = "room:chat:";
    private static final int CHAT_CACHE_SIZE = 50; // 缓存最近的50条消息

    @Override
    @Transactional
    public ChatMessage saveMessage(ChatMessageDto messageDto) {
        Optional<Room> roomOpt = roomRepository.findByRoomCode(messageDto.getRoomId());
        
        if (roomOpt.isEmpty()) {
            throw new IllegalArgumentException("房间不存在: " + messageDto.getRoomId());
        }
        
        Room room = roomOpt.get();
        
        Optional<User> senderOpt = userRepository.findById(Long.parseLong(messageDto.getSenderId()));
        if (senderOpt.isEmpty()) {
            throw new IllegalArgumentException("用户不存在: " + messageDto.getSenderId());
        }
        
        User sender = senderOpt.get();
        
        // 检查用户是否在房间中
        if (!room.getMembers().contains(sender)) {
            throw new IllegalArgumentException("用户不在房间中");
        }
        
        // 创建并保存消息
        ChatMessage message = ChatMessage.builder()
                .room(room)
                .sender(sender)
                .content(messageDto.getContent())
                .type(messageDto.getType())
                .imageUrl(messageDto.getImageUrl())
                .build();
        
        ChatMessage savedMessage = chatMessageRepository.save(message);
        
        // 缓存最近消息
        try {
            if (redisTemplate != null) {
                String cacheKey = ROOM_CHAT_CACHE_KEY_PREFIX + messageDto.getRoomId();
                redisTemplate.opsForList().leftPush(cacheKey, messageDto);
                redisTemplate.opsForList().trim(cacheKey, 0, CHAT_CACHE_SIZE - 1);
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
        
        return savedMessage;
    }

    @Override
    public List<ChatMessage> getRoomMessages(String roomId, int limit) {
        // 尝试从缓存获取
        try {
            if (redisTemplate != null) {
                String cacheKey = ROOM_CHAT_CACHE_KEY_PREFIX + roomId;
                List<Object> cachedMessages = redisTemplate.opsForList().range(cacheKey, 0, limit - 1);
                
                if (cachedMessages != null && !cachedMessages.isEmpty()) {
                    // 缓存中存储的是DTO，需要转换回实体
                    // 实际项目中应该使用更高效的方式，这里简化处理
                    return getChatMessagesFromDatabase(roomId, limit);
                }
            }
        } catch (Exception e) {
            log.warn("Redis操作失败，可能是开发环境没有配置Redis: {}", e.getMessage());
        }
        
        return getChatMessagesFromDatabase(roomId, limit);
    }
    
    private List<ChatMessage> getChatMessagesFromDatabase(String roomId, int limit) {
        Optional<Room> roomOpt = roomRepository.findByRoomCode(roomId);
        
        if (roomOpt.isEmpty()) {
            throw new IllegalArgumentException("房间不存在: " + roomId);
        }
        
        Room room = roomOpt.get();
        
        // 获取最近的消息
        return chatMessageRepository.findByRoom(
                room,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }
}