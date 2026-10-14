package com.collegeassist.backend.service;

import com.collegeassist.backend.model.DocumentMetadata;
import com.collegeassist.backend.model.KnowledgeChunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
 * Robust institutional document ingestion pipeline supporting Markdown and PDF formats.
 * Extracts text, headings, and tables, records source metadata, executes SHA-256 deduplication,
 * detects superseded versions, and supports dynamic re-ingestion.
 */
@Service
public class DocumentIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentIngestionService.class);

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern H2_PATTERN = Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern H3_PATTERN = Pattern.compile("^###\\s+(.+)$", Pattern.MULTILINE);

    private static final Pattern META_SOURCE_URL = Pattern.compile("(?i)Official Sources?:\\s*\\[.*?\\]\\((https?://[^\\s)]+)\\)");
    private static final Pattern META_DOC_TYPE = Pattern.compile("(?i)Document Type:\\s*([A-Za-z_-]+)");
    private static final Pattern META_PUB_DATE = Pattern.compile("(?i)Publication Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern META_EFF_DATE = Pattern.compile("(?i)Effective Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern META_RET_DATE = Pattern.compile("(?i)Last Checked:\\s*(\\d{4}-\\d{2}-\\d{2})|Retrieval Date:\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern META_VERSION = Pattern.compile("(?i)Version:\\s*([0-9a-zA-Z._-]+)");
    private static final Pattern META_SUPERSEDES = Pattern.compile("(?i)Supersedes?:\\s*([0-9a-zA-Z._-]+)");

    private final Map<String, DocumentMetadata> documentMetadataMap = new ConcurrentHashMap<>();
    private final Map<String, String> checksumToDocId = new ConcurrentHashMap<>();
    private final List<KnowledgeChunk> activeChunks = new ArrayList<>();
    private final List<KnowledgeChunk> allChunks = new ArrayList<>();

    public synchronized void reingestAll(Path rootDir) {
        if (rootDir == null || !Files.exists(rootDir) || !Files.isDirectory(rootDir)) {
            logger.warn("DocumentIngestionService: Docs directory '{}' is invalid or does not exist.", rootDir);
            return;
        }

        documentMetadataMap.clear();
        checksumToDocId.clear();
        activeChunks.clear();
        allChunks.clear();

        List<Path> files = scanSupportedFiles(rootDir);
        logger.info("DocumentIngestionService: Found {} candidate document(s) in '{}'", files.size(), rootDir);

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

        logger.info("DocumentIngestionService: Completed ingestion. Indexed {} document(s) into {} active chunk(s) ({} total).",
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

        return new DocumentMetadata(
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
    }

    private static class PdfIngestionResult {
        List<KnowledgeChunk> chunks;
        DocumentMetadata metadata;
    }

    private PdfIngestionResult processPdfFile(Path filePath, String category, String displayPath, String filename) throws IOException {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        String docTitle = filename.replaceAll("(?i)\\.pdf$", "").replace('_', ' ').replace('-', ' ');
        String fullText = "";

        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            if (document.getDocumentInformation() != null && document.getDocumentInformation().getTitle() != null) {
                String metaTitle = document.getDocumentInformation().getTitle().trim();
                if (!metaTitle.isBlank()) {
                    docTitle = metaTitle;
                }
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            fullText = stripper.getText(document);
        }

        if (fullText == null || fullText.isBlank()) {
            fullText = "Official PDF Document: " + docTitle;
        }

        // Chunk PDF text by headings or section numbers (e.g. "1. ", "2. ", "Section ", "Article ")
        chunks = chunkPdfText(fullText, category, displayPath, filename, docTitle);

        DocumentMetadata meta = new DocumentMetadata(
                filename,
                "https://vitap.ac.in/official-circulars",
                docTitle,
                determineDocumentType(filename, category),
                "",
                "",
                "2026-10-12",
                "1.0",
                null,
                false,
                "",
                displayPath,
                chunks.size()
        );

        PdfIngestionResult res = new PdfIngestionResult();
        res.chunks = chunks;
        res.metadata = meta;
        return res;
    }

    private List<KnowledgeChunk> chunkPdfText(String text, String category, String displayPath, String filename, String docTitle) {
        List<KnowledgeChunk> result = new ArrayList<>();
        String[] lines = text.split("\\r?\\n");
        StringBuilder currentSection = new StringBuilder();
        String currentHeading = "Document Overview";
        int sectionIndex = 0;

        Pattern sectionStartPattern = Pattern.compile("^(?:\\d+\\.|SECTION|ARTICLE|CHAPTER|NOTE|RULE)\\s+(.+)", Pattern.CASE_INSENSITIVE);

        for (String line : lines) {
            String trimmed = line.trim();
            Matcher matcher = sectionStartPattern.matcher(trimmed);
            if (matcher.find() && currentSection.length() > 150) {
                String body = currentSection.toString().trim();
                if (!body.isBlank()) {
                    String id = slugify(category + "-" + filename + "-sec" + sectionIndex + "-" + currentHeading);
                    result.add(new KnowledgeChunk(id, category, displayPath, filename, docTitle, currentHeading, body));
                    sectionIndex++;
                }
                currentHeading = trimmed;
                currentSection = new StringBuilder();
                currentSection.append(line).append("\n");
            } else {
                currentSection.append(line).append("\n");
            }
        }

        String body = currentSection.toString().trim();
        if (!body.isBlank()) {
            String id = slugify(category + "-" + filename + "-sec" + sectionIndex + "-" + currentHeading);
            result.add(new KnowledgeChunk(id, category, displayPath, filename, docTitle, currentHeading, body));
        }

        if (result.isEmpty() && !text.isBlank()) {
            String id = slugify(category + "-" + filename + "-general");
            result.add(new KnowledgeChunk(id, category, displayPath, filename, docTitle, "General", text.trim()));
        }

        return result;
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

    private String computeSha256(Path file) {
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

    private String slugify(String input) {
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
}
