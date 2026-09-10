package com.kankan.websocket.controller;

import com.kankan.service.RoomService;
import com.kankan.websocket.message.VideoSyncMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class VideoSyncController {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomService roomService;

    @MessageMapping("/video.sync")
    public void handleVideoSync(@Payload VideoSyncMessage message) {
        log.debug("Received video sync message: {}", message);
        
        // 验证房间存在
        if (!roomService.roomExists(message.getRoomId())) {
            log.warn("Room {} does not exist", message.getRoomId());
            return;
        }
        
        // 更新房间状态（如果需要）
        if (message.getAction() == VideoSyncMessage.VideoAction.PLAY || 
            message.getAction() == VideoSyncMessage.VideoAction.PAUSE || 
            message.getAction() == VideoSyncMessage.VideoAction.SEEK) {
            roomService.updateRoomVideoState(message.getRoomId(), message.getCurrentTime(), 
                    message.getAction() == VideoSyncMessage.VideoAction.PLAY);
        }
        
        // 广播消息给房间内所有成员
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId() + ".video", message);
        
        log.debug("Broadcasted video sync message to room {}", message.getRoomId());
    }
    
    @MessageMapping("/video.join")
    public void handleJoin(@Payload VideoSyncMessage message) {
        log.debug("User {} joined room {}", message.getUserId(), message.getRoomId());
        
        // 验证房间存在
        if (!roomService.roomExists(message.getRoomId())) {
            log.warn("Room {} does not exist", message.getRoomId());
            return;
        }
        
        // 获取当前房间视频状态
        VideoSyncMessage syncState = roomService.getRoomVideoState(message.getRoomId());
        if (syncState != null) {
            // 发送当前状态给新加入的用户
            messagingTemplate.convertAndSendToUser(
                    message.getUserId(), 
                    "/queue/video.sync", 
                    syncState);
        }
        
        // 通知房间其他人有新用户加入
        message.setAction(VideoSyncMessage.VideoAction.JOIN);
        messagingTemplate.convertAndSend("/topic/room." + message.getRoomId() + ".video", message);
    }
}