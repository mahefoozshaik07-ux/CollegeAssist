package com.collegeassist.backend.service;

import com.collegeassist.backend.model.DocumentMetadata;
import com.collegeassist.backend.model.KnowledgeChunk;
import com.collegeassist.backend.repository.FileKnowledgeIndexRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Institutional document ingestion pipeline supporting Markdown and PDF formats.
 * Extracts text, headings, sections, and page numbers; detects empty, scanned (OCR required),
 * or encrypted files; executes SHA-256 deduplication and change detection; and persists
 * the resulting index to disk files without requiring any database.
 */
@Service
public class DocumentIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern META_SOURCE_URL = Pattern.compile("(?i)Official Sources?:\\s*\\[.*?\\]\\((https?://[^\\s)]+)\\)");
    private static final Pattern META_DOC_TYPE = Pattern.compile("(?i)Document Type:\\s*([A-Za-z_-]+)");
    private static final Pattern META_PUB_DATE = Pattern.compile("(?i)Publication Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern META_EFF_DATE = Pattern.compile("(?i)Effective Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern META_RET_DATE = Pattern.compile("(?i)Last Checked:\\s*(\\d{4}-\\d{2}-\\d{2})|Retrieval Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern META_VERSION = Pattern.compile("(?i)Version:\\s*([0-9a-zA-Z._-]+)");
    private static final Pattern META_SUPERSEDES = Pattern.compile("(?i)Supersedes?:\\s*([0-9a-zA-Z._-]+)");

    // Patterns for PDF heading and section detection
    private static final Pattern PDF_SECTION_HEADING = Pattern.compile("^(?:(\\d+(?:\\.\\d+)*\\.?)|(?:Section|Table|Chapter|Article|Annexure)\\s*\\d*\\.?)\\s+([A-Z0-9].*)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_TABLE_HEADING = Pattern.compile("^(Table\\s*[-:]?\\s*\\d+\\s*[:\\.]?\\s*.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_RUNNING_FOOTER = Pattern.compile("(?i)^FFCS Academic Regulations\\s*-\\s*Version\\s*4\\.0.*$");

    private final FileKnowledgeIndexRepository diskRepository;
    private final Map<String, DocumentMetadata> documentMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, String> checksumToDocId = new ConcurrentHashMap<>();
    private final List<KnowledgeChunk> activeChunks = new ArrayList<>();
    private final List<KnowledgeChunk> allChunks = new ArrayList<>();

    public DocumentIngestionService() {
        this(new FileKnowledgeIndexRepository("data/knowledge"));
    }

    @Autowired
    public DocumentIngestionService(FileKnowledgeIndexRepository diskRepository) {
        this.diskRepository = diskRepository != null ? diskRepository : new FileKnowledgeIndexRepository("data/knowledge");
    }

    public synchronized void reingestAll(Path rootDir) {
        reindex(rootDir, false);
    }

    public synchronized void reindex(Path rootDir, boolean forceReingest) {
        if (rootDir == null || !Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            logger.warn("DocumentIngestionService: Docs directory '{}' is invalid or does not exist.", rootDir);
            return;
        }

        List<Path> files = scanSupportedFiles(rootDir);
        logger.info("DocumentIngestionService: Found {} candidate document(s) in '{}'", files.size(), rootDir);

        // Check if persisted index on disk can be loaded
        if (!forceReingest && diskRepository != null && diskRepository.hasPersistedIndex()) {
            Map<String, DocumentMetadata> persistedMeta = diskRepository.loadMetadataMap();
            List<KnowledgeChunk> persistedChunks = diskRepository.loadChunks();

            boolean allMatch = !persistedMeta.isEmpty() && !persistedChunks.isEmpty();
            Map<String, String> currentChecksums = new HashMap<>();

            for (Path file : files) {
                String checksum = computeSha256(file);
                String filename = file.getFileName().toString();
                currentChecksums.put(filename, checksum);

                DocumentMetadata meta = persistedMeta.get(filename);
                if (meta == null || !checksum.equals(meta.getSha256Checksum())) {
                    allMatch = false;
                    logger.info("Document '{}' is new or modified; disk index requires update.", filename);
                    break;
                }
            }

            // Also check if any deleted documents exist in persisted index
            if (allMatch && persistedMeta.size() == files.size()) {
                documentMetadataMap.clear();
                checksumToDocId.clear();
                activeChunks.clear();
                allChunks.clear();

                documentMetadataMap.putAll(persistedMeta);
                for (Map.Entry<String, DocumentMetadata> e : persistedMeta.entrySet()) {
                    checksumToDocId.put(e.getValue().getSha256Checksum(), e.getKey());
                }
                allChunks.addAll(persistedChunks);
                for (KnowledgeChunk chunk : allChunks) {
                    DocumentMetadata meta = documentMetadataMap.get(chunk.getFilename());
                    if (meta == null || !meta.isOutdated()) {
                        activeChunks.add(chunk);
                    }
                }
                logger.info("DocumentIngestionService: Loaded persisted index from disk across {} document(s) and {} active chunk(s) (survived restart).",
                        documentMetadataMap.size(), activeChunks.size());
                return;
            }
        }

        // Full or incremental reingest
        documentMetadataMap.clear();
        checksumToDocId.clear();
        activeChunks.clear();
        allChunks.clear();

        for (Path file : files) {
            try {
                ingestFile(rootDir, file);
            } catch (Exception ex) {
                logger.error("Failed to ingest document '{}': {}", file.getFileName(), ex.getMessage(), ex);
            }
        }

        // Post-processing: detect and mark superseded documents
        resolveSupersededVersions();

        // Filter active chunks to exclude outdated or superseded documents
        for (KnowledgeChunk chunk : allChunks) {
            DocumentMetadata meta = documentMetadataMap.get(chunk.getFilename());
            if (meta == null || !meta.isOutdated()) {
                activeChunks.add(chunk);
            }
        }

        // Persist to disk repository so index survives restart
        if (diskRepository != null) {
            diskRepository.saveIndex(allChunks, documentMetadataMap);
        }

        logger.info("DocumentIngestionService: Completed ingestion. Indexed {} document(s) into {} active chunk(s) ({} total). Persisted to disk.",
                documentMetadataMap.size(), activeChunks.size(), allChunks.size());
    }

    private List<Path> scanSupportedFiles(Path rootDir) {
        try (Stream<Path> stream = Files.walk(rootDir, 5)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ENGLISH);
                        return (name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".pdf"))
                                && !name.startsWith(".");
                    })
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error scanning docs path '{}': {}", rootDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    public synchronized void ingestFile(Path rootDir, Path filePath) throws IOException {
        String filename = filePath.getFileName().toString();
        String lowerName = filename.toLowerCase(Locale.ENGLISH);

        String checksum = computeSha256(filePath);
        if (checksumToDocId.containsKey(checksum)) {
            logger.info("Skipping duplicate document '{}' (matches '{}' with hash {})",
                    filename, checksumToDocId.get(checksum), checksum);
            return;
        }

        Path relativePath = rootDir.relativize(filePath);
        String displayPath = "docs/" + relativePath.toString().replace('\\', '/');
        String category = determineCategory(relativePath);

        List<KnowledgeChunk> chunks;
        DocumentMetadata metadata;

        if (lowerName.endsWith(".pdf")) {
            PdfIngestionResult pdfResult = processPdfFile(filePath, category, displayPath, filename);
            chunks = pdfResult.chunks;
            metadata = pdfResult.metadata;
        } else {
            MarkdownIngestionResult mdResult = processMarkdownFile(filePath, category, displayPath, filename);
            chunks = mdResult.chunks;
            metadata = mdResult.metadata;
        }

        metadata.setSha256Checksum(checksum);
        metadata.setFilePath(displayPath);
        metadata.setChunkCount(chunks.size());

        checksumToDocId.put(checksum, filename);
        documentMetadataMap.put(filename, metadata);
        allChunks.addAll(chunks);
    }

    private static class MarkdownIngestionResult {
        List<KnowledgeChunk> chunks;
        DocumentMetadata metadata;
    }

    private MarkdownIngestionResult processMarkdownFile(Path filePath, String category, String displayPath, String filename) throws IOException {
        String rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
        String docTitle = extractDocumentTitle(rawContent, filename);

        DocumentMetadata meta = parseMarkdownMetadata(rawContent, filename, docTitle, category, displayPath);
        List<KnowledgeChunk> chunks = splitMarkdownIntoChunks(rawContent, category, displayPath, filename, docTitle);

        if (diskRepository != null) {
            diskRepository.saveExtractedDocumentText(filename, rawContent);
        }

        MarkdownIngestionResult res = new MarkdownIngestionResult();
        res.chunks = chunks;
        res.metadata = meta;
        return res;
    }

    private DocumentMetadata parseMarkdownMetadata(String content, String filename, String docTitle, String category, String displayPath) {
        String sourceUrl = "https://vitap.ac.in";
        Matcher urlMatcher = META_SOURCE_URL.matcher(content);
        if (urlMatcher.find()) {
            sourceUrl = urlMatcher.group(1);
        }

        String docType = determineDocumentType(filename, category);
        Matcher typeMatcher = META_DOC_TYPE.matcher(content);
        if (typeMatcher.find()) {
            docType = typeMatcher.group(1).toUpperCase(Locale.ENGLISH);
        }

        String pubDate = "";
        Matcher pubMatcher = META_PUB_DATE.matcher(content);
        if (pubMatcher.find()) {
            pubDate = pubMatcher.group(1);
        }

        String effDate = "";
        Matcher effMatcher = META_EFF_DATE.matcher(content);
        if (effMatcher.find()) {
            effDate = effMatcher.group(1);
        }

        String retDate = "2026-10-10";
        Matcher retMatcher = META_RET_DATE.matcher(content);
        if (retMatcher.find()) {
            retDate = retMatcher.group(1) != null ? retMatcher.group(1) : retMatcher.group(2);
        }

        String version = "1.0";
        Matcher verMatcher = META_VERSION.matcher(content);
        if (verMatcher.find()) {
            version = verMatcher.group(1);
        }

        String supersedes = null;
        Matcher supMatcher = META_SUPERSEDES.matcher(content);
        if (supMatcher.find()) {
            supersedes = supMatcher.group(1);
        }

        DocumentMetadata meta = new DocumentMetadata(
                filename,
                sourceUrl,
                docTitle,
                docType,
                pubDate,
                effDate,
                retDate,
                version,
                supersedes,
                false,
                "",
                displayPath,
                0
        );
        meta.setExtractionStatus("SUCCESS");
        return meta;
    }

    public static class PdfIngestionResult {
        public List<KnowledgeChunk> chunks;
        public DocumentMetadata metadata;
    }

    public PdfIngestionResult processPdfFile(Path filePath, String category, String displayPath, String filename) throws IOException {
        File file = filePath.toFile();
        if (!file.exists() || file.length() == 0) {
            DocumentMetadata meta = new DocumentMetadata();
            meta.setId(filename);
            meta.setFilePath(displayPath);
            meta.setExtractionStatus("EMPTY");
            meta.setStatusMessage("PDF file is empty (0 bytes).");
            logger.warn("PDF file '{}' is empty (0 bytes). Skipping indexing.", filename);
            PdfIngestionResult res = new PdfIngestionResult();
            res.chunks = Collections.emptyList();
            res.metadata = meta;
            return res;
        }

        try (PDDocument document = Loader.loadPDF(file)) {
            // Check for encryption
            if (document.isEncrypted()) {
                DocumentMetadata meta = new DocumentMetadata();
                meta.setId(filename);
                meta.setFilePath(displayPath);
                meta.setExtractionStatus("ENCRYPTED");
                meta.setStatusMessage("PDF document is encrypted or password-protected. Decryption is required.");
                logger.warn("PDF document '{}' is encrypted/password-protected. Skipping indexing.", filename);
                PdfIngestionResult res = new PdfIngestionResult();
                res.chunks = Collections.emptyList();
                res.metadata = meta;
                return res;
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                DocumentMetadata meta = new DocumentMetadata();
                meta.setId(filename);
                meta.setFilePath(displayPath);
                meta.setExtractionStatus("EMPTY");
                meta.setStatusMessage("PDF document contains 0 pages.");
                PdfIngestionResult res = new PdfIngestionResult();
                res.chunks = Collections.emptyList();
                res.metadata = meta;
                return res;
            }

            // Extract page-by-page text
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<String> pageTexts = new ArrayList<>();
            StringBuilder fullTextAccumulator = new StringBuilder();
            int totalExtractedChars = 0;

            for (int p = 1; p <= pageCount; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(document);
                pageTexts.add(pageText != null ? pageText : "");
                if (pageText != null) {
                    totalExtractedChars += pageText.trim().length();
                    fullTextAccumulator.append("--- PAGE ").append(p).append(" ---\n").append(pageText).append("\n\n");
                }
            }

            // Detect scanned / image-only PDF requiring OCR
            if (totalExtractedChars < 50 || (totalExtractedChars / pageCount < 10 && pageCount > 1)) {
                DocumentMetadata meta = new DocumentMetadata();
                meta.setId(filename);
                meta.setFilePath(displayPath);
                meta.setPageCount(pageCount);
                meta.setExtractionStatus("OCR_REQUIRED");
                meta.setStatusMessage("Scanned image-only PDF detected (" + totalExtractedChars + " characters across " + pageCount + " pages). Optical Character Recognition (OCR) is required to index this content; OCR is not enabled.");
                logger.warn("Scanned image-only PDF detected for '{}'. OCR is required. No text indexed.", filename);
                PdfIngestionResult res = new PdfIngestionResult();
                res.chunks = Collections.emptyList();
                res.metadata = meta;
                return res;
            }

            // Save extracted document text to disk
            if (diskRepository != null) {
                diskRepository.saveExtractedDocumentText(filename, fullTextAccumulator.toString());
            }

            // Determine title and version
            String docTitle = "FFCS Academic Regulations";
            String version = "4.0";
            String sourceUrl = "https://vitap.ac.in/academic-regulations";

            String firstPage = pageTexts.isEmpty() ? "" : pageTexts.get(0);
            if (firstPage.contains("FFCS Academic Regulations")) {
                docTitle = "FFCS Academic Regulations";
            } else if (document.getDocumentInformation() != null && document.getDocumentInformation().getTitle() != null && !document.getDocumentInformation().getTitle().isBlank()) {
                docTitle = document.getDocumentInformation().getTitle().trim();
            } else {
                docTitle = filename.replaceAll("(?i)\\.pdf$", "").replace('_', ' ').replace('-', ' ');
            }

            Matcher verMatcher = Pattern.compile("(?i)Version\\s*[:\s-]?\\s*([0-9]+\\.[0-9]+|[0-9]+)").matcher(firstPage);
            if (verMatcher.find()) {
                version = "Version " + verMatcher.group(1);
            }

            // Segment pages into overlapping, page-annotated KnowledgeChunks
            List<KnowledgeChunk> chunks = chunkPdfByPages(pageTexts, category, displayPath, filename, docTitle, version);

            DocumentMetadata meta = new DocumentMetadata(
                    filename,
                    sourceUrl,
                    docTitle,
                    "ACADEMIC_REGULATIONS",
                    "2024-07-01",
                    "2024-07-01",
                    "2026-10-14",
                    version,
                    null,
                    false,
                    "",
                    displayPath,
                    chunks.size()
            );
            meta.setPageCount(pageCount);
            meta.setExtractionStatus("SUCCESS");
            meta.setStatusMessage("Successfully extracted " + pageCount + " pages into " + chunks.size() + " chunks.");

            PdfIngestionResult res = new PdfIngestionResult();
            res.chunks = chunks;
            res.metadata = meta;
            return res;
        }
    }

    private List<KnowledgeChunk> chunkPdfByPages(
            List<String> pageTexts,
            String category,
            String displayPath,
            String filename,
            String docTitle,
            String version) {

        List<KnowledgeChunk> result = new ArrayList<>();
        String currentRunningSection = "General Academic Regulations";
        int chunkSeq = 0;

        for (int pIdx = 0; pIdx < pageTexts.size(); pIdx++) {
            int pageNumber = pIdx + 1;
            String rawPage = pageTexts.get(pIdx);
            if (rawPage == null || rawPage.isBlank()) {
                continue;
            }

            // Remove recurring headers/footers
            String[] rawLines = rawPage.split("\\r?\\n");
            List<String> cleanedLines = new ArrayList<>();
            for (String line : rawLines) {
                String trimmed = line.trim();
                if (PDF_RUNNING_FOOTER.matcher(trimmed).matches()) {
                    continue;
                }
                // Strip bare page numbers from very top or bottom
                if (trimmed.matches("^\\d{1,3}$") && (cleanedLines.isEmpty() || cleanedLines.size() > 25)) {
                    continue;
                }
                cleanedLines.add(line);
            }

            if (cleanedLines.isEmpty()) {
                continue;
            }

            // Group lines on this page by sections/tables
            StringBuilder sectionBuffer = new StringBuilder();
            String pageSectionTitle = currentRunningSection;

            for (int lineIdx = 0; lineIdx < cleanedLines.size(); lineIdx++) {
                String line = cleanedLines.get(lineIdx);
                String trimmed = line.trim();

                Matcher headingMatcher = PDF_SECTION_HEADING.matcher(trimmed);
                Matcher tableMatcher = PDF_TABLE_HEADING.matcher(trimmed);

                boolean isHeading = headingMatcher.matches() || tableMatcher.matches();

                if (isHeading && sectionBuffer.length() > 200) {
                    // Flush accumulated section chunk
                    String body = sectionBuffer.toString().trim();
                    if (!body.isBlank()) {
                        String id = slugify(category + "-" + filename + "-p" + pageNumber + "-c" + chunkSeq + "-" + pageSectionTitle);
                        result.add(new KnowledgeChunk(
                                id, category, displayPath, filename, docTitle, pageSectionTitle, body,
                                pageNumber, String.valueOf(pageNumber), "ACADEMIC_REGULATIONS", version, System.currentTimeMillis()
                        ));
                        chunkSeq++;
                    }

                    // Carry over the last 150 characters for overlap to preserve context/rules
                    String overlap = extractOverlap(body, 150);
                    sectionBuffer = new StringBuilder();
                    if (!overlap.isBlank()) {
                        sectionBuffer.append("[Context from previous section on Page ").append(pageNumber).append("]\n")
                                .append(overlap).append("\n\n");
                    }

                    if (tableMatcher.matches()) {
                        pageSectionTitle = tableMatcher.group(1).trim();
                    } else {
                        pageSectionTitle = trimmed;
                    }
                    currentRunningSection = pageSectionTitle;
                }

                sectionBuffer.append(line).append("\n");
            }

            // Flush remaining text on page
            String remainingBody = sectionBuffer.toString().trim();
            if (!remainingBody.isBlank()) {
                String id = slugify(category + "-" + filename + "-p" + pageNumber + "-c" + chunkSeq + "-" + pageSectionTitle);
                result.add(new KnowledgeChunk(
                        id, category, displayPath, filename, docTitle, pageSectionTitle, remainingBody,
                        pageNumber, String.valueOf(pageNumber), "ACADEMIC_REGULATIONS", version, System.currentTimeMillis()
                ));
                chunkSeq++;
            }
        }

        return result;
    }

    private String extractOverlap(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return "";
        }
        int start = Math.max(0, text.length() - maxChars);
        // Find nearest line break
        int newline = text.indexOf('\n', start);
        if (newline != -1 && newline < text.length() - 20) {
            return text.substring(newline + 1).trim();
        }
        return text.substring(start).trim();
    }

    private List<KnowledgeChunk> splitMarkdownIntoChunks(
            String content,
            String category,
            String sourcePath,
            String filename,
            String documentTitle) {
        List<KnowledgeChunk> result = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        boolean hasH2 = false;
        boolean hasH3 = false;
        boolean hasH1 = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                hasH2 = true;
                break;
            } else if (trimmed.startsWith("### ")) {
                hasH3 = true;
            } else if (trimmed.startsWith("# ")) {
                hasH1 = true;
            }
        }

        String headingPrefix = hasH2 ? "## " : (hasH3 ? "### " : (hasH1 ? "# " : null));

        if (headingPrefix == null) {
            String trimmed = content.trim();
            if (!trimmed.isBlank()) {
                String id = slugify(category + "-" + filename + "-general");
                result.add(new KnowledgeChunk(id, category, sourcePath, filename, documentTitle, "General", trimmed));
            }
            return result;
        }

        String currentSectionTitle = "Document Overview";
        StringBuilder currentSectionBody = new StringBuilder();
        int sectionIndex = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(headingPrefix)) {
                String body = currentSectionBody.toString().trim();
                if (!body.isBlank()) {
                    String id = slugify(category + "-" + filename + "-sec" + sectionIndex + "-" + currentSectionTitle);
                    result.add(new KnowledgeChunk(id, category, sourcePath, filename, documentTitle, currentSectionTitle, body));
                    sectionIndex++;
                }

                currentSectionTitle = trimmed.substring(headingPrefix.length()).trim();
                currentSectionBody = new StringBuilder();
                currentSectionBody.append(line).append("\n");
            } else {
                currentSectionBody.append(line).append("\n");
            }
        }

        String body = currentSectionBody.toString().trim();
        if (!body.isBlank()) {
            String id = slugify(category + "-" + filename + "-sec" + sectionIndex + "-" + currentSectionTitle);
            result.add(new KnowledgeChunk(id, category, sourcePath, filename, documentTitle, currentSectionTitle, body));
        }

        return result;
    }

    private void resolveSupersededVersions() {
        for (DocumentMetadata meta : documentMetadataMap.values()) {
            if (meta.getSupersedesId() != null && !meta.getSupersedesId().isBlank()) {
                String targetId = meta.getSupersedesId().trim();
                DocumentMetadata superseded = documentMetadataMap.get(targetId);
                if (superseded != null) {
                    superseded.setOutdated(true);
                    logger.info("Marked document '{}' as outdated because it is superseded by '{}'", targetId, meta.getId());
                }
            }
        }
    }

    private String determineCategory(Path relativePath) {
        if (relativePath.getNameCount() > 1) {
            return relativePath.getName(0).toString().toLowerCase(Locale.ENGLISH);
        }
        return "general";
    }

    private String determineDocumentType(String filename, String category) {
        String lower = filename.toLowerCase(Locale.ENGLISH);
        if (lower.contains("policy")) return "POLICY";
        if (lower.contains("guideline") || lower.contains("regulation")) return "REGULATION";
        if (lower.contains("circular") || lower.contains("notice")) return "CIRCULAR";
        if (lower.contains("handbook") || lower.contains("manual")) return "HANDBOOK";
        if (lower.contains("fee") || lower.contains("structure")) return "FINANCIAL_SCHEDULE";
        if (category.equals("notices")) return "CIRCULAR";
        return "REGULATION";
    }

    private String extractDocumentTitle(String content, String fallbackFilename) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallbackFilename.replaceAll("(?i)\\.(md|markdown|pdf)$", "");
    }

    public String computeSha256(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(fileBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            return "hash-" + file.getFileName().toString() + "-" + file.toFile().length();
        }
    }

    public String slugify(String input) {
        if (input == null) return "chunk";
        String normalized = input.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "chunk" : normalized;
    }

    public List<KnowledgeChunk> getActiveChunks() {
        return Collections.unmodifiableList(activeChunks);
    }

    public List<KnowledgeChunk> getAllChunks() {
        return Collections.unmodifiableList(allChunks);
    }

    public Map<String, DocumentMetadata> getDocumentMetadataMap() {
        return Collections.unmodifiableMap(documentMetadataMap);
    }

    public DocumentMetadata getDocumentMetadata(String documentId) {
        return documentMetadataMap.get(documentId);
    }

    public FileKnowledgeIndexRepository getDiskRepository() {
        return diskRepository;
    }
}
