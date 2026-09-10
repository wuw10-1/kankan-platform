package com.kankan.service;

import com.kankan.model.ChatMessage;
import com.kankan.websocket.message.ChatMessageDto;

import java.util.List;

public interface ChatService {
    
    ChatMessage saveMessage(ChatMessageDto messageDto);
    
    List<ChatMessage> getRoomMessages(String roomId, int limit);
}