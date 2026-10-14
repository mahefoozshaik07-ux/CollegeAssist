package com.collegeassist.backend.service;

import com.collegeassist.backend.model.QueryScope;
import com.collegeassist.backend.model.RetrievalResult;
import com.collegeassist.backend.model.ScoredKnowledgeChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeRetrievalServiceTest {

    private static DocumentKnowledgeService documentKnowledgeService;
    private static KnowledgeRetrievalService retrievalService;

    @BeforeAll
    static void setUp() {
        documentKnowledgeService = new DocumentKnowledgeService("docs");
        documentKnowledgeService.loadDocuments();
        assertTrue(documentKnowledgeService.isLoaded(), "Knowledge base must be loaded for tests");
        assertTrue(documentKnowledgeService.getChunkCount() > 0, "Chunks must exist");

        retrievalService = new KnowledgeRetrievalService(documentKnowledgeService, 10.0, 3);
    }

    @Test
    @DisplayName("Retrieves attendance policy section for 'attendance shortage'")
    void testSearch_attendanceShortage() {
        RetrievalResult result = retrievalService.search("attendance shortage");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertTrue(result.hasSufficientEvidence());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertNotNull(top);
        assertEquals("attendance", top.getCategory());
        assertEquals("docs/attendance/attendance-policy.md", top.getSourcePath());
        assertTrue(top.getScore() >= 10.0, "Score should exceed threshold: " + top.getScore());
        assertTrue(top.getSectionTitle().contains("Attendance") || top.getSectionTitle().contains("Threshold"));
    }

    @Test
    @DisplayName("Retrieves minimum threshold section for 'minimum attendance'")
    void testSearch_minimumAttendance() {
        RetrievalResult result = retrievalService.search("What is the minimum attendance required?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("docs/attendance/attendance-policy.md", top.getSourcePath());
        assertTrue(top.getSectionTitle().contains("Minimum Attendance Threshold"));
        assertTrue(top.getContent().contains("75%"));
    }

    @Test
    @DisplayName("Retrieves examination guidelines for 'FAT hall ticket'")
    void testSearch_fatHallTicket() {
        RetrievalResult result = retrievalService.search("How do I get my FAT hall ticket on VTOP?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("exam-rules", top.getCategory());
        assertEquals("docs/exam-rules/examination-guidelines.md", top.getSourcePath());
        assertTrue(top.getSectionTitle().contains("Hall Ticket") || top.getSectionTitle().contains("Eligibility"));
        assertTrue(top.getContent().contains("Hall Ticket"));
    }

    @Test
    @DisplayName("Retrieves hostel curfew rules for 'hostel curfew'")
    void testSearch_hostelCurfew() {
        RetrievalResult result = retrievalService.search("What is the hostel curfew and night in-time?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("hostel", top.getCategory());
        assertEquals("docs/hostel/hostel-regulations.md", top.getSourcePath());
        assertTrue(top.getSectionTitle().contains("Residential Rules") || top.getContent().contains("curfew"));
    }

    @Test
    @DisplayName("Retrieves fee refund guidelines for 'fee refund'")
    void testSearch_feeRefund() {
        RetrievalResult result = retrievalService.search("What is the process for college fee refund if I withdraw?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("fees", top.getCategory());
        assertEquals("docs/fees/tuition-and-hostel-fees.md", top.getSourcePath());
        assertTrue(top.getSectionTitle().contains("Refund"));
        assertTrue(top.getContent().contains("UGC") || top.getContent().contains("refund"));
    }

    @Test
    @DisplayName("Retrieves FFCS course registration principles for 'FFCS registration'")
    void testSearch_ffcsRegistration() {
        RetrievalResult result = retrievalService.search("How does FFCS course registration work?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("college-rules", top.getCategory());
        assertEquals("docs/college-rules/code-of-conduct-and-ffcs.md", top.getSourcePath());
        assertTrue(top.getSectionTitle().contains("FFCS"));
    }

    @Test
    @DisplayName("Matches are strictly ordered by descending score")
    void testScoreOrdering() {
        RetrievalResult result = retrievalService.search("hostel AC room and mess food catering", 5, 5.0);

        List<ScoredKnowledgeChunk> matches = result.getMatches();
        assertTrue(matches.size() >= 2, "Should return multiple matches");

        for (int i = 0; i < matches.size() - 1; i++) {
            assertTrue(matches.get(i).getScore() >= matches.get(i + 1).getScore(),
                    "Scores must be descending: " + matches.get(i).getScore() + " vs " + matches.get(i + 1).getScore());
        }
    }

    @Test
    @DisplayName("Threshold filters chunks appropriately")
    void testThresholdHandling() {
        String query = "hostel curfew rules";

        // Lower threshold returns matches
        RetrievalResult normalResult = retrievalService.search(query, 3, 10.0);
        assertFalse(normalResult.getMatches().isEmpty());

        // Extremely high threshold excludes matches
        RetrievalResult strictResult = retrievalService.search(query, 3, 200.0);
        assertTrue(strictResult.getMatches().isEmpty());
        // Since it's a college query, without sufficient evidence it becomes COLLEGE_UNGROUNDED
        assertEquals(QueryScope.COLLEGE_UNGROUNDED, strictResult.getScope());
    }

    @Test
    @DisplayName("Handles empty, null, or whitespace-only queries safely")
    void testEmptyAndBlankQueries() {
        RetrievalResult nullResult = retrievalService.search(null);
        assertEquals(QueryScope.OUT_OF_SCOPE, nullResult.getScope());
        assertTrue(nullResult.getMatches().isEmpty());
        assertEquals(0.0, nullResult.getTopScore());

        RetrievalResult emptyResult = retrievalService.search("");
        assertEquals(QueryScope.OUT_OF_SCOPE, emptyResult.getScope());
        assertTrue(emptyResult.getMatches().isEmpty());

        RetrievalResult whitespaceResult = retrievalService.search("    \n\t  ");
        assertEquals(QueryScope.OUT_OF_SCOPE, whitespaceResult.getScope());
        assertTrue(whitespaceResult.getMatches().isEmpty());

        RetrievalResult stopWordsOnly = retrievalService.search("is it what and the of");
        assertEquals(QueryScope.OUT_OF_SCOPE, stopWordsOnly.getScope());
        assertTrue(stopWordsOnly.getMatches().isEmpty());
    }

    @Test
    @DisplayName("Classifies unrelated non-college queries as OUT_OF_SCOPE with zero or low score")
    void testIrrelevantQueries_outOfScope() {
        String[] unrelatedQueries = {
                "How do I build a crypto trading bot with Binance API?",
                "What is the capital of France and Germany?",
                "Bake a chocolate chip cake with vanilla cream",
                "Who won the FIFA World Cup in 2022?"
        };

        for (String query : unrelatedQueries) {
            RetrievalResult result = retrievalService.search(query);
            assertEquals(QueryScope.OUT_OF_SCOPE, result.getScope(),
                    "Query '" + query + "' should be classified OUT_OF_SCOPE but was " + result.getScope());
            assertTrue(result.getMatches().isEmpty(), "No chunks should pass threshold for: " + query);
        }
    }

    @Test
    @DisplayName("Classifies college-related questions with missing document coverage as COLLEGE_UNGROUNDED")
    void testMissingInformation_collegeUngrounded() {
        // Query has college intent ("college", "bus", "transport"), but our 7 docs do not contain bus schedule tables
        String query = "What is the college bus route and bus pickup schedule for Vijayawada city?";
        RetrievalResult result = retrievalService.search(query, 3, 10.0);

        assertEquals(QueryScope.COLLEGE_UNGROUNDED, result.getScope(),
                "Query with campus intent but lacking doc coverage should be COLLEGE_UNGROUNDED");
        assertFalse(result.hasSufficientEvidence());
    }

    @Test
    @DisplayName("Proves metadata (sourcePath, sectionTitle, category, matchedTerms) survives retrieval")
    void testMetadataPreservationOnRetrievedChunks() {
        RetrievalResult result = retrievalService.search("Super Dream placement packages CDC recruiters");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        for (ScoredKnowledgeChunk chunk : result.getMatches()) {
            assertNotNull(chunk.getId(), "Chunk ID must be present");
            assertNotNull(chunk.getCategory(), "Category must be present");
            assertNotNull(chunk.getSourcePath(), "Source path must be present");
            assertTrue(chunk.getSourcePath().startsWith("docs/"), "Source path must start with docs/");
            assertNotNull(chunk.getSectionTitle(), "Section title must be present");
            assertNotNull(chunk.getDocumentTitle(), "Document title must be present");
            assertNotNull(chunk.getContent(), "Content must be present");
            assertTrue(chunk.getWordCount() > 0, "Word count must be positive");
            assertTrue(chunk.getScore() > 0.0, "Score must be positive");
            assertFalse(chunk.getMatchedTerms().isEmpty(), "Matched terms must be recorded");
        }

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("placements", top.getCategory());
        assertEquals("docs/placements/career-development-centre.md", top.getSourcePath());
        assertNotNull(top.getSectionTitle(), "Section title must not be null");
        assertFalse(top.getSectionTitle().isBlank(), "Section title must not be blank");
    }

    @Test
    @DisplayName("Query for unsupported lab-specific threshold retrieves attendance policy with general 75% rule and pending verification items")
    void testSearch_unsupportedLabAttendanceQueryRetrievesGeneralPolicy() {
        RetrievalResult result = retrievalService.search("What is the separate laboratory course attendance percentage threshold vs theory?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getTopChunk();
        assertEquals("docs/attendance/attendance-policy.md", top.getSourcePath());
        // Source document mentions the general 75% benchmark for all course components and marks batch differences as pending
        assertTrue(top.getContent().contains("75%") || top.getContent().contains("theory, laboratory, and embedded"));
    }

    @Test
    @DisplayName("Query for missing academic calendar dates retrieves notice section marked as Pending Verification")
    void testSearch_missingInformationAcademicCalendarDatesRetrievesPendingNotice() {
        RetrievalResult result = retrievalService.search("What are the exact academic calendar dates for CAT-1 and FAT exam commencement?");

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertFalse(result.getMatches().isEmpty());

        boolean foundNoticeOrExam = result.getMatches().stream()
                .anyMatch(sc -> sc.getSourcePath().equals("docs/notices/campus-circulars-framework.md")
                        || sc.getSourcePath().equals("docs/exam-rules/examination-guidelines.md"));
        assertTrue(foundNoticeOrExam, "Must retrieve notices or exam rules containing assessment structure / pending calendar");
    }

    @Test
    @DisplayName("Multi-topic query retrieves distinct documents with non-concatenated individual paths")
    void testSearch_multipleRelevantDocumentsRetrievedWithoutConcatenation() {
        RetrievalResult result = retrievalService.search("attendance shortage condonation hostel night curfew", 4, 8.0);

        assertEquals(QueryScope.COLLEGE_GROUNDED, result.getScope());
        assertTrue(result.getMatches().size() >= 2, "Should retrieve multiple matching chunks across categories");

        for (ScoredKnowledgeChunk chunk : result.getMatches()) {
            assertNotNull(chunk.getSourcePath());
            assertTrue(chunk.getSourcePath().startsWith("docs/"), "Source path must be a clean standalone docs/ path");
            assertFalse(chunk.getSourcePath().contains(","), "Source path must not contain concatenated commas");
            assertFalse(chunk.getSourcePath().contains(";"), "Source path must not contain concatenated semicolons");
            assertFalse(chunk.getSourcePath().matches(".*\\.md.+"), "Source path must not have run-on concatenated paths");
        }
    }
}
