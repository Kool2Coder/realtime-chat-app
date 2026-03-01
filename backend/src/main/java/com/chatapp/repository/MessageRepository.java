package com.chatapp.repository;

import com.chatapp.model.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT m FROM Message m WHERE m.roomId IS NULL AND m.receiverId IS NULL ORDER BY m.timestamp ASC")
    List<Message> findPublicMessages();

    @Query("SELECT m FROM Message m WHERE m.roomId = :roomId ORDER BY m.timestamp ASC")
    List<Message> findByRoomId(@Param("roomId") String roomId);

    @Query("SELECT m FROM Message m WHERE " +
            "((m.senderId = :userId AND m.receiverId = :otherUserId) OR " +
            "(m.senderId = :otherUserId AND m.receiverId = :userId)) " +
            "AND m.roomId IS NULL ORDER BY m.timestamp ASC")
    List<Message> findPrivateMessages(@Param("userId") Long userId, @Param("otherUserId") Long otherUserId);
}
