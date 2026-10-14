package com.collegeassist.backend.model;

/**
 * Classifies a user query into one of three distinct domain scopes:
 * 1. COLLEGE_GROUNDED: College-related with sufficient document evidence meeting the relevance threshold.
 * 2. COLLEGE_UNGROUNDED: College/academic in nature, but no verified documentation in the knowledge base meets the threshold.
 * 3. OUT_OF_SCOPE: Clearly unrelated to university rules, academics, or campus life.
 */
public enum QueryScope {
    COLLEGE_GROUNDED,
    COLLEGE_UNGROUNDED,
    OUT_OF_SCOPE
}
