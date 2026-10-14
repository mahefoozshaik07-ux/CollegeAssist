package com.collegeassist.backend.repository;

import com.collegeassist.backend.model.ChatMessageRecord;
import com.collegeassist.backend.model.Conversation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory implementation of ChatHistoryStore.
 * Allows the chatbot to persist and retrieve chat conversations without requiring
 * MySQL, H2, Docker, or any database server.
 */
@Component
public class InMemoryChatHistoryStore implements ChatHistoryStore {

    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();
    private final Map<String, List<ChatMessageRecord>> messagesByConv = new ConcurrentHashMap<>();

    @Override
    public Conversation saveConversation(Conversation conversation) {
        if (conversation == null || conversation.getId() == null) {
            return conversation;
        }
        conversations.put(conversation.getId(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> findConversationByIdAndStudentEmail(String id, String studentEmail) {
        if (id == null) {
            return Optional.empty();
        }
        Conversation conv = conversations.get(id);
        if (conv == null) {
            return Optional.empty();
        }
        if (studentEmail == null || studentEmail.equalsIgnoreCase(conv.getStudentEmail())) {
            return Optional.of(conv);
        }
        return Optional.empty();
    }

    @Override
    public List<Conversation> findConversationsByStudentEmailOrderByUpdatedAtDesc(String studentEmail) {
        if (studentEmail == null || studentEmail.isBlank()) {
            return Collections.emptyList();
        }
        return conversations.values().stream()
                .filter(c -> studentEmail.equalsIgnoreCase(c.getStudentEmail()))
                .sorted(Comparator.comparing(Conversation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsConversationByIdAndStudentEmail(String id, String studentEmail) {
        return findConversationByIdAndStudentEmail(id, studentEmail).isPresent();
    }

    @Override
    public void deleteConversationByIdAndStudentEmail(String id, String studentEmail) {
        if (id != null && existsConversationByIdAndStudentEmail(id, studentEmail)) {
            conversations.remove(id);
            messagesByConv.remove(id);
        }
    }

    @Override
    public ChatMessageRecord saveMessage(ChatMessageRecord messageRecord) {
        if (messageRecord == null || messageRecord.getConversationId() == null) {
            return messageRecord;
        }
        messagesByConv.computeIfAbsent(messageRecord.getConversationId(), k -> new CopyOnWriteArrayList<>())
                .add(messageRecord);
        return messageRecord;
    }

    @Override
    public List<ChatMessageRecord> findMessagesByConversationIdOrderBySequenceNumberAsc(String conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }
        List<ChatMessageRecord> list = messagesByConv.get(conversationId);
        if (list == null) {
            return Collections.emptyList();
        }
        List<ChatMessageRecord> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparing(ChatMessageRecord::getSequenceNumber, Comparator.nullsLast(Comparator.naturalOrder())));
        return copy;
    }

    @Override
    public void deleteMessagesByConversationId(String conversationId) {
        if (conversationId != null) {
            messagesByConv.remove(conversationId);
        }
    }

    @Override
    public long countMessagesByConversationId(String conversationId) {
        if (conversationId == null) {
            return 0;
        }
        List<ChatMessageRecord> list = messagesByConv.get(conversationId);
        return list != null ? list.size() : 0;
    }

    @Override
    public void clearAll() {
        conversations.clear();
        messagesByConv.clear();
    }
}
