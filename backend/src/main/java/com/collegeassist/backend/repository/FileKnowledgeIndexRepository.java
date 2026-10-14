package com.collegeassist.backend.repository;

import com.collegeassist.backend.model.DocumentMetadata;
import com.collegeassist.backend.model.KnowledgeChunk;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based disk persistence repository for institutional knowledge-base index,
 * extracted text caches, and document metadata.
 * Survives backend restarts without requiring any SQL, NoSQL, or embedded database.
 */
@Repository
public class FileKnowledgeIndexRepository {

    private static final Logger logger = LoggerFactory.getLogger(FileKnowledgeIndexRepository.class);

    private final Path storageDirectory;
    private final Path chunksFile;
    private final Path metadataFile;
    private final Path extractedTextDir;
    private final ObjectMapper objectMapper;

    public FileKnowledgeIndexRepository() {
        this("data/knowledge");
    }

    @Autowired
    public FileKnowledgeIndexRepository(
            @Value("${collegeassist.knowledge.index-path:data/knowledge}") String storagePath) {
        this.storageDirectory = Path.of(storagePath).normalize();
        this.chunksFile = storageDirectory.resolve("index").resolve("chunks.json");
        this.metadataFile = storageDirectory.resolve("metadata").resolve("documents.json");
        this.extractedTextDir = storageDirectory.resolve("extracted");
        this.objectMapper = new ObjectMapper();
        initStorageDirectories();
    }

    public FileKnowledgeIndexRepository(Path customStorageDir) {
        this.storageDirectory = customStorageDir.normalize();
        this.chunksFile = storageDirectory.resolve("index").resolve("chunks.json");
        this.metadataFile = storageDirectory.resolve("metadata").resolve("documents.json");
        this.extractedTextDir = storageDirectory.resolve("extracted");
        this.objectMapper = new ObjectMapper();
        initStorageDirectories();
    }

    private void initStorageDirectories() {
        try {
            Files.createDirectories(chunksFile.getParent());
            Files.createDirectories(metadataFile.getParent());
            Files.createDirectories(extractedTextDir);
        } catch (IOException e) {
            logger.error("Failed to initialize knowledge index storage directories at '{}': {}",
                    storageDirectory, e.getMessage());
        }
    }

    public boolean hasPersistedIndex() {
        return Files.exists(chunksFile) && Files.isRegularFile(chunksFile)
                && Files.exists(metadataFile) && Files.isRegularFile(metadataFile);
    }

    public synchronized void saveIndex(List<KnowledgeChunk> chunks, Map<String, DocumentMetadata> metadataMap) {
        initStorageDirectories();
        try {
            // Atomic write using temp file
            Path tempChunks = chunksFile.resolveSibling("chunks.json.tmp");
            objectMapper.writeValue(tempChunks.toFile(), chunks != null ? chunks : Collections.emptyList());
            Files.move(tempChunks, chunksFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            Path tempMetadata = metadataFile.resolveSibling("documents.json.tmp");
            objectMapper.writeValue(tempMetadata.toFile(), metadataMap != null ? metadataMap : Collections.emptyMap());
            Files.move(tempMetadata, metadataFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);

            logger.info("Persisted {} chunks and {} document metadata records to disk at '{}'",
                    chunks != null ? chunks.size() : 0,
                    metadataMap != null ? metadataMap.size() : 0,
                    storageDirectory);
        } catch (IOException e) {
            logger.error("Error saving knowledge index to disk: {}", e.getMessage(), e);
        }
    }

    public synchronized List<KnowledgeChunk> loadChunks() {
        if (!Files.exists(chunksFile)) {
            return Collections.emptyList();
        }
        try {
            String json = Files.readString(chunksFile, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<List<KnowledgeChunk>>() {});
        } catch (Exception e) {
            logger.error("Error loading chunks from '{}': {}", chunksFile, e.getMessage());
            return Collections.emptyList();
        }
    }

    public synchronized Map<String, DocumentMetadata> loadMetadataMap() {
        if (!Files.exists(metadataFile)) {
            return Collections.emptyMap();
        }
        try {
            String json = Files.readString(metadataFile, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, new TypeReference<Map<String, DocumentMetadata>>() {});
        } catch (Exception e) {
            logger.error("Error loading document metadata from '{}': {}", metadataFile, e.getMessage());
            return Collections.emptyMap();
        }
    }


    public synchronized void saveExtractedDocumentText(String filename, String content) {
        try {
            Files.createDirectories(extractedTextDir);
            Path filePath = extractedTextDir.resolve(filename + ".extracted.txt");
            Files.writeString(filePath, content != null ? content : "", StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.warn("Could not save extracted text cache for '{}': {}", filename, e.getMessage());
        }
    }

    public synchronized String loadExtractedDocumentText(String filename) {
        Path filePath = extractedTextDir.resolve(filename + ".extracted.txt");
        if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
            try {
                return Files.readString(filePath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.warn("Could not read extracted text for '{}': {}", filename, e.getMessage());
            }
        }
        return null;
    }

    public synchronized void clearStorage() {
        try {
            if (Files.exists(chunksFile)) Files.deleteIfExists(chunksFile);
            if (Files.exists(metadataFile)) Files.deleteIfExists(metadataFile);
        } catch (IOException e) {
            logger.warn("Error clearing index files: {}", e.getMessage());
        }
    }

    public Path getStorageDirectory() {
        return storageDirectory;
    }
}
