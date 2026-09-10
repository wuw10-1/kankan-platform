package com.kankan.service;

import com.kankan.model.Room;
import com.kankan.model.User;
import com.kankan.model.Video;
import com.kankan.websocket.message.VideoSyncMessage;

import java.util.List;
import java.util.Optional;

public interface RoomService {
    
    Room createRoom(String name, User owner, Video video, boolean isPrivate, String password, int maxMembers);
    
    Optional<Room> findByRoomCode(String roomCode);
    
    List<Room> findPublicRooms();
    
    List<Room> findByOwner(User owner);
    
    boolean joinRoom(String roomCode, User user, String password);
    
    boolean leaveRoom(String roomCode, User user);
    
    boolean deleteRoom(String roomCode, User owner);
    
    boolean changeVideo(String roomCode, Video video, User owner);
    
    boolean roomExists(String roomId);
    
    void updateRoomVideoState(String roomId, Double currentTime, boolean isPlaying);
    
    VideoSyncMessage getRoomVideoState(String roomId);
}