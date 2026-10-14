package com.collegeassist.backend.service;

import com.collegeassist.backend.model.KnowledgeChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentKnowledgeServiceTest {

    @Test
    @DisplayName("Splits standard document with ## headings and preserves preamble")
    void testSplitIntoChunks_standardH2Document() {
        DocumentKnowledgeService service = new DocumentKnowledgeService();

        String markdown = """
                # VIT-AP University: Attendance Regulations
                
                > **Category:** Attendance
                > **Status:** Verified
                
                ---
                
                ## 1. Attendance Calculation
                Attendance is tracked continuously on VTOP.
                
                ## 2. Minimum Threshold
                Students must maintain at least 75% attendance.
                
                ### Medical Note
                Medical condonation requires submission within 3 days.
                
                ## 3. Proctor Role
                Consult your Proctor for issues.
                """;

        List<KnowledgeChunk> chunks = service.splitIntoChunks(
                markdown,
                "attendance",
                "docs/attendance/attendance-policy.md",
                "attendance-policy.md",
                "VIT-AP University: Attendance Regulations"
        );

        // Expect 4 chunks: Document Overview (preamble), Section 1, Section 2, Section 3
        assertEquals(4, chunks.size(), "Should produce 4 distinct chunks including preamble");

        KnowledgeChunk preamble = chunks.get(0);
        assertEquals("Document Overview", preamble.getSectionTitle());
        assertEquals("attendance", preamble.getCategory());
        assertEquals("docs/attendance/attendance-policy.md", preamble.getSourcePath());
        assertEquals("attendance-policy.md", preamble.getFilename());
        assertEquals("VIT-AP University: Attendance Regulations", preamble.getDocumentTitle());
        assertTrue(preamble.getContent().contains("Verified"), "Actual preamble content was: [" + preamble.getContent() + "]");
        assertTrue(preamble.getWordCount() > 0);
        assertTrue(preamble.getCharCount() > 0);

        KnowledgeChunk sec1 = chunks.get(1);
        assertEquals("1. Attendance Calculation", sec1.getSectionTitle());
        assertTrue(sec1.getContent().contains("VTOP"));

        KnowledgeChunk sec2 = chunks.get(2);
        assertEquals("2. Minimum Threshold", sec2.getSectionTitle());
        assertTrue(sec2.getContent().contains("75%"));
        assertTrue(sec2.getContent().contains("Medical Note"), "Sub-headings (###) should remain part of the parent ## chunk");

        KnowledgeChunk sec3 = chunks.get(3);
        assertEquals("3. Proctor Role", sec3.getSectionTitle());
        assertTrue(sec3.getContent().contains("Proctor"));
    }

    @Test
    @DisplayName("Splits document with only ### headings when ## is absent")
    void testSplitIntoChunks_h3Fallback() {
        DocumentKnowledgeService service = new DocumentKnowledgeService();

        String markdown = """
                ### First Topic
                Details on first topic.
                
                ### Second Topic
                Details on second topic.
                """;

        List<KnowledgeChunk> chunks = service.splitIntoChunks(
                markdown, "general", "docs/test.md", "test.md", "Test Doc");

        assertEquals(2, chunks.size());
        assertEquals("First Topic", chunks.get(0).getSectionTitle());
        assertEquals("Second Topic", chunks.get(1).getSectionTitle());
    }

    @Test
    @DisplayName("Treats un-sectioned document as single general chunk")
    void testSplitIntoChunks_noHeadings() {
        DocumentKnowledgeService service = new DocumentKnowledgeService();

        String markdown = "This is a simple plain text announcement without any markdown headings.";
        List<KnowledgeChunk> chunks = service.splitIntoChunks(
                markdown, "notices", "docs/notice.md", "notice.md", "Notice");

        assertEquals(1, chunks.size());
        assertEquals("General", chunks.get(0).getSectionTitle());
        assertEquals(markdown, chunks.get(0).getContent());
    }

    @Test
    @DisplayName("Returns empty chunk list for blank or whitespace-only content")
    void testProcessEmptyFile(@TempDir Path tempDir) throws IOException {
        DocumentKnowledgeService service = new DocumentKnowledgeService(tempDir);

        Path emptyFile = tempDir.resolve("empty.md");
        Files.writeString(emptyFile, "   \n\n  ");

        List<KnowledgeChunk> chunks = service.processMarkdownFile(tempDir, emptyFile);
        assertTrue(chunks.isEmpty(), "Blank document should produce zero chunks");
    }

    @Test
    @DisplayName("Handles non-existent directory gracefully without exception")
    void testMissingDirectory_handlesGracefully() {
        Path nonExistent = Path.of("non-existent-directory-xyz-12345");
        DocumentKnowledgeService service = new DocumentKnowledgeService(nonExistent);
        service.loadDocuments();

        assertTrue(service.isLoaded());
        assertEquals(0, service.getDocumentCount());
        assertEquals(0, service.getChunkCount());
        assertTrue(service.getAllChunks().isEmpty());
    }

    @Test
    @DisplayName("Loads and indexes multiple documents across subdirectories in a directory tree")
    void testLoadDocuments_withTemporaryDirectory(@TempDir Path tempDir) throws IOException {
        Path attendanceDir = Files.createDirectory(tempDir.resolve("attendance"));
        Path feesDir = Files.createDirectory(tempDir.resolve("fees"));

        Files.writeString(attendanceDir.resolve("attendance.md"), """
                # Attendance Rules
                ## Rule 1
                Rule 1 details.
                ## Rule 2
                Rule 2 details.
                """);

        Files.writeString(feesDir.resolve("tuition.md"), """
                # Fee Guidelines
                ## Semester Fees
                Payment instructions.
                """);

        // Non-markdown file to ensure filter safety
        Files.writeString(tempDir.resolve(".gitkeep"), "placeholder");
        Files.writeString(tempDir.resolve("notes.txt"), "ignored text");

        DocumentKnowledgeService service = new DocumentKnowledgeService(tempDir);
        service.loadDocuments();

        assertEquals(2, service.getDocumentCount(), "Should only load .md files");
        assertTrue(service.getChunkCount() >= 3);

        Map<String, Integer> categoryCounts = service.getChunkCountByCategory();
        assertTrue(categoryCounts.containsKey("attendance"));
        assertTrue(categoryCounts.containsKey("fees"));
        assertFalse(categoryCounts.containsKey(".gitkeep"));

        List<KnowledgeChunk> attendanceChunks = service.getChunksByCategory("attendance");
        assertFalse(attendanceChunks.isEmpty());
        assertEquals("attendance", attendanceChunks.get(0).getCategory());
    }

    @Test
    @DisplayName("Verifies live project knowledge-base documents are loaded and indexed correctly")
    void testLiveDocsDirectory_loadsSevenOfficialFiles() {
        DocumentKnowledgeService service = new DocumentKnowledgeService("docs");
        service.loadDocuments();

        assertTrue(service.isLoaded(), "Service should be initialized");
        assertEquals(8, service.getDocumentCount(), "Should discover all 8 category documents in docs/");
        assertTrue(service.getChunkCount() > 20, "Should generate chunks across all documents (expected > 20, got " + service.getChunkCount() + ")");

        List<String> categories = service.getLoadedCategories();
        assertThat(categories, hasItems("attendance", "college-rules", "exam-rules", "fees", "hostel", "notices", "placements", "academic"));

        Map<String, Integer> perDocCounts = service.getChunkCountByDocument();
        assertTrue(perDocCounts.containsKey("attendance-policy.md"));
        assertTrue(perDocCounts.containsKey("code-of-conduct-and-ffcs.md"));
        assertTrue(perDocCounts.containsKey("examination-guidelines.md"));
        assertTrue(perDocCounts.containsKey("tuition-and-hostel-fees.md"));
        assertTrue(perDocCounts.containsKey("hostel-regulations.md"));
        assertTrue(perDocCounts.containsKey("career-development-centre.md"));
        assertTrue(perDocCounts.containsKey("campus-circulars-framework.md"));
        assertTrue(perDocCounts.containsKey("Academic-Regulations.pdf"));

        // Verify metadata integrity on loaded chunks
        for (KnowledgeChunk chunk : service.getAllChunks()) {
            assertNotNull(chunk.getId());
            assertFalse(chunk.getId().isBlank());
            assertNotNull(chunk.getCategory());
            assertFalse(chunk.getCategory().isBlank());
            assertNotNull(chunk.getSourcePath());
            assertTrue(chunk.getSourcePath().startsWith("docs/"));
            assertNotNull(chunk.getSectionTitle());
            assertFalse(chunk.getSectionTitle().isBlank());
            assertNotNull(chunk.getContent());
            assertFalse(chunk.getContent().isBlank());
            assertTrue(chunk.getWordCount() > 0);
            assertTrue(chunk.getCharCount() > 0);
        }
    }
}
