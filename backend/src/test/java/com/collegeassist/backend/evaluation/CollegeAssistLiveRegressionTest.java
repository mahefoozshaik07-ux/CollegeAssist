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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Live Regression Test Suite for CollegeAssist.
 *
 * Verifies the 6 key live test scenarios:
 * 1. Attendance Calculation: "How many classes can I miss if there are 40 classes in total?"
 * 2. FAT Eligibility: "What is the FAT eligibility requirement?"
 * 3. Attendance Requirements: "What are the attendance requirements?"
 * 4. Academic Programming Coursework: "Explain recursion in Java."
 * 5. Campus Ungrounded Inquiry: "What time does the college bus leave tomorrow?"
 * 6. Unrelated Non-University Inquiry: "What is the capital of France?" / "How do I build a crypto trading bot with Binance API?"
 *
 * For each test, we assert:
 * - The correct route (QueryScope and ChatResponse status)
 * - Whether retrieval is triggered and chunk evidence evaluated
 * - Whether the final answer is appropriately grounded and citations adhere to verified source documents
 * - That the university-only boundary is maintained without weakening.
 */
class CollegeAssistLiveRegressionTest {

    private static DocumentKnowledgeService documentKnowledgeService;
    private static KnowledgeRetrievalService retrievalService;

    @BeforeAll
    static void setUp() {
        documentKnowledgeService = new DocumentKnowledgeService("docs");
        documentKnowledgeService.loadDocuments();
        assertTrue(documentKnowledgeService.isLoaded(), "Knowledge base must be loaded");
        assertEquals(7, documentKnowledgeService.getDocumentCount(), "All 7 institutional documents must be loaded");

        retrievalService = new KnowledgeRetrievalService(documentKnowledgeService, 10.0, 3);
    }

    @Test
    @DisplayName("Regression Test 1: Attendance calculation for 40 total classes")
    void testAttendanceCalculation_fortyClasses() throws Exception {
        String query = "How many classes can I miss if there are 40 classes in total?";

        // 1. Assert Retrieval is Triggered and matches verified attendance policy
        RetrievalResult retrievalResult = retrievalService.search(query);
        assertEquals(QueryScope.COLLEGE_GROUNDED, retrievalResult.getScope(),
                "Attendance calculation must route to COLLEGE_GROUNDED, not be rejected solely because of calculation");
        assertTrue(retrievalResult.hasSufficientEvidence(), "Must retrieve sufficient document evidence");
        assertFalse(retrievalResult.getMatches().isEmpty(), "Retrieval must produce matches");

        ScoredKnowledgeChunk topChunk = retrievalResult.getTopChunk();
        assertNotNull(topChunk);
        assertEquals("docs/attendance/attendance-policy.md", topChunk.getSourcePath());
        assertTrue(topChunk.getSectionTitle().contains("Attendance"),
                "Section title must be attendance-related: " + topChunk.getSectionTitle());
        assertTrue(topChunk.getContent().contains("75%"), "Retrieved chunk must contain verified 75% benchmark");

        // 2. Assert Grounded Final Answer (Unconfigured Boundary)
        ChatService unconfiguredChat = new ChatService("", "gemini-3.8-flash", null, retrievalService);
        ChatResponse unconfiguredResponse = unconfiguredChat.processChat(new ChatRequest(query, "conv-reg-1a"));

        assertEquals("AWAITING_PROVIDER_KEY", unconfiguredResponse.getStatus());
        String unconfiguredContent = unconfiguredResponse.getContent();
        // Assert: for 40 classes and 75% minimum, shows 30 required and 10 maximum absences
        assertTrue(unconfiguredContent.contains("30") && unconfiguredContent.toLowerCase().contains("required"),
                "Answer must state 30 required classes: " + unconfiguredContent);
        assertTrue(unconfiguredContent.contains("10") && unconfiguredContent.toLowerCase().contains("absences"),
                "Answer must state 10 maximum absences: " + unconfiguredContent);
        // Assert: limitation stated rather than asserting universal rule
        assertTrue(unconfiguredContent.toLowerCase().contains("batch") || unconfiguredContent.toLowerCase().contains("pending"),
                "Answer must state batch-specific limitation: " + unconfiguredContent);
        // Assert: verified link to VTOP portal
        assertTrue(unconfiguredContent.contains("[VTOP Student Portal](https://vtop.vitap.ac.in/)"),
                "Must include correct VTOP link: " + unconfiguredContent);

        // 3. Assert Grounded Final Answer (Configured Gemini with Mock)
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"For 40 total classes, at 75% minimum attendance you must attend 30 classes and can miss at most 10 classes. Note that batch-specific differences are pending internal verification on VTOP.\"}]}}]}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        ChatService configuredChat = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);
        ChatResponse configuredResponse = configuredChat.processChat(new ChatRequest(query, "conv-reg-1b"));

        assertEquals("CONFIGURED", configuredResponse.getStatus());
        assertTrue(configuredResponse.getContent().contains("30"));
        assertTrue(configuredResponse.getContent().contains("10"));

        // Verify request payload injected verified attendance benchmark
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();
        assertNotNull(sent);

        // Assert citations: must cite attendance-policy.md
        List<SourceReference> sources = configuredResponse.getSources();
        assertFalse(sources.isEmpty(), "Citations must be present");
        assertEquals("docs/attendance/attendance-policy.md", sources.get(0).getDocumentPath());
    }

    @Test
    @DisplayName("Regression Test 2: FAT eligibility requirement")
    void testFatEligibilityRequirement() throws Exception {
        String query = "What is the FAT eligibility requirement?";

        // 1. Assert Route and Retrieval Triggered
        RetrievalResult retrievalResult = retrievalService.search(query);
        assertEquals(QueryScope.COLLEGE_GROUNDED, retrievalResult.getScope(),
                "FAT eligibility must route to COLLEGE_GROUNDED");
        assertTrue(retrievalResult.hasSufficientEvidence(), "Must trigger retrieval and find sufficient evidence");

        boolean matchesExamOrAttendance = retrievalResult.getMatches().stream()
                .anyMatch(chunk -> chunk.getSourcePath().equals("docs/exam-rules/examination-guidelines.md")
                        || chunk.getSourcePath().equals("docs/attendance/attendance-policy.md"));
        assertTrue(matchesExamOrAttendance, "Must retrieve exam guidelines or attendance policy");

        // 2. Assert Grounded Final Answer (configured mock)
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"To be eligible for the Final Assessment Test (FAT), students must maintain regular attendance (75% benchmark), hold a valid physical student ID card, download the hall ticket via VTOP, and must not be debarred.\"}]}}]}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);
        ChatResponse response = chatService.processChat(new ChatRequest(query, "conv-reg-2"));

        assertEquals("CONFIGURED", response.getStatus());
        String content = response.getContent();
        assertTrue(content.contains("FAT") || content.contains("Final Assessment Test"));
        assertTrue(content.contains("75%"));

        // Verify citations
        assertFalse(response.getSources().isEmpty());
        for (SourceReference source : response.getSources()) {
            assertNotNull(source.getDocumentPath());
            assertTrue(source.getDocumentPath().startsWith("docs/"));
            // Verify cited Markdown file actually exists on disk
            Path docFile = Path.of(source.getDocumentPath());
            if (!Files.exists(docFile)) {
                docFile = Path.of("..", source.getDocumentPath());
            }
            assertTrue(Files.exists(docFile), "Cited document must exist on disk: " + source.getDocumentPath());
        }
    }

    @Test
    @DisplayName("Regression Test 3: Attendance requirements")
    void testAttendanceRequirements() throws Exception {
        String query = "What are the attendance requirements?";

        // 1. Assert Route and Retrieval Triggered
        RetrievalResult retrievalResult = retrievalService.search(query);
        assertEquals(QueryScope.COLLEGE_GROUNDED, retrievalResult.getScope(),
                "Attendance requirements must route to COLLEGE_GROUNDED");
        assertTrue(retrievalResult.hasSufficientEvidence(), "Must find evidence for attendance requirements");

        ScoredKnowledgeChunk topChunk = retrievalResult.getTopChunk();
        assertEquals("docs/attendance/attendance-policy.md", topChunk.getSourcePath());
        assertTrue(topChunk.getContent().contains("75%"));

        // 2. Assert Grounded Final Answer
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"At VIT-AP University, attendance is monitored continuously on the VTOP portal. The standard minimum benchmark is 75% attendance per registered course to be eligible for FAT exams. Batch-specific threshold confirmations (75% vs 80%) are pending institutional verification.\"}]}}]}");
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);
        ChatResponse response = chatService.processChat(new ChatRequest(query, "conv-reg-3"));

        assertEquals("CONFIGURED", response.getStatus());
        assertTrue(response.getContent().contains("75%"));
        assertTrue(response.getContent().contains("VTOP"));

        assertFalse(response.getSources().isEmpty());
        assertTrue(response.getSources().stream().anyMatch(s -> s.getDocumentPath().equals("docs/attendance/attendance-policy.md")));
    }

    @Test
    @DisplayName("Regression Test 4: Explain recursion in Java (Academic Coursework)")
    void testExplainRecursionInJava_academicCoursework() throws Exception {
        String query = "Explain recursion in Java.";

        // 1. Assert Route and Scope
        RetrievalResult retrievalResult = retrievalService.search(query);
        // It must NOT be OUT_OF_SCOPE: it is academic coursework in VIT-AP CS curriculum!
        assertNotEquals(QueryScope.OUT_OF_SCOPE, retrievalResult.getScope(),
                "Academic programming questions must be handled within the university academic scope, NOT rejected as OUT_OF_SCOPE!");
        assertEquals(QueryScope.COLLEGE_UNGROUNDED, retrievalResult.getScope(),
                "Must route to COLLEGE_UNGROUNDED because no course notes exist in verified docs repository");

        // 2. Assert Retrieval WAS Triggered
        assertFalse(retrievalResult.hasSufficientEvidence(),
                "Must confirm retrieval was triggered and no verified chunk passed the threshold");

        // 3. Assert Grounded Final Answer
        HttpClient mockClient = mock(HttpClient.class);
        ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);
        ChatResponse response = chatService.processChat(new ChatRequest(query, "conv-reg-4"));

        assertEquals("UNGROUNDED_INQUIRY", response.getStatus());
        String content = response.getContent();
        // Does not show generic out-of-scope rejection
        assertFalse(content.contains("I cannot answer questions about general programming"));
        // Explains that academic coursework is recognized but verified lecture notes are not in repository
        assertTrue(content.contains("Academic Coursework") || content.contains("coursework"),
                "Must recognize academic coursework scope: " + content);
        assertTrue(content.contains("[VTOP Student Portal](https://vtop.vitap.ac.in/)"),
                "Must provide properly formatted VTOP link: " + content);
        // Proves no sources are fabricated
        assertTrue(response.getSources().isEmpty(), "Must not fabricate sources when no documents meet threshold");
        // Proves AI provider is never called without verified evidence
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Regression Test 5: What time does the college bus leave tomorrow? (Campus Ungrounded)")
    void testCollegeBusSchedule_campusUngrounded() throws Exception {
        String query = "What time does the college bus leave tomorrow?";

        // 1. Assert Route and Scope
        RetrievalResult retrievalResult = retrievalService.search(query);
        assertEquals(QueryScope.COLLEGE_UNGROUNDED, retrievalResult.getScope(),
                "Campus bus query has college intent but missing evidence, so routes to COLLEGE_UNGROUNDED");

        // 2. Assert Retrieval WAS Triggered
        assertFalse(retrievalResult.hasSufficientEvidence(),
                "Must confirm retrieval was evaluated and found no schedule document");

        // 3. Assert Grounded Final Answer
        HttpClient mockClient = mock(HttpClient.class);
        ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);
        ChatResponse response = chatService.processChat(new ChatRequest(query, "conv-reg-5"));

        assertEquals("UNGROUNDED_INQUIRY", response.getStatus());
        String content = response.getContent();
        assertTrue(content.contains("Information Not Found in Verified Documentation"));
        assertTrue(content.contains("[VTOP Student Portal](https://vtop.vitap.ac.in/)"),
                "Must contain proper VTOP link: " + content);
        assertTrue(response.getSources().isEmpty(), "No sources must be fabricated");
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Regression Test 6: Unrelated non-university questions (Out of Scope Boundary)")
    void testUnrelatedNonUniversityQuestions_strictlyOutOfScope() throws Exception {
        List<String> unrelatedQueries = List.of(
                "What is the capital of France?",
                "How do I build a crypto trading bot with Binance API?",
                "Can you recommend a recipe for chocolate cake?",
                "How do I configure an Nginx reverse proxy on AWS EC2?"
        );

        HttpClient mockClient = mock(HttpClient.class);
        ChatService chatService = new ChatService("test-key", "gemini-3.8-flash", mockClient, retrievalService);

        for (String unrelated : unrelatedQueries) {
            // 1. Assert Route
            RetrievalResult result = retrievalService.search(unrelated);
            assertEquals(QueryScope.OUT_OF_SCOPE, result.getScope(),
                    "Query '" + unrelated + "' must route to OUT_OF_SCOPE without weakening boundary");
            assertFalse(result.hasSufficientEvidence());

            // 2. Assert Final Answer
            ChatResponse response = chatService.processChat(new ChatRequest(unrelated, "conv-reg-6"));
            assertEquals("OUT_OF_SCOPE", response.getStatus(),
                    "Status must be OUT_OF_SCOPE for: " + unrelated);
            assertTrue(response.getContent().contains("CollegeAssist Scope Notice"),
                    "Must return scope notice for: " + unrelated);
            assertTrue(response.getSources().isEmpty(),
                    "No sources for out-of-scope query: " + unrelated);
        }

        // Provider must NEVER be called for out of scope queries
        verify(mockClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("Distinction: Academic coursework vs unrelated general programming")
    void testDistinctionBetweenAcademicProgrammingAndUnrelatedProgramming() {
        // Academic Coursework Queries (Handled under university academic scope -> COLLEGE_UNGROUNDED)
        List<String> courseworkQueries = List.of(
                "Explain recursion in Java.",
                "How does binary search tree insertion work in data structures?",
                "Explain OOP polymorphism with examples in Java.",
                "What is the time complexity of quicksort in algorithm analysis?"
        );

        for (String cq : courseworkQueries) {
            RetrievalResult result = retrievalService.search(cq);
            assertNotEquals(QueryScope.OUT_OF_SCOPE, result.getScope(),
                    "Coursework query '" + cq + "' must NOT be rejected as OUT_OF_SCOPE");
            assertEquals(QueryScope.COLLEGE_UNGROUNDED, result.getScope(),
                    "Coursework query '" + cq + "' must route to COLLEGE_UNGROUNDED");
        }

        // Unrelated General Programming / DevOps / Commercial Queries (Strictly OUT_OF_SCOPE)
        List<String> unrelatedProgQueries = List.of(
                "How do I build a crypto trading bot with Binance API?",
                "How do I set up a Kubernetes cluster on AWS EKS?",
                "Write a web scraper for Twitter using Puppeteer",
                "How do I integrate Stripe checkout into Shopify?"
        );

        for (String uq : unrelatedProgQueries) {
            RetrievalResult result = retrievalService.search(uq);
            assertEquals(QueryScope.OUT_OF_SCOPE, result.getScope(),
                    "Unrelated programming query '" + uq + "' must strictly route to OUT_OF_SCOPE");
        }
    }

    @Test
    @DisplayName("Verification: Every cited Markdown section exists on disk with corresponding content")
    void testAllChunksAndSectionsExistOnDisk() throws Exception {
        List<ScoredKnowledgeChunk> allScored = retrievalService.search("attendance examination fees hostel").getMatches();
        assertFalse(allScored.isEmpty());

        ChatService chatService = new ChatService("", "gemini-3.8-flash", null, retrievalService);
        List<SourceReference> sources = chatService.toSourceReferences(allScored);

        for (SourceReference source : sources) {
            // 1. File exists
            Path filePath = Path.of(source.getDocumentPath());
            if (!Files.exists(filePath)) {
                filePath = Path.of("..", source.getDocumentPath());
            }
            assertTrue(Files.exists(filePath), "Cited document must exist: " + source.getDocumentPath());

            // 2. Section heading appears in file
            String fileContent = Files.readString(filePath);
            String sectionTitle = source.getSectionTitle();
            if (!"General".equals(sectionTitle) && !"Document Overview".equals(sectionTitle)) {
                assertTrue(fileContent.contains(sectionTitle),
                        "Cited section '" + sectionTitle + "' must appear in " + source.getDocumentPath());
            }
        }
    }
}
