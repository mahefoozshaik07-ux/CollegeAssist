package com.collegeassist.backend.controller;

import com.collegeassist.backend.dto.ChatRequest;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.ChatStatusResponse;
import com.collegeassist.backend.dto.ConversationDetailDto;
import com.collegeassist.backend.dto.ConversationSummaryDto;
import com.collegeassist.backend.service.ChatHistoryService;
import com.collegeassist.backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(ChatService chatService, ChatHistoryService chatHistoryService) {
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> processChat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader(value = "X-Student-Email", required = false) String headerEmail) {

        String effectiveEmail = resolveEmail(request.getStudentEmail(), headerEmail);
        String convId = (request.getConversationId() != null && !request.getConversationId().isBlank() && !request.getConversationId().equals("new"))
                ? request.getConversationId().trim()
                : "session-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 5);
        request.setConversationId(convId);

        // 1. Process via Gemini & verified RAG index
        ChatResponse response = chatService.processChat(request);
        response.setConversationId(convId);

        // 2. Persist turn ONLY after successful processing
        chatHistoryService.persistChatTurn(convId, effectiveEmail, request.getMessage(), response);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/status")
    public ResponseEntity<ChatStatusResponse> getChatStatus() {
        ChatStatusResponse status = chatService.getStatus();
        return ResponseEntity.ok(status);
    }

    /**
     * Triggers dynamic document reindexing across all markdown files and PDFs.
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex(
            @RequestParam(value = "force", defaultValue = "false") boolean force) {
        chatService.reindex(force);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("status", "SUCCESS");
        resp.put("message", "Document knowledge base reindexed successfully.");
        resp.put("details", chatService.getStatus().getMessage());
        return ResponseEntity.ok(resp);
    }

    /**
     * Retrieves all chat history conversations for the given student.
     */
    @GetMapping({"/conversations", "/history"})
    public ResponseEntity<List<ConversationSummaryDto>> getConversations(
            @RequestParam(value = "email", required = false) String emailParam,
            @RequestParam(value = "studentEmail", required = false) String studentEmailParam,
            @RequestHeader(value = "X-Student-Email", required = false) String headerEmail) {

        String effectiveEmail = resolveEmail(studentEmailParam != null ? studentEmailParam : emailParam, headerEmail);
        List<ConversationSummaryDto> list = chatHistoryService.getStudentConversations(effectiveEmail);
        return ResponseEntity.ok(list);
    }

    /**
     * Retrieves a full conversation by ID, enforcing ownership.
     */
    @GetMapping({"/conversations/{conversationId}", "/history/{conversationId}"})
    public ResponseEntity<?> getConversation(
            @PathVariable String conversationId,
            @RequestParam(value = "email", required = false) String emailParam,
            @RequestParam(value = "studentEmail", required = false) String studentEmailParam,
            @RequestHeader(value = "X-Student-Email", required = false) String headerEmail) {

        String effectiveEmail = resolveEmail(studentEmailParam != null ? studentEmailParam : emailParam, headerEmail);
        Optional<ConversationDetailDto> detailOpt = chatHistoryService.getConversationDetails(conversationId, effectiveEmail);

        if (detailOpt.isPresent()) {
            return ResponseEntity.ok(detailOpt.get());
        }

        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "Conversation not found or access denied for student: " + effectiveEmail);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Deletes a conversation by ID with student ownership check.
     */
    @DeleteMapping({"/conversations/{conversationId}", "/history/{conversationId}"})
    public ResponseEntity<?> deleteConversation(
            @PathVariable String conversationId,
            @RequestParam(value = "email", required = false) String emailParam,
            @RequestParam(value = "studentEmail", required = false) String studentEmailParam,
            @RequestHeader(value = "X-Student-Email", required = false) String headerEmail) {

        String effectiveEmail = resolveEmail(studentEmailParam != null ? studentEmailParam : emailParam, headerEmail);
        boolean deleted = chatHistoryService.deleteConversation(conversationId, effectiveEmail);

        if (deleted) {
            return ResponseEntity.noContent().build();
        }

        Map<String, String> error = new LinkedHashMap<>();
        error.put("error", "Conversation not found or cannot be deleted by student: " + effectiveEmail);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    private String resolveEmail(String payloadEmail, String headerEmail) {
        if (payloadEmail != null && !payloadEmail.isBlank()) {
            return payloadEmail.trim().toLowerCase();
        }
        if (headerEmail != null && !headerEmail.isBlank()) {
            return headerEmail.trim().toLowerCase();
        }
        return "alex.student@university.edu";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
