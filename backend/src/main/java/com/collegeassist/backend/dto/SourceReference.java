package com.collegeassist.backend.dto;

import java.util.Objects;

public class SourceReference {

    private String category;
    private String documentPath;
    private String status;
    private String relevanceNote;
    private String sectionTitle;
    private String filename;
    private int pageNumber;
    private String pageRange;
    private String documentTitle;
    private String version;

    public SourceReference() {
    }

    public SourceReference(String category, String documentPath, String status, String relevanceNote) {
        this(category, documentPath, status, relevanceNote, null, null, 0, "", null, null);
    }

    public SourceReference(String category, String documentPath, String status, String relevanceNote, String sectionTitle, String filename) {
        this(category, documentPath, status, relevanceNote, sectionTitle, filename, 0, "", null, null);
    }

    public SourceReference(String category, String documentPath, String status, String relevanceNote,
                           String sectionTitle, String filename, int pageNumber, String pageRange,
                           String documentTitle, String version) {
        this.category = category;
        this.documentPath = documentPath;
        this.status = status;
        this.relevanceNote = relevanceNote;
        this.sectionTitle = sectionTitle;
        this.filename = filename;
        this.pageNumber = pageNumber;
        this.pageRange = pageRange != null ? pageRange : (pageNumber > 0 ? String.valueOf(pageNumber) : "");
        this.documentTitle = documentTitle;
        this.version = version;
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

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public String getPageRange() {
        return pageRange;
    }

    public void setPageRange(String pageRange) {
        this.pageRange = pageRange;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SourceReference that = (SourceReference) o;
        return Objects.equals(category, that.category) &&
                Objects.equals(documentPath, that.documentPath) &&
                Objects.equals(sectionTitle, that.sectionTitle) &&
                Objects.equals(pageNumber, that.pageNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, documentPath, sectionTitle, pageNumber);
    }

    @Override
    public String toString() {
        return "SourceReference{" +
                "category='" + category + '\'' +
                ", documentPath='" + documentPath + '\'' +
                ", status='" + status + '\'' +
                ", sectionTitle='" + sectionTitle + '\'' +
                ", filename='" + filename + '\'' +
                ", pageNumber=" + pageNumber +
                ", version='" + version + '\'' +
                '}';
    }
}
