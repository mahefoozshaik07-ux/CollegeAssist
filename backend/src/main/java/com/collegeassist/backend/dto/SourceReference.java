package com.collegeassist.backend.dto;

public class SourceReference {

    private String category;
    private String documentPath;
    private String status;
    private String relevanceNote;
    private String sectionTitle;
    private String filename;

    public SourceReference() {
    }

    public SourceReference(String category, String documentPath, String status, String relevanceNote) {
        this(category, documentPath, status, relevanceNote, null, null);
    }

    public SourceReference(String category, String documentPath, String status, String relevanceNote, String sectionTitle, String filename) {
        this.category = category;
        this.documentPath = documentPath;
        this.status = status;
        this.relevanceNote = relevanceNote;
        this.sectionTitle = sectionTitle;
        this.filename = filename;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRelevanceNote() {
        return relevanceNote;
    }

    public void setRelevanceNote(String relevanceNote) {
        this.relevanceNote = relevanceNote;
    }

    public String getSectionTitle() {
        return sectionTitle;
    }

    public void setSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceReference that = (SourceReference) o;
        return java.util.Objects.equals(category, that.category) &&
                java.util.Objects.equals(documentPath, that.documentPath) &&
                java.util.Objects.equals(sectionTitle, that.sectionTitle);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(category, documentPath, sectionTitle);
    }

    @Override
    public String toString() {
        return "SourceReference{" +
                "category='" + category + '\'' +
                ", documentPath='" + documentPath + '\'' +
                ", status='" + status + '\'' +
                ", sectionTitle='" + sectionTitle + '\'' +
                ", filename='" + filename + '\'' +
                '}';
    }
}
