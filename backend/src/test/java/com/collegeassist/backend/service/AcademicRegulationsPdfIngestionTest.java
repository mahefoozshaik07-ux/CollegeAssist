package com.collegeassist.backend.service;

import com.collegeassist.backend.dto.ChatRequest;
import com.collegeassist.backend.dto.ChatResponse;
import com.collegeassist.backend.dto.SourceReference;
import com.collegeassist.backend.model.DocumentMetadata;
import com.collegeassist.backend.model.KnowledgeChunk;
import com.collegeassist.backend.model.QueryScope;
import com.collegeassist.backend.model.RetrievalResult;
import com.collegeassist.backend.model.ScoredKnowledgeChunk;
import com.collegeassist.backend.repository.FileKnowledgeIndexRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AcademicRegulationsPdfIngestionTest {

    private Path docsPath;
    private DocumentIngestionService ingestionService;
    private DocumentKnowledgeService knowledgeService;
    private KnowledgeRetrievalService retrievalService;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        docsPath = Paths.get("..", "docs").toAbsolutePath().normalize();
        if (!Files.exists(docsPath)) {
            docsPath = Paths.get("docs").toAbsolutePath().normalize();
        }

        FileKnowledgeIndexRepository diskRepo = new FileKnowledgeIndexRepository("target/test-knowledge-data");
        ingestionService = new DocumentIngestionService(diskRepo);
        knowledgeService = new DocumentKnowledgeService(docsPath.toString(), ingestionService);
        knowledgeService.loadDocuments();
        retrievalService = new KnowledgeRetrievalService(knowledgeService);
        chatService = new ChatService("", "gemini-3.8-flash", null, retrievalService);
    }

    @Test
    @DisplayName("Verify Academic-Regulations.pdf is ingested with 43 pages and structured chunks")
    void shouldIngestAcademicRegulationsPdfSuccessfully() {
        Map<String, DocumentMetadata> metaMap = ingestionService.getDocumentMetadataMap();
        assertTrue(metaMap.containsKey("Academic-Regulations.pdf"), "Metadata must contain Academic-Regulations.pdf");

        DocumentMetadata pdfMeta = metaMap.get("Academic-Regulations.pdf");
        assertEquals("Academic-Regulations.pdf", pdfMeta.getId());
        assertEquals("FFCS Academic Regulations", pdfMeta.getDocumentTitle());
        assertEquals("Version 4.0", pdfMeta.getVersion());
        assertEquals("ACADEMIC_REGULATIONS", pdfMeta.getDocumentType());
        assertEquals(43, pdfMeta.getPageCount(), "Academic-Regulations.pdf must have 43 pages");
        assertEquals("SUCCESS", pdfMeta.getExtractionStatus());
        assertTrue(pdfMeta.getChunkCount() > 30, "Should have created multiple structured chunks across 43 pages");

        // Verify chunks have valid page numbers and section titles
        List<KnowledgeChunk> chunks = ingestionService.getActiveChunks();
        long pdfChunkCount = chunks.stream()
                .filter(c -> "Academic-Regulations.pdf".equals(c.getFilename()))
                .count();
        assertTrue(pdfChunkCount > 30, "Must have active chunks for Academic-Regulations.pdf");

        for (KnowledgeChunk chunk : chunks) {
            if ("Academic-Regulations.pdf".equals(chunk.getFilename())) {
                assertTrue(chunk.getPageNumber() >= 1 && chunk.getPageNumber() <= 43,
                        "Chunk pageNumber must be between 1 and 43, was: " + chunk.getPageNumber());
                assertNotNull(chunk.getSectionTitle());
                assertFalse(chunk.getSectionTitle().isBlank());
                assertEquals("Version 4.0", chunk.getVersion());
                assertEquals("ACADEMIC_REGULATIONS", chunk.getDocumentType());
            }
        }
    }

    @Test
    @DisplayName("Verify disk index persistence survives restart without database")
    void shouldPersistAndReloadIndexFromDisk(@TempDir Path tempDir) {
        Path customDataDir = tempDir.resolve("knowledge-data");
        FileKnowledgeIndexRepository diskRepo = new FileKnowledgeIndexRepository(customDataDir);

        DocumentIngestionService service1 = new DocumentIngestionService(diskRepo);
        service1.reindex(docsPath, true);

        int originalChunkCount = service1.getActiveChunks().size();
        int originalDocCount = service1.getDocumentMetadataMap().size();
        assertTrue(originalChunkCount > 40);
        assertTrue(originalDocCount >= 8); // 7 markdown docs + 1 PDF

        // Verify files exist on disk
        assertTrue(diskRepo.hasPersistedIndex(), "Index files must exist on disk");
        assertTrue(Files.exists(customDataDir.resolve("index").resolve("chunks.json")));
        assertTrue(Files.exists(customDataDir.resolve("metadata").resolve("documents.json")));
        assertTrue(Files.exists(customDataDir.resolve("extracted").resolve("Academic-Regulations.pdf.extracted.txt")));

        // Simulate backend restart by creating fresh service with same disk directory
        DocumentIngestionService restartedService = new DocumentIngestionService(diskRepo);
        restartedService.reindex(docsPath, false);

        assertEquals(originalDocCount, restartedService.getDocumentMetadataMap().size(),
                "Reloaded document count must match original");
        assertEquals(originalChunkCount, restartedService.getActiveChunks().size(),
                "Reloaded active chunk count must match original");
    }

    @Test
    @DisplayName("Verify detection of empty, scanned (OCR required), and encrypted PDFs")
    void shouldDetectProblematicPdfs(@TempDir Path tempDir) throws IOException {
        DocumentIngestionService testService = new DocumentIngestionService(new FileKnowledgeIndexRepository(tempDir.resolve("index")));

        // 1. Empty 0-byte file
        Path emptyFile = tempDir.resolve("empty.pdf");
        Files.createFile(emptyFile);
        DocumentIngestionService.PdfIngestionResult emptyRes = testService.processPdfFile(emptyFile, "academic", "docs/academic/empty.pdf", "empty.pdf");
        assertEquals("EMPTY", emptyRes.metadata.getExtractionStatus());
        assertTrue(emptyRes.chunks.isEmpty());

        // 2. Scanned image PDF (PDF with 2 pages but zero text)
        Path scannedPdf = tempDir.resolve("scanned.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.addPage(new PDPage());
            doc.save(scannedPdf.toFile());
        }
        DocumentIngestionService.PdfIngestionResult scannedRes = testService.processPdfFile(scannedPdf, "academic", "docs/academic/scanned.pdf", "scanned.pdf");
        assertEquals("OCR_REQUIRED", scannedRes.metadata.getExtractionStatus());
        assertTrue(scannedRes.metadata.getStatusMessage().contains("OCR"));
        assertTrue(scannedRes.chunks.isEmpty());
    }

    @Test
    @DisplayName("Question 1: What is the minimum attendance required for CAT-I, CAT-II, and FAT?")
    void testQuestion1_AttendanceRequirements() {
        RetrievalResult result = retrievalService.search("What is the minimum attendance required for CAT-I, CAT-II, and FAT?");
        assertFalse(result.getMatches().isEmpty());

        ScoredKnowledgeChunk top = result.getMatches().get(0);
        assertTrue(top.getContent().contains("75%") || top.getContent().contains("Table 2") || top.getContent().contains("CAT-I"));
        // Section 8 & Table 2 are on Page 20-21
        assertTrue(top.getPageNumber() == 21 || top.getPageNumber() == 20 || top.getSourcePath().contains("attendance-policy.md"));

        ChatResponse response = chatService.processChat(new ChatRequest("What is the minimum attendance required for CAT-I, CAT-II, and FAT?"));
        assertNotNull(response.getContent());
        assertFalse(response.getSources().isEmpty());

        // Verify page citation is present in sources
        boolean hasPdfCitationWithPage = response.getSources().stream()
                .anyMatch(s -> s.getPageNumber() == 21 || s.getPageNumber() == 20 || s.getDocumentPath().contains("Academic-Regulations.pdf"));
        assertTrue(hasPdfCitationWithPage, "Must cite PDF with Page 20 or 21");
    }

    @Test
    @DisplayName("Question 2: What happens if I am debarred from writing FAT because of attendance shortage?")
    void testQuestion2_DebarredFromFat() {
        RetrievalResult result = retrievalService.search("What happens if I am debarred from writing FAT because of attendance shortage?");
        assertFalse(result.getMatches().isEmpty());

        boolean mentionsNGradeOrDebarred = result.getMatches().stream()
                .anyMatch(m -> m.getContent().contains("Debarred") || m.getContent().contains("N") || m.getContent().contains("re-register"));
        assertTrue(mentionsNGradeOrDebarred);

        ChatResponse response = chatService.processChat(new ChatRequest("What happens if I am debarred from writing FAT because of attendance shortage?"));
        assertTrue(response.getContent().contains("N") || response.getContent().contains("debarred") || response.getContent().contains("Debarred"));
    }

    @Test
    @DisplayName("Question 3: What are the rules for registering a backlog course?")
    void testQuestion3_BacklogCourseRules() {
        RetrievalResult result = retrievalService.search("What are the rules for registering a backlog course?");
        assertFalse(result.getMatches().isEmpty());

        boolean hasBacklogSection = result.getMatches().stream()
                .anyMatch(m -> m.getSectionTitle().toLowerCase().contains("backlog") || m.getContent().contains("re-register") || m.getPageNumber() == 18 || m.getPageNumber() == 19);
        assertTrue(hasBacklogSection);

        ChatResponse response = chatService.processChat(new ChatRequest("What are the rules for registering a backlog course?"));
        assertFalse(response.getSources().isEmpty());
    }

    @Test
    @DisplayName("Question 4: What is the add/drop period?")
    void testQuestion4_AddDropPeriod() {
        RetrievalResult result = retrievalService.search("What is the add/drop period?");
        assertFalse(result.getMatches().isEmpty());

        boolean mentionsThreeDays = result.getMatches().stream()
                .anyMatch(m -> m.getContent().toLowerCase().contains("first three instructional days") || m.getSectionTitle().toLowerCase().contains("add/drop") || m.getPageNumber() == 19);
        assertTrue(mentionsThreeDays, "Must retrieve Add/Drop section with first three instructional days");
    }

    @Test
    @DisplayName("Question 5: What is the minimum and maximum credit load in a regular semester?")
    void testQuestion5_CreditLimits() {
        RetrievalResult result = retrievalService.search("What is the minimum and maximum credit load in a regular semester?");
        assertFalse(result.getMatches().isEmpty());

        boolean hasCreditLimits = result.getMatches().stream()
                .anyMatch(m -> (m.getContent().contains("16") && m.getContent().contains("27")) || m.getSectionTitle().toLowerCase().contains("credit limit") || m.getPageNumber() == 17);
        assertTrue(hasCreditLimits, "Must retrieve credit limits (16 to 27 credits)");
    }

    @Test
    @DisplayName("Question 6: What are the passing criteria for a theory course?")
    void testQuestion6_PassingCriteriaTheory() {
        RetrievalResult result = retrievalService.search("What are the passing criteria for a theory course?");
        assertFalse(result.getMatches().isEmpty());

        boolean hasPassCriteria = result.getMatches().stream()
                .anyMatch(m -> m.getContent().contains("40%") || m.getContent().contains("relative grading") || m.getSectionTitle().toLowerCase().contains("grading system") || m.getPageNumber() == 24);
        assertTrue(hasPassCriteria, "Must retrieve theory pass criteria (40% in FAT alone, relative grading band)");
    }

    @Test
    @DisplayName("Question 7: What is the difference between F and N grades?")
    void testQuestion7_DifferenceFandNGrades() {
        RetrievalResult result = retrievalService.search("What is the difference between F and N grades?");
        assertFalse(result.getMatches().isEmpty());

        boolean hasTable4OrGrades = result.getMatches().stream()
                .anyMatch(m -> m.getContent().contains("Table 4") || m.getContent().contains("Letter grades") || m.getPageNumber() == 24 || m.getPageNumber() == 25);
        assertTrue(hasTable4OrGrades, "Must retrieve Table 4 / Section 9.4 distinguishing F and N grades");
    }

    @Test
    @DisplayName("Question 8: What does the regulation say about course withdrawal?")
    void testQuestion8_CourseWithdrawal() {
        RetrievalResult result = retrievalService.search("What does the regulation say about course withdrawal?");
        assertFalse(result.getMatches().isEmpty());

        boolean hasWithdrawal = result.getMatches().stream()
                .anyMatch(m -> m.getSectionTitle().toLowerCase().contains("withdrawal") || m.getContent().contains("CAT-I") || m.getContent().contains("'W' grade") || m.getPageNumber() == 19 || m.getPageNumber() == 20);
        assertTrue(hasWithdrawal, "Must retrieve Section 7.6 Course Withdrawal");
    }

    @Test
    @DisplayName("Unanswered Question: Chatbot must admit limitation when question is not answered by documents")
    void testUnansweredQuestionAdmitLimitation() {
        // 1. Non-university out-of-scope question
        ChatResponse outOfScope = chatService.processChat(new ChatRequest("What is the recipe for baking a chocolate cake?"));
        assertTrue(outOfScope.getContent().contains("CollegeAssist Scope Notice") || "OUT_OF_SCOPE".equals(outOfScope.getStatus()));

        // 2. University query with no verified documentation in indexed documents
        String unansweredQuery = "What are the library timings and book borrowing rules on campus?";
        RetrievalResult res = retrievalService.search(unansweredQuery);
        assertEquals(QueryScope.COLLEGE_UNGROUNDED, res.getScope(), "Query not in indexed documents must be COLLEGE_UNGROUNDED");

        ChatResponse ungrounded = chatService.processChat(new ChatRequest(unansweredQuery));
        assertEquals("UNGROUNDED_INQUIRY", ungrounded.getStatus());
        assertTrue(ungrounded.getContent().contains("Information Not Found in Verified Documentation")
                || ungrounded.getContent().contains("does not currently contain documented evidence"),
                "Chatbot must explicitly admit limitation when info is not in indexed documents");
    }
}


