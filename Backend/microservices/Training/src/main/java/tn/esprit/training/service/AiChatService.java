package tn.esprit.training.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.ChatResponseDTO;
import tn.esprit.training.entity.Training;
import tn.esprit.training.entity.TrainingContent;
import tn.esprit.training.repository.AvisRepository;
import tn.esprit.training.repository.PlatformSnapshotJdbcRepository;
import tn.esprit.training.repository.TrainingContentRepository;
import tn.esprit.training.repository.TrainingRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiChatService {
    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "what", "which", "who", "how", "when", "where", "why",
            "can", "could", "would", "should", "i", "me", "my", "do", "does", "did", "to", "of", "in", "for", "on",
            "with", "and", "or", "but", "it", "its", "this", "that", "you", "your", "we", "our", "they", "them",
            "have", "has", "had", "be", "been", "being", "get", "got", "give", "tell", "about", "some", "any",
            "there", "here", "want", "need", "like", "please", "thanks", "thank", "hello", "hi", "hey", "yes", "no",
            "not", "just", "really", "very", "too", "also", "into", "from", "at", "as", "by", "if", "so", "than"
    );

    /** Short greetings only — answered from DB stats without calling the LLM. */
    private static final Pattern P_GREET_SHORT = Pattern.compile(
            "(?i)^\\s*(hi|hello|hey|bonjour|salut|good\\s+morning|good\\s+afternoon|good\\s+evening)\\b[\\s,!?.]*$");

    private static final Pattern P_COUNT_COURSES = Pattern.compile(
            "(?i)\\b(how\\s+many\\s+courses?|how\\s+many\\s+trainings?|number\\s+of\\s+courses?|count\\s+of\\s+courses?|"
                    + "courses?\\s+count|total\\s+courses?|nombre\\s+de\\s+cours)\\b");

    private static final Pattern P_LIST_TITLES = Pattern.compile(
            "(?i)\\b(list|show|give)\\s+(me\\s+)?(all\\s+)?(the\\s+)?(course\\s+)?titles?\\b|\\b(all\\s+)?course\\s+titles?\\b|"
                    + "\\btitles?\\s+(please|now)?\\s*$|\\ball\\s+titles?\\b");

    /** "Show me Web Development courses" → treat as catalog search (topic), not OUT_OF_SCOPE. */
    private static final Pattern P_SHOW_LIST_COURSES = Pattern.compile(
            "(?i)\\b(show|give|list|recommend)(\\s+me)?\\s+.+\bcourses?\\b");

    private final TrainingRepository trainingRepository;
    private final TrainingContentRepository contentRepository;
    private final AvisRepository avisRepository;
    private final PlatformSnapshotJdbcRepository platformJdbc;
    private final AnthropicClientService anthropicClientService;
    private final GroqClientService groqClientService;

    public AiChatService(
            TrainingRepository trainingRepository,
            TrainingContentRepository contentRepository,
            AvisRepository avisRepository,
            PlatformSnapshotJdbcRepository platformJdbc,
            AnthropicClientService anthropicClientService,
            GroqClientService groqClientService
    ) {
        this.trainingRepository = trainingRepository;
        this.contentRepository = contentRepository;
        this.avisRepository = avisRepository;
        this.platformJdbc = platformJdbc;
        this.anthropicClientService = anthropicClientService;
        this.groqClientService = groqClientService;
    }

    public ChatResponseDTO chat(String message, String userDisplayName) throws Exception {
        String sanitizedName = sanitizeDisplayName(userDisplayName);
        Intent intent = detectIntent(message);
        if (intent.type == IntentType.OUT_OF_SCOPE) {
            return platformGroundedChat(message != null ? message.trim() : "", sanitizedName);
        }
        List<Training> trainings = trainingRepository.findAll();
        List<Long> highlightIds = new ArrayList<>();

        List<Map<String, Object>> items = new ArrayList<>();

        switch (intent.type) {
            case TOP_RATED -> {
                trainings = trainings.stream()
                        .sorted((a, b) -> Double.compare(avg(b.getId()), avg(a.getId())))
                        .limit(3)
                        .collect(Collectors.toList());
                highlightIds = trainings.stream().map(Training::getId).collect(Collectors.toList());
            }
            case CHEAPEST -> {
                trainings = trainings.stream().sorted(Comparator.comparing(Training::getPrice)).limit(3).collect(Collectors.toList());
                highlightIds = trainings.stream().map(Training::getId).collect(Collectors.toList());
            }
            case MOST_REVIEWED -> {
                trainings = trainings.stream().sorted((a, b) -> Long.compare(count(b.getId()), count(a.getId()))).limit(3).collect(Collectors.toList());
                highlightIds = trainings.stream().map(Training::getId).collect(Collectors.toList());
            }
            case LEVEL -> {
                trainings = trainings.stream().filter(t -> t.getLevel() != null && t.getLevel().toLowerCase().contains(intent.level.toLowerCase()))
                        .sorted((a, b) -> Double.compare(avg(b.getId()), avg(a.getId()))).limit(3).collect(Collectors.toList());
                highlightIds = trainings.stream().map(Training::getId).collect(Collectors.toList());
            }
            case LESSONS_OF -> trainings = trainings.stream().filter(t -> t.getTitle() != null && t.getTitle().toLowerCase().contains(intent.title.toLowerCase())).limit(1).collect(Collectors.toList());
            case DETAILS_OF -> {
                trainings = trainings.stream().filter(t -> t.getTitle() != null && t.getTitle().toLowerCase().contains(intent.title.toLowerCase())).limit(1).collect(Collectors.toList());
                highlightIds = trainings.stream().map(Training::getId).collect(Collectors.toList());
            }
            case SEARCH -> {
                String q = intent.title != null ? intent.title : "";
                trainings = trainings.stream()
                        .filter(t -> trainingMatchesSearchQuery(t, q))
                        .limit(12)
                        .collect(Collectors.toList());
                highlightIds = trainings.stream().map(Training::getId).collect(Collectors.toList());
            }
            case REVIEWS_OF, AVG_RATING_OF, REVIEWS_COUNT_OF -> {
                // Built below
            }
            case OUT_OF_SCOPE -> trainings = List.of();
        }

        if (intent.type == IntentType.REVIEWS_OF || intent.type == IntentType.AVG_RATING_OF || intent.type == IntentType.REVIEWS_COUNT_OF) {
            Optional<Training> match = trainingRepository.findAll().stream()
                    .filter(t -> t.getTitle() != null && t.getTitle().toLowerCase().contains((intent.title != null ? intent.title : "").toLowerCase()))
                    .findFirst();
            if (match.isPresent()) {
                Training t = match.get();
                if (intent.type == IntentType.REVIEWS_OF) {
                    var reviews = avisRepository.findByTraining_IdOrderByCreatedAtDesc(t.getId()).stream().limit(5).toList();
                    for (var r : reviews) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("trainingId", t.getId());
                        row.put("trainingTitle", t.getTitle());
                        row.put("authorName", r.getAuthorName());
                        row.put("rating", r.getRating());
                        row.put("comment", r.getComment());
                        row.put("createdAt", r.getCreatedAt());
                        items.add(row);
                    }
                } else if (intent.type == IntentType.AVG_RATING_OF) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("trainingId", t.getId());
                    row.put("trainingTitle", t.getTitle());
                    row.put("averageRating", round(avg(t.getId())));
                    items.add(row);
                } else if (intent.type == IntentType.REVIEWS_COUNT_OF) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("trainingId", t.getId());
                    row.put("trainingTitle", t.getTitle());
                    row.put("reviewsCount", count(t.getId()));
                    items.add(row);
                }
                highlightIds = List.of(t.getId());
            }
        } else {
            for (Training t : trainings) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", t.getId());
                row.put("title", t.getTitle());
                row.put("category", t.getCategory());
                row.put("level", t.getLevel());
                row.put("price", t.getPrice());
                row.put("averageRating", round(avg(t.getId())));
                row.put("reviewsCount", count(t.getId()));
                List<TrainingContent> contents = contentRepository.findByTraining_Id(t.getId());
                row.put("contentsCount", contents.size());
                if (intent.type == IntentType.LESSONS_OF) {
                    row.put("contents", contents.stream().sorted(Comparator.comparing(TrainingContent::getId).reversed())
                            .map(c -> Map.of("id", c.getId(), "title", c.getTitle())).collect(Collectors.toList()));
                }
                items.add(row);
            }
        }

        String prompt = buildTrainingPlusPlatformPrompt(message, sanitizedName, items, trainings.size());

        String answer = callLlmOrNull(prompt);
        if (answer == null || answer.isBlank()) {
            answer = fallbackAnswer(intent, items);
        } else if (looksLikeGenericRefusal(answer)) {
            answer = fallbackAnswer(intent, items);
        }

        ChatResponseDTO resp = new ChatResponseDTO();
        resp.setReply(answer);
        resp.setIntent(intent.label());
        resp.setData(items);
        resp.setSqlGenerated(intent.sqlHint());
        return resp;
    }

    /** Backward-compatible overload for older clients. */
    public ChatResponseDTO chat(String message) throws Exception {
        return chat(message, null);
    }

    /**
     * Free-form questions across the whole Skillio platform:
     * - Pull keyword-ranked courses from DB
     * - Pull best-effort platform snapshot (companies, job offers, events, pricing plans, subscription/user aggregates)
     * - Let the LLM answer strictly from that JSON wrapper
     */
    private ChatResponseDTO platformGroundedChat(String message, String sanitizedDisplayName) {
        String norm = message != null ? message.trim() : "";
        List<Training> all = trainingRepository.findAll();

        String quick = tryQuickPlatformReply(norm, sanitizedDisplayName, all);
        if (quick != null) {
            List<Training> picked = selectTrainingsForQuestion(norm, all);
            List<Map<String, Object>> items = buildCatalogContextRows(picked);
            ChatResponseDTO resp = new ChatResponseDTO();
            resp.setReply(quick);
            resp.setIntent("PLATFORM_QUICK");
            resp.setData(items);
            resp.setSqlGenerated("Direct answer from database");
            return resp;
        }

        List<Training> picked = selectTrainingsForQuestion(norm, all);
        List<Map<String, Object>> items = buildCatalogContextRows(picked);

        String platformJson = buildPlatformWrapperJson(norm, sanitizedDisplayName, items, all.size());
        String prompt = buildFreeFormPlatformPrompt(norm, platformJson);
        String answer = callLlmOrNull(prompt);
        if (answer == null || answer.isBlank()) {
            answer = summarizeCatalogFallback(items, all.size());
        } else if (looksLikeGenericRefusal(answer)) {
            answer = summarizeCatalogFallback(items, all.size());
        }

        ChatResponseDTO resp = new ChatResponseDTO();
        resp.setReply(answer);
        resp.setIntent("PLATFORM_AWARE");
        resp.setData(items);
        resp.setSqlGenerated("Keyword-ranked platform snapshot + LLM");
        return resp;
    }

    /**
     * Instant answers for common questions — no LLM, so behavior is always grounded in the DB.
     */
    private String tryQuickPlatformReply(String m, String sanitizedDisplayName, List<Training> all) {
        if (m.isEmpty()) {
            return null;
        }
        int n = all == null ? 0 : all.size();
        long companies = safeCount(() -> platformJdbc.companiesCount(), "companiesCount");
        long jobs = safeCount(() -> platformJdbc.jobOffersCount(), "jobOffersCount");
        long events = safeCount(() -> platformJdbc.eventsCount(), "eventsCount");
        long plans = safeCount(() -> platformJdbc.pricingPlansCount(), "pricingPlansCount");
        long subs = safeCount(() -> platformJdbc.subscriptionsCount(), "subscriptionsCount");

        if (P_GREET_SHORT.matcher(m).matches()) {
            String name = formatGreetingDisplayName(sanitizedDisplayName);
            String hello = name.isBlank() ? "Hello!" : ("Hello, " + name + "!");
            return hello + " Here’s what I can see in the database right now: "
                    + n + " course(s), "
                    + companies + " companie(s), "
                    + jobs + " job offer(s), "
                    + events + " event(s), "
                    + plans + " pricing plan(s), "
                    + subs + " subscription(s). "
                    + "Ask about any of these (counts, lists, or details).";
        }
        if (P_COUNT_COURSES.matcher(m).find()) {
            if (n == 0) {
                return "There are 0 courses in the database right now.";
            }
            return "There are " + n + " course(s) in the catalog.";
        }
        if (containsAny(m, "company", "companies", "entreprise", "entreprises", "société", "societe")) {
            return "There are " + companies + " companie(s) in the database.";
        }
        if (containsAny(m, "job", "jobs", "job offer", "job offers", "offre", "offres", "emploi", "emplois")) {
            return "There are " + jobs + " job offer(s) in the database.";
        }
        if (containsAny(m, "event", "events", "événement", "evenement", "événements", "evenements")) {
            return "There are " + events + " event(s) in the database.";
        }
        if (containsAny(m, "pricing", "price plan", "plan", "plans", "tarif", "tarifs", "pricing plan", "pricing plans", "abonnement", "abonnements")) {
            // If user asked about subscriptions/pricing broadly, give both counts.
            return "There are " + plans + " pricing plan(s) and " + subs + " subscription(s) in the database.";
        }
        if (P_LIST_TITLES.matcher(m).find()) {
            if (n == 0) {
                return "There are no courses in the database yet.";
            }
            String bullet = all.stream()
                    .map(Training::getTitle)
                    .filter(t -> t != null && !t.isBlank())
                    .map(String::trim)
                    .map(t -> "• " + t)
                    .collect(Collectors.joining("\n"));
            return "Here are all " + n + " course title(s):\n" + bullet;
        }
        return null;
    }

    private List<Map<String, Object>> buildCatalogContextRows(List<Training> picked) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Training t : picked) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", t.getId());
            row.put("title", t.getTitle());
            row.put("category", t.getCategory());
            row.put("level", t.getLevel());
            row.put("price", t.getPrice() != null ? t.getPrice().doubleValue() : 0);
            row.put("language", t.getLanguage());
            row.put("status", t.getStatus());
            String d = t.getDescription();
            if (d != null && d.length() > 360) {
                d = d.substring(0, 357) + "...";
            }
            row.put("description", d != null ? d : "");
            row.put("averageRating", round(avg(t.getId())));
            row.put("reviewsCount", count(t.getId()));
            List<TrainingContent> contents = contentRepository.findByTraining_Id(t.getId());
            row.put("lessonsCount", contents.size());
            items.add(row);
        }
        return items;
    }

    private boolean looksLikeGenericRefusal(String answer) {
        String x = answer.toLowerCase(Locale.ROOT);
        return x.contains("i can only answer")
                || x.contains("related to trainings")
                || x.contains("trainings, contents and reviews")
                || x.contains("i'm not able to help with that")
                || x.contains("i cannot help")
                || x.contains("can't answer that")
                || x.contains("outside my scope");
    }

    /** Match user search text against title, category, and description. */
    private boolean trainingMatchesSearchQuery(Training t, String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String blob = (nz(t.getTitle()) + " " + nz(t.getCategory()) + " " + nz(t.getDescription())).toLowerCase(Locale.ROOT);
        String q = raw.toLowerCase(Locale.ROOT).trim();
        if (blob.contains(q)) {
            return true;
        }
        for (String w : q.split("\\s+")) {
            if (w.length() < 2) {
                continue;
            }
            if (blob.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private String extractCourseTopicPhrase(String message) {
        String t = message.toLowerCase(Locale.ROOT);
        t = t.replaceAll("(?i)^(show|give|list|recommend)(\\s+me)?\\s+", "");
        t = t.replaceAll("(?i)\\b(please|some|any|the|all|my|every|available|only|just)\\b", " ");
        t = t.replaceAll("(?i)\\b(courses?|training|trainings|class|classes)\\b", " ");
        return t.replaceAll("\\s+", " ").trim();
    }

    private String buildFreeFormCatalogPrompt(String userMessage, String coursesJson) {
        String safe = userMessage == null ? "" : userMessage.replace("\\", "\\\\").replace("\"", "'");
        return "You are Skillio's AI assistant for an e-learning platform.\n\n"
                + "The JSON includes \"courses\" (relevant rows) and \"totalCoursesInDatabase\" (full count). "
                + "Every course object is REAL data from the database.\n\n"
                + "Rules:\n"
                + "- Answer using ONLY this JSON. Never invent courses, prices, ratings, or lesson counts.\n"
                + "- NEVER reply with refusals such as \"I can only answer questions about trainings\" or similar — "
                + "you already have catalog data. If \"courses\" is non-empty, help the user with it. "
                + "If you need the full list of titles, say how many exist (totalCoursesInDatabase) and summarize what appears in \"courses\".\n"
                + "- For \"how many courses\": use totalCoursesInDatabase.\n"
                + "- If the data is insufficient, say what is missing; do not refuse outright.\n"
                + "- Use exact \"title\" strings when naming courses.\n"
                + "- Be concise; use short bullets when comparing courses.\n"
                + "- For greetings, greet briefly and offer examples based on categories/levels in the JSON.\n\n"
                + "Catalog JSON:\n"
                + coursesJson
                + "\n\nUser message:\n"
                + safe;
    }

    private String buildFreeFormPlatformPrompt(String userMessage, String platformJson) {
        String safe = userMessage == null ? "" : userMessage.replace("\\", "\\\\").replace("\"", "'");
        return "You are Skillio's AI assistant for the whole platform (courses, companies, job offers, events, pricing plans, subscriptions stats, user role stats).\n\n"
                + "The JSON below contains REAL rows from the platform database and aggregates.\n\n"
                + "Rules:\n"
                + "- Answer using ONLY this JSON. Never invent entities, counts, prices, dates, or stats.\n"
                + "- NEVER refuse for \"non-training\" topics. Use the companies/jobOffers/events/pricingPlans/subscriptionsByStatus/userCountsByRole sections.\n"
                + "- If userDisplayName is present in JSON and the user greets you, you may greet as \"Hello, <Full Name>\".\n"
                + "- If a table is empty/missing in JSON, say it is not available in the database snapshot.\n"
                + "- For count questions: use totals.* when available.\n"
                + "- Be concise; use short bullets when listing.\n\n"
                + "Platform JSON:\n"
                + platformJson
                + "\n\nUser message:\n"
                + safe;
    }

    private String buildTrainingPlusPlatformPrompt(String userMessage, String sanitizedDisplayName, List<Map<String, Object>> courseItems, int totalCoursesInDb) {
        String norm = userMessage != null ? userMessage.trim() : "";
        Set<String> tokens = tokenize(norm);

        List<Map<String, Object>> companies = rankMapsByTokens(platformJdbc.companies(120), tokens, "name", "industry", "location", "email", "website");
        List<Map<String, Object>> jobOffers = rankMapsByTokens(platformJdbc.jobOffersWithCompanyName(120), tokens, "title", "description", "companyName");
        List<Map<String, Object>> events = rankMapsByTokens(platformJdbc.events(120), tokens, "title", "location", "startDate", "endDate");
        List<Map<String, Object>> pricingPlans = rankMapsByTokens(platformJdbc.pricingPlans(120), tokens, "name", "description");

        Map<String, Object> wrapper = new HashMap<>();
        if (sanitizedDisplayName != null && !sanitizedDisplayName.isBlank()) {
            wrapper.put("userDisplayName", sanitizedDisplayName);
        }
        wrapper.put("courses", courseItems);
        wrapper.put("companies", topN(companies, 20));
        wrapper.put("jobOffers", topN(jobOffers, 20));
        wrapper.put("events", topN(events, 20));
        wrapper.put("pricingPlans", topN(pricingPlans, 20));
        wrapper.put("subscriptionsByStatus", platformJdbc.subscriptionsByStatus());
        wrapper.put("userCountsByRole", platformJdbc.userCountsByRole());

        Map<String, Object> totals = new HashMap<>();
        totals.put("courses", totalCoursesInDb);
        totals.put("companies", safeCount(() -> platformJdbc.companiesCount(), "companiesCount"));
        totals.put("jobOffers", safeCount(() -> platformJdbc.jobOffersCount(), "jobOffersCount"));
        totals.put("events", safeCount(() -> platformJdbc.eventsCount(), "eventsCount"));
        totals.put("pricingPlans", safeCount(() -> platformJdbc.pricingPlansCount(), "pricingPlansCount"));
        totals.put("subscriptions", safeCount(() -> platformJdbc.subscriptionsCount(), "subscriptionsCount"));
        totals.put("users", safeCount(() -> platformJdbc.usersCount(), "usersCount"));
        wrapper.put("totals", totals);

        String json = toJson(wrapper);
        String safeMsg = userMessage == null ? "" : userMessage.replace("\\", "\\\\").replace("\"", "'");
        return "You are Skillio AI assistant.\n\n"
                + "Use ONLY the JSON data below (courses + platform snapshot). Never invent facts.\n"
                + "If the answer is not present in JSON, reply: 'Not found in database.'\n"
                + "If userDisplayName is present and the user greets you, you may greet with their full name.\n"
                + "Be concise.\n\n"
                + "JSON:\n"
                + json
                + "\n\nUser question:\n"
                + safeMsg;
    }

    private String buildPlatformWrapperJson(String userMessage, String sanitizedDisplayName, List<Map<String, Object>> rankedCourseRows, int totalCoursesInDb) {
        String norm = userMessage != null ? userMessage.trim() : "";
        Set<String> tokens = tokenize(norm);

        List<Map<String, Object>> companies = rankMapsByTokens(platformJdbc.companies(160), tokens, "name", "industry", "location", "email", "website");
        List<Map<String, Object>> jobOffers = rankMapsByTokens(platformJdbc.jobOffersWithCompanyName(160), tokens, "title", "description", "companyName");
        List<Map<String, Object>> events = rankMapsByTokens(platformJdbc.events(160), tokens, "title", "location", "startDate", "endDate");
        List<Map<String, Object>> pricingPlans = rankMapsByTokens(platformJdbc.pricingPlans(160), tokens, "name", "description");

        Map<String, Object> wrapper = new HashMap<>();
        if (sanitizedDisplayName != null && !sanitizedDisplayName.isBlank()) {
            wrapper.put("userDisplayName", sanitizedDisplayName);
        }
        wrapper.put("courses", topN(rankedCourseRows, 24));
        wrapper.put("companies", topN(companies, 24));
        wrapper.put("jobOffers", topN(jobOffers, 24));
        wrapper.put("events", topN(events, 24));
        wrapper.put("pricingPlans", topN(pricingPlans, 24));
        wrapper.put("subscriptionsByStatus", platformJdbc.subscriptionsByStatus());
        wrapper.put("userCountsByRole", platformJdbc.userCountsByRole());

        Map<String, Object> totals = new HashMap<>();
        totals.put("courses", totalCoursesInDb);
        totals.put("companies", safeCount(() -> platformJdbc.companiesCount(), "companiesCount"));
        totals.put("jobOffers", safeCount(() -> platformJdbc.jobOffersCount(), "jobOffersCount"));
        totals.put("events", safeCount(() -> platformJdbc.eventsCount(), "eventsCount"));
        totals.put("pricingPlans", safeCount(() -> platformJdbc.pricingPlansCount(), "pricingPlansCount"));
        totals.put("subscriptions", safeCount(() -> platformJdbc.subscriptionsCount(), "subscriptionsCount"));
        totals.put("users", safeCount(() -> platformJdbc.usersCount(), "usersCount"));
        wrapper.put("totals", totals);
        return toJson(wrapper);
    }

    private String sanitizeDisplayName(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        // Keep letters/digits/spaces/hyphen/apostrophe only.
        s = s.replaceAll("[^\\p{L}\\p{N}\\s\\-']", " ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.length() > 80) {
            s = s.substring(0, 80).trim();
        }
        return s;
    }

    private String formatGreetingDisplayName(String sanitized) {
        if (sanitized == null) {
            return "";
        }
        String s = sanitized.trim();
        if (s.isEmpty()) {
            return "";
        }
        String[] parts = s.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            String w = p.toLowerCase(Locale.ROOT);
            String tc = w.substring(0, 1).toUpperCase(Locale.ROOT) + (w.length() > 1 ? w.substring(1) : "");
            if (out.length() > 0) out.append(' ');
            out.append(tc);
        }
        return out.toString().trim();
    }

    private static boolean containsAny(String msg, String... needles) {
        String m = msg == null ? "" : msg.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n == null || n.isBlank()) continue;
            if (m.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private long safeCount(java.util.concurrent.Callable<Long> c, String label) {
        try {
            Long v = c.call();
            return v != null ? v : 0L;
        } catch (Exception e) {
            log.debug("Platform count '{}' failed: {}", label, e.getMessage());
            return 0L;
        }
    }

    private List<Map<String, Object>> topN(List<Map<String, Object>> rows, int n) {
        if (rows == null || rows.isEmpty()) return List.of();
        if (rows.size() <= n) return rows;
        return rows.subList(0, n);
    }

    private List<Map<String, Object>> rankMapsByTokens(List<Map<String, Object>> rows, Set<String> tokens, String... fields) {
        if (rows == null || rows.isEmpty()) return List.of();
        if (tokens == null || tokens.isEmpty()) return rows;
        record Scored(Map<String, Object> row, int score) {}
        List<Scored> scored = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            StringBuilder blob = new StringBuilder();
            for (String f : fields) {
                Object v = r.get(f);
                if (v != null) {
                    blob.append(v.toString()).append(' ');
                }
            }
            String b = blob.toString().toLowerCase(Locale.ROOT);
            int s = 0;
            for (String tok : tokens) {
                if (tok.length() < 2) continue;
                if (b.contains(tok)) s += 2;
            }
            scored.add(new Scored(r, s));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        boolean allZero = scored.stream().allMatch(x -> x.score() == 0);
        if (allZero) {
            return rows;
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Scored s : scored) {
            out.add(s.row());
        }
        return out;
    }

    private String callLlmOrNull(String prompt) {
        try {
            return anthropicClientService.callClaude(prompt, 2048);
        } catch (Exception e) {
            try {
                return groqClientService.chat(prompt);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private String summarizeCatalogFallback(List<Map<String, Object>> items, int totalInDb) {
        if (items.isEmpty() && totalInDb == 0) {
            return "The course catalog is empty right now. Please check back later.";
        }
        String titles = items.stream()
                .map(i -> java.util.Objects.toString(i.get("title"), ""))
                .filter(s -> !s.isBlank())
                .limit(16)
                .collect(Collectors.joining(", "));
        String head = totalInDb > 0
                ? "The catalog has " + totalInDb + " course(s) in total. Here are the most relevant ones I loaded: "
                : "Here are courses from the database: ";
        return head + titles
                + "\n\n(Configure ANTHROPIC_API_KEY or GROQ_API_KEY on the server for richer AI wording.)";
    }

    private List<Training> selectTrainingsForQuestion(String message, List<Training> all) {
        final int limit = 24;
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        Set<String> tokens = tokenize(message);
        record Scored(Training t, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (Training t : all) {
            int s = scoreTraining(t, tokens);
            scored.add(new Scored(t, s));
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        boolean allZero = scored.stream().allMatch(x -> x.score() == 0);
        List<Training> out = new ArrayList<>();
        if (allZero) {
            all.stream()
                    .sorted((a, b) -> Double.compare(avg(b.getId()), avg(a.getId())))
                    .limit(limit)
                    .forEach(out::add);
        } else {
            for (Scored sc : scored) {
                if (out.size() >= limit) {
                    break;
                }
                out.add(sc.t());
            }
        }
        return out;
    }

    private int scoreTraining(Training t, Set<String> tokens) {
        if (tokens.isEmpty()) {
            return 0;
        }
        String title = nz(t.getTitle()).toLowerCase(Locale.ROOT);
        String cat = nz(t.getCategory()).toLowerCase(Locale.ROOT);
        String lev = nz(t.getLevel()).toLowerCase(Locale.ROOT);
        String desc = nz(t.getDescription()).toLowerCase(Locale.ROOT);
        int s = 0;
        for (String tok : tokens) {
            if (title.contains(tok)) {
                s += 6;
            } else if (cat.contains(tok)) {
                s += 4;
            } else if (lev.contains(tok)) {
                s += 4;
            } else if (desc.contains(tok)) {
                s += 1;
            }
        }
        return s;
    }

    private static String nz(String s) {
        return s != null ? s : "";
    }

    private Set<String> tokenize(String message) {
        if (message == null || message.isBlank()) {
            return Set.of();
        }
        String m = message.toLowerCase(Locale.ROOT);
        String[] parts = m.split("[^\\p{L}0-9]+");
        Set<String> out = new HashSet<>();
        for (String p : parts) {
            if (p.length() >= 2 && !STOPWORDS.contains(p)) {
                out.add(p);
            }
        }
        return out;
    }

    private String fallbackAnswer(Intent intent, List<Map<String, Object>> items) {
        if (items.isEmpty()) return "Not found in database.";
        switch (intent.type) {
            case TOP_RATED -> {
                return "Top rated: " + items.stream()
                        .map(i -> java.util.Objects.toString(i.get("title"), ""))
                        .filter(s -> !s.isBlank())
                        .limit(3)
                        .collect(Collectors.joining(", "));
            }
            case CHEAPEST -> {
                return "Cheapest: " + items.stream()
                        .map(i -> java.util.Objects.toString(i.get("title"), ""))
                        .filter(s -> !s.isBlank())
                        .limit(3)
                        .collect(Collectors.joining(", "));
            }
            case MOST_REVIEWED -> {
                return "Most reviewed: " + items.stream()
                        .map(i -> java.util.Objects.toString(i.get("title"), ""))
                        .filter(s -> !s.isBlank())
                        .limit(3)
                        .collect(Collectors.joining(", "));
            }
            case LEVEL -> {
                return "Best " + intent.level + " courses: " + items.stream()
                        .map(i -> java.util.Objects.toString(i.get("title"), ""))
                        .filter(s -> !s.isBlank())
                        .limit(3)
                        .collect(Collectors.joining(", "));
            }
            case LESSONS_OF -> {
                @SuppressWarnings("unchecked")
                var c = (List<Map<String, Object>>) items.get(0).getOrDefault("contents", List.of());
                if (c.isEmpty()) return "No lessons found.";
                return "Lessons: " + c.stream()
                        .map(m -> java.util.Objects.toString(m.get("title"), ""))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.joining(", "));
            }
            case DETAILS_OF -> {
                Map<String, Object> i = items.get(0);
                return String.format("%s — rating %s★, reviews %s, lessons %s, price %s",
                        i.get("title"), i.get("averageRating"), i.get("reviewsCount"), i.get("contentsCount"), i.get("price"));
            }
            case SEARCH -> {
                return "Search results: " + items.stream()
                        .map(i -> java.util.Objects.toString(i.get("title"), ""))
                        .filter(s -> !s.isBlank())
                        .limit(5)
                        .collect(Collectors.joining(", "));
            }
            case REVIEWS_OF -> {
                String course = java.util.Objects.toString(items.get(0).get("trainingTitle"), "");
                String reviews = items.stream().map(i -> String.format("%s (%s★): %s",
                                java.util.Objects.toString(i.get("authorName"), ""),
                                java.util.Objects.toString(i.get("rating"), ""),
                                java.util.Objects.toString(i.get("comment"), "")))
                        .limit(5)
                        .collect(Collectors.joining(" | "));
                return reviews.isBlank() ? "No reviews found." : ("Latest reviews for " + course + ": " + reviews);
            }
            case AVG_RATING_OF -> {
                Map<String, Object> i = items.get(0);
                return String.format("Average rating for %s: %s★",
                        i.get("trainingTitle"), i.get("averageRating"));
            }
            case REVIEWS_COUNT_OF -> {
                Map<String, Object> i = items.get(0);
                return String.format("Number of reviews for %s: %s",
                        i.get("trainingTitle"), i.get("reviewsCount"));
            }
            case OUT_OF_SCOPE -> {
                return "No matching data in the catalog for that question.";
            }
        }
        return "Not found in database.";
    }

    private static boolean containsWholeWord(String haystack, String word) {
        return Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b").matcher(haystack).find();
    }

    private Intent detectIntent(String message) {
        if (message == null || message.isBlank()) {
            return new Intent(IntentType.OUT_OF_SCOPE);
        }
        String m = message.toLowerCase(Locale.ROOT);

        // Platform domains should not be misclassified as training search.
        if (containsAny(m,
                "subscription", "subscriptions", "abonnement", "abonnements",
                "pricing", "price plan", "pricing plan", "pricing plans", "tarif", "tarifs", "plan tarifaire", "plans tarifaires",
                "company", "companies", "entreprise", "entreprises", "societe", "société",
                "job offer", "job offers", "job", "jobs", "offre", "offres", "emploi", "emplois",
                "event", "events", "evenement", "événement", "evenements", "événements"
        )) {
            return new Intent(IntentType.OUT_OF_SCOPE);
        }

        if (m.contains("top") && m.contains("rated")) return new Intent(IntentType.TOP_RATED);
        if (m.contains("cheapest") || m.contains("low price")) return new Intent(IntentType.CHEAPEST);
        if (m.contains("most reviewed") || m.contains("most reviews")) return new Intent(IntentType.MOST_REVIEWED);
        if (m.contains("beginner")) return new Intent(IntentType.LEVEL, "beginner");
        if (m.contains("advanced")) return new Intent(IntentType.LEVEL, "advanced");
        if (P_SHOW_LIST_COURSES.matcher(m).find()) {
            return new Intent(IntentType.SEARCH, null, extractCourseTopicPhrase(message));
        }
        if (containsWholeWord(m, "search") || containsWholeWord(m, "find")) {
            String t = m.replaceAll("(?i)\\bsearch\\b", " ")
                    .replaceAll("(?i)\\bfind\\b", " ")
                    .trim()
                    .replaceAll("\\s+", " ");
            return new Intent(IntentType.SEARCH, null, t);
        }
        if (m.contains("reviews of") || m.contains("show reviews")) {
            String t = m.replace("reviews of", "").replace("show reviews", "").trim();
            return new Intent(IntentType.REVIEWS_OF, null, t);
        }
        if (m.contains("average rating of") || m.contains("avg rating of")) {
            String t = m.replace("average rating of", "").replace("avg rating of", "").trim();
            return new Intent(IntentType.AVG_RATING_OF, null, t);
        }
        if (m.contains("number of reviews for") || m.contains("reviews count of")) {
            String t = m.replace("number of reviews for", "").replace("reviews count of", "").trim();
            return new Intent(IntentType.REVIEWS_COUNT_OF, null, t);
        }
        if (m.contains("lessons of") || m.contains("show lessons")) {
            String t = m.replace("lessons of", "").replace("show lessons", "").trim();
            return new Intent(IntentType.LESSONS_OF, null, t);
        }
        if (m.contains("details of") || m.contains("course details")) {
            String t = m.replace("details of", "").replace("course details", "").trim();
            return new Intent(IntentType.DETAILS_OF, null, t);
        }
        return new Intent(IntentType.OUT_OF_SCOPE);
    }

    private double avg(Long trainingId) {
        Double v = avisRepository.averageRatingByTrainingId(trainingId);
        return v != null ? v : 0.0;
    }

    private long count(Long trainingId) {
        return avisRepository.countByTraining_Id(trainingId);
    }

    private double round(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String toJson(Object o) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    enum IntentType {
        TOP_RATED,
        CHEAPEST,
        MOST_REVIEWED,
        LEVEL,
        LESSONS_OF,
        DETAILS_OF,
        SEARCH,
        REVIEWS_OF,
        AVG_RATING_OF,
        REVIEWS_COUNT_OF,
        OUT_OF_SCOPE
    }

    static class Intent {
        IntentType type;
        String level;
        String title;

        Intent(IntentType t) {
            this.type = t;
        }

        Intent(IntentType t, String level) {
            this.type = t;
            this.level = level;
        }

        Intent(IntentType t, String level, String title) {
            this.type = t;
            this.level = level;
            this.title = title;
        }

        String label() {
            return switch (type) {
                case TOP_RATED -> "TOP_RATED";
                case CHEAPEST -> "CHEAPEST";
                case MOST_REVIEWED -> "MOST_REVIEWED";
                case LEVEL -> "LEVEL:" + (level != null ? level : "");
                case LESSONS_OF -> "LESSONS_OF";
                case DETAILS_OF -> "DETAILS_OF";
                case SEARCH -> "SEARCH";
                case REVIEWS_OF -> "REVIEWS_OF";
                case AVG_RATING_OF -> "AVG_RATING_OF";
                case REVIEWS_COUNT_OF -> "REVIEWS_COUNT_OF";
                case OUT_OF_SCOPE -> "OUT_OF_SCOPE";
            };
        }

        String sqlHint() {
            return switch (type) {
                case TOP_RATED -> "Aggregate AVG(rating) by training and sort DESC LIMIT 5";
                case CHEAPEST -> "Sort trainings by price ASC LIMIT 5";
                case MOST_REVIEWED -> "Count reviews per training and sort DESC LIMIT 5";
                case LEVEL -> "Filter trainings by level and sort by AVG(rating) DESC LIMIT 5";
                case LESSONS_OF -> "Select contents by training";
                case DETAILS_OF -> "Select training by title/id";
                case SEARCH -> "Filter trainings by title contains";
                case REVIEWS_OF -> "Select avis by training ordered by createdAt DESC LIMIT 5";
                case AVG_RATING_OF -> "Aggregate AVG(rating) for training";
                case REVIEWS_COUNT_OF -> "Count avis for training";
                case OUT_OF_SCOPE -> "";
            };
        }
    }
}

