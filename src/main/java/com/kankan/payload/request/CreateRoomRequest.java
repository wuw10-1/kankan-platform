package com.kankan.payload.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateRoomRequest {
    
    @NotBlank
    private String name;
    
    @NotNull
    private Long videoId;
    
    private boolean isPrivate;
    
    private String password;
    
    @Min(2)
    private int maxMembers = 10;
}