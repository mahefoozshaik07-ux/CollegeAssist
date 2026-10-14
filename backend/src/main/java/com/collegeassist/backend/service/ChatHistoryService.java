package com.collegeassist.backend.service;

import com.collegeassist.backend.dto.ChatMessageDto;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.ConversationDetailDto;
import com.collegeassist.backend.dto.ConversationSummaryDto;
import com.collegeassist.backend.dto.SourceReference;
import com.collegeassist.backend.model.ChatMessageRecord;
import com.collegeassist.backend.model.Conversation;
import com.collegeassist.backend.repository.ChatMessageRecordRepository;
import com.collegeassist.backend.repository.ConversationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final String DEFAULT_STUDENT_EMAIL = "alex.student@university.edu";

    private final ConversationRepository conversationRepository;
    private final ChatMessageRecordRepository messageRecordRepository;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(
            ConversationRepository conversationRepository,
            ChatMessageRecordRepository messageRecordRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRecordRepository = messageRecordRepository;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Persists a completed chat turn (user question + assistant response) to the database.
     * Guaranteed to execute only after the backend successfully processes the response.
     */
    @Transactional
    public void persistChatTurn(String conversationId, String studentEmail, String userText, ChatResponse response) {
        if (userText == null || userText.isBlank() || response == null) {
            return;
        }

        String effectiveEmail = (studentEmail != null && !studentEmail.isBlank())
                ? studentEmail.trim().toLowerCase()
                : DEFAULT_STUDENT_EMAIL;

        String effectiveConvId = (conversationId != null && !conversationId.isBlank() && !conversationId.equals("new") && !conversationId.equals("default-session"))
                ? conversationId.trim()
                : "conv-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 6);

        long now = System.currentTimeMillis();

        // 1. Get or create conversation record
        Conversation conversation = conversationRepository
                .findByIdAndStudentEmail(effectiveConvId, effectiveEmail)
                .orElseGet(() -> {
                    String title = generateTitleFromQuestion(userText);
                    Conversation newConv = new Conversation(effectiveConvId, effectiveEmail, title, now, now);
                    return conversationRepository.save(newConv);
                });

        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        // 2. Determine sequence number for messages
        int currentCount = (int) messageRecordRepository.countByConversationId(effectiveConvId);

        // 3. Persist User message
        String userMsgId = "user-" + now + "-" + UUID.randomUUID().toString().substring(0, 5);
        ChatMessageRecord userRecord = new ChatMessageRecord(
                userMsgId,
                effectiveConvId,
                "user",
                userText.trim(),
                response.getTimestamp(),
                "SENT",
                null,
                currentCount + 1
        );
        messageRecordRepository.save(userRecord);

        // 4. Persist Assistant message with verified sources
        String sourcesJson = serializeSources(response.getSources());
        String asstMsgId = (response.getId() != null && !response.getId().isBlank())
                ? response.getId()
                : "asst-" + now + "-" + UUID.randomUUID().toString().substring(0, 5);

        ChatMessageRecord asstRecord = new ChatMessageRecord(
                asstMsgId,
                effectiveConvId,
                "assistant",
                response.getContent(),
                response.getTimestamp(),
                response.getStatus(),
                sourcesJson,
                currentCount + 2
        );
        messageRecordRepository.save(asstRecord);

        log.debug("ChatHistoryService: Persisted turn for conversation {} (user: {})", effectiveConvId, effectiveEmail);
    }

    /**
     * Retrieves all conversations belonging to the authenticated student, ordered newest first.
     */
    @Transactional(readOnly = true)
    public List<ConversationSummaryDto> getStudentConversations(String studentEmail) {
        String effectiveEmail = (studentEmail != null && !studentEmail.isBlank())
                ? studentEmail.trim().toLowerCase()
                : DEFAULT_STUDENT_EMAIL;

        List<Conversation> conversations = conversationRepository.findByStudentEmailOrderByUpdatedAtDesc(effectiveEmail);
        List<ConversationSummaryDto> dtos = new ArrayList<>();

        for (Conversation conv : conversations) {
            long count = messageRecordRepository.countByConversationId(conv.getId());
            dtos.add(new ConversationSummaryDto(
                    conv.getId(),
                    conv.getTitle(),
                    conv.getStudentEmail(),
                    conv.getCreatedAt(),
                    conv.getUpdatedAt(),
                    count
            ));
        }

        return dtos;
    }

    /**
     * Retrieves a full conversation and its messages. Enforces strict ownership.
     */
    @Transactional(readOnly = true)
    public Optional<ConversationDetailDto> getConversationDetails(String conversationId, String studentEmail) {
        if (conversationId == null || conversationId.isBlank()) {
            return Optional.empty();
        }

        String effectiveEmail = (studentEmail != null && !studentEmail.isBlank())
                ? studentEmail.trim().toLowerCase()
                : DEFAULT_STUDENT_EMAIL;

        Optional<Conversation> convOpt = conversationRepository.findByIdAndStudentEmail(conversationId.trim(), effectiveEmail);
        if (convOpt.isEmpty()) {
            return Optional.empty();
        }

        Conversation conv = convOpt.get();
        List<ChatMessageRecord> records = messageRecordRepository.findByConversationIdOrderBySequenceNumberAsc(conv.getId());
        List<ChatMessageDto> messageDtos = new ArrayList<>();

        for (ChatMessageRecord rec : records) {
            List<SourceReference> sources = deserializeSources(rec.getSourcesJson());
            messageDtos.add(new ChatMessageDto(
                    rec.getId(),
                    rec.getSender(),
                    rec.getContent(),
                    rec.getTimestamp(),
                    rec.getStatus(),
                    sources
            ));
        }

        return Optional.of(new ConversationDetailDto(
                conv.getId(),
                conv.getTitle(),
                conv.getStudentEmail(),
                conv.getCreatedAt(),
                conv.getUpdatedAt(),
                messageDtos
        ));
    }

    /**
     * Deletes a conversation and its messages. Enforces ownership check.
     */
    @Transactional
    public boolean deleteConversation(String conversationId, String studentEmail) {
        if (conversationId == null || conversationId.isBlank()) {
            return false;
        }

        String effectiveEmail = (studentEmail != null && !studentEmail.isBlank())
                ? studentEmail.trim().toLowerCase()
                : DEFAULT_STUDENT_EMAIL;

        if (!conversationRepository.existsByIdAndStudentEmail(conversationId.trim(), effectiveEmail)) {
            return false;
        }

        messageRecordRepository.deleteByConversationId(conversationId.trim());
        conversationRepository.deleteByIdAndStudentEmail(conversationId.trim(), effectiveEmail);
        log.info("ChatHistoryService: Deleted conversation {} for student {}", conversationId, effectiveEmail);
        return true;
    }

    private String generateTitleFromQuestion(String text) {
        if (text == null) return "Campus Inquiry";
        String clean = text.trim().replaceAll("\\s+", " ");
        if (clean.length() <= 45) {
            return clean;
        }
        return clean.substring(0, 42).trim() + "...";
    }

    private String serializeSources(List<SourceReference> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (Exception e) {
            log.warn("ChatHistoryService: Failed to serialize sources: {}", e.getMessage());
            return null;
        }
    }

    private List<SourceReference> deserializeSources(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SourceReference>>() {});
        } catch (Exception e) {
            log.warn("ChatHistoryService: Failed to deserialize sources: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
