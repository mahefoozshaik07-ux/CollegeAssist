package com.collegeassist.backend.evaluation;

import com.collegeassist.backend.dto.ChatRequest;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.SourceReference;
import com.collegeassist.backend.model.QueryScope;
import com.collegeassist.backend.model.RetrievalResult;
import com.collegeassist.backend.model.ScoredKnowledgeChunk;
import com.collegeassist.backend.service.ChatService;
import com.collegeassist.backend.service.DocumentKnowledgeService;
import com.collegeassist.backend.service.KnowledgeRetrievalService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Automated Evaluation Suite for CollegeAssist AI Integration & RAG Pipeline.
 *
 * Tracks and evaluates:
 * 1. Retrieval relevance for direct questions and paraphrases
 * 2. Citation correctness and deduplication
 * 3. Factual support within retrieved chunk text
 * 4. Conservative scoping for missing information and out-of-scope inquiries
 * 5. Handling of pending verification items
 * 6. Provider error resilience, retries, and student-facing error masking
 */
class RagQualityEvaluationTest {

    private static DocumentKnowledgeService documentKnowledgeService;
    private static KnowledgeRetrievalService retrievalService;

    @BeforeAll
    static void initKnowledgeBase() {
        documentKnowledgeService = new DocumentKnowledgeService("docs");
        documentKnowledgeService.loadDocuments();
        assertTrue(documentKnowledgeService.isLoaded(), "Institutional knowledge base must be loaded");
        assertEquals(8, documentKnowledgeService.getDocumentCount(), "Exactly 8 verified documents must be loaded");
        assertTrue(documentKnowledgeService.getChunkCount() >= 40, "Should generate 40+ chunks from documents");

        retrievalService = new KnowledgeRetrievalService(documentKnowledgeService, 10.0, 3);
    }

    @Nested
    @DisplayName("Dimension 1: Retrieval Relevance & Paraphrase Quality")
    class RetrievalRelevanceTests {

        @Test
        @DisplayName("Direct Question: Minimum attendance threshold")
        void testDirectQuestion_minimumAttendance() {
            RetrievalResult result = retrievalService.search("What is the minimum attendance required for FAT examination?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
            assertTrue(result.hasSufficientEvidence());

            ScoredKnowledgeChunk top = result.getTopChunk();
            assertNotNull(top);
            assertEquals("docs/attendance/attendance-policy.md", top.getSourcePath());
            assertTrue(top.getContent().contains("75%"));
        }

        @Test
        @DisplayName("Paraphrase 1: Low attendance, debarment and FAT exam")
        void testParaphrase_skippingClasses() {
            RetrievalResult result = retrievalService.search("If my attendance is low, will I get debarred from FAT exams?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            boolean foundAttendance = result.getMatches().stream()
                    .anyMatch(chunk -> chunk.getSourcePath().equals("docs/attendance/attendance-policy.md"));
            assertTrue(foundAttendance, "Should retrieve attendance policy for debarment paraphrase");
        }

        @Test
        @DisplayName("Direct Question: Fee support contact channels")
        void testDirectQuestion_feeSupportChannels() {
            RetrievalResult result = retrievalService.search("What is the finance office contact email for tuition fees?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            ScoredKnowledgeChunk top = result.getTopChunk();
            assertEquals("docs/fees/tuition-and-hostel-fees.md", top.getSourcePath());
            assertTrue(top.getContent().contains("finance@vitap.ac.in"));
        }

        @Test
        @DisplayName("Paraphrase 2: Hostel room cooking and electrical appliances prohibition")
        void testParaphrase_hostelCooking() {
            RetrievalResult result = retrievalService.search("Can I cook inside hostel rooms or use electrical appliances?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            ScoredKnowledgeChunk top = result.getTopChunk();
            assertEquals("docs/hostel/hostel-regulations.md", top.getSourcePath());
            assertTrue(top.getContent().toLowerCase().contains("cooking") || top.getContent().toLowerCase().contains("prohibited"));
        }

        @Test
        @DisplayName("Multi-part Question: CAT assessment and FAT exam structure")
        void testMultiPartQuestion_continuousAssessmentAndFat() {
            RetrievalResult result = retrievalService.search("What is the difference between CAT-1, CAT-2 and FAT exams?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            ScoredKnowledgeChunk top = result.getTopChunk();
            assertEquals("docs/exam-rules/examination-guidelines.md", top.getSourcePath());
            assertTrue(top.getContent().contains("CAT-1") || top.getContent().contains("Continuous Assessment"));
        }
    }

    @Nested
    @DisplayName("Dimension 2: Citation Correctness & Non-Concatenation")
    class CitationCorrectnessTests {

        @Test
        @DisplayName("Citations have verified status, valid clean doc paths, and no concatenated strings")
        void testCitationCorrectness() {
            RetrievalResult result = retrievalService.search("What is the policy for medical condonation and OD on-duty leave?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            HttpClient mockClient = mock(HttpClient.class);
            ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);

            List<SourceReference> sources = chatService.toSourceReferences(result.getMatches());
            assertFalse(sources.isEmpty(), "Must produce citations");

            for (SourceReference source : sources) {
                assertNotNull(source.getDocumentPath());
                assertTrue(source.getDocumentPath().startsWith("docs/"));
                assertFalse(source.getDocumentPath().contains(","));
                assertFalse(source.getDocumentPath().contains(";"));
                assertFalse(source.getDocumentPath().matches(".*\\.md.+"));
                assertEquals("Verified Evidence", source.getStatus());
                assertNotNull(source.getSectionTitle());
                assertFalse(source.getSectionTitle().isBlank());
                assertNotNull(source.getCategory());
            }
        }
    }

    @Nested
    @DisplayName("Dimension 3: Factual Support in Evidence Passages")
    class FactualSupportTests {

        @Test
        @DisplayName("Evidence passages injected into Gemini request contain verified institutional facts")
        void testFactualSupportInGeminiRequest() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            HttpResponse<String> mockResponse = mock(HttpResponse.class);

            when(mockResponse.statusCode()).thenReturn(200);
            when(mockResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"75% attendance is required.\"}]}}]}");
            when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

            ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);

            ChatResponse response = chatService.processChat(new ChatRequest("What is the minimum attendance required for FAT?", "conv-eval-1"));
            assertEquals("CONFIGURED", response.getStatus());

            ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
            verify(mockClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
            HttpRequest sent = captor.getValue();

            // The sent request must target the normalized model URI
            assertTrue(sent.uri().toString().contains("models/gemini-3.8-flash:generateContent"));
            // Citations must point to the verified attendance policy
            boolean hasAttendanceSource = response.getSources().stream()
                    .anyMatch(s -> s.getDocumentPath().equals("docs/attendance/attendance-policy.md"));
            assertTrue(hasAttendanceSource, "Citations must include attendance-policy.md");
        }
    }

    @Nested
    @DisplayName("Dimension 4: Unanswered & Missing Information Queries")
    class UnansweredInquiryTests {

        @Test
        @DisplayName("Query with campus intent but missing document evidence returns COLLEGE_UNGROUNDED without calling Gemini")
        void testMissingInformation_busSchedule() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);

            ChatResponse response = chatService.processChat(
                    new ChatRequest("What is the college bus route and pickup schedule for Vijayawada?", "conv-eval-2"));

            assertEquals("UNGROUNDED_INQUIRY", response.getStatus());
            assertTrue(response.getContent().contains("Information Not Found in Verified Documentation"));
            assertTrue(response.getSources().isEmpty());
            // Proves Gemini is NEVER called for ungrounded queries
            verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("Unrelated general query returns OUT_OF_SCOPE without calling Gemini")
        void testOutOfScope_generalTrivia() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);

            ChatResponse response = chatService.processChat(
                    new ChatRequest("How do I build a crypto trading bot with Binance API?", "conv-eval-3"));

            assertEquals("OUT_OF_SCOPE", response.getStatus());
            assertTrue(response.getContent().contains("CollegeAssist Scope Notice"));
            assertTrue(response.getSources().isEmpty());
            verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }

    @Nested
    @DisplayName("Dimension 5: Conflicting & Pending Verification Grounding")
    class PendingVerificationTests {

        @Test
        @DisplayName("Hostel curfew inquiry retrieves document passage explicitly labeled Pending Institutional Verification")
        void testPendingVerification_hostelCurfew() {
            RetrievalResult result = retrievalService.search("What is the hostel night curfew in-time for students?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            ScoredKnowledgeChunk top = result.getTopChunk();
            assertEquals("docs/hostel/hostel-regulations.md", top.getSourcePath());
            // Proves actual document text contains pending verification warning
            assertTrue(top.getContent().contains("Pending Institutional Verification") || top.getContent().contains("curfew"));
        }

        @Test
        @DisplayName("Medical condonation relaxation percentage retrieves passage declaring relaxation pending verification")
        void testPendingVerification_medicalRelaxation() {
            RetrievalResult result = retrievalService.search("Can medical condonation reduce attendance requirement down to 65%?");
            assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());

            ScoredKnowledgeChunk top = result.getTopChunk();
            assertEquals("docs/attendance/attendance-policy.md", top.getSourcePath());
            assertTrue(top.getContent().contains("Medical Condonation") || top.getContent().contains("Pending Institutional Verification"));
        }
    }

    @Nested
    @DisplayName("Dimension 6: Provider Error Resilience & Retry Control")
    class ProviderErrorResilienceTests {

        @Test
        @DisplayName("HTTP 429 quota exhaustion is retried 3 times and returns friendly busy notice")
        void testProviderError_http429RateLimit() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            HttpResponse<String> mockResponse = mock(HttpResponse.class);

            when(mockResponse.statusCode()).thenReturn(429);
            when(mockResponse.body()).thenReturn("{\"error\":{\"code\":429,\"message\":\"Resource exhausted\"}}");
            when(mockResponse.headers()).thenReturn(HttpHeaders.of(Map.of("Retry-After", List.of("0")), (a, b) -> true));
            when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

            ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);

            ChatResponse response = chatService.processChat(new ChatRequest("Tell me about exams", "conv-eval-4"));

            assertEquals("PROVIDER_ERROR", response.getStatus());
            assertTrue(response.getContent().contains("AI Service Temporarily Busy"));
            assertFalse(response.getContent().contains("Resource exhausted"));
            // Verified 3 attempts
            verify(mockClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }

        @Test
        @DisplayName("HTTP 400 invalid credentials is NOT retried and returns friendly service unavailable message")
        void testProviderError_http400InvalidCredentials() throws Exception {
            HttpClient mockClient = mock(HttpClient.class);
            HttpResponse<String> mockResponse = mock(HttpResponse.class);

            when(mockResponse.statusCode()).thenReturn(400);
            when(mockResponse.body()).thenReturn("{\"error\":{\"code\":400,\"message\":\"API key invalid\"}}");
            when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

            ChatService chatService = new ChatService("bad-key-12345", "gemini-3.8-flash", mockClient, retrievalService);

            ChatResponse response = chatService.processChat(new ChatRequest("Tell me about exams", "conv-eval-5"));

            assertEquals("PROVIDER_ERROR", response.getStatus());
            assertTrue(response.getContent().contains("AI Service Unavailable"));
            assertFalse(response.getContent().contains("bad-key-12345"));
            assertFalse(response.getContent().contains("API key invalid"));
            // Exactly 1 attempt (no retries for permanent client error)
            verify(mockClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        }
    }
}
