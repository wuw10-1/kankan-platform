package com.kankan.repository;

import com.kankan.model.Room;
import com.kankan.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    
    Optional<Room> findByRoomCode(String roomCode);
    
    List<Room> findByIsPrivateFalseAndStatus(Room.RoomStatus status);
    
    List<Room> findByOwner(User owner);
    
    boolean existsByRoomCode(String roomCode);
}