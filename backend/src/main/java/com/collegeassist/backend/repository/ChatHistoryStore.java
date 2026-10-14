package com.collegeassist.backend.repository;

import com.collegeassist.backend.model.ChatMessageRecord;
import com.collegeassist.backend.model.Conversation;

import java.util.List;
import java.util.Optional;

/**
 * Clean abstraction for chat history persistence.
 * Decouples chat business logic from storage mechanisms (in-memory, file-based, or database).
 */
public interface ChatHistoryStore {

    Conversation saveConversation(Conversation conversation);

    Optional<Conversation> findConversationByIdAndStudentEmail(String id, String studentEmail);

    List<Conversation> findConversationsByStudentEmailOrderByUpdatedAtDesc(String studentEmail);

    boolean existsConversationByIdAndStudentEmail(String id, String studentEmail);

    void deleteConversationByIdAndStudentEmail(String id, String studentEmail);

    ChatMessageRecord saveMessage(ChatMessageRecord messageRecord);

    List<ChatMessageRecord> findMessagesByConversationIdOrderBySequenceNumberAsc(String conversationId);

    void deleteMessagesByConversationId(String conversationId);

    long countMessagesByConversationId(String conversationId);

    void clearAll();
}
