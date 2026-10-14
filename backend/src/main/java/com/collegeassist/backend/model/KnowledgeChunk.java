package com.collegeassist.backend.model;

import java.util.Objects;

/**
 * Represents a segmented section (chunk) of an institutional knowledge-base document.
 * Preserves structural metadata (category, source path, section title, page number, version) alongside text.
 */
public class KnowledgeChunk {

    private String id;
    private String category;
    private String sourcePath;
    private String filename;
    private String documentTitle;
    private String sectionTitle;
    private String content;
    private int charCount;
    private int wordCount;
    private int pageNumber;
    private String pageRange;
    private String documentType;
    private String version;
    private long ingestionTimestamp;

    // No-arg constructor for Jackson serialization/deserialization
    public KnowledgeChunk() {
    }

    public KnowledgeChunk(
            String id,
            String category,
            String sourcePath,
            String filename,
            String documentTitle,
            String sectionTitle,
            String content) {
        this(id, category, sourcePath, filename, documentTitle, sectionTitle, content, 0, "", "POLICY", "1.0", System.currentTimeMillis());
    }

    public KnowledgeChunk(
            String id,
            String category,
            String sourcePath,
            String filename,
            String documentTitle,
            String sectionTitle,
            String content,
            int pageNumber,
            String pageRange,
            String documentType,
            String version,
            long ingestionTimestamp) {
        this.id = id != null ? id : "";
        this.category = category != null ? category : "";
        this.sourcePath = sourcePath != null ? sourcePath : "";
        this.filename = filename != null ? filename : "";
        this.documentTitle = documentTitle != null ? documentTitle : "";
        this.sectionTitle = sectionTitle != null ? sectionTitle : "";
        this.content = content != null ? content : "";
        this.charCount = this.content.length();
        this.wordCount = calculateWordCount(this.content);
        this.pageNumber = pageNumber;
        this.pageRange = pageRange != null ? pageRange : (pageNumber > 0 ? String.valueOf(pageNumber) : "");
        this.documentType = documentType != null ? documentType : "POLICY";
        this.version = version != null ? version : "1.0";
        this.ingestionTimestamp = ingestionTimestamp > 0 ? ingestionTimestamp : System.currentTimeMillis();
    }

    private static int calculateWordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String[] words = text.trim().split("\\s+");
        return words.length;
    }

    public String getId() {
        return id;
    }

    public String getCategory() {
        return category;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getFilename() {
        return filename;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }

    public int getWordCount() {
        return wordCount;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public String getPageRange() {
        return pageRange;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getVersion() {
        return version;
    }

    public long getIngestionTimestamp() {
        return ingestionTimestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeChunk that = (KnowledgeChunk) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(sourcePath, that.sourcePath) &&
                Objects.equals(sectionTitle, that.sectionTitle);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sourcePath, sectionTitle);
    }

    @Override
    public String toString() {
        return "KnowledgeChunk{" +
                "id='" + id + '\'' +
                ", category='" + category + '\'' +
                ", sourcePath='" + sourcePath + '\'' +
                ", sectionTitle='" + sectionTitle + '\'' +
                ", pageNumber=" + pageNumber +
                ", version='" + version + '\'' +
                ", wordCount=" + wordCount +
                '}';
    }
}
