package com.collegeassist.backend.model;

import java.util.Objects;

/**
 * Represents a segmented section (chunk) of an institutional knowledge-base document.
 * Preserves structural metadata (category, source path, section title) alongside the text content.
 */
public class KnowledgeChunk {

    private final String id;
    private final String category;
    private final String sourcePath;
    private final String filename;
    private final String documentTitle;
    private final String sectionTitle;
    private final String content;
    private final int charCount;
    private final int wordCount;

    public KnowledgeChunk(
            String id,
            String category,
            String sourcePath,
            String filename,
            String documentTitle,
            String sectionTitle,
            String content) {
        this.id = id != null ? id : "";
        this.category = category != null ? category : "";
        this.sourcePath = sourcePath != null ? sourcePath : "";
        this.filename = filename != null ? filename : "";
        this.documentTitle = documentTitle != null ? documentTitle : "";
        this.sectionTitle = sectionTitle != null ? sectionTitle : "";
        this.content = content != null ? content : "";
        this.charCount = this.content.length();
        this.wordCount = calculateWordCount(this.content);
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
                ", wordCount=" + wordCount +
                ", charCount=" + charCount +
                '}';
    }
}
