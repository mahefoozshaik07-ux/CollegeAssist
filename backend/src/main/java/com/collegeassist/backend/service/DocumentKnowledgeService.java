package com.collegeassist.backend.service;

import com.collegeassist.backend.model.DocumentMetadata;
import com.collegeassist.backend.model.KnowledgeChunk;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service responsible for ingesting institutional knowledge-base Markdown files
 * from the docs/ repository and chunking them into structured in-memory sections on startup.
 */
@Service
public class DocumentKnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentKnowledgeService.class);

    private static final Pattern TITLE_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern H2_PATTERN = Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern H3_PATTERN = Pattern.compile("^###\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern H1_PATTERN = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);

    private final String configuredDocsPath;
    private final DocumentIngestionService ingestionService;
    private final List<KnowledgeChunk> chunks = new ArrayList<>();
    private int documentCount = 0;
    private Path resolvedDocsDirectory;
    private boolean loaded = false;

    public DocumentKnowledgeService() {
        this("docs", new DocumentIngestionService());
    }

    public DocumentKnowledgeService(String configuredDocsPath) {
        this(configuredDocsPath, new DocumentIngestionService());
    }

    @Autowired
    public DocumentKnowledgeService(
            @Value("${collegeassist.knowledge.base-path:docs}") String configuredDocsPath,
            @Autowired(required = false) DocumentIngestionService ingestionService) {
        this.configuredDocsPath = (configuredDocsPath != null && !configuredDocsPath.isBlank())
                ? configuredDocsPath.trim()
                : "docs";
        this.ingestionService = ingestionService != null ? ingestionService : new DocumentIngestionService();
    }

    public DocumentKnowledgeService(Path explicitDocsDirectory) {
        this(explicitDocsDirectory != null ? explicitDocsDirectory.toString() : "docs", new DocumentIngestionService());
    }

    @PostConstruct
    public void init() {
        loadDocuments();
    }

    public synchronized void reindex(boolean force) {
        chunks.clear();
        documentCount = 0;
        loaded = false;
        resolvedDocsDirectory = resolveDocsPath();
        if (resolvedDocsDirectory != null && Files.exists(resolvedDocsDirectory)) {
            ingestionService.reindex(resolvedDocsDirectory, force);
            chunks.addAll(ingestionService.getActiveChunks());
            documentCount = ingestionService.getDocumentMetadataMap().size();
            loaded = true;
            logger.info("DocumentKnowledgeService: Reindexed {} document(s) into {} chunk(s).", documentCount, chunks.size());
        }
    }

    /**
     * Locates the configured docs directory and ingests all markdown files into memory.
     * This method is synchronized and safe to call at startup or during tests.
     */
    public synchronized void loadDocuments() {
        chunks.clear();
        documentCount = 0;
        loaded = false;

        resolvedDocsDirectory = resolveDocsPath();

        if (resolvedDocsDirectory == null || !Files.exists(resolvedDocsDirectory)) {
            logger.warn("DocumentKnowledgeService: Knowledge base directory '{}' does not exist. Initialized with 0 chunks.",
                    configuredDocsPath);
            loaded = true;
            return;
        }

        if (!Files.isDirectory(resolvedDocsDirectory)) {
            logger.warn("DocumentKnowledgeService: Knowledge base path '{}' is not a directory. Initialized with 0 chunks.",
                    resolvedDocsDirectory);
            loaded = true;
            return;
        }

        ingestionService.reingestAll(resolvedDocsDirectory);
        chunks.addAll(ingestionService.getActiveChunks());
        documentCount = ingestionService.getDocumentMetadataMap().size();

        loaded = true;
        logger.info("DocumentKnowledgeService: Successfully indexed {} document(s) into {} chunk(s) across {} categories from '{}'",
                documentCount, chunks.size(), getLoadedCategories().size(), resolvedDocsDirectory);
    }

    /**
     * Resolves the docs directory using runtime and parent directory fallbacks.
     */
    public Path resolveDocsPath() {
        Path direct = Path.of(configuredDocsPath).normalize();
        if (Files.exists(direct) && Files.isDirectory(direct)) {
            return direct.toAbsolutePath().normalize();
        }

        // When running inside backend/ subdirectory, try parent ../docs
        Path parentRelative = Path.of("..", configuredDocsPath).normalize();
        if (Files.exists(parentRelative) && Files.isDirectory(parentRelative)) {
            return parentRelative.toAbsolutePath().normalize();
        }

        // Current working directory subpath
        Path cwdRelative = Path.of(".", configuredDocsPath).normalize();
        if (Files.exists(cwdRelative) && Files.isDirectory(cwdRelative)) {
            return cwdRelative.toAbsolutePath().normalize();
        }

        return direct.toAbsolutePath().normalize();
    }

    private List<Path> findMarkdownFiles(Path rootDir) {
        try (Stream<Path> stream = Files.walk(rootDir, 5)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String name = path.getFileName().toString().toLowerCase(Locale.ENGLISH);
                        return (name.endsWith(".md") || name.endsWith(".markdown")) && !name.startsWith(".");
                    })
                    .filter(path -> path.normalize().startsWith(rootDir.normalize()))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            logger.error("Error scanning knowledge base directory '{}': {}", rootDir, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parses a single Markdown file into discrete KnowledgeChunks using heading delimiters.
     */
    public List<KnowledgeChunk> processMarkdownFile(Path rootDir, Path filePath) throws IOException {
        String rawContent = Files.readString(filePath, StandardCharsets.UTF_8);
        if (rawContent == null || rawContent.isBlank()) {
            return Collections.emptyList();
        }

        Path relativePath = rootDir.relativize(filePath);
        String category = determineCategory(relativePath);
        String filename = filePath.getFileName().toString();
        String displayPath = "docs/" + relativePath.toString().replace('\\', '/');

        String documentTitle = extractDocumentTitle(rawContent, filename);
        return splitIntoChunks(rawContent, category, displayPath, filename, documentTitle);
    }

    private String determineCategory(Path relativePath) {
        if (relativePath.getNameCount() > 1) {
            return relativePath.getName(0).toString().toLowerCase(Locale.ENGLISH);
        }
        return "general";
    }

    private String extractDocumentTitle(String content, String fallbackFilename) {
        Matcher matcher = TITLE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return fallbackFilename.replaceAll("(?i)\\.md$", "");
    }

    /**
     * Splits markdown content by headings into distinct sections.
     * Prefers level 2 (##) headings. If absent, falls back to level 3 (###) or level 1 (#).
     * Text appearing prior to the first heading is preserved as a preamble/overview chunk.
     */
    public List<KnowledgeChunk> splitIntoChunks(
            String content,
            String category,
            String sourcePath,
            String filename,
            String documentTitle) {
        List<KnowledgeChunk> result = new ArrayList<>();
        String[] lines = content.split("\\r?\\n");

        // Determine primary heading delimiter: prefer '##', then '###', then '#'
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
            // No recognized headings; entire file is one chunk
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
                // Flush previous section if it has non-blank content
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

        // Flush final section
        String body = currentSectionBody.toString().trim();
        if (!body.isBlank()) {
            String id = slugify(category + "-" + filename + "-sec" + sectionIndex + "-" + currentSectionTitle);
            result.add(new KnowledgeChunk(id, category, sourcePath, filename, documentTitle, currentSectionTitle, body));
        }

        return result;
    }

    private String slugify(String input) {
        if (input == null) return "chunk";
        String normalized = input.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return normalized.isEmpty() ? "chunk" : normalized;
    }

    // Inspection and query methods

    public List<KnowledgeChunk> getAllChunks() {
        return Collections.unmodifiableList(chunks);
    }

    public int getDocumentCount() {
        return documentCount;
    }

    public int getChunkCount() {
        return chunks.size();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Path getResolvedDocsDirectory() {
        return resolvedDocsDirectory;
    }

    public List<String> getLoadedCategories() {
        return chunks.stream()
                .map(KnowledgeChunk::getCategory)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    public List<KnowledgeChunk> getChunksByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Collections.emptyList();
        }
        String target = category.trim().toLowerCase(Locale.ENGLISH);
        return chunks.stream()
                .filter(chunk -> chunk.getCategory().equalsIgnoreCase(target))
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getChunkCountByCategory() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            counts.merge(chunk.getCategory(), 1, Integer::sum);
        }
        return counts;
    }

    public Map<String, Integer> getChunkCountByDocument() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (KnowledgeChunk chunk : chunks) {
            counts.merge(chunk.getFilename(), 1, Integer::sum);
        }
        return counts;
    }

    public DocumentIngestionService getIngestionService() {
        return ingestionService;
    }

    public Map<String, DocumentMetadata> getDocumentMetadataMap() {
        return ingestionService.getDocumentMetadataMap();
    }

    public DocumentMetadata getDocumentMetadata(String documentId) {
        return ingestionService.getDocumentMetadata(documentId);
    }
}
