package com.collegeassist.backend.controller;

import com.collegeassist.backend.dto.ChatRequest;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.ChatStatusResponse;
import com.collegeassist.backend.dto.SourceReference;
import com.collegeassist.backend.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private MockMvc mockMvc;

    @Mock
    private ChatService chatService;

    @Mock
    private com.collegeassist.backend.service.ChatHistoryService chatHistoryService;

    @InjectMocks
    private ChatController chatController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(chatController)
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldReturnChatStatus() throws Exception {
        ChatStatusResponse mockStatus = new ChatStatusResponse(
                "Google Gemini",
                false,
                "gemini-3.8-flash",
                "docs/",
                Arrays.asList("attendance", "exam-rules", "fees", "hostel"),
                "AI provider is not yet configured. Provide GEMINI_API_KEY to enable live AI."
        );

        when(chatService.getStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/api/chat/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiProvider", is("Google Gemini")))
                .andExpect(jsonPath("$.configured", is(false)))
                .andExpect(jsonPath("$.model", is("gemini-3.8-flash")))
                .andExpect(jsonPath("$.knowledgeBaseDirectory", is("docs/")))
                .andExpect(jsonPath("$.categories", hasSize(4)));
    }

    @Test
    void shouldProcessChatSuccessfully() throws Exception {
        SourceReference ref = new SourceReference(
                "Attendance & Condonation",
                "docs/attendance/",
                "Planned Source",
                "Attendance regulations for 75% rule."
        );

        ChatResponse mockResponse = new ChatResponse(
                "test-uuid-123",
                "Grounded response regarding attendance",
                "AWAITING_PROVIDER_KEY",
                "02:30 PM",
                false,
                Collections.singletonList(ref)
        );

        when(chatService.processChat(any(ChatRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"What is the attendance policy?\",\"conversationId\":\"conv-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("test-uuid-123")))
                .andExpect(jsonPath("$.role", is("assistant")))
                .andExpect(jsonPath("$.content", is("Grounded response regarding attendance")))
                .andExpect(jsonPath("$.status", is("AWAITING_PROVIDER_KEY")))
                .andExpect(jsonPath("$.configured", is(false)))
                .andExpect(jsonPath("$.sources", hasSize(1)))
                .andExpect(jsonPath("$.sources[0].category", is("Attendance & Condonation")))
                .andExpect(jsonPath("$.sources[0].documentPath", is("docs/attendance/")));
    }

    @Test
    void shouldReturnBadRequestWhenMessageIsBlank() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldReturnBadRequestWhenMessageIsMissing() throws Exception {
        mockMvc.perform(post("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldReturnStudentConversations() throws Exception {
        com.collegeassist.backend.dto.ConversationSummaryDto summary =
                new com.collegeassist.backend.dto.ConversationSummaryDto("c1", "Attendance query", "alex.student@university.edu", 1000L, 2000L, 2);
        when(chatHistoryService.getStudentConversations("alex.student@university.edu"))
                .thenReturn(Collections.singletonList(summary));

        mockMvc.perform(get("/api/chat/conversations")
                .param("studentEmail", "alex.student@university.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is("c1")))
                .andExpect(jsonPath("$[0].title", is("Attendance query")));
    }

    @Test
    void shouldReturnConversationDetailsWhenOwned() throws Exception {
        com.collegeassist.backend.dto.ChatMessageDto msg =
                new com.collegeassist.backend.dto.ChatMessageDto("m1", "user", "Hello", "12:00 PM", "SENT", Collections.emptyList());
        com.collegeassist.backend.dto.ConversationDetailDto detail =
                new com.collegeassist.backend.dto.ConversationDetailDto("c1", "Attendance query", "alex.student@university.edu", 1000L, 2000L, Collections.singletonList(msg));
        when(chatHistoryService.getConversationDetails("c1", "alex.student@university.edu"))
                .thenReturn(java.util.Optional.of(detail));

        mockMvc.perform(get("/api/chat/conversations/c1")
                .param("studentEmail", "alex.student@university.edu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is("c1")))
                .andExpect(jsonPath("$.messages", hasSize(1)));
    }

    @Test
    void shouldDeleteConversationWhenOwned() throws Exception {
        when(chatHistoryService.deleteConversation("c1", "alex.student@university.edu"))
                .thenReturn(true);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/chat/conversations/c1")
                .param("studentEmail", "alex.student@university.edu"))
                .andExpect(status().isNoContent());
    }
}
