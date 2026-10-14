package com.collegeassist.backend.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Wraps a KnowledgeChunk with a computed lexical relevance score and
 * the specific query terms that contributed to the match.
 */
public class ScoredKnowledgeChunk implements Comparable<ScoredKnowledgeChunk> {

    private final KnowledgeChunk chunk;
    private final double score;
    private final List<String> matchedTerms;

    public ScoredKnowledgeChunk(KnowledgeChunk chunk, double score, List<String> matchedTerms) {
        this.chunk = Objects.requireNonNull(chunk, "chunk must not be null");
        this.score = score;
        this.matchedTerms = matchedTerms != null ? Collections.unmodifiableList(matchedTerms) : Collections.emptyList();
    }

    public KnowledgeChunk getChunk() {
        return chunk;
    }

    public double getScore() {
        return score;
    }

    public List<String> getMatchedTerms() {
        return matchedTerms;
    }

    // Convenience delegates for metadata preservation
    public String getId() {
        return chunk.getId();
    }

    public String getCategory() {
        return chunk.getCategory();
    }

    public String getSourcePath() {
        return chunk.getSourcePath();
    }

    public String getFilename() {
        return chunk.getFilename();
    }

    public String getDocumentTitle() {
        return chunk.getDocumentTitle();
    }

    public String getSectionTitle() {
        return chunk.getSectionTitle();
    }

    public String getContent() {
        return chunk.getContent();
    }

    public int getWordCount() {
        return chunk.getWordCount();
    }

    @Override
    public int compareTo(ScoredKnowledgeChunk other) {
        // Descending order by score
        return Double.compare(other.score, this.score);
    }

    @Override
    public String toString() {
        return "ScoredKnowledgeChunk{" +
                "score=" + String.format("%.2f", score) +
                ", sectionTitle='" + getSectionTitle() + '\'' +
                ", sourcePath='" + getSourcePath() + '\'' +
                ", matchedTerms=" + matchedTerms +
                '}';
    }
}
