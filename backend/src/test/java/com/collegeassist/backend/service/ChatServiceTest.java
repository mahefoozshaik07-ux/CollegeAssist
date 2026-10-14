package com.collegeassist.backend.service;

import com.collegeassist.backend.dto.ChatRequest;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.ChatStatusResponse;
import com.collegeassist.backend.dto.SourceReference;
import com.collegeassist.backend.model.KnowledgeChunk;
import com.collegeassist.backend.model.QueryScope;
import com.collegeassist.backend.model.RetrievalResult;
import com.collegeassist.backend.model.ScoredKnowledgeChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    @Test
    void shouldHaveAutowiredAnnotationOnPrimaryConstructor() throws NoSuchMethodException {
        java.lang.reflect.Constructor<ChatService> constructor = 
                ChatService.class.getConstructor(String.class, String.class);
        assertTrue(constructor.isAnnotationPresent(org.springframework.beans.factory.annotation.Autowired.class),
                "Primary constructor must be annotated with @Autowired for Spring dependency injection");
        
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash");
        assertTrue(service.isConfigured());
        assertEquals("gemini-3.8-flash", service.getStatus().getModel());
    }

    @Test
    void shouldReturnConfiguredStatusWhenApiKeyPresent() {
        ChatService service = new ChatService("sample-test-key", "gemini-3.8-flash", httpClient);

        ChatStatusResponse status = service.getStatus();
        assertTrue(status.isConfigured());
        assertEquals("gemini-3.8-flash", status.getModel());
        assertEquals("Google Gemini", status.getAiProvider());
        assertTrue(status.getCategories().contains("attendance"));
        assertTrue(status.getCategories().contains("exam-rules"));
    }

    @Test
    void shouldReturnUnconfiguredStatusWhenApiKeyAbsent() {
        ChatService service = new ChatService("", "gemini-3.8-flash", httpClient);

        ChatStatusResponse status = service.getStatus();
        assertFalse(status.isConfigured());
        assertEquals("gemini-3.8-flash", status.getModel());
    }

    @Test
    void shouldReturnUnconfiguredExplanationWhenKeyEmpty() {
        ChatService service = new ChatService("", "gemini-3.8-flash", httpClient);

        ChatResponse response = service.processChat(new ChatRequest("What is the attendance rule?", "c1"));
        assertEquals("AWAITING_PROVIDER_KEY", response.getStatus());
        assertFalse(response.isConfigured());
        assertNotNull(response.getSources());
        assertFalse(response.getSources().isEmpty());
        assertEquals("Attendance & Condonation", response.getSources().get(0).getCategory());
        assertTrue(response.getContent().contains("Provider Setup Required"));
    }

    @Test
    void shouldSuccessfullyCallGeminiAndReturnConfiguredResponse() throws Exception {
        String testApiKey = "secret-test-key-12345";
        ChatService service = new ChatService(testApiKey, "gemini-3.8-flash", httpClient);

        String mockResponseBody = "{"
                + "\"candidates\":[{"
                + "\"content\":{\"parts\":[{\"text\":\"Official attendance requires a minimum of 75% at VIT-AP.\"}]}"
                + "}]"
                + "}";

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn(mockResponseBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("What is the attendance criteria?", "c1"));

        assertEquals("CONFIGURED", response.getStatus());
        assertTrue(response.isConfigured());
        assertEquals("Official attendance requires a minimum of 75% at VIT-AP.", response.getContent());
        assertFalse(response.getSources().isEmpty());

        // Verify request details
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sentRequest = requestCaptor.getValue();

        // 1. Verify URL uses models/gemini-3.8-flash without API key query param
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", sentRequest.uri().toString());
        assertFalse(sentRequest.uri().toString().contains("key="));

        // 2. Verify x-goog-api-key header contains the key
        assertTrue(sentRequest.headers().firstValue("x-goog-api-key").isPresent());
        assertEquals(testApiKey, sentRequest.headers().firstValue("x-goog-api-key").get());
    }

    @Test
    void shouldHandleProviderErrorAndRedactSecretWhenGeminiFailsWith404() throws Exception {
        String testApiKey = "secret-test-key-99999";
        ChatService service = new ChatService(testApiKey, "gemini-1.5-flash", httpClient);

        String mockErrorBody = "{"
                + "\"error\":{"
                + "\"code\":404,"
                + "\"message\":\"models/gemini-1.5-flash is not found for API version v1beta, or is not supported for generateContent.\","
                + "\"status\":\"NOT_FOUND\""
                + "}"
                + "}";

        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn(mockErrorBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("Tell me about exams", "c1"));

        assertEquals("PROVIDER_ERROR", response.getStatus());
        assertTrue(response.isConfigured());
        // Priority 2: Friendly service unavailable message for configuration errors
        assertTrue(response.getContent().contains("AI Service Unavailable"));
        assertFalse(response.getContent().contains("models/gemini-1.5-flash"));
        // Ensure secret key is NEVER exposed in the content
        assertFalse(response.getContent().contains(testApiKey));
    }

    @Test
    void shouldRedactApiKeyIfPresentInErrorMessage() throws Exception {
        String testApiKey = "super-secret-key-xyz";
        ChatService service = new ChatService(testApiKey, "gemini-3.8-flash", httpClient);

        String mockErrorBody = "{"
                + "\"error\":{"
                + "\"code\":400,"
                + "\"message\":\"API key super-secret-key-xyz is invalid.\","
                + "\"status\":\"INVALID_ARGUMENT\""
                + "}"
                + "}";

        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn(mockErrorBody);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("Fees deadline", "c1"));

        assertEquals("PROVIDER_ERROR", response.getStatus());
        // Priority 2: Student receives friendly message without raw provider errors or secrets
        assertTrue(response.getContent().contains("AI Service Unavailable"));
        assertFalse(response.getContent().contains(testApiKey));
        assertFalse(response.getContent().contains("super-secret-key-xyz"));
    }

    @Test
    void shouldNormalizeModelNameWhenModelsPrefixProvided() throws Exception {
        String testApiKey = "key-test";
        // User sets GEMINI_MODEL="models/gemini-3.8-flash"
        ChatService service = new ChatService(testApiKey, "models/gemini-3.8-flash", httpClient);

        assertEquals("gemini-3.8-flash", service.getNormalizedModel());

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        service.processChat(new ChatRequest("What is the attendance criteria?", "c1"));

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sentRequest = requestCaptor.getValue();

        // Must not become models/models/gemini-3.8-flash
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", sentRequest.uri().toString());
    }

    @Test
    void shouldRejectOutOfScopeQueryWithoutCallingGemini() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        ChatResponse response = service.processChat(new ChatRequest("How do I build a crypto trading bot with Binance API?", "c1"));

        assertEquals("OUT_OF_SCOPE", response.getStatus());
        assertTrue(response.getSources().isEmpty());
        assertTrue(response.getContent().contains("CollegeAssist Scope Notice"));
        // Gemini MUST NOT be called!
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldRejectCollegeUngroundedQueryWithoutCallingGemini() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        ChatResponse response = service.processChat(new ChatRequest("What is the college bus route and bus pickup schedule for Vijayawada city?", "c1"));

        assertEquals("UNGROUNDED_INQUIRY", response.getStatus());
        assertTrue(response.getSources().isEmpty());
        assertTrue(response.getContent().contains("Information Not Found in Verified Documentation"));
        // Gemini MUST NOT be called without supporting document evidence!
        verify(httpClient, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldIncludeRetrievedEvidencePassagesAndSectionHeadersInGeminiPayload() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        KnowledgeChunk chunk = new KnowledgeChunk(
                "c1",
                "attendance",
                "docs/attendance/attendance-policy.md",
                "attendance-policy.md",
                "VIT-AP University: Attendance Regulations",
                "2. Minimum Attendance Threshold & Exam Eligibility",
                "Students must maintain at least 75% attendance to be eligible for FAT."
        );
        ScoredKnowledgeChunk scoredChunk = new ScoredKnowledgeChunk(chunk, 25.0, List.of("attendance", "minimum"));

        String systemInstruction = "CRITICAL GROUNDING AND INTEGRITY INSTRUCTIONS: NO TEMPLATE CONFIRMATION";
        String payload = service.buildJsonRequest(systemInstruction, "What is the minimum attendance?", List.of(scoredChunk));

        assertTrue(payload.contains("=== BEGIN OFFICIAL VIT-AP EVIDENCE ==="));
        assertTrue(payload.contains("=== END OFFICIAL VIT-AP EVIDENCE ==="));
        assertTrue(payload.contains("docs/attendance/attendance-policy.md"));
        assertTrue(payload.contains("2. Minimum Attendance Threshold & Exam Eligibility"));
        assertTrue(payload.contains("75% attendance"));
        assertTrue(payload.contains("STUDENT INQUIRY:"));
        assertTrue(payload.contains("What is the minimum attendance?"));
        assertTrue(payload.contains("NO TEMPLATE CONFIRMATION"));
    }

    @Test
    void shouldGenerateGenuineCitationsFromRetrievedChunks() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"75% attendance is required.\"}]}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("What is the minimum attendance required?", "c1"));

        assertEquals("CONFIGURED", response.getStatus());
        assertFalse(response.getSources().isEmpty());

        // Citations must be derived exclusively from real chunks
        SourceReference citation = response.getSources().get(0);
        assertEquals("Attendance & Condonation", citation.getCategory());
        assertEquals("docs/attendance/attendance-policy.md", citation.getDocumentPath());
        assertEquals("Verified Evidence", citation.getStatus());
        assertNotNull(citation.getSectionTitle());
        assertTrue(citation.getSectionTitle().contains("Minimum Attendance Threshold"));
    }

    @Test
    void shouldDeduplicateIdenticalSourceReferences() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        KnowledgeChunk chunk1 = new KnowledgeChunk(
                "c1", "attendance", "docs/attendance/attendance-policy.md", "attendance-policy.md",
                "VIT-AP Attendance", "2. Minimum Attendance Threshold", "Students need 75% attendance."
        );
        KnowledgeChunk chunk2 = new KnowledgeChunk(
                "c2", "attendance", "docs/attendance/attendance-policy.md", "attendance-policy.md",
                "VIT-AP Attendance", "2. Minimum Attendance Threshold", "Students need 75% attendance."
        );
        KnowledgeChunk chunk3 = new KnowledgeChunk(
                "c3", "attendance", "docs/attendance/attendance-policy.md", "attendance-policy.md",
                "VIT-AP Attendance", "2. Minimum Attendance Threshold", "Students need 75% attendance."
        );

        ScoredKnowledgeChunk sc1 = new ScoredKnowledgeChunk(chunk1, 20.0, List.of("attendance"));
        ScoredKnowledgeChunk sc2 = new ScoredKnowledgeChunk(chunk2, 18.0, List.of("attendance"));
        ScoredKnowledgeChunk sc3 = new ScoredKnowledgeChunk(chunk3, 15.0, List.of("attendance"));

        List<SourceReference> sources = service.toSourceReferences(List.of(sc1, sc2, sc3));

        // Must deduplicate identical documentPath + sectionTitle to exactly 1 source reference
        assertEquals(1, sources.size(), "Duplicate chunks must be deduplicated into a single source reference");
        assertEquals("docs/attendance/attendance-policy.md", sources.get(0).getDocumentPath());
        assertEquals("2. Minimum Attendance Threshold", sources.get(0).getSectionTitle());
    }

    @Test
    void shouldSplitAndNormalizeConcatenatedCitationPaths() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        // 1. Comma and semicolon separated concatenated paths
        String commaSeparated = "docs/attendance/attendance-policy.md, docs/hostel/hostel-regulations.md; docs/fees/tuition-and-hostel-fees.md";
        List<String> splitPaths = ChatService.splitAndNormalizePaths(commaSeparated);
        assertEquals(3, splitPaths.size());
        assertEquals("docs/attendance/attendance-policy.md", splitPaths.get(0));
        assertEquals("docs/hostel/hostel-regulations.md", splitPaths.get(1));
        assertEquals("docs/fees/tuition-and-hostel-fees.md", splitPaths.get(2));

        // 2. Run-on concatenation without delimiter (e.g. .mddocs/)
        String runOn = "docs/attendance/attendance-policy.mddocs/hostel/hostel-regulations.md";
        List<String> runOnSplit = ChatService.splitAndNormalizePaths(runOn);
        assertEquals(2, runOnSplit.size());
        assertEquals("docs/attendance/attendance-policy.md", runOnSplit.get(0));
        assertEquals("docs/hostel/hostel-regulations.md", runOnSplit.get(1));

        // 3. toSourceReferences with a concatenated chunk
        KnowledgeChunk chunk = new KnowledgeChunk(
                "c1", "hostel", runOn, "hostel-regulations.md",
                "VIT-AP Hostel", "4. Key Residential Rules", "Curfew cutoff pending verification."
        );
        ScoredKnowledgeChunk sc = new ScoredKnowledgeChunk(chunk, 15.0, List.of("curfew"));

        List<SourceReference> sources = service.toSourceReferences(List.of(sc));
        assertEquals(2, sources.size(), "Concatenated path must be cleanly decomposed into 2 distinct references");
        assertEquals("docs/attendance/attendance-policy.md", sources.get(0).getDocumentPath());
        assertEquals("docs/hostel/hostel-regulations.md", sources.get(1).getDocumentPath());
    }

    @Test
    void shouldEnforceStrictGroundingAgainstUnsupportedClaimsInSystemInstruction() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);
        String systemInstruction = service.buildSystemInstruction();

        // 1. Medical condonation
        assertTrue(systemInstruction.contains("Medical Condonation"));
        assertTrue(systemInstruction.contains("within 3 days"));
        assertTrue(systemInstruction.contains("65%"));
        assertTrue(systemInstruction.contains("pending official verification"));

        // 2. On-Duty (OD)
        assertTrue(systemInstruction.contains("On-Duty (OD)"));
        assertTrue(systemInstruction.contains("prior to the event"));
        assertTrue(systemInstruction.contains("pending verification"));

        // 3. Attendance thresholds for theory and lab
        assertTrue(systemInstruction.contains("Attendance Thresholds"));
        assertTrue(systemInstruction.contains("75% per registered course"));
        assertTrue(systemInstruction.contains("Never invent separate numeric thresholds for laboratory courses"));

        // 4. Hostel rules
        assertTrue(systemInstruction.contains("Hostel Rules"));
        assertTrue(systemInstruction.contains("curfew hours"));
        assertTrue(systemInstruction.contains("Never present hostel residential rules as academic course requirements"));

        // 5. Academic calendar dates
        assertTrue(systemInstruction.contains("Academic Calendar"));
        assertTrue(systemInstruction.contains("Never invent dates for semester commencement, CAT-1, CAT-2"));
    }

    @Test
    void shouldEnforceMissingInformationAndPendingVerificationInstructions() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);
        String systemInstruction = service.buildSystemInstruction();

        assertTrue(systemInstruction.contains("3. MISSING INFORMATION & PENDING VERIFICATION"));
        assertTrue(systemInstruction.contains("explicitly inform the student that official verified details for this item are currently unavailable in university records"));
        assertTrue(systemInstruction.contains("consult VTOP or the academic office"));
    }

    @Test
    void shouldEnforceConflictingSourceDocumentsInstructions() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);
        String systemInstruction = service.buildSystemInstruction();

        assertTrue(systemInstruction.contains("4. CONFLICTING SOURCE DOCUMENTS"));
        assertTrue(systemInstruction.contains("explicitly present both perspectives or explain the discrepancy"));
        assertTrue(systemInstruction.contains("advise consulting the issuing university authority for clarification"));
    }

    @Test
    void shouldEnsureCitationsPointOnlyToActuallyRetrievedSources() throws Exception {
        KnowledgeRetrievalService mockRetrieval = org.mockito.Mockito.mock(KnowledgeRetrievalService.class);
        KnowledgeChunk chunk = new KnowledgeChunk(
                "c1", "hostel", "docs/hostel/hostel-regulations.md", "hostel-regulations.md",
                "Hostel Regulations", "4. Key Residential Rules & Conduct",
                "Cooking Prohibition: Cooking inside hostel rooms is strictly prohibited."
        );
        ScoredKnowledgeChunk scored = new ScoredKnowledgeChunk(chunk, 15.0, List.of("cooking"));
        RetrievalResult mockResult = new RetrievalResult(
                "Can I cook in the hostel room?", QueryScope.COLLEGE_GROUNDED, List.of(scored), 15.0, 10.0, "Found"
        );
        when(mockRetrieval.search(any())).thenReturn(mockResult);

        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient, mockRetrieval);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hostel cooking is prohibited under fire-safety regulations.\"}]}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("Can I cook in the hostel room?", "c1"));

        assertEquals("CONFIGURED", response.getStatus());
        assertEquals(1, response.getSources().size());

        SourceReference source = response.getSources().get(0);
        assertEquals("docs/hostel/hostel-regulations.md", source.getDocumentPath(),
                "Citations must point only to the source document retrieved for the query");
        assertEquals("Hostel & Residential Regulations", source.getCategory());
        assertEquals("Verified Evidence", source.getStatus());
        assertEquals("4. Key Residential Rules & Conduct", source.getSectionTitle());
    }

    @Test
    void shouldRetryTransient429UpToThreeTimesAndReturnFriendlyMessage() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        when(httpResponse.statusCode()).thenReturn(429);
        when(httpResponse.body()).thenReturn("{\"error\":{\"code\":429,\"message\":\"Quota exceeded\"}}");
        when(httpResponse.headers()).thenReturn(java.net.http.HttpHeaders.of(
                java.util.Map.of("Retry-After", java.util.List.of("0")),
                (a, b) -> true
        ));
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("What is the attendance policy?", "c1"));

        assertEquals("PROVIDER_ERROR", response.getStatus());
        assertTrue(response.getContent().contains("AI Service Temporarily Busy"));
        assertFalse(response.getContent().contains("Quota exceeded"));
        // Verified 3 total attempts
        verify(httpClient, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldNotRetryPermanentClientErrors() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        when(httpResponse.statusCode()).thenReturn(404);
        when(httpResponse.body()).thenReturn("{\"error\":{\"code\":404,\"message\":\"Model not found\"}}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("What is the attendance policy?", "c1"));

        assertEquals("PROVIDER_ERROR", response.getStatus());
        assertTrue(response.getContent().contains("AI Service Unavailable"));
        assertFalse(response.getContent().contains("Model not found"));
        // Must NOT retry: exactly 1 attempt
        verify(httpClient, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void shouldParseMultiPartCandidatesUsingJackson() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        String json = "{"
                + "\"candidates\":[{"
                + "\"content\":{"
                + "\"parts\":["
                + "{\"text\":\"Line 1: 75% attendance.\\n\"},"
                + "{\"text\":\"Line 2: FAT exam eligibility.\"}"
                + "]"
                + "}"
                + "}]"
                + "}";

        String result = service.extractTextFromGeminiResponse(json);
        assertEquals("Line 1: 75% attendance.\nLine 2: FAT exam eligibility.", result);
    }

    @Test
    void shouldHandleBlockedPromptResponseSafely() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        String json = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";
        String result = service.extractTextFromGeminiResponse(json);
        assertTrue(result.contains("safety policies"));
    }

    @Test
    void shouldHandleSafetyFinishReasonSafely() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        String json = "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}";
        String result = service.extractTextFromGeminiResponse(json);
        assertTrue(result.contains("safety policies"));
    }

    @Test
    void shouldHandleEmptyCandidatesArraySafely() throws Exception {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        String json = "{\"candidates\":[]}";
        String result = service.extractTextFromGeminiResponse(json);
        assertTrue(result.contains("no text payload was generated"));
    }

    @Test
    void shouldThrowGeminiResponseParseExceptionOnMalformedJson() {
        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient);

        assertThrows(ChatService.GeminiResponseParseException.class, () -> {
            service.extractTextFromGeminiResponse("{malformed json text");
        });
    }

    @Test
    void shouldProveEvidencePassagesSuppliedToGeminiMatchReturnedSourceReferences() throws Exception {
        // Use real DocumentKnowledgeService and real KnowledgeRetrievalService with real docs/
        DocumentKnowledgeService docService = new DocumentKnowledgeService("docs");
        docService.loadDocuments();
        KnowledgeRetrievalService retrieval = new KnowledgeRetrievalService(docService);

        ChatService service = new ChatService("sample-key", "gemini-3.8-flash", httpClient, retrieval);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"75% attendance is required.\"}]}}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(httpResponse);

        ChatResponse response = service.processChat(new ChatRequest("What is the minimum attendance required?", "c1"));

        assertEquals("CONFIGURED", response.getStatus());
        assertFalse(response.getSources().isEmpty(), "Must return verified source references");

        // Capture request sent to Gemini
        org.mockito.ArgumentCaptor<HttpRequest> captor = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(captor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest sent = captor.getValue();

        // Verify request body publisher and URI
        assertTrue(sent.bodyPublisher().isPresent(), "Request body publisher must be present");
        assertTrue(sent.uri().toString().contains("models/gemini-3.8-flash:generateContent"));

        // Verify each SourceReference has matching verified evidence in response
        for (SourceReference src : response.getSources()) {
            assertNotNull(src.getDocumentPath());
            assertTrue(src.getDocumentPath().startsWith("docs/"));
            assertEquals("Verified Evidence", src.getStatus());
            assertNotNull(src.getSectionTitle());
        }
    }
}


