package com.collegeassist.backend.evaluation;

import com.collegeassist.backend.dto.ChatRequest;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.SourceReference;
import com.collegeassist.backend.model.QueryScope;
import com.collegeassist.backend.model.RetrievalResult;
import com.collegeassist.backend.model.ScoredKnowledgeChunk;
import com.collegeassist.backend.service.ChatService;
import com.collegeassist.backend.service.DocumentKnowledgeService;
import com.collegeassist.backend.service.HybridRetrievalService;
import com.collegeassist.backend.service.KnowledgeRetrievalService;
import com.collegeassist.backend.service.QuestionUnderstandingService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive University Evaluation & Benchmark Suite for CollegeAssist.
 *
 * Evaluates the full RAG and Question Understanding pipeline across 12 distinct intents:
 * 1. Attendance
 * 2. Examinations & Grading
 * 3. FFCS (Course Registration)
 * 4. Academic Curriculum & Policies
 * 5. Tuition Fees & Finance
 * 6. Scholarships & Concessions
 * 7. Hostels & Residential Rules
 * 8. Campus Transport & Shuttles
 * 9. Placements & CDC
 * 10. Notices & Circulars
 * 11. Campus Facilities & Library
 * 12. Follow-Up Contextual Inquiries
 *
 * Covers required question variations:
 * - Normal phrasing
 * - Misspellings and typos (Damerau-Levenshtein correction)
 * - Informal / abbreviated campus phrasing
 * - Paraphrased inquiries
 * - Numerical calculations (attendance benchmark & hostel refund)
 * - Ambiguous / underspecified inquiries
 * - Unrelated out-of-scope inquiries
 *
 * Measures:
 * - Scope Classification Accuracy
 * - Retrieval Recall (Top-K ground truth coverage)
 * - Source Citation Validity & Disk Correctness
 * - Semantic Equivalence Stability
 * - Latency (mean & p95)
 * - Failure Rate
 */
public class ComprehensiveUniversityEvaluationTest {

    private static final Logger logger = LoggerFactory.getLogger(ComprehensiveUniversityEvaluationTest.class);

    private static DocumentKnowledgeService documentKnowledgeService;
    private static HybridRetrievalService hybridRetrievalService;
    private static KnowledgeRetrievalService retrievalService;
    private static QuestionUnderstandingService questionUnderstandingService;
    private static ChatService chatService;

    public record EvalTestCase(
            String intent,
            String variationType,
            String query,
            QueryScope expectedScope,
            String expectedDocPath,
            boolean expectCalculation,
            boolean expectClarification
    ) {}

    private static final List<EvalTestCase> BENCHMARK_DATASET = List.of(
            // --- 1. Attendance ---
            new EvalTestCase("Attendance", "Normal", "What is the minimum attendance required for FAT examination?", QueryScope.COLLEGE_GROUNDED, "docs/attendance/attendance-policy.md", false, false),
            new EvalTestCase("Attendance", "Typo", "Wat is the minium atendence requirment for final exams?", QueryScope.COLLEGE_GROUNDED, "docs/attendance/attendance-policy.md", false, false),
            new EvalTestCase("Attendance", "Abbreviated", "Can I bunk classes if my attendance is 80%?", QueryScope.COLLEGE_GROUNDED, "docs/attendance/attendance-policy.md", false, false),
            new EvalTestCase("Attendance", "Paraphrase", "What percentage of lectures must a student attend to sit for FAT?", QueryScope.COLLEGE_GROUNDED, "docs/attendance/attendance-policy.md", false, false),
            new EvalTestCase("Attendance", "Calculation", "How many classes can I miss if there are 40 classes in total?", QueryScope.COLLEGE_GROUNDED, "docs/attendance/attendance-policy.md", true, false),
            new EvalTestCase("Attendance", "Calculation", "How many classes can I miss if there are 60 classes in total?", QueryScope.COLLEGE_GROUNDED, "docs/attendance/attendance-policy.md", true, false),

            // --- 2. Examinations & Grading ---
            new EvalTestCase("Exams", "Normal", "What are the rules and guidelines for FAT examinations?", QueryScope.COLLEGE_GROUNDED, "docs/exam-rules/examination-guidelines.md", false, false),
            new EvalTestCase("Exams", "Typo", "What is the malpractise rule during examnations?", QueryScope.COLLEGE_GROUNDED, "docs/exam-rules/examination-guidelines.md", false, false),
            new EvalTestCase("Exams", "Abbreviated", "What happens if a student gets caught with a phone during CAT-1?", QueryScope.COLLEGE_GROUNDED, "docs/exam-rules/examination-guidelines.md", false, false),
            new EvalTestCase("Exams", "Paraphrase", "What are the penalties for cheating during semester assessments?", QueryScope.COLLEGE_GROUNDED, "docs/exam-rules/examination-guidelines.md", false, false),

            // --- 3. FFCS (Course Registration) ---
            new EvalTestCase("FFCS", "Normal", "What is the minimum and maximum credit limit per semester under FFCS?", QueryScope.COLLEGE_GROUNDED, "docs/college-rules/code-of-conduct-and-ffcs.md", false, false),
            new EvalTestCase("FFCS", "Typo", "How many credts can I regist during FFCS enrolmnt?", QueryScope.COLLEGE_GROUNDED, "docs/college-rules/code-of-conduct-and-ffcs.md", false, false),
            new EvalTestCase("FFCS", "Abbreviated", "What is the add drop course procedure during ffcs registration?", QueryScope.COLLEGE_GROUNDED, "docs/college-rules/code-of-conduct-and-ffcs.md", false, false),
            new EvalTestCase("FFCS", "Paraphrase", "How does slot and timetable selection work for students?", QueryScope.COLLEGE_GROUNDED, "docs/college-rules/code-of-conduct-and-ffcs.md", false, false),

            // --- 4. Academic Curriculum & Coursework ---
            new EvalTestCase("Academics", "Normal", "What is the zero tolerance policy on ragging and reporting hotline?", QueryScope.COLLEGE_GROUNDED, "docs/college-rules/code-of-conduct-and-ffcs.md", false, false),
            new EvalTestCase("Academics", "Normal", "What is the 10-point grade point scale for S grade and A grade?", QueryScope.COLLEGE_GROUNDED, "docs/exam-rules/examination-guidelines.md", false, false),
            new EvalTestCase("Academics", "Coursework", "Explain recursion in Java.", QueryScope.COLLEGE_UNGROUNDED, null, false, false),
            new EvalTestCase("Academics", "Coursework", "How does quicksort algorithm work in data structures?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),

            // --- 5. Tuition Fees & Finance ---
            new EvalTestCase("Fees", "Normal", "What is the tuition fee payment deadline and late fine policy?", QueryScope.COLLEGE_GROUNDED, "docs/fees/tuition-and-hostel-fees.md", false, false),
            new EvalTestCase("Fees", "Typo", "What is the contact emial for tution fee queries and finance office?", QueryScope.COLLEGE_GROUNDED, "docs/fees/tuition-and-hostel-fees.md", false, false),
            new EvalTestCase("Fees", "Abbreviated", "What is the fee refund policy if a student withdraws admission?", QueryScope.COLLEGE_GROUNDED, "docs/fees/tuition-and-hostel-fees.md", false, false),

            // --- 6. Scholarships & Concessions ---
            new EvalTestCase("Scholarships", "Normal", "What are the rules regarding disciplinary forfeiture of scholarships?", QueryScope.COLLEGE_GROUNDED, "docs/college-rules/code-of-conduct-and-ffcs.md", false, false),
            new EvalTestCase("Scholarships", "UngroundedScheme", "How does the GV Merit scholarshp award work for top rankers?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),
            new EvalTestCase("Scholarships", "UngroundedWaiver", "Is there a tuition fee waiver or concession for top rankers?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),

            // --- 7. Hostels & Residential Rules ---
            new EvalTestCase("Hostels", "Normal", "What is the hostel in-time curfew for students in men and ladies hostels?", QueryScope.COLLEGE_GROUNDED, "docs/hostel/hostel-regulations.md", false, false),
            new EvalTestCase("Hostels", "Typo", "What are the hostl rules regarding electrc appliances and cooking?", QueryScope.COLLEGE_GROUNDED, "docs/hostel/hostel-regulations.md", false, false),
            new EvalTestCase("Hostels", "Abbreviated", "What is the MH intime on weekends?", QueryScope.COLLEGE_GROUNDED, "docs/hostel/hostel-regulations.md", false, false),
            new EvalTestCase("Hostels", "Calculation", "If I vacate the hostel after 2 months, how much refund do I receive?", QueryScope.COLLEGE_GROUNDED, "docs/hostel/hostel-regulations.md", true, false),

            // --- 8. Campus Transport & Shuttles (Unsupported Schedule / Routes) ---
            new EvalTestCase("Transport", "UngroundedSchedule", "What time does the college bus leave tomorrow morning?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),
            new EvalTestCase("Transport", "UngroundedRoute", "Which route does the campus shuttle bus take to Vijayawada?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),

            // --- 9. Placements & CDC ---
            new EvalTestCase("Placements", "Normal", "What are the placement eligibility criteria at Career Development Centre?", QueryScope.COLLEGE_GROUNDED, "docs/placements/career-development-centre.md", false, false),
            new EvalTestCase("Placements", "Typo", "What is the CGPA requrment for super dream placments?", QueryScope.COLLEGE_GROUNDED, "docs/placements/career-development-centre.md", false, false),
            new EvalTestCase("Placements", "Abbreviated", "Can a student accept two PPO job offers through CDC?", QueryScope.COLLEGE_GROUNDED, "docs/placements/career-development-centre.md", false, false),

            // --- 10. Notices & Circulars ---
            new EvalTestCase("Notices", "Normal", "Where are official university notices, circulars, and announcements published?", QueryScope.COLLEGE_GROUNDED, "docs/notices/campus-circulars-framework.md", false, false),
            new EvalTestCase("Notices", "Paraphrase", "How do students receive authoritative circulars regarding holiday schedules?", QueryScope.COLLEGE_GROUNDED, "docs/notices/campus-circulars-framework.md", false, false),

            // --- 11. Campus Facilities & Library (Unsupported Hours) ---
            new EvalTestCase("Facilities", "UngroundedLibrary", "What are the library timings and book borrowing rules on campus?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),
            new EvalTestCase("Facilities", "UngroundedWeekend", "What are the libary facilty workng hours on weekends?", QueryScope.COLLEGE_UNGROUNDED, null, false, false),

            // --- 12. Ambiguous & Underspecified Queries ---
            new EvalTestCase("Ambiguous", "Underspecified", "Can I take leave?", QueryScope.COLLEGE_UNGROUNDED, null, false, true),
            new EvalTestCase("Ambiguous", "Underspecified", "How much is the fee?", QueryScope.COLLEGE_GROUNDED, "docs/fees/tuition-and-hostel-fees.md", false, true),

            // --- 13. Unrelated Out-of-Scope Queries ---
            new EvalTestCase("OutOfScope", "Unrelated", "What is the capital of France?", QueryScope.OUT_OF_SCOPE, null, false, false),
            new EvalTestCase("OutOfScope", "Unrelated", "How do I build a crypto trading bot with Binance API?", QueryScope.OUT_OF_SCOPE, null, false, false),
            new EvalTestCase("OutOfScope", "Unrelated", "Give me a recipe for chocolate cake.", QueryScope.OUT_OF_SCOPE, null, false, false),
            new EvalTestCase("OutOfScope", "Unrelated", "Who won the FIFA World Cup in 2022?", QueryScope.OUT_OF_SCOPE, null, false, false)
    );

    @BeforeAll
    static void setUp() {
        documentKnowledgeService = new DocumentKnowledgeService("docs");
        documentKnowledgeService.loadDocuments();
        assertTrue(documentKnowledgeService.isLoaded(), "Knowledge base must be loaded");
        assertEquals(8, documentKnowledgeService.getDocumentCount(), "Must load 8 institutional documents");

        questionUnderstandingService = new QuestionUnderstandingService();
        retrievalService = new KnowledgeRetrievalService(documentKnowledgeService, 10.0, 3);
        chatService = new ChatService("", "gemini-3.8-flash", null, retrievalService);
    }

    @Test
    @DisplayName("Comprehensive Benchmark: Measure Scope Accuracy, Recall, Latency, and Failure Rate")
    void testComprehensiveEvaluationSuite() {
        int totalTests = BENCHMARK_DATASET.size();
        int correctScopeCount = 0;
        int correctRecallCount = 0;
        int validCitationCount = 0;
        int failureCount = 0;
        List<Long> latencies = new ArrayList<>();

        System.out.println("=========================================================================================");
        System.out.println("COLLEGEASSIST INSTITUTIONAL RETRIEVAL & SCOPE EVALUATION BENCHMARK");
        System.out.println("=========================================================================================");

        for (EvalTestCase tc : BENCHMARK_DATASET) {
            long startTime = System.nanoTime();
            try {
                RetrievalResult result = retrievalService.search(tc.query());
                long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
                latencies.add(durationMs);

                // 1. Evaluate Scope Classification Accuracy
                boolean scopeMatch = (result.getScope() == tc.expectedScope());
                if (scopeMatch) {
                    correctScopeCount++;
                } else {
                    logger.warn("Scope mismatch for [{} - {}]: Query='{}', Expected={}, Actual={}",
                            tc.intent(), tc.variationType(), tc.query(), tc.expectedScope(), result.getScope());
                }

                // 2. Evaluate Recall for Grounded Queries
                if (tc.expectedDocPath() != null) {
                    boolean foundTargetDoc = result.getMatches().stream()
                            .anyMatch(chunk -> chunk.getSourcePath().equals(tc.expectedDocPath()));
                    if (foundTargetDoc) {
                        correctRecallCount++;
                    } else {
                        logger.warn("Recall failure for [{} - {}]: Query='{}', Expected Doc='{}', Top Matches={}",
                                tc.intent(), tc.variationType(), tc.query(), tc.expectedDocPath(),
                                result.getMatches().stream().map(ScoredKnowledgeChunk::getSourcePath).toList());
                    }
                }

                // 3. Evaluate Citation Validity & Physical File Integrity
                if (result.getScope() == QueryScope.COLLEGE_GROUNDED && !result.getMatches().isEmpty()) {
                    for (ScoredKnowledgeChunk match : result.getMatches()) {
                        Path docPath = Path.of(match.getSourcePath());
                        if (!Files.exists(docPath)) {
                            docPath = Path.of("..", match.getSourcePath());
                        }
                        if (Files.exists(docPath)) {
                            validCitationCount++;
                        }
                    }
                }

            } catch (Exception e) {
                failureCount++;
                logger.error("Exception during benchmark query '{}': {}", tc.query(), e.getMessage(), e);
            }
        }

        // Compute Metrics
        double scopeAccuracy = (double) correctScopeCount / totalTests * 100.0;
        long groundedTestsWithDoc = BENCHMARK_DATASET.stream().filter(tc -> tc.expectedDocPath() != null).count();
        double recallAccuracy = (double) correctRecallCount / groundedTestsWithDoc * 100.0;
        double failureRate = (double) failureCount / totalTests * 100.0;

        Collections.sort(latencies);
        double meanLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p95Latency = latencies.isEmpty() ? 0 : latencies.get((int) Math.ceil(latencies.size() * 0.95) - 1);

        System.out.printf(Locale.ENGLISH, "Total Test Inquiries Evaluated: %d%n", totalTests);
        System.out.printf(Locale.ENGLISH, "Scope Classification Accuracy:  %.2f%% (%d/%d)%n", scopeAccuracy, correctScopeCount, totalTests);
        System.out.printf(Locale.ENGLISH, "Retrieval Recall Accuracy:      %.2f%% (%d/%d)%n", recallAccuracy, correctRecallCount, groundedTestsWithDoc);
        System.out.printf(Locale.ENGLISH, "Valid Citation File Citations:  %d verified citations%n", validCitationCount);
        System.out.printf(Locale.ENGLISH, "Mean Retrieval Latency:         %.2f ms%n", meanLatency);
        System.out.printf(Locale.ENGLISH, "P95 Retrieval Latency:          %d ms%n", p95Latency);
        System.out.printf(Locale.ENGLISH, "Failure Rate:                   %.2f%%%n", failureRate);
        System.out.println("=========================================================================================");

        // Quality Gate Assertions
        assertTrue(scopeAccuracy >= 95.0, "Scope classification accuracy must be >= 95%, actual: " + scopeAccuracy);
        assertTrue(recallAccuracy >= 90.0, "Retrieval recall must be >= 90%, actual: " + recallAccuracy);
        assertEquals(0.0, failureRate, "Failure rate must be 0.0%");
    }

    @Test
    @DisplayName("Semantic Equivalence Test: Equivalent Phrasings Produce Identical Scope Decisions")
    void testSemanticEquivalenceStability() {
        // Attendance Equivalents
        List<String> attendanceQueries = List.of(
                "What is the minimum attendance required for FAT examination?",
                "Wat is minium atendence for final exam?",
                "Can I bunk lectures if my attendance is 80%?",
                "What percentage of lectures must a student attend to sit for FAT?"
        );

        for (String q : attendanceQueries) {
            RetrievalResult res = retrievalService.search(q);
            assertEquals(QueryScope.COLLEGE_GROUNDED, res.getScope(), "Must route to COLLEGE_GROUNDED for: " + q);
            boolean hasAttendanceDoc = res.getMatches().stream()
                    .anyMatch(c -> c.getSourcePath().equals("docs/attendance/attendance-policy.md"));
            assertTrue(hasAttendanceDoc, "Must retrieve attendance policy for: " + q);
        }

        // Examination Malpractice Equivalents
        List<String> examQueries = List.of(
                "What are the rules and guidelines for FAT examinations?",
                "What is the malpractise rule during examnations?",
                "What are the penalties for cheating during semester assessments?"
        );

        for (String q : examQueries) {
            RetrievalResult res = retrievalService.search(q);
            assertEquals(QueryScope.COLLEGE_GROUNDED, res.getScope(), "Must route to COLLEGE_GROUNDED for: " + q);
            boolean hasExamDoc = res.getMatches().stream()
                    .anyMatch(c -> c.getSourcePath().equals("docs/exam-rules/examination-guidelines.md"));
            assertTrue(hasExamDoc, "Must retrieve examination guidelines for: " + q);
        }
    }

    @Test
    @DisplayName("Numerical Calculation Verification: Attendance Benchmark & Hostel Deduction Rules")
    void testNumericalCalculations() {
        // 1. Attendance Calculation: 40 classes
        ChatResponse resp40 = chatService.processChat(new ChatRequest("How many classes can I miss if there are 40 classes in total?", "calc-conv-1"));
        assertNotNull(resp40.getContent());
        assertTrue(resp40.getContent().contains("30") && resp40.getContent().toLowerCase().contains("required"),
                "40 classes: 30 required: " + resp40.getContent());
        assertTrue(resp40.getContent().contains("10") && resp40.getContent().toLowerCase().contains("absences"),
                "40 classes: 10 max absences: " + resp40.getContent());

        // 2. Attendance Calculation: 60 classes
        ChatResponse resp60 = chatService.processChat(new ChatRequest("How many classes can I miss if there are 60 classes in total?", "calc-conv-2"));
        assertNotNull(resp60.getContent());
        assertTrue(resp60.getContent().contains("45") && resp60.getContent().toLowerCase().contains("required"),
                "60 classes: 45 required: " + resp60.getContent());
        assertTrue(resp60.getContent().contains("15") && resp60.getContent().toLowerCase().contains("absences"),
                "60 classes: 15 max absences: " + resp60.getContent());

        // 3. Hostel Refund Calculation: 2 months
        ChatResponse respHostel = chatService.processChat(new ChatRequest("If I vacate the hostel after 2 months, how much refund do I receive?", "calc-conv-3"));
        assertNotNull(respHostel.getContent());
        assertTrue(respHostel.getContent().contains("20%"), "Must state 20% deduction for 2 months: " + respHostel.getContent());
        assertTrue(respHostel.getContent().contains("80%"), "Must state 80% refund: " + respHostel.getContent());
    }

    @Test
    @DisplayName("Multi-Turn Follow-Up Resolution: Follow-up pronouns resolved through context")
    void testContextualFollowUp() {
        String convId = "followup-test-" + UUID.randomUUID();

        // Turn 1: Discuss medical condonation
        QuestionUnderstandingService qus = new QuestionUnderstandingService();
        QuestionUnderstandingService.ProcessedQuestion turn1 = qus.processQuestion("What are the medical condonation rules?", convId);
        assertFalse(turn1.isFollowUp());

        // Turn 2: Follow-up question with pronoun
        QuestionUnderstandingService.ProcessedQuestion turn2 = qus.processQuestion("Who approves it?", convId);
        assertTrue(turn2.isFollowUp(), "Must be detected as follow-up question");
    }

    @Test
    @DisplayName("Ambiguity Clarification Generator: Concise clarification requested for underspecified questions")
    void testAmbiguityClarification() {
        QuestionUnderstandingService qus = new QuestionUnderstandingService();

        QuestionUnderstandingService.ProcessedQuestion pqLeave = qus.processQuestion("Can I take leave?", "conv-ambig-1");
        assertTrue(pqLeave.isNeedsClarification(), "Underspecified leave inquiry must require clarification");
        assertNotNull(pqLeave.getClarificationPrompt());
        assertTrue(pqLeave.getClarificationPrompt().toLowerCase().contains("hostel")
                || pqLeave.getClarificationPrompt().toLowerCase().contains("leave"),
                "Prompt must be contextually helpful: " + pqLeave.getClarificationPrompt());

        QuestionUnderstandingService.ProcessedQuestion pqFee = qus.processQuestion("How much is the fee?", "conv-ambig-2");
        assertTrue(pqFee.isNeedsClarification(), "Underspecified fee inquiry must require clarification");
        assertNotNull(pqFee.getClarificationPrompt());
    }
}
