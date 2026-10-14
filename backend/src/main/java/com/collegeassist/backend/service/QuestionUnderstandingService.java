package com.collegeassist.backend.service;

import com.collegeassist.backend.model.ChatMessageRecord;
import com.collegeassist.backend.repository.ChatHistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service responsible for robust university question understanding:
 * - Typo and misspelling correction via Damerau-Levenshtein distance
 * - Campus abbreviation and synonym expansion
 * - Preservation of numbers, dates, course codes, and negations
 * - Multi-turn conversational context resolution using message history
 * - Ambiguity and missing-detail detection with concise clarification prompts
 */
@Service
public class QuestionUnderstandingService {

    private static final Logger logger = LoggerFactory.getLogger(QuestionUnderstandingService.class);

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9]+(-[a-zA-Z0-9]+)?");
    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("\\b[a-zA-Z]{3,4}\\d{3,4}[a-zA-Z]?\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?%?\\b");

    private final ChatHistoryStore chatHistoryStore;

    // Institutional Vocabulary for Spell Correction
    private static final Set<String> DOMAIN_VOCABULARY = Set.of(
            "attendance", "attend", "condonation", "absence", "absent", "leave", "medical", "duty",
            "examination", "examinations", "exam", "grading", "grade", "grades", "evaluation",
            "hostel", "hostels", "curfew", "warden", "room", "roommate", "mess", "outing",
            "tuition", "fees", "fee", "refund", "scholarship", "payment", "receipt", "finance",
            "placement", "placements", "internship", "recruitment", "recruiter", "package", "super", "dream",
            "proctor", "proctorship", "faculty", "student", "dean", "director", "registrar",
            "circular", "circulars", "notice", "notices", "calendar", "schedule", "deadline",
            "transport", "bus", "shuttle", "route", "campus", "library", "wifi", "security",
            "registration", "curriculum", "syllabus", "credit", "credits", "semester", "session",
            "malpractice", "discipline", "ragging", "ethics", "debarred", "arrear", "hallticket",
            "requirement", "requirements", "threshold", "eligibility", "criteria", "minimum", "policy", "policies", "rules", "rule"
    );

    // Common Campus Abbreviations mapped to full expansion
    private static final Map<String, String> ABBREVIATION_EXPANSIONS = new LinkedHashMap<>();
    static {
        ABBREVIATION_EXPANSIONS.put("fat", "fat final assessment test");
        ABBREVIATION_EXPANSIONS.put("cat", "cat continuous assessment test");
        ABBREVIATION_EXPANSIONS.put("cat-1", "cat-1 continuous assessment test");
        ABBREVIATION_EXPANSIONS.put("cat-2", "cat-2 continuous assessment test");
        ABBREVIATION_EXPANSIONS.put("od", "od on-duty on duty leave");
        ABBREVIATION_EXPANSIONS.put("ffcs", "ffcs fully flexible credit system registration");
        ABBREVIATION_EXPANSIONS.put("cdc", "cdc career development centre placement");
        ABBREVIATION_EXPANSIONS.put("coe", "coe controller of examinations");
        ABBREVIATION_EXPANSIONS.put("hod", "hod head of department");
        ABBREVIATION_EXPANSIONS.put("dsw", "dsw directorate of student welfare");
        ABBREVIATION_EXPANSIONS.put("ppo", "ppo pre-placement offer");
        ABBREVIATION_EXPANSIONS.put("udc", "udc university disciplinary committee");
        ABBREVIATION_EXPANSIONS.put("gpa", "gpa grade point average");
        ABBREVIATION_EXPANSIONS.put("cgpa", "cgpa cumulative grade point average");
        ABBREVIATION_EXPANSIONS.put("vtop", "vtop student portal");
        ABBREVIATION_EXPANSIONS.put("vitap", "vit-ap university");
        ABBREVIATION_EXPANSIONS.put("vit-ap", "vit-ap university");
        ABBREVIATION_EXPANSIONS.put("lid", "lid last instructional day");
        ABBREVIATION_EXPANSIONS.put("mh", "mh men hostel mens hostel");
        ABBREVIATION_EXPANSIONS.put("lh", "lh ladies hostel womens hostel");
    }

    // Synonym Dictionary for Campus Intent Enrichment
    private static final Map<String, List<String>> SYNONYM_MAP = new LinkedHashMap<>();
    static {
        SYNONYM_MAP.put("requirement", List.of("threshold", "minimum", "eligibility", "criteria"));
        SYNONYM_MAP.put("requirements", List.of("threshold", "minimum", "eligibility", "criteria"));
        SYNONYM_MAP.put("bunk", List.of("miss", "absent", "absence", "attendance"));
        SYNONYM_MAP.put("bunking", List.of("missing", "absent", "attendance"));
        SYNONYM_MAP.put("skip", List.of("miss", "absent", "attendance"));
        SYNONYM_MAP.put("skipping", List.of("missing", "absent", "attendance"));
        SYNONYM_MAP.put("dorm", List.of("hostel", "residential", "room"));
        SYNONYM_MAP.put("dormitory", List.of("hostel", "residential"));
        SYNONYM_MAP.put("waiver", List.of("scholarship", "concession", "refund", "fee"));
        SYNONYM_MAP.put("concession", List.of("scholarship", "waiver", "fee"));
        SYNONYM_MAP.put("shuttle", List.of("bus", "transport", "route"));
        SYNONYM_MAP.put("commute", List.of("transport", "bus"));
        SYNONYM_MAP.put("recheck", List.of("reevaluation", "re-evaluation", "answer", "script"));
        SYNONYM_MAP.put("scrutiny", List.of("reevaluation", "review", "grading"));
        SYNONYM_MAP.put("intime", List.of("curfew", "in-time", "hostel", "entry"));
        SYNONYM_MAP.put("entry time", List.of("curfew", "in-time", "hostel"));
        SYNONYM_MAP.put("wifi", List.of("network", "internet", "it", "lab"));
        SYNONYM_MAP.put("internet", List.of("network", "wifi", "it"));
        SYNONYM_MAP.put("sick", List.of("medical", "hospital", "condonation", "leave"));
        SYNONYM_MAP.put("illness", List.of("medical", "condonation", "leave"));
        SYNONYM_MAP.put("hospital", List.of("medical", "condonation"));
        SYNONYM_MAP.put("arrear", List.of("backlog", "f grade", "re-registration"));
        SYNONYM_MAP.put("backlog", List.of("arrear", "f grade", "fail"));
        SYNONYM_MAP.put("debarred", List.of("n grade", "attendance shortfall", "ineligible"));
        SYNONYM_MAP.put("food", List.of("mess", "catering", "dining", "canteen"));
        SYNONYM_MAP.put("cheat", List.of("malpractice", "cheating", "disciplinary", "penalty"));
        SYNONYM_MAP.put("cheating", List.of("malpractice", "disciplinary", "penalty", "exam"));
    }

    // Follow-up Anaphoric / Context-dependent Indicators
    private static final Set<String> FOLLOW_UP_PRONOUNS = Set.of(
            "it", "that", "this", "those", "these", "there", "them",
            "for that", "about that", "for it", "about it", "who signs",
            "who approves", "how many days", "what about", "is there a fee",
            "can i apply", "what is the deadline", "who to contact"
    );

    @Autowired(required = false)
    public QuestionUnderstandingService(ChatHistoryStore chatHistoryStore) {
        this.chatHistoryStore = chatHistoryStore;
    }

    public QuestionUnderstandingService() {
        this(null);
    }

    /**
     * Complete normalized representation of a processed question.
     */
    public static class ProcessedQuestion {
        private final String originalQuery;
        private final String normalizedQuery;
        private final String enrichedSearchQuery;
        private final List<String> extractedTokens;
        private final List<String> numbers;
        private final List<String> courseCodes;
        private final boolean isFollowUp;
        private final String resolvedTopicContext;
        private final boolean needsClarification;
        private final String clarificationPrompt;

        public ProcessedQuestion(
                String originalQuery,
                String normalizedQuery,
                String enrichedSearchQuery,
                List<String> extractedTokens,
                List<String> numbers,
                List<String> courseCodes,
                boolean isFollowUp,
                String resolvedTopicContext,
                boolean needsClarification,
                String clarificationPrompt) {
            this.originalQuery = originalQuery;
            this.normalizedQuery = normalizedQuery;
            this.enrichedSearchQuery = enrichedSearchQuery;
            this.extractedTokens = extractedTokens;
            this.numbers = numbers;
            this.courseCodes = courseCodes;
            this.isFollowUp = isFollowUp;
            this.resolvedTopicContext = resolvedTopicContext;
            this.needsClarification = needsClarification;
            this.clarificationPrompt = clarificationPrompt;
        }

        public String getOriginalQuery() { return originalQuery; }
        public String getNormalizedQuery() { return normalizedQuery; }
        public String getEnrichedSearchQuery() { return enrichedSearchQuery; }
        public List<String> getExtractedTokens() { return extractedTokens; }
        public List<String> getNumbers() { return numbers; }
        public List<String> getCourseCodes() { return courseCodes; }
        public boolean isFollowUp() { return isFollowUp; }
        public String getResolvedTopicContext() { return resolvedTopicContext; }
        public boolean isNeedsClarification() { return needsClarification; }
        public String getClarificationPrompt() { return clarificationPrompt; }
    }

    /**
     * Analyzes and enriches the user query for search and grounding.
     */
    public ProcessedQuestion processQuestion(String rawQuery, String conversationId) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return new ProcessedQuestion("", "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), false, null, false, null);
        }

        String cleaned = rawQuery.trim();
        List<String> numbers = extractNumbers(cleaned);
        List<String> courseCodes = extractCourseCodes(cleaned);

        // 1. Correct spelling and typos
        String typoCorrected = correctTypos(cleaned);

        // 2. Expand abbreviations and synonyms
        String expanded = expandAbbreviationsAndSynonyms(typoCorrected);

        // 3. Resolve multi-turn conversational context if it's a follow-up
        boolean isFollowUp = detectFollowUpIntent(cleaned);
        String resolvedContext = null;
        String finalEnrichedQuery = expanded;

        if (isFollowUp && conversationId != null && chatHistoryStore != null) {
            resolvedContext = resolveConversationContext(conversationId);
            if (resolvedContext != null && !resolvedContext.isBlank()) {
                finalEnrichedQuery = expanded + " " + resolvedContext;
                logger.debug("Resolved follow-up context for conv {}: '{}'", conversationId, resolvedContext);
            }
        }

        // 4. Detect Ambiguity / Underspecified questions requiring clarification
        boolean needsClarification = checkNeedsClarification(cleaned);
        String clarificationPrompt = needsClarification ? generateClarificationPrompt(cleaned) : null;

        List<String> tokens = extractTokens(finalEnrichedQuery);

        return new ProcessedQuestion(
                cleaned,
                typoCorrected,
                finalEnrichedQuery,
                tokens,
                numbers,
                courseCodes,
                isFollowUp,
                resolvedContext,
                needsClarification,
                clarificationPrompt
        );
    }

    /**
     * Corrects misspellings by matching tokens against domain vocabulary using edit distance.
     */
    public String correctTypos(String text) {
        if (text == null || text.isBlank()) return "";
        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            String cleanWord = word.replaceAll("[^a-zA-Z0-9-]", "").toLowerCase(Locale.ENGLISH);

            if (cleanWord.length() >= 4 && !DOMAIN_VOCABULARY.contains(cleanWord)) {
                String bestMatch = findClosestVocabularyMatch(cleanWord);
                if (bestMatch != null) {
                    // Replace while preserving surrounding punctuation
                    String corrected = word.toLowerCase(Locale.ENGLISH).replace(cleanWord, bestMatch);
                    result.append(corrected).append(" ");
                    continue;
                }
            }
            result.append(word).append(" ");
        }
        return result.toString().trim();
    }

    private String findClosestVocabularyMatch(String target) {
        String bestMatch = null;
        int minDistance = Integer.MAX_VALUE;

        for (String vocab : DOMAIN_VOCABULARY) {
            // Only compare words of similar length
            if (Math.abs(vocab.length() - target.length()) <= 2) {
                int dist = damerauLevenshteinDistance(target, vocab);
                // Allow distance <= 2 for words >= 5 letters, or distance <= 1 for 4-letter words
                int maxAllowed = target.length() >= 5 ? 2 : 1;
                if (dist <= maxAllowed && dist < minDistance) {
                    minDistance = dist;
                    bestMatch = vocab;
                }
            }
        }
        return bestMatch;
    }

    public static int damerauLevenshteinDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] d = new int[len1 + 1][len2 + 1];

        for (int i = 0; i <= len1; i++) d[i][0] = i;
        for (int j = 0; j <= len2; j++) d[0][j] = j;

        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = (s1.charAt(i - 1) == s2.charAt(j - 1)) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost
                );
                // Transposition
                if (i > 1 && j > 1 && s1.charAt(i - 1) == s2.charAt(j - 2) && s1.charAt(i - 2) == s2.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + cost);
                }
            }
        }
        return d[len1][len2];
    }

    /**
     * Expands known institutional abbreviations and synonyms.
     */
    public String expandAbbreviationsAndSynonyms(String text) {
        if (text == null || text.isBlank()) return "";
        String lower = text.toLowerCase(Locale.ENGLISH);
        StringBuilder expansions = new StringBuilder(text);

        for (Map.Entry<String, String> entry : ABBREVIATION_EXPANSIONS.entrySet()) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(lower).find()) {
                expansions.append(" ").append(entry.getValue());
            }
        }

        for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(lower).find()) {
                for (String syn : entry.getValue()) {
                    expansions.append(" ").append(syn);
                }
            }
        }

        return expansions.toString();
    }

    /**
     * Detects if the student query is a contextual follow-up.
     */
    public boolean detectFollowUpIntent(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase(Locale.ENGLISH);

        for (String marker : FOLLOW_UP_PRONOUNS) {
            if (lower.contains(marker)) {
                return true;
            }
        }

        List<String> tokens = extractTokens(query);
        // Very short inquiries like "what about deadline?", "and for labs?"
        return tokens.size() <= 4 && (lower.startsWith("and ") || lower.startsWith("what about") || lower.startsWith("how about"));
    }

    /**
     * Resolves context from recent turns in the message history repository.
     */
    public String resolveConversationContext(String conversationId) {
        if (chatHistoryStore == null || conversationId == null || conversationId.isBlank()) {
            return null;
        }

        try {
            List<ChatMessageRecord> history = chatHistoryStore.findMessagesByConversationIdOrderBySequenceNumberAsc(conversationId);
            if (history == null || history.isEmpty()) {
                return null;
            }

            // Look at the last 2 messages (prior assistant answer and prior user question)
            int size = history.size();
            StringBuilder contextTerms = new StringBuilder();
            Set<String> seen = new HashSet<>();

            for (int i = size - 1; i >= Math.max(0, size - 3); i--) {
                ChatMessageRecord record = history.get(i);
                String content = record.getContent();
                if (content != null && !content.isBlank()) {
                    List<String> tokens = extractTokens(content);
                    for (String token : tokens) {
                        if (DOMAIN_VOCABULARY.contains(token) && seen.add(token)) {
                            contextTerms.append(token).append(" ");
                        }
                    }
                }
            }

            return contextTerms.toString().trim();
        } catch (Exception ex) {
            logger.warn("Could not retrieve conversation history context: {}", ex.getMessage());
            return null;
        }
    }

    public List<String> extractNumbers(String text) {
        List<String> nums = new ArrayList<>();
        Matcher m = NUMBER_PATTERN.matcher(text);
        while (m.find()) {
            nums.add(m.group());
        }
        return nums;
    }

    public List<String> extractCourseCodes(String text) {
        List<String> codes = new ArrayList<>();
        Matcher m = COURSE_CODE_PATTERN.matcher(text);
        while (m.find()) {
            codes.add(m.group().toUpperCase(Locale.ENGLISH));
        }
        return codes;
    }

    public List<String> extractTokens(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ENGLISH));
        while (matcher.find()) {
            String word = matcher.group();
            if (word.length() > 1 || Character.isDigit(word.charAt(0)) || "fnwups".indexOf(word.charAt(0)) != -1) {
                tokens.add(word);
            }
        }

        return tokens;
    }

    public boolean checkNeedsClarification(String query) {
        if (query == null) return false;
        String clean = query.replaceAll("[^a-zA-Z0-9 ]", " ").replaceAll("\\s+", " ").trim().toLowerCase(Locale.ENGLISH);
        // Single word or underspecified queries
        return clean.equals("fee") || clean.equals("fees") || clean.equals("what is the fee")
                || clean.equals("what is the fees") || clean.equals("how much is the fee")
                || clean.equals("tell me about fee") || clean.equals("refund")
                || clean.equals("how to get refund")
                || clean.equals("leave") || clean.equals("can i take leave")
                || clean.equals("how to take leave")
                || clean.equals("i want leave") || clean.equals("can i get leave")
                || clean.equals("apply for leave");
    }

    public String generateClarificationPrompt(String query) {
        String lower = query.toLowerCase(Locale.ENGLISH);
        if (lower.contains("fee") || lower.contains("fees")) {
            return "### ❓ Clarification Needed\n\n"
                    + "Could you please specify which fee category you are inquiring about?\n\n"
                    + "- **Semester Tuition Fees:** Academic program fee payments and late fines via VTOP.\n"
                    + "- **Hostel & Mess Fees:** Room allocation, caution deposit, and non-refundable admission fees.\n"
                    + "- **Arrear / Make-up Exam Fees:** Re-registration fee per credit under FFCS.\n"
                    + "- **Re-evaluation Fees:** Processing fee for viewing and re-assessing FAT answer scripts.";
        }
        if (lower.contains("refund")) {
            return "### ❓ Clarification Needed\n\n"
                    + "Could you please clarify whether you are asking about:\n\n"
                    + "- **Tuition Fee Refund:** Under UGC admission withdrawal guidelines (15 to 30 working days processing).\n"
                    + "- **Hostel Fee Refund:** Monthly 10% deduction schedule for vacated rooms.";
        }
        if (lower.contains("leave")) {
            return "### ❓ Clarification Needed\n\n"
                    + "Could you please specify which type of leave you are inquiring about?\n\n"
                    + "- **Hostel Outing / Home Leave:** Weekend or overnight exit permit requiring warden/parent approval on VTOP.\n"
                    + "- **Medical Leave / Condonation:** Absence due to illness requiring medical certificates submitted to Proctor within working days.\n"
                    + "- **On-Duty (OD) Leave:** Academic conferences, sports competitions, or placement drives endorsed by faculty coordinator and HOD.";
        }
        return null;
    }
}
