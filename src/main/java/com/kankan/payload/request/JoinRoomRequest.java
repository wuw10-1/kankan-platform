package com.kankan.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinRoomRequest {
    
    @NotBlank
    private String roomCode;
    
    private String password;
}