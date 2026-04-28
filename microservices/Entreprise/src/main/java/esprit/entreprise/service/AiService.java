package esprit.entreprise.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import esprit.entreprise.entity.Company;
import esprit.entreprise.entity.JobOffer;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AiService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${groq.api.enabled:false}")
    private boolean groqEnabled;

    @Value("${groq.api.url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqApiUrl;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.api.model:llama-3.1-8b-instant}")
    private String groqModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Tika tika = new Tika();

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    public Map<String, Object> generateJobOffer(String jobTitle) throws Exception {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            System.out.println("⚠️ Pas de clé Gemini configurée, utilisation du fallback local");
            return generateFallbackData(jobTitle);
        }

        try {
            return callGeminiAPI(jobTitle);
        } catch (Exception e) {
            System.err.println("❌ Erreur Gemini API: " + e.getMessage());
            System.out.println("🔄 Utilisation du fallback local");
            return generateFallbackData(jobTitle);
        }
    }

    public Map<String, Object> generateQuestionsForOffer(JobOffer offer) throws Exception {
        if (offer == null) {
            return Map.of("questions", List.of());
        }

        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }

        try {
            return callGroqQuestions(offer);
        } catch (Exception e) {
            System.err.println("❌ Erreur Groq API: " + e.getMessage());
            throw e;
        }
    }

    private Map<String, Object> callGroqQuestions(JobOffer offer) throws Exception {
        String systemPrompt =
                "You are an expert technical recruiter. Respond ONLY with valid JSON (no markdown). "
                        + "Return an object with this exact shape: {\"questions\":[{\"question\":\"...\",\"why\":\"...\"}]} "
                        + "Generate exactly 6 concise questions tailored to the job offer.";

        String userContent =
                "Job offer info:\n"
                        + "Title: " + safe(offer.getTitle()) + "\n"
                        + "Contract type: " + safe(offer.getContractType()) + "\n"
                        + "Location: " + safe(offer.getLocation()) + "\n"
                        + "Remote: " + safe(offer.getRemote()) + "\n"
                        + "Description: " + safe(offer.getDescription()) + "\n"
                        + "Requirements: " + safe(offer.getRequirements()) + "\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

        if (!parsed.containsKey("questions")) throw new RuntimeException("Groq JSON missing 'questions' field");
        return parsed;
    }

    public Map<String, Object> generatePracticeQuizForOffer(JobOffer offer) throws Exception {
        if (offer == null) {
            return Map.of("questions", List.of());
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        return callGroqPracticeQuiz(offer);
    }

    private Map<String, Object> callGroqPracticeQuiz(JobOffer offer) throws Exception {
        String systemPrompt =
                "You are an expert technical interviewer. Respond ONLY with valid JSON (no markdown). "
                        + "Return an object: {\"questions\":[...]} where each element is "
                        + "{\"question\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"correctIndex\":0,\"explanation\":\"brief\"}. "
                        + "Rules: exactly 4 options per question; correctIndex is 0-based (0-3). "
                        + "Generate exactly 8 multiple-choice questions that reflect real skills for this role.";

        String userContent =
                "Build a practice quiz for a candidate applying to this job.\n"
                        + "Job title: " + safe(offer.getTitle()) + "\n"
                        + "Contract: " + safe(offer.getContractType()) + "\n"
                        + "Location: " + safe(offer.getLocation()) + "\n"
                        + "Remote: " + safe(offer.getRemote()) + "\n"
                        + "Description: " + safe(offer.getDescription()) + "\n"
                        + "Requirements: " + safe(offer.getRequirements()) + "\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.25);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

        if (!parsed.containsKey("questions")) {
            throw new RuntimeException("Groq JSON missing 'questions' field");
        }
        Object rawQs = parsed.get("questions");
        if (!(rawQs instanceof List)) {
            throw new RuntimeException("Groq JSON 'questions' must be an array");
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) rawQs;
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map)) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> q = (Map<String, Object>) o;
            String question = q.get("question") instanceof String ? ((String) q.get("question")).trim() : "";
            Object optsObj = q.get("options");
            List<String> options = new ArrayList<>();
            if (optsObj instanceof List) {
                for (Object x : (List<?>) optsObj) {
                    if (x != null) options.add(String.valueOf(x).trim());
                }
            }
            while (options.size() < 4) options.add("");
            if (options.size() > 4) options = options.subList(0, 4);

            int correct = 0;
            if (q.get("correctIndex") instanceof Number) {
                correct = ((Number) q.get("correctIndex")).intValue();
            } else if (q.get("correctIndex") instanceof String) {
                try {
                    correct = Integer.parseInt(((String) q.get("correctIndex")).trim());
                } catch (NumberFormatException ignored) {
                    correct = 0;
                }
            }
            if (correct < 0) correct = 0;
            if (correct > 3) correct = 3;

            String explanation = q.get("explanation") instanceof String ? ((String) q.get("explanation")).trim() : "";
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("question", question);
            one.put("options", options);
            one.put("correctIndex", correct);
            one.put("explanation", explanation);
            if (!question.isEmpty()) normalized.add(one);
        }
        return Map.of("questions", normalized);
    }

    public Map<String, Object> translateJobOffer(JobOffer offer) throws Exception {
        if (offer == null) {
            throw new IllegalArgumentException("Offre introuvable");
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        Map<String, Object> raw = callGroqTranslateJobOffer(offer);
        return normalizeJobOfferTranslation(raw);
    }

    private Map<String, Object> callGroqTranslateJobOffer(JobOffer offer) throws Exception {
        String systemPrompt =
                "You are a professional translator for job postings. Respond ONLY with valid JSON (no markdown). "
                        + "Return exactly this top-level shape: "
                        + "{\"fr\":{\"title\":\"...\",\"description\":\"...\",\"location\":\"...\","
                        + "\"contractTypeLabel\":\"...\",\"remoteLabel\":\"...\",\"requiredSkills\":[\"...\"]},"
                        + "\"en\":{\"title\":\"...\",\"description\":\"...\",\"location\":\"...\","
                        + "\"contractTypeLabel\":\"...\",\"remoteLabel\":\"...\",\"requiredSkills\":[\"...\"]}}. "
                        + "Rules: the 'fr' block must be entirely in natural French; the 'en' block entirely in natural English. "
                        + "Translate faithfully; keep well-known proper nouns when appropriate. "
                        + "contractTypeLabel: short human-readable label in that language. "
                        + "remoteLabel: short label for the remote work policy in that language. "
                        + "requiredSkills: 3 to 14 concise skills in that language; infer from description and requirements if needed. "
                        + "Do not invent salary or benefits not in the source.";

        String userContent =
                "Translate this job offer into both fr and en objects.\n\n"
                        + "title: " + normalizeAndTruncate(safe(offer.getTitle()), 300) + "\n"
                        + "description: " + normalizeAndTruncate(safe(offer.getDescription()), 6000) + "\n"
                        + "location: " + normalizeAndTruncate(safe(offer.getLocation()), 300) + "\n"
                        + "contractType: " + normalizeAndTruncate(safe(offer.getContractType()), 80) + "\n"
                        + "remote: " + normalizeAndTruncate(safe(offer.getRemote()), 80) + "\n"
                        + "requirements: " + normalizeAndTruncate(safe(offer.getRequirements()), 2000) + "\n";

        return postGroqJsonObject(systemPrompt, userContent, 0.2);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeJobOfferTranslation(Map<String, Object> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String lang : List.of("fr", "en")) {
            Object blockObj = raw.get(lang);
            if (!(blockObj instanceof Map)) {
                throw new RuntimeException("Invalid translation response: missing '" + lang + "'");
            }
            Map<String, Object> b = (Map<String, Object>) blockObj;
            Map<String, Object> fixed = new LinkedHashMap<>();
            fixed.put("title", translationCoerceString(b.get("title")));
            fixed.put("description", translationCoerceString(b.get("description")));
            fixed.put("location", translationCoerceString(b.get("location")));
            fixed.put("contractTypeLabel", translationCoerceString(b.get("contractTypeLabel")));
            fixed.put("remoteLabel", translationCoerceString(b.get("remoteLabel")));
            fixed.put("requiredSkills", translationCoerceStringList(b.get("requiredSkills")));
            out.put(lang, fixed);
        }
        return out;
    }

    private static String translationCoerceString(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static List<String> translationCoerceStringList(Object o) {
        if (o == null) {
            return List.of();
        }
        if (o instanceof List) {
            List<String> list = new ArrayList<>();
            for (Object x : (List<?>) o) {
                if (x == null) {
                    continue;
                }
                String s = String.valueOf(x).trim();
                if (!s.isEmpty()) {
                    list.add(s);
                }
            }
            return list;
        }
        if (o instanceof String) {
            String s = ((String) o).trim();
            if (s.isEmpty()) {
                return List.of();
            }
            List<String> parts = new ArrayList<>();
            for (String p : s.split("[,;\\n]+")) {
                String t = p.trim();
                if (!t.isEmpty()) {
                    parts.add(t);
                }
            }
            return parts;
        }
        return List.of();
    }

    public Map<String, Object> assistStudentCv(String notes, String targetRole) throws Exception {
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        String n = normalizeAndTruncate(safe(notes), 4500);
        if (n.isBlank()) {
            throw new IllegalArgumentException("Décrivez au moins brièvement votre parcours ou vos compétences.");
        }
        return callGroqStudentCvAssist(n, safe(targetRole));
    }

    private Map<String, Object> callGroqStudentCvAssist(String notes, String targetRole) throws Exception {
        String systemPrompt =
                "You are an expert career coach for students in Tunisia and French-speaking markets. "
                        + "Respond ONLY with valid JSON (no markdown). Return exactly this shape:\n"
                        + "{\"headline\":\"one short professional headline in French\",\n"
                        + "\"professionalSummary\":\"3-5 sentences in French for CV summary\",\n"
                        + "\"experienceBullets\":[\"action verb + result in French\", ... 5 to 8 items],\n"
                        + "\"skills\":[\"skill1\", ... 8 to 14 concise items],\n"
                        + "\"educationHint\":\"one sentence in French on how to present education\",\n"
                        + "\"languagesHint\":\"one sentence in French on languages section\",\n"
                        + "\"tips\":[\"tip in French\", ... 4 to 6 items]}\n"
                        + "Use truthful, professional French. Do not invent employers or diplomas not implied by the student text; "
                        + "you may suggest generic wording where details are missing.";

        String userContent =
                "Student notes (may be messy):\n" + notes + "\n\n"
                        + "Target role (optional): " + (targetRole.isBlank() ? "(not specified)" : targetRole) + "\n";

        return postGroqJsonObject(systemPrompt, userContent, 0.35);
    }

    public Map<String, Object> assistStudentCoverLetter(String notes, String targetRole, String companyName) throws Exception {
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        String n = normalizeAndTruncate(safe(notes), 4500);
        if (n.isBlank()) {
            throw new IllegalArgumentException("Décrivez votre motivation et votre parcours (quelques phrases minimum).");
        }
        return callGroqStudentCoverLetter(n, safe(targetRole), safe(companyName));
    }

    private Map<String, Object> callGroqStudentCoverLetter(String notes, String targetRole, String companyName) throws Exception {
        String systemPrompt =
                "You write compelling French cover letters for students and juniors (Tunisia / Maghreb / France). "
                        + "Respond ONLY with valid JSON (no markdown). Return exactly:\n"
                        + "{\"subjectLine\":\"short email subject in French\",\n"
                        + "\"salutation\":\"Madame, Monsieur, or Cher recruteur,\",\n"
                        + "\"opening\":\"2-3 sentences\",\n"
                        + "\"bodyParagraphs\":[\"paragraph 1\", \"paragraph 2\", \"paragraph 3\"],\n"
                        + "\"closing\":\"1-2 sentences before signature\",\n"
                        + "\"signatureHint\":\"Cordialement, [Votre Prénom NOM]\"}\n"
                        + "Tone: professional, sincere, not exaggerated. Do not invent concrete employers, dates, or diplomas "
                        + "not hinted in the student notes; use neutral wording if unknown.";

        String userContent =
                "Student notes:\n" + notes + "\n\n"
                        + "Target role: " + (targetRole.isBlank() ? "(not specified)" : targetRole) + "\n"
                        + "Company name if any: " + (companyName.isBlank() ? "(not specified)" : companyName) + "\n";

        return postGroqJsonObject(systemPrompt, userContent, 0.4);
    }

    public Map<String, Object> assistStudentInterviewPrep(String notes, String targetRole) throws Exception {
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        String n = normalizeAndTruncate(safe(notes), 4500);
        if (n.isBlank()) {
            throw new IllegalArgumentException("Décrivez votre parcours pour personnaliser la préparation.");
        }
        return callGroqStudentInterviewPrep(n, safe(targetRole));
    }

    private Map<String, Object> callGroqStudentInterviewPrep(String notes, String targetRole) throws Exception {
        String systemPrompt =
                "You coach students for job interviews in French. Respond ONLY with valid JSON (no markdown). Return exactly:\n"
                        + "{\"elevatorPitch\":\"20-40 seconds spoken French pitch in 3-5 short sentences\",\n"
                        + "\"keyStrengths\":[\"strength in French\", ... 4 to 6 items],\n"
                        + "\"likelyQuestions\":[{\"question\":\"...\",\"hintAnswer\":\"concise bullet-style answer in French\"}, ... 6 to 8 items],\n"
                        + "\"tips\":[\"tip in French\", ... 5 to 7 items]}\n"
                        + "Be realistic for juniors; no invented employers or achievements not implied by notes.";

        String userContent =
                "Student notes:\n" + notes + "\n\n"
                        + "Target role: " + (targetRole.isBlank() ? "(not specified)" : targetRole) + "\n";

        return postGroqJsonObject(systemPrompt, userContent, 0.35);
    }

    public Map<String, Object> analyzeAdminCompanyPlatformFit(List<Map<String, Object>> companiesRows) throws Exception {
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        if (companiesRows == null || companiesRows.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("summary", "Aucune entreprise enregistrée sur la plateforme.");
            empty.put("bienServies", List.of());
            empty.put("aAmeliorer", List.of());
            return empty;
        }

        String systemPrompt =
                "Tu es analyste produit B2B pour la plateforme Skillio (Tunisie / francophone) : recrutement, offres d’emploi, "
                        + "candidatures, messagerie support admin–entreprise.\n"
                        + "Tu reçois UN SEUL tableau JSON d’entreprises RÉELLES avec des métriques d’usage (aucune autre source).\n"
                        + "Règles strictes :\n"
                        + "- Ne JAMAIS inventer d’entreprise, d’ID ou de nom : chaque entrée de sortie doit reprendre exactement "
                        + "companyId et companyName d’un objet du tableau INPUT.\n"
                        + "- Chaque entreprise du INPUT doit apparaître exactement une fois : soit dans bienServies, soit dans aAmeliorer.\n"
                        + "- Tu fais une prédiction / lecture d’alignement « satisfaction / valeur tirée de la plateforme » "
                        + "à partir des métriques (offres actives, volume de candidatures, messages support entreprise = possible friction).\n"
                        + "Réponds UNIQUEMENT avec un JSON valide (pas de markdown). Forme exacte :\n"
                        + "{\"summary\":\"3 à 6 phrases en français pour l’admin\",\n"
                        + "\"bienServies\":[{\"companyId\":number,\"companyName\":\"\",\"indicateurs\":[\"...\", \"...\"],\"commentaire\":\"...\"}],\n"
                        + "\"aAmeliorer\":[{\"companyId\":number,\"companyName\":\"\",\"indicateurs\":[\"...\"],\"risqueOuFrein\":\"...\",\n"
                        + "\"propositionsPourLaPlateformeSkillio\":[\"action concrète côté produit / onboarding / support / pricing\", ... 4 à 8],\n"
                        + "\"propositionsPourLEntreprise\":[\"action concrète côté usage (offres, suivi candidats, contenu)\", ... 3 à 7]}]}\n"
                        + "Style : professionnel, clair, en français.";

        String userContent =
                "INPUT (tableau JSON autoritaire des entreprises et métriques) :\n"
                        + objectMapper.writeValueAsString(companiesRows);

        return postGroqJsonObject(systemPrompt, userContent, 0.25);
    }

    public Map<String, Object> companyActivationEmojiInsight(Map<String, Object> snapshot) throws Exception {
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }
        if (snapshot == null || snapshot.isEmpty()) {
            throw new IllegalArgumentException("Snapshot entreprise vide.");
        }

        String systemPrompt =
                "Tu es coach pour entreprises sur Skillio (recrutement en ligne, Tunisie / francophone).\n"
                        + "Tu reçois UN objet JSON factuel sur UNE entreprise (profil + offres). "
                        + "N’invente AUCUN chiffre ni fait : base-toi uniquement sur les booléens et nombres fournis.\n"
                        + "Réponds UNIQUEMENT avec un JSON valide (pas de markdown). Forme EXACTE :\n"
                        + "{\"heroEmoji\":\"exactement un emoji principal qui résume l’activation sur la plateforme\",\n"
                        + "\"activationStrip\":\"4 à 7 emojis Unicode collés SANS espaces ni ponctuation (jauge visuelle)\",\n"
                        + "\"statusTitle\":\"2 à 5 mots en français (ex: Bien lancée, À booster)\",\n"
                        + "\"subtitle\":\"une phrase courte en français, ton encourageant\",\n"
                        + "\"pills\":[{\"emoji\":\"un emoji\",\"text\":\"8 mots max en français\"}, "
                        + "{\"emoji\":\"...\",\"text\":\"...\"}, {\"emoji\":\"...\",\"text\":\"...\"}, "
                        + "{\"emoji\":\"...\",\"text\":\"...\"}]}\n"
                        + "Les 4 pills doivent couvrir dans l’ordre : (1) complétude du profil, "
                        + "(2) présence / qualité du recrutement (offres), (3) visibilité côté candidats, "
                        + "(4) une prochaine action concrète sur Skillio.\n"
                        + "Si peu d’offres actives, reste bienveillant ; si tout est rempli et offres actives, célèbre avec des emojis dynamiques.";

        String userContent =
                "Métriques entreprise (seule source) :\n"
                        + objectMapper.writeValueAsString(snapshot);

        return postGroqJsonObject(systemPrompt, userContent, 0.38);
    }

    private Map<String, Object> postGroqJsonObject(String systemPrompt, String userContent, double temperature) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", temperature);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> parseCompanySearch(String text) throws Exception {
        String t = safe(text);
        if (t.isBlank()) {
            return Map.of("query", "", "industry", "");
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }

        String systemPrompt =
                "You help map a user's company search to filters. Respond ONLY with valid JSON (no markdown). "
                        + "Return exactly: {\"query\":\"...\",\"industry\":\"\"|\"tech\"|\"finance\"|\"education\"|\"healthcare\"}. "
                        + "Rules: industry must be one of the allowed values. If unknown, use empty string.";

        String userContent =
                "User search: " + t + "\n"
                        + "Examples:\n"
                        + "- \"entreprises tech\" => {\"query\":\"\",\"industry\":\"tech\"}\n"
                        + "- \"HP\" => {\"query\":\"HP\",\"industry\":\"\"}\n"
                        + "- \"entreprises santé à Sfax\" => {\"query\":\"Sfax\",\"industry\":\"healthcare\"}\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

        String query = parsed.get("query") instanceof String ? ((String) parsed.get("query")).trim() : "";
        String industry = parsed.get("industry") instanceof String ? ((String) parsed.get("industry")).trim() : "";

        Set<String> allowed = Set.of("", "tech", "finance", "education", "healthcare");
        if (!allowed.contains(industry)) industry = "";

        return Map.of("query", query, "industry", industry);
    }

    public Map<String, Object> parseJobOfferSearch(String text) throws Exception {
        String t = safe(text);
        if (t.isBlank()) {
            return Map.of("query", "", "contractTypes", List.of(), "remote", "all");
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }

        String systemPrompt =
                "You map a user's job offer search to filters. Respond ONLY with valid JSON (no markdown). "
                        + "Return exactly: {\"query\":\"...\",\"contractTypes\":[\"CDI\"|\"CDD\"|\"Stage\"],\"remote\":\"all\"|\"Complet\"|\"Partiel\"|\"Non\"}. "
                        + "Rules: contractTypes is an array (can be empty). remote must be one of the allowed values.";

        String userContent =
                "User search: " + t + "\n"
                        + "Hints: CDI/CDD/Stage, and remote keywords like remote/full remote/hybride/on-site/pr\u00e9sentiel.\n";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        Map<String, Object> parsed = objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});

        String query = parsed.get("query") instanceof String ? ((String) parsed.get("query")).trim() : "";
        String remote = parsed.get("remote") instanceof String ? ((String) parsed.get("remote")).trim() : "all";

        List<String> contractTypes = new ArrayList<>();
        Object ct = parsed.get("contractTypes");
        if (ct instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) contractTypes.add(s.trim());
            }
        }

        Set<String> allowedRemote = Set.of("all", "Complet", "Partiel", "Non");
        if (!allowedRemote.contains(remote)) remote = "all";

        Set<String> allowedCt = Set.of("CDI", "CDD", "Stage");
        contractTypes.removeIf(s -> !allowedCt.contains(s));

        return Map.of("query", query, "contractTypes", contractTypes, "remote", remote);
    }

    public Map<String, Object> analyzeCv(byte[] cvBytes, String fileName, String contentType, JobOffer offer) throws Exception {
        if (cvBytes == null || cvBytes.length == 0) {
            throw new IllegalArgumentException("CV vide");
        }
        if (offer == null) {
            throw new IllegalArgumentException("Offre introuvable");
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }

        String cvText = extractText(cvBytes);
        cvText = normalizeAndTruncate(cvText, 12000);

        String offerText = normalizeAndTruncate(
                "Title: " + safe(offer.getTitle()) + "\n"
                        + "Description: " + safe(offer.getDescription()) + "\n"
                        + "Requirements: " + safe(offer.getRequirements()) + "\n"
                        + "ContractType: " + safe(offer.getContractType()) + "\n"
                        + "Location: " + safe(offer.getLocation()) + "\n"
                        + "Remote: " + safe(offer.getRemote()) + "\n",
                4000
        );

        String systemPrompt =
                "You are an expert recruiter. Evaluate the candidate CV against the job offer. "
                        + "Respond ONLY with valid JSON (no markdown). "
                        + "Return exactly: {\"score\":<0-100 integer>,\"summary\":\"...\",\"strengths\":[\"...\"],\"gaps\":[\"...\"],\"recommendations\":[\"...\"]}. "
                        + "Rules: be concise, grounded in the CV content. If data missing, mention it in gaps.";

        String userContent =
                "JOB OFFER:\n" + offerText + "\n\n"
                        + "CV METADATA:\n"
                        + "fileName: " + safe(fileName) + "\n"
                        + "contentType: " + safe(contentType) + "\n\n"
                        + "CV TEXT:\n" + cvText;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.2);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> suggestNextOffers(Company company, List<JobOffer> existingOffers) throws Exception {
        if (company == null) {
            throw new IllegalArgumentException("Entreprise introuvable");
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }

        String comp =
                "Company:\n"
                        + "Name: " + safe(company.getName()) + "\n"
                        + "Industry: " + safe(company.getIndustry()) + "\n"
                        + "Location: " + safe(company.getLocation()) + "\n"
                        + "Website: " + safe(company.getWebsite()) + "\n";

        StringBuilder history = new StringBuilder();
        history.append("Existing offers (titles/requirements only):\n");
        if (existingOffers != null) {
            int i = 0;
            for (JobOffer o : existingOffers) {
                if (o == null) continue;
                history.append("- ").append(safe(o.getTitle()));
                if (o.getRequirements() != null && !o.getRequirements().isBlank()) {
                    history.append(" | req=").append(normalizeAndTruncate(safe(o.getRequirements()), 240));
                }
                history.append("\n");
                if (++i >= 12) break;
            }
        }

        String systemPrompt =
                "You are an HR strategist. Propose future job offers this company should publish next month. "
                        + "Respond ONLY with valid JSON (no markdown). "
                        + "Return exactly: {\"suggestions\":[{"
                        + "\"id\":\"stable-short-id\","
                        + "\"title\":\"...\","
                        + "\"contractType\":\"CDI|CDD|Stage|Freelance\","
                        + "\"remote\":\"Non|Partiel|Complet\","
                        + "\"experienceLevel\":\"Débutant|Intermédiaire|Expert|Senior\","
                        + "\"requiredSkills\":[\"...\"],"
                        + "\"reason\":\"1 sentence why it fits\""
                        + "}]} "
                        + "Rules: generate 6 suggestions, titles in English/French OK, skills 5-8 items, do not repeat existing titles.";

        String userContent = comp + "\n" + history;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.4);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
    }

    public Map<String, Object> predictOffersAiDecline(Company company, List<JobOffer> existingOffers) throws Exception {
        if (company == null) {
            throw new IllegalArgumentException("Entreprise introuvable");
        }
        if (!groqEnabled || groqApiKey == null || groqApiKey.isBlank()) {
            throw new IllegalStateException("Groq is not configured. Set groq.api.enabled=true and groq.api.key.");
        }

        String comp =
                "Company:\n"
                        + "Name: " + safe(company.getName()) + "\n"
                        + "Industry: " + safe(company.getIndustry()) + "\n"
                        + "Location: " + safe(company.getLocation()) + "\n";

        StringBuilder offersText = new StringBuilder();
        offersText.append("Existing job offers:\n");
        if (existingOffers != null) {
            int i = 0;
            for (JobOffer o : existingOffers) {
                if (o == null) continue;
                offersText.append("- title=").append(safe(o.getTitle())).append("\n");
                if (o.getDescription() != null && !o.getDescription().isBlank()) {
                    offersText.append("  desc=").append(normalizeAndTruncate(safe(o.getDescription()), 420)).append("\n");
                }
                if (o.getRequirements() != null && !o.getRequirements().isBlank()) {
                    offersText.append("  req=").append(normalizeAndTruncate(safe(o.getRequirements()), 420)).append("\n");
                }
                if (++i >= 12) break;
            }
        }

        String systemPrompt =
                "You are a labor-market analyst specialized in AI impact. "
                        + "Given the company's existing job offers, predict which roles will likely decline in demand next year due to AI automation/augmentation. "
                        + "Respond ONLY with valid JSON (no markdown). "
                        + "Return exactly: {\"risks\":[{\"title\":\"...\",\"risk\":<0-100 integer>,\"why\":\"...\",\"aiImpact\":\"...\",\"mitigation\":[\"...\"],\"alternatives\":[\"...\"]}]}. "
                        + "Rules: include only titles from the provided offers. Return up to 6 items, highest risk first. "
                        + "risk meaning: 0 low risk, 100 high risk. Keep explanations concise and practical.";

        String userContent = comp + "\n" + offersText;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey.trim());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", groqModel);
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)
        ));
        body.put("temperature", 0.25);
        body.put("response_format", Map.of("type", "json_object"));

        HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        ResponseEntity<String> response = restTemplate.exchange(groqApiUrl, HttpMethod.POST, entity, String.class);
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Groq API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Groq response missing content");
        }

        String cleaned = stripMarkdownFences(content).trim();
        return objectMapper.readValue(cleaned, new TypeReference<Map<String, Object>>() {});
    }

    private String extractText(byte[] bytes) {
        try {
            return tika.parseToString(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return new String(bytes);
        }
    }

    private static String normalizeAndTruncate(String s, int maxChars) {
        String t = (s == null ? "" : s).replace("\u0000", " ").trim();
        if (t.length() <= maxChars) return t;
        return t.substring(0, maxChars);
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private static String stripMarkdownFences(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > -1) t = t.substring(firstNl + 1);
            int lastFence = t.lastIndexOf("```");
            if (lastFence > -1) t = t.substring(0, lastFence);
        }
        return t;
    }

    private Map<String, Object> callGeminiAPI(String jobTitle) throws Exception {
        String prompt = buildPrompt(jobTitle);

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(textPart));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        String url = GEMINI_API_URL + "?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(
                objectMapper.writeValueAsString(requestBody),
                headers
        );

        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Gemini API error: " + response.getStatusCode());
        }

        JsonNode root = objectMapper.readTree(response.getBody());

        String textContent = root
                .path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText("");

        textContent = textContent
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        Map<String, Object> result = objectMapper.readValue(
                textContent,
                new TypeReference<Map<String, Object>>() {}
        );

        System.out.println("✅ Gemini AI generated job offer for: " + jobTitle);
        return result;
    }

    private String buildPrompt(String jobTitle) {
        return "Tu es un expert en ressources humaines. Génère une fiche de poste complète pour le titre: \""
                + jobTitle + "\".\n\n"
                + "Réponds UNIQUEMENT en JSON valide, sans markdown, sans backticks, sans texte avant ou après. "
                + "Voici la structure exacte attendue:\n\n"
                + "{\n"
                + "  \"title\": \"le titre du poste\",\n"
                + "  \"description\": \"description détaillée du poste avec missions et responsabilités (3-5 phrases)\",\n"
                + "  \"contractType\": \"CDI ou CDD ou Stage ou Freelance\",\n"
                + "  \"remote\": \"Non ou Partiel ou Complet\",\n"
                + "  \"experienceLevel\": \"Débutant ou Intermédiaire ou Expert ou Senior\",\n"
                + "  \"salaryMin\": nombre entier en DT annuel,\n"
                + "  \"salaryMax\": nombre entier en DT annuel,\n"
                + "  \"numberOfPositions\": nombre entre 1 et 5,\n"
                + "  \"requiredSkills\": [\"skill1\", \"skill2\", \"skill3\", \"skill4\", \"skill5\"],\n"
                + "  \"location\": \"une ville en Tunisie\"\n"
                + "}\n\n"
                + "Règles:\n"
                + "- La description doit être en français, professionnelle et détaillée\n"
                + "- Les salaires doivent être réalistes pour le marché tunisien\n"
                + "- Les compétences doivent être techniques et pertinentes (5-8 compétences)\n"
                + "- La localisation doit être une ville tunisienne (Tunis, Sfax, Sousse, etc.)";
    }

    private Map<String, Object> generateFallbackData(String jobTitle) {
        Map<String, Object> result = new LinkedHashMap<>();
        String titleLower = jobTitle.toLowerCase();

        result.put("title", jobTitle);

        if (titleLower.contains("angular") || titleLower.contains("frontend") || titleLower.contains("front-end")) {
            result.put("description", "Nous recherchons un développeur Frontend passionné pour rejoindre notre équipe technique. "
                    + "Vous serez responsable du développement et de la maintenance d'applications web modernes et performantes. "
                    + "Vos missions incluront la conception d'interfaces utilisateur ergonomiques, l'intégration d'APIs REST, "
                    + "et la collaboration étroite avec les équipes backend et design pour garantir une expérience utilisateur optimale.");
            result.put("requiredSkills", List.of("Angular", "TypeScript", "RxJS", "HTML/CSS", "SCSS", "REST API", "Git"));
            result.put("salaryMin", 24000);
            result.put("salaryMax", 48000);
        } else if (titleLower.contains("react")) {
            result.put("description", "Nous cherchons un développeur React expérimenté pour concevoir et développer des interfaces web dynamiques. "
                    + "Vous participerez à la construction d'applications single-page performantes, à l'optimisation du rendu et "
                    + "à la mise en place de tests unitaires. Vous travaillerez en méthodologie Agile avec une équipe pluridisciplinaire.");
            result.put("requiredSkills", List.of("React", "TypeScript", "Redux", "Next.js", "CSS Modules", "Jest", "Git"));
            result.put("salaryMin", 24000);
            result.put("salaryMax", 50000);
        } else if (titleLower.contains("full stack") || titleLower.contains("fullstack")) {
            result.put("description", "Nous recherchons un développeur Full Stack polyvalent pour travailler sur l'ensemble de notre stack technique. "
                    + "Vous interviendrez tant sur le frontend que le backend, en développant des fonctionnalités complètes, "
                    + "en optimisant les performances et en participant aux revues de code. "
                    + "Une bonne compréhension des architectures microservices est un atout.");
            result.put("requiredSkills", List.of("Angular", "Spring Boot", "TypeScript", "Java", "PostgreSQL", "Docker", "REST API", "Git"));
            result.put("salaryMin", 30000);
            result.put("salaryMax", 60000);
        } else if (titleLower.contains("data") || titleLower.contains("machine learning") || titleLower.contains("ml")) {
            result.put("description", "Nous recrutons un spécialiste Data/ML pour analyser nos données et développer des modèles prédictifs. "
                    + "Vous serez chargé de collecter, nettoyer et transformer les données, de concevoir des pipelines ETL, "
                    + "et de déployer des modèles de machine learning en production. "
                    + "Vous collaborerez avec les équipes produit pour identifier les opportunités d'amélioration basées sur les données.");
            result.put("requiredSkills", List.of("Python", "TensorFlow", "Pandas", "SQL", "Scikit-learn", "Docker", "AWS/GCP"));
            result.put("salaryMin", 30000);
            result.put("salaryMax", 55000);
        } else if (titleLower.contains("devops") || titleLower.contains("sre")) {
            result.put("description", "Nous recherchons un ingénieur DevOps/SRE pour automatiser et optimiser notre infrastructure cloud. "
                    + "Vos responsabilités incluront la mise en place de pipelines CI/CD, la gestion des conteneurs, "
                    + "le monitoring des applications et la garantie de la haute disponibilité de nos services.");
            result.put("requiredSkills", List.of("Docker", "Kubernetes", "Jenkins", "AWS", "Terraform", "Linux", "Git", "Prometheus"));
            result.put("salaryMin", 28000);
            result.put("salaryMax", 55000);
        } else if (titleLower.contains("backend") || titleLower.contains("java") || titleLower.contains("spring")) {
            result.put("description", "Nous cherchons un développeur Backend pour concevoir et maintenir nos APIs et services métier. "
                    + "Vous développerez des microservices performants, optimiserez les requêtes base de données, "
                    + "et assurerez la sécurité et la scalabilité de notre architecture.");
            result.put("requiredSkills", List.of("Java", "Spring Boot", "PostgreSQL", "REST API", "Docker", "JUnit", "Git"));
            result.put("salaryMin", 26000);
            result.put("salaryMax", 52000);
        } else if (titleLower.contains("mobile") || titleLower.contains("flutter") || titleLower.contains("android") || titleLower.contains("ios")) {
            result.put("description", "Nous recherchons un développeur Mobile pour concevoir et développer des applications mobiles innovantes. "
                    + "Vous serez responsable de l'ensemble du cycle de développement, de la conception UI/UX à la publication sur les stores. "
                    + "Vous collaborerez avec l'équipe backend pour l'intégration des APIs et assurerez la performance de l'application.");
            result.put("requiredSkills", List.of("Flutter", "Dart", "Firebase", "REST API", "Git", "UI/UX", "Android/iOS"));
            result.put("salaryMin", 24000);
            result.put("salaryMax", 48000);
        } else if (titleLower.contains("cyber") || titleLower.contains("security") || titleLower.contains("sécurité")) {
            result.put("description", "Nous recherchons un ingénieur en cybersécurité pour protéger nos systèmes et données. "
                    + "Vous serez chargé de réaliser des audits de sécurité, de mettre en place des solutions de protection, "
                    + "et de former les équipes aux bonnes pratiques de sécurité informatique.");
            result.put("requiredSkills", List.of("Pentesting", "SIEM", "Firewall", "Linux", "Python", "ISO 27001", "Networking"));
            result.put("salaryMin", 30000);
            result.put("salaryMax", 58000);
        } else {
            result.put("description", "Nous recherchons un(e) " + jobTitle + " talentueux(se) pour rejoindre notre équipe en pleine croissance. "
                    + "Vous serez responsable de contribuer activement aux projets de l'entreprise, "
                    + "de collaborer avec les différentes équipes et de participer à l'amélioration continue de nos processus. "
                    + "Ce poste offre une opportunité d'évolution dans un environnement dynamique et innovant.");
            result.put("requiredSkills", List.of("Communication", "Travail d'équipe", "Résolution de problèmes", "Gestion de projet", "Adaptabilité"));
            result.put("salaryMin", 20000);
            result.put("salaryMax", 40000);
        }

        result.put("contractType", "CDI");
        result.put("remote", titleLower.contains("remote") ? "Complet" : "Partiel");
        result.put("experienceLevel", titleLower.contains("senior") || titleLower.contains("lead") ? "Senior"
                : titleLower.contains("junior") || titleLower.contains("stage") ? "Débutant" : "Intermédiaire");
        result.put("numberOfPositions", 1);
        result.put("location", "Tunis, Tunisie");

        return result;
    }
}

