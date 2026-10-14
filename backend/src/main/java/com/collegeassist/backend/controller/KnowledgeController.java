package com.collegeassist.backend.controller;

import com.collegeassist.backend.model.DocumentMetadata;
import com.collegeassist.backend.service.DocumentKnowledgeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller providing institutional knowledge repository inspection and re-ingestion endpoints.
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final DocumentKnowledgeService documentKnowledgeService;

    public KnowledgeController(DocumentKnowledgeService documentKnowledgeService) {
        this.documentKnowledgeService = documentKnowledgeService;
    }

    /**
     * Lists all verified documents and their provenance metadata.
     */
    @GetMapping("/documents")
    public ResponseEntity<Collection<DocumentMetadata>> getDocuments() {
        return ResponseEntity.ok(documentKnowledgeService.getDocumentMetadataMap().values());
    }

    /**
     * Triggers dynamic re-ingestion of the knowledge base documents.
     */
    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindexKnowledge() {
        Path docsPath = documentKnowledgeService.resolveDocsPath();
        documentKnowledgeService.loadDocuments();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("message", "Knowledge repository re-indexed successfully.");
        result.put("documentCount", documentKnowledgeService.getDocumentCount());
        result.put("chunkCount", documentKnowledgeService.getChunkCount());
        result.put("categories", documentKnowledgeService.getLoadedCategories());
        result.put("resolvedPath", docsPath != null ? docsPath.toString() : "unknown");

        return ResponseEntity.ok(result);
    }
}
