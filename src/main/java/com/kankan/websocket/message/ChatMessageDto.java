package com.kankan.websocket.message;

import com.kankan.model.ChatMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    
    @NotBlank(message = "Room ID is required")
    private String roomId;
    
    private String senderId;
    
    private String senderName;
    
    private String senderAvatar;
    
    @NotBlank(message = "Message content is required")
    private String content;
    
    @NotNull(message = "Message type is required")
    private ChatMessage.MessageType type;
    
    private String imageUrl;
    
    private Long timestamp;
}