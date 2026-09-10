package com.kankan.websocket.controller;

import com.kankan.service.ChatService;
import com.kankan.websocket.message.ChatMessageDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void handleChatMessage(@Payload @Valid ChatMessageDto message, SimpMessageHeaderAccessor headerAccessor) {
        // 获取当前用户
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Unauthorized chat message attempt");
            return;
        }
        
        String username = authentication.getName();
        message.setSenderName(username);
        
        log.debug("Received chat message from {}: {}", username, message);
        
        // 保存消息到数据库
        chatService.saveMessage(message);
        
        // 设置时间戳
        message.setTimestamp(System.currentTimeMillis());
        
        // 广播消息给房间内所有成员
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId() + ".chat", message);
        
        log.debug("Broadcasted chat message to room {}", message.getRoomId());
    }
}