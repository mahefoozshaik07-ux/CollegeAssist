package com.collegeassist.backend.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata model representing verified institutional document provenance,
 * publication information, effective period, versioning, and deduplication checksum.
 */
public class DocumentMetadata implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String sourceUrl;
    private String documentTitle;
    private String documentType; // REGULATION, HANDBOOK, CIRCULAR, POLICY, FAQ, NOTICE, WEB_PAGE
    private String publicationDate;
    private String effectiveDate;
    private String retrievalDate;
    private String version;
    private String supersedesId;
    private boolean outdated;
    private String sha256Checksum;
    private String filePath;
    private int chunkCount;

    public DocumentMetadata() {
    }

    public DocumentMetadata(
            String id,
            String sourceUrl,
            String documentTitle,
            String documentType,
            String publicationDate,
            String effectiveDate,
            String retrievalDate,
            String version,
            String supersedesId,
            boolean outdated,
            String sha256Checksum,
            String filePath,
            int chunkCount) {
        this.id = id != null ? id : "";
        this.sourceUrl = sourceUrl != null ? sourceUrl : "";
        this.documentTitle = documentTitle != null ? documentTitle : "";
        this.documentType = documentType != null ? documentType : "REGULATION";
        this.publicationDate = publicationDate != null ? publicationDate : "";
        this.effectiveDate = effectiveDate != null ? effectiveDate : "";
        this.retrievalDate = retrievalDate != null ? retrievalDate : "";
        this.version = version != null ? version : "1.0";
        this.supersedesId = supersedesId;
        this.outdated = outdated;
        this.sha256Checksum = sha256Checksum != null ? sha256Checksum : "";
        this.filePath = filePath != null ? filePath : "";
        this.chunkCount = chunkCount;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public void setDocumentTitle(String documentTitle) {
        this.documentTitle = documentTitle;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }

    public String getEffectiveDate() {
        return effectiveDate;
    }

    public void setEffectiveDate(String effectiveDate) {
        this.effectiveDate = effectiveDate;
    }

    public String getRetrievalDate() {
        return retrievalDate;
    }

    public void setRetrievalDate(String retrievalDate) {
        this.retrievalDate = retrievalDate;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSupersedesId() {
        return supersedesId;
    }

    public void setSupersedesId(String supersedesId) {
        this.supersedesId = supersedesId;
    }

    public boolean isOutdated() {
        return outdated;
    }

    public void setOutdated(boolean outdated) {
        this.outdated = outdated;
    }

    public String getSha256Checksum() {
        return sha256Checksum;
    }

    public void setSha256Checksum(String sha256Checksum) {
        this.sha256Checksum = sha256Checksum;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentMetadata that = (DocumentMetadata) o;
        return Objects.equals(id, that.id) || Objects.equals(sha256Checksum, that.sha256Checksum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, sha256Checksum);
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "id='" + id + '\'' +
                ", documentTitle='" + documentTitle + '\'' +
                ", documentType='" + documentType + '\'' +
                ", version='" + version + '\'' +
                ", outdated=" + outdated +
                ", chunkCount=" + chunkCount +
                '}';
    }
}
