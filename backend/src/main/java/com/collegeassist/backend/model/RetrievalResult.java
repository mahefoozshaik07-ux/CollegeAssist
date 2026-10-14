package com.collegeassist.backend.model;

import java.util.Collections;
import java.util.List;

/**
 * Encapsulates the complete result of a document search and scope evaluation.
 */
public class RetrievalResult {

    private final String query;
    private final QueryScope scope;
    private final List<ScoredKnowledgeChunk> matches;
    private final double topScore;
    private final double threshold;
    private final String explanation;

    public RetrievalResult(
            String query,
            QueryScope scope,
            List<ScoredKnowledgeChunk> matches,
            double topScore,
            double threshold,
            String explanation) {
        this.query = query != null ? query : "";
        this.scope = scope;
        this.matches = matches != null ? Collections.unmodifiableList(matches) : Collections.emptyList();
        this.topScore = topScore;
        this.threshold = threshold;
        this.explanation = explanation != null ? explanation : "";
    }

    public String getQuery() {
        return query;
    }

    public QueryScope getScope() {
        return scope;
    }

    public List<ScoredKnowledgeChunk> getMatches() {
        return matches;
    }

    public double getTopScore() {
        return topScore;
    }

    public double getThreshold() {
        return threshold;
    }

    public String getExplanation() {
        return explanation;
    }

    public boolean hasSufficientEvidence() {
        return scope == QueryScope.COLLEGE_GROUNDED && !matches.isEmpty();
    }

    public ScoredKnowledgeChunk getTopChunk() {
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public String toString() {
        return "RetrievalResult{" +
                "scope=" + scope +
                ", topScore=" + String.format("%.2f", topScore) +
                ", threshold=" + String.format("%.2f", threshold) +
                ", matchCount=" + matches.size() +
                ", explanation='" + explanation + '\'' +
                '}';
    }
}
