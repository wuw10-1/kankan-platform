package com.kankan.websocket.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoSyncMessage {
    
    private String roomId;
    private VideoAction action;
    private Double currentTime;
    private Double playbackRate;
    private String userId;
    private String username;
    private Long timestamp;
    
    public enum VideoAction {
        PLAY, PAUSE, SEEK, RATE_CHANGE, JOIN, LEAVE, SYNC_REQUEST, SYNC_RESPONSE
    }
}