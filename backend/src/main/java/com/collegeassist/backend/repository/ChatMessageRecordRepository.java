package com.collegeassist.backend.repository;

import com.collegeassist.backend.model.ChatMessageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRecordRepository extends JpaRepository<ChatMessageRecord, String> {

    List<ChatMessageRecord> findByConversationIdOrderBySequenceNumberAsc(String conversationId);

    void deleteByConversationId(String conversationId);

    long countByConversationId(String conversationId);
}
