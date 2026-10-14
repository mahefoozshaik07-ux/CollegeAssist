package com.collegeassist.backend.service;

import com.collegeassist.backend.model.KnowledgeChunk;
import com.collegeassist.backend.model.QueryScope;
import com.collegeassist.backend.model.RetrievalResult;
import com.collegeassist.backend.model.ScoredKnowledgeChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service responsible for lexical search, in-memory relevance scoring,
 * and domain scope evaluation against ingested knowledge-base chunks.
 */
@Service
public class KnowledgeRetrievalService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalService.class);

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+(-[a-zA-Z0-9]+)?");

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "about", "above", "after", "again", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
            "can", "can't", "cannot", "could", "did", "didn't", "do", "does", "doesn't", "doing", "don't",
            "down", "during", "each", "few", "for", "from", "further", "had", "has", "have", "having",
            "he", "her", "here", "hers", "herself", "him", "himself", "his", "how", "i", "if", "in",
            "into", "is", "isn't", "it", "its", "itself", "just", "me", "more", "most", "my", "myself",
            "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours",
            "ourselves", "out", "over", "own", "please", "same", "she", "should", "so", "some", "such",
            "than", "that", "the", "their", "theirs", "them", "themselves", "then", "there", "these",
            "they", "this", "those", "through", "to", "too", "under", "until", "up", "very", "was",
            "wasn't", "we", "were", "weren't", "what", "when", "where", "which", "while", "who", "whom",
            "why", "with", "won't", "would", "you", "your", "yours", "yourself", "yourselves", "tell", "give",
            "know", "want", "like", "much", "many"
    );

    /**
     * University and academic domain markers used for conservative scope classification.
     */
    private static final Set<String> COLLEGE_DOMAIN_TERMS = Set.of(
            // Institutions & Campus
            "vit", "vitap", "vit-ap", "vtop", "university", "college", "campus", "academic", "academics",
            "school", "department", "institution", "registrar", "dean", "director", "proctor", "faculty", "professor", "hod",
            "bus", "transport", "wifi", "sports", "gym",
            // Academic concepts, Attendance & FFCS
            "attendance", "attend", "condonation", "absence", "absent", "leave", "medical", "od", "on-duty",
            "hospital", "sick", "illness", "class", "classes", "miss", "missed", "missing", "bunk", "bunking",
            "percentage", "percent",
            "ffcs", "course", "courses", "credit", "credits", "curriculum", "syllabus", "registration", "register",
            "add-drop", "prerequisite", "semester", "session", "lecture", "lab", "laboratory", "instructional",
            // Academic Programming & Computer Science Coursework
            "coursework", "recursion", "recursive", "java", "python", "programming", "algorithm", "algorithms",
            "dsa", "oop", "pointer", "pointers", "array", "arrays", "linkedlist", "stack", "stacks",
            "queue", "queues", "tree", "trees", "sorting", "quicksort", "mergesort", "dbms", "sql",
            // Examinations & Grading
            "exam", "exams", "examination", "examinations", "cat", "cat-1", "cat-2", "fat", "quiz", "quizzes",
            "test", "tests", "assessment", "assessments", "hall ticket", "ticket", "hallticket", "grade", "grades",
            "grading", "gpa", "cgpa", "marks", "score", "scores", "arrear", "arrears", "backlog", "backlogs",
            "re-evaluation", "reevaluation", "re-eval", "debarred", "makeup", "make-up", "malpractice",
            // Finance & Fees
            "fee", "fees", "tuition", "payment", "fine", "fines", "due", "dues", "refund", "refunds",
            "scholarship", "scholarships", "receipt", "receipts", "installment", "finance", "bank",
            // Student Life & Residential
            "hostel", "hostels", "mess", "warden", "curfew", "in-time", "intime", "outing", "outings",
            "room", "roommate", "bed", "ac", "non-ac", "laundry", "canteen", "cafeteria", "library",
            // Placement & Career
            "placement", "placements", "cdc", "pat", "internship", "internships", "recruiter", "recruiters",
            "recruitment", "ppo", "ctc", "package", "super dream", "dream offer", "job", "career",
            // Discipline & Notices
            "ragging", "anti-ragging", "udc", "disciplinary", "discipline", "id card", "rules", "regulations",
            "circular", "circulars", "notice", "notices", "handbook", "policy", "policies"
    );

    /**
     * Markers indicating commercial, DevOps, or non-coursework programming tasks
     * that lie strictly outside the university academic coursework scope.
     */
    private static final Set<String> UNRELATED_PROGRAMMING_MARKERS = Set.of(
            "crypto", "cryptocurrency", "bitcoin", "ethereum", "binance", "trading",
            "scraper", "scraping", "selenium", "puppeteer", "beautifulsoup",
            "nginx", "docker", "kubernetes", "k8s", "aws", "ec2", "s3", "azure", "gcp", "devops",
            "stripe", "paypal", "shopify", "wordpress", "minecraft", "game cheat", "hack"
    );

    private final DocumentKnowledgeService documentKnowledgeService;
    private final double minScoreThreshold;
    private final int defaultTopK;
    private QuestionUnderstandingService questionUnderstandingService;
    private HybridRetrievalService hybridRetrievalService;

    @Autowired
    public KnowledgeRetrievalService(
            DocumentKnowledgeService documentKnowledgeService,
            @Value("${collegeassist.retrieval.min-score-threshold:10.0}") double minScoreThreshold,
            @Value("${collegeassist.retrieval.top-k:3}") int defaultTopK) {
        this.documentKnowledgeService = Objects.requireNonNull(documentKnowledgeService, "documentKnowledgeService must not be null");
        this.minScoreThreshold = minScoreThreshold > 0 ? minScoreThreshold : 10.0;
        this.defaultTopK = defaultTopK > 0 ? defaultTopK : 3;
        this.questionUnderstandingService = new QuestionUnderstandingService();
        this.hybridRetrievalService = new HybridRetrievalService(documentKnowledgeService, this.questionUnderstandingService, this.minScoreThreshold, this.defaultTopK);
    }

    public KnowledgeRetrievalService(DocumentKnowledgeService documentKnowledgeService) {
        this(documentKnowledgeService, 10.0, 3);
    }

    public KnowledgeRetrievalService(DocumentKnowledgeService documentKnowledgeService, double minScoreThreshold) {
        this(documentKnowledgeService, minScoreThreshold, 3);
    }

    @Autowired(required = false)
    public void setQuestionUnderstandingService(QuestionUnderstandingService questionUnderstandingService) {
        this.questionUnderstandingService = questionUnderstandingService;
        if (this.hybridRetrievalService != null && questionUnderstandingService != null) {
            this.hybridRetrievalService = new HybridRetrievalService(documentKnowledgeService, questionUnderstandingService, this.minScoreThreshold, this.defaultTopK);
        }
    }

    @Autowired(required = false)
    public void setHybridRetrievalService(HybridRetrievalService hybridRetrievalService) {
        this.hybridRetrievalService = hybridRetrievalService;
    }

    /**
     * Executes hybrid search with default top-K and score threshold.
     */
    public RetrievalResult search(String query) {
        return search(query, null, defaultTopK, minScoreThreshold);
    }

    /**
     * Executes hybrid search with conversation history context resolution.
     */
    public RetrievalResult search(String query, String conversationId) {
        return search(query, conversationId, defaultTopK, minScoreThreshold);
    }

    /**
     * Executes hybrid search and domain scope evaluation for a student query.
     */
    public RetrievalResult search(String query, int topK, double threshold) {
        return search(query, null, topK, threshold);
    }

    /**
     * Executes hybrid search with conversation history context resolution.
     */
    public RetrievalResult search(String query, String conversationId, int topK, double threshold) {
        if (this.hybridRetrievalService != null) {
            return this.hybridRetrievalService.search(query, conversationId, topK, threshold);
        }

        if (query == null || query.isBlank()) {
            return new RetrievalResult(query, QueryScope.OUT_OF_SCOPE, Collections.emptyList(), 0.0, threshold, "Query is empty or blank.");
        }

        List<String> rawTokens = extractTokens(query);
        List<String> queryTokens = rawTokens.stream()
                .filter(t -> !STOP_WORDS.contains(t))
                .collect(Collectors.toList());

        if (queryTokens.isEmpty()) {
            return new RetrievalResult(query, QueryScope.OUT_OF_SCOPE, Collections.emptyList(), 0.0, threshold, "Query contains only generic stop words.");
        }

        List<String> queryStems = queryTokens.stream()
                .map(this::stemWord)
                .collect(Collectors.toList());

        List<String> queryBigrams = extractBigrams(queryTokens);

        List<KnowledgeChunk> allChunks = documentKnowledgeService.getAllChunks();
        List<ScoredKnowledgeChunk> scoredChunks = new ArrayList<>();
        boolean isAttendanceCalc = isAttendanceCalculationQuery(query);

        for (KnowledgeChunk chunk : allChunks) {
            ScoredKnowledgeChunk scored = scoreChunk(chunk, queryTokens, queryStems, queryBigrams, isAttendanceCalc);
            if (scored.getScore() > 0.0) {
                scoredChunks.add(scored);
            }
        }

        Collections.sort(scoredChunks);

        double topScore = scoredChunks.isEmpty() ? 0.0 : scoredChunks.get(0).getScore();

        // Filter by threshold and take top-K
        List<ScoredKnowledgeChunk> passingMatches = scoredChunks.stream()
                .filter(sc -> sc.getScore() >= threshold)
                .limit(Math.max(1, topK))
                .collect(Collectors.toList());

        boolean hasCollegeIntent = detectCollegeIntent(rawTokens, queryStems);

        QueryScope scope;
        String explanation;

        if (!passingMatches.isEmpty()) {
            scope = QueryScope.COLLEGE_GROUNDED;
            explanation = String.format(Locale.ENGLISH,
                    "Retrieved %d relevant section(s) with sufficient evidence (top score: %.2f >= threshold: %.2f).",
                    passingMatches.size(), topScore, threshold);
        } else if (hasCollegeIntent) {
            scope = QueryScope.COLLEGE_UNGROUNDED;
            explanation = String.format(Locale.ENGLISH,
                    "Query indicates university/college context, but no verified documentation in the knowledge base met the relevance threshold (top score: %.2f < threshold: %.2f).",
                    topScore, threshold);
        } else {
            scope = QueryScope.OUT_OF_SCOPE;
            explanation = String.format(Locale.ENGLISH,
                    "Query does not match university/college subject matter and lacks document relevance (top score: %.2f < threshold: %.2f).",
                    topScore, threshold);
        }

        return new RetrievalResult(query, scope, passingMatches, topScore, threshold, explanation);
    }

    /**
     * Detects if the query is asking about missing classes or attendance calculation.
     */
    public boolean isAttendanceCalculationQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase(Locale.ENGLISH);
        boolean hasAttendanceOrClass = lower.contains("class") || lower.contains("attendance") || lower.contains("lecture");
        boolean hasAbsenceOrMiss = lower.contains("miss") || lower.contains("absent") || lower.contains("absence") || lower.contains("bunk") || lower.contains("skip");
        boolean hasCalcOrCount = lower.contains("how many") || lower.contains("total") || lower.contains("calculate") || lower.matches(".*\\b\\d+\\b.*");
        return (hasAttendanceOrClass && hasAbsenceOrMiss) || (hasAttendanceOrClass && hasCalcOrCount && hasAbsenceOrMiss);
    }

    /**
     * Scores a single KnowledgeChunk against normalized query tokens, stems, and bigrams.
     */
    public ScoredKnowledgeChunk scoreChunk(
            KnowledgeChunk chunk,
            List<String> queryTokens,
            List<String> queryStems,
            List<String> queryBigrams) {
        return scoreChunk(chunk, queryTokens, queryStems, queryBigrams, false);
    }

    /**
     * Scores a single KnowledgeChunk with optional attendance calculation query awareness.
     */
    public ScoredKnowledgeChunk scoreChunk(
            KnowledgeChunk chunk,
            List<String> queryTokens,
            List<String> queryStems,
            List<String> queryBigrams,
            boolean isAttendanceCalc) {

        double score = 0.0;
        Set<String> matchedTerms = new LinkedHashSet<>();

        String secTitleNorm = normalizeText(chunk.getSectionTitle());
        String docTitleNorm = normalizeText(chunk.getDocumentTitle());
        String catNorm = normalizeText(chunk.getCategory());
        String contentNorm = normalizeText(chunk.getContent());

        // Boost verified attendance policy when answering attendance calculation inquiries
        if (isAttendanceCalc && chunk.getSourcePath().contains("attendance-policy.md")) {
            if (secTitleNorm.contains("minimum attendance threshold") || secTitleNorm.contains("overview attendance calculation")) {
                score += 25.0;
                matchedTerms.add("attendance-threshold-rule (attendance-policy.md)");
            }
        }

        List<String> secTitleTokens = extractTokens(secTitleNorm);
        List<String> secTitleStems = secTitleTokens.stream().map(this::stemWord).collect(Collectors.toList());

        List<String> contentTokens = extractTokens(contentNorm);
        List<String> contentStems = contentTokens.stream().map(this::stemWord).collect(Collectors.toList());

        int matchedTokenCount = 0;

        for (int i = 0; i < queryTokens.size(); i++) {
            String token = queryTokens.get(i);
            String stem = queryStems.get(i);
            boolean tokenMatched = false;

            // Section title match: highest relevance
            if (secTitleTokens.contains(token)) {
                score += 6.0;
                matchedTerms.add(token + " (section-exact)");
                tokenMatched = true;
            } else if (secTitleStems.contains(stem)) {
                score += 4.5;
                matchedTerms.add(token + " (section-stem)");
                tokenMatched = true;
            }

            // Category match: strong topical signal
            if (catNorm.contains(token) || catNorm.contains(stem)) {
                score += 4.0;
                matchedTerms.add(token + " (category)");
                tokenMatched = true;
            }

            // Document title match
            if (docTitleNorm.contains(token) || docTitleNorm.contains(stem)) {
                score += 3.0;
                matchedTerms.add(token + " (doc-title)");
                tokenMatched = true;
            }

            // Content match with term-frequency dampening
            long stemMatchesInContent = contentStems.stream().filter(s -> s.equals(stem)).count();
            if (stemMatchesInContent > 0) {
                // Diminishing returns: 1 match = 1.5, capped at max 4.5
                double contentScore = Math.min(stemMatchesInContent, 4) * 1.2;
                score += contentScore;
                matchedTerms.add(token + " (content)");
                tokenMatched = true;
            }

            if (tokenMatched) {
                matchedTokenCount++;
            }
        }

        // Multi-term coverage multiplier: rewards chunks that cover multiple query facets
        if (!queryTokens.isEmpty() && matchedTokenCount > 0) {
            double coverageRatio = (double) matchedTokenCount / queryTokens.size();
            // Multiplier ranges from 0.4 to 1.2 based on how many query tokens found a match
            double coverageMultiplier = 0.4 + (0.8 * coverageRatio);
            score *= coverageMultiplier;
        }

        // Phrase / Bigram bonus: rewards natural adjacent phrasings (e.g., "minimum attendance", "fee refund")
        for (String bigram : queryBigrams) {
            if (secTitleNorm.contains(bigram)) {
                score += 8.0;
                matchedTerms.add("\"" + bigram + "\" (section-bigram)");
            } else if (contentNorm.contains(bigram)) {
                score += 4.0;
                matchedTerms.add("\"" + bigram + "\" (content-bigram)");
            }
        }

        return new ScoredKnowledgeChunk(chunk, score, new ArrayList<>(matchedTerms));
    }

    /**
     * Determines whether the user query has conservative college/academic intent.
     */
    public boolean detectCollegeIntent(List<String> rawTokens, List<String> queryStems) {
        // If unrelated general programming markers are present without explicit university context, reject
        boolean hasUnrelatedProg = rawTokens.stream().anyMatch(UNRELATED_PROGRAMMING_MARKERS::contains);
        if (hasUnrelatedProg) {
            boolean hasExplicitCollege = rawTokens.stream().anyMatch(t ->
                    t.equals("vit") || t.equals("vitap") || t.equals("vit-ap") || t.equals("university")
                    || t.equals("college") || t.equals("campus") || t.equals("proctor") || t.equals("hod")
                    || t.equals("course") || t.equals("curriculum") || t.equals("syllabus"));
            if (!hasExplicitCollege) {
                return false;
            }
        }

        for (String token : rawTokens) {
            if (COLLEGE_DOMAIN_TERMS.contains(token)) {
                return true;
            }
            String stem = stemWord(token);
            if (COLLEGE_DOMAIN_TERMS.contains(stem)) {
                return true;
            }
        }
        for (String stem : queryStems) {
            if (COLLEGE_DOMAIN_TERMS.contains(stem)) {
                return true;
            }
        }
        return false;
    }

    public List<String> extractTokens(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ENGLISH));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() > 1 || Character.isDigit(word.charAt(0))) {
                tokens.add(word);
            }
        }
        return tokens;
    }

    public List<String> extractBigrams(List<String> tokens) {
        if (tokens == null || tokens.size() < 2) {
            return Collections.emptyList();
        }
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            bigrams.add(tokens.get(i) + " " + tokens.get(i + 1));
        }
        return bigrams;
    }

    public String normalizeText(String input) {
        if (input == null) return "";
        return input.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9\\s-]", " ");
    }

    /**
     * Lightweight English rule-based suffix normalizer/stemmer.
     */
    public String stemWord(String word) {
        if (word == null || word.length() <= 3) {
            return word != null ? word : "";
        }
        String lower = word.toLowerCase(Locale.ENGLISH);

        // Normalize common college domain synonyms
        if (lower.equals("shortage") || lower.equals("shortages") || lower.equals("shortfall")) {
            return "short";
        }
        if (lower.equals("examinations") || lower.equals("examination") || lower.equals("exams")) {
            return "exam";
        }
        if (lower.equals("registrations") || lower.equals("registering")) {
            return "regist";
        }
        if (lower.startsWith("hospital") || lower.equals("ill") || lower.equals("illness") || lower.equals("sick") || lower.equals("sickness")) {
            return "medic";
        }
        if (lower.startsWith("condon")) {
            return "condon";
        }
        if (lower.startsWith("proctor")) {
            return "proctor";
        }
        if (lower.equals("classes") || lower.equals("class")) {
            return "class";
        }
        if (lower.equals("miss") || lower.equals("missed") || lower.equals("missing")) {
            return "miss";
        }

        // Rule-based suffix stripping
        if (lower.endsWith("sses")) {
            return lower.substring(0, lower.length() - 2);
        }
        if (lower.endsWith("ies")) {
            return lower.substring(0, lower.length() - 3) + "y";
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && !lower.endsWith("is")) {
            lower = lower.substring(0, lower.length() - 1);
        }
        if (lower.endsWith("ing") && lower.length() > 5) {
            lower = lower.substring(0, lower.length() - 3);
        } else if (lower.endsWith("tion") && lower.length() > 5) {
            lower = lower.substring(0, lower.length() - 4);
        } else if (lower.endsWith("ment") && lower.length() > 5) {
            lower = lower.substring(0, lower.length() - 4);
        } else if (lower.endsWith("able") && lower.length() > 5) {
            lower = lower.substring(0, lower.length() - 4);
        } else if (lower.endsWith("ed") && lower.length() > 4) {
            lower = lower.substring(0, lower.length() - 2);
        }

        return lower;
    }

    public double getMinScoreThreshold() {
        return minScoreThreshold;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }
}
