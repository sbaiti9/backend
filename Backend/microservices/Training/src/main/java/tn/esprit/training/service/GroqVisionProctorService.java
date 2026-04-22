package tn.esprit.training.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import tn.esprit.training.dto.ProctorAnalyzeResponseDTO;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/**
 * Option A: rule-based frame quality checks + Groq vision for face / cheating heuristics.
 */
@Service
public class GroqVisionProctorService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GroqClientService groq;

    public GroqVisionProctorService(GroqClientService groq) {
        this.groq = groq;
    }

    public ProctorAnalyzeResponseDTO analyzeFrame(String base64JpegWithoutPrefix, String phase) {
        String ph = phase == null ? "FACE_CHECK" : phase.trim().toUpperCase();
        if (!ph.equals("FACE_CHECK") && !ph.equals("MONITOR")) {
            ph = "FACE_CHECK";
        }

        ProctorAnalyzeResponseDTO pre = deterministicFrameCheck(base64JpegWithoutPrefix);
        if (pre != null) {
            return pre;
        }

        String prompt = buildPrompt(ph);
        String raw;
        try {
            raw = groq.chatWithImage(prompt, base64JpegWithoutPrefix);
        } catch (IllegalStateException e) {
            return visionUnavailableResponse(ph, e);
        } catch (Exception e) {
            return visionUnavailableResponse(ph, e);
        }

        return parseAndDecide(raw, ph);
    }

    private ProctorAnalyzeResponseDTO visionUnavailableResponse(String phase, Exception e) {
        String msg = safeMsg(e.getMessage());
        if ("FACE_CHECK".equals(phase)) {
            ProctorAnalyzeResponseDTO d = new ProctorAnalyzeResponseDTO(false, "low", msg, "WARN", "SKIPPED");
            d.setFaceVisible(false);
            return d;
        }
        return new ProctorAnalyzeResponseDTO(false, "low", msg, "ALLOW", "SKIPPED");
    }

    private static String buildPrompt(String phase) {
        String jsonShape = "{\"cheating\":true|false,\"confidence\":\"high\"|\"medium\"|\"low\",\"reason\":\"short text\"}";
        if ("MONITOR".equals(phase)) {
            return "You proctor a LIVE online quiz from a webcam. Your top priority is detecting CHEATING with handheld devices.\n"
                    + "Set cheating=true with confidence high if you clearly see: a smartphone or tablet IN THE HANDS or being actively used "
                    + "(typing, scrolling, reading the screen while glancing down), a second phone, smartwatch used to read answers, "
                    + "or obvious crib notes / papers held up to copy.\n"
                    + "Set cheating=true confidence medium if you probably see a phone-like rectangle in hand but image is slightly unclear.\n"
                    + "Do NOT flag: phone lying flat on a distant desk, TV on wall, laptop frame edge, or empty hands. "
                    + "If unsure, cheating=false and confidence low.\n"
                    + "Also flag: second person, face completely left frame, camera covered.\n"
                    + "Respond ONLY JSON (no markdown): " + jsonShape;
        }
        String jsonFace = "{\"faceVisible\":true|false,\"cheating\":true|false,\"confidence\":\"high\"|\"medium\"|\"low\",\"reason\":\"short text\"}";
        return "You verify ONE webcam frame BEFORE an online quiz. You MUST output valid JSON only (no markdown).\n"
                + "Field faceVisible:\n"
                + "- true ONLY if exactly one human face is clearly visible, reasonably lit, and facing roughly toward the camera (eyes/nose area discernible).\n"
                + "- false if: no one in frame, user stepped away, only ceiling/wall/empty chair/desk, back of head, silhouette without clear facial features, "
                + "face too small or blurry to confirm, or you are not sure.\n"
                + "When in doubt, faceVisible=false and confidence=medium or high.\n"
                + "Field cheating: true if multiple faces, OR phone/tablet actively held/used in hands. Otherwise false.\n"
                + "If faceVisible=false, usually cheating=false unless another rule applies; explain in reason.\n"
                + "Respond ONLY JSON: " + jsonFace;
    }

    /**
     * @return non-null response to short-circuit Groq when the frame is unusable
     */
    private static ProctorAnalyzeResponseDTO deterministicFrameCheck(String b64) {
        if (b64 == null || b64.isBlank()) {
            return new ProctorAnalyzeResponseDTO(false, "high", "No image captured", "WARN", "TOO_SMALL");
        }
        String cleaned = b64.trim();
        if (cleaned.length() < 800) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Image too small — move closer to the camera", "WARN", "TOO_SMALL");
        }

        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Invalid image data", "WARN", "TOO_SMALL");
        }
        if (raw.length < 2500) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Image too small — improve lighting or move closer", "WARN", "TOO_SMALL");
        }

        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(raw));
        } catch (Exception e) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Could not read camera frame", "WARN", "TOO_SMALL");
        }
        if (img == null || img.getWidth() < 64 || img.getHeight() < 64) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Frame resolution too low", "WARN", "TOO_SMALL");
        }

        int w = img.getWidth();
        int h = img.getHeight();
        int step = Math.max(1, Math.min(w, h) / 48);
        long sum = 0;
        long sumSq = 0;
        long n = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                int lum = (r * 30 + g * 59 + b * 11) / 100;
                sum += lum;
                sumSq += (long) lum * lum;
                n++;
            }
        }
        if (n == 0) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Empty frame", "WARN", "TOO_SMALL");
        }
        double mean = sum / (double) n;
        double var = (sumSq / (double) n) - (mean * mean);

        if (mean < 22) {
            return new ProctorAnalyzeResponseDTO(false, "high", "Frame too dark — turn on lights or adjust exposure", "WARN", "DARK");
        }
        if (var < 80) {
            return new ProctorAnalyzeResponseDTO(false, "medium", "Frame looks flat or blurry — focus the camera", "WARN", "BLURRY");
        }

        return null;
    }

    private static ProctorAnalyzeResponseDTO parseAndDecide(String raw, String phase) {
        try {
            String json = extractJsonObject(stripMarkdownFences(raw).trim());
            JsonNode n = MAPPER.readTree(json);
            boolean cheating = n.path("cheating").asBoolean(false);
            String conf = n.path("confidence").asText("low").toLowerCase();
            if (!conf.equals("high") && !conf.equals("medium") && !conf.equals("low")) {
                conf = "low";
            }
            String reason = n.path("reason").asText("").trim();
            if (reason.isEmpty()) {
                reason = "No details";
            }

            if (reasonImpliesHeldDevice(reason)) {
                cheating = true;
                conf = "high";
            }

            boolean highOrMed = "high".equals(conf) || "medium".equals(conf);
            String action = "ALLOW";
            if (cheating && highOrMed) {
                action = "EJECT";
            } else if (cheating) {
                action = "WARN";
            }

            ProctorAnalyzeResponseDTO out = new ProctorAnalyzeResponseDTO(cheating, conf, reason, action, "OK");

            if ("FACE_CHECK".equals(phase)) {
                JsonNode fvNode = n.path("faceVisible");
                boolean explicitFv = fvNode.isBoolean();
                boolean modelFaceOk = explicitFv && fvNode.asBoolean();
                if (!explicitFv) {
                    modelFaceOk = false;
                    if (!reason.toLowerCase().contains("face")) {
                        reason = reason + " (faceVisible missing — use JSON schema)";
                    }
                }
                if (reasonImpliesNoFace(reason)) {
                    modelFaceOk = false;
                }

                boolean faceApproved = modelFaceOk && !cheating;
                out.setReason(reason);
                out.setFaceVisible(faceApproved);

                if (!faceApproved || cheating) {
                    if ("EJECT".equals(action)) {
                        /* keep */
                    } else {
                        out.setAction("WARN");
                    }
                } else {
                    out.setAction("ALLOW");
                }
            }

            return out;
        } catch (Exception e) {
            ProctorAnalyzeResponseDTO d = new ProctorAnalyzeResponseDTO(false, "low", "Could not parse vision response", "WARN", "OK");
            if ("FACE_CHECK".equals(phase)) {
                d.setFaceVisible(false);
            }
            return d;
        }
    }

    /** True if the model text clearly indicates no usable face / person in frame. */
    private static boolean reasonImpliesNoFace(String reason) {
        if (reason == null || reason.isBlank()) {
            return true;
        }
        String x = reason.toLowerCase();
        if ((x.contains("cannot see") || x.contains("can't see")) && x.contains("phone")) {
            return false;
        }
        if (x.contains("no face")) {
            return true;
        }
        if (x.contains("no person") || x.contains("nobody") || x.contains("no human")) {
            return true;
        }
        if (x.contains("face not visible") || x.contains("face is not visible") || x.contains("face not in")) {
            return true;
        }
        if (x.contains("not visible") && (x.contains("face") || x.contains("user") || x.contains("person"))) {
            return true;
        }
        if (x.contains("missing") && x.contains("face")) {
            return true;
        }
        if (x.contains("away from") || x.contains("stepped away") || x.contains("left the frame")) {
            return true;
        }
        if (x.contains("turned away") || x.contains("back to") || x.contains("back of head")) {
            return true;
        }
        if (x.contains("empty frame") || x.contains("empty room") || x.contains("empty chair")) {
            return true;
        }
        if (x.contains("only ceiling") || x.contains("only wall") || x.contains("only desk")) {
            return true;
        }
        if (x.contains("unclear") && x.contains("face")) {
            return true;
        }
        if (x.contains("too dark") && (x.contains("face") || x.contains("see"))) {
            return true;
        }
        if (x.contains("covered") && x.contains("camera")) {
            return true;
        }
        return false;
    }

    /**
     * Heuristic on model text: held phone / tablet / second device (avoids common negations).
     */
    private static boolean reasonImpliesHeldDevice(String reason) {
        if (reason == null || reason.isBlank()) {
            return false;
        }
        String x = reason.toLowerCase();
        if (x.contains("not holding")) {
            return false;
        }
        if (x.contains("no phone") || x.contains("no smartphone") || x.contains("no tablet") || x.contains("no mobile")) {
            return false;
        }
        if (x.contains("without phone") || x.contains("without a phone")) {
            return false;
        }
        if (x.contains("phone not visible") || x.contains("tablet not visible")) {
            return false;
        }
        if ((x.contains("cannot see") || x.contains("can't see")) && (x.contains("phone") || x.contains("tablet"))) {
            return false;
        }
        if (x.contains("holding") && (x.contains("phone") || x.contains("tablet") || x.contains("smartphone"))) {
            return true;
        }
        if (x.contains("using") && x.contains("phone")) {
            return true;
        }
        if (x.contains("typing on") && (x.contains("phone") || x.contains("tablet"))) {
            return true;
        }
        if (x.contains("scrolling on") && x.contains("phone")) {
            return true;
        }
        if (x.contains("second device") || x.contains("another phone") || x.contains("second phone")) {
            return true;
        }
        if (x.contains("looking at") && x.contains("phone")) {
            return true;
        }
        if (x.contains("reading from") && (x.contains("phone") || x.contains("tablet"))) {
            return true;
        }
        boolean device = x.contains("phone") || x.contains("smartphone") || x.contains("mobile phone") || x.contains("cell phone")
                || x.contains("tablet") || x.contains("ipad");
        boolean active = x.contains("holding") || x.contains("using") || x.contains("in hand") || x.contains("in their hand")
                || x.contains("typing") || x.contains("scrolling") || x.contains("glancing at");
        if (device && active) {
            return true;
        }
        return false;
    }

    private static String safeMsg(String m) {
        if (m == null) {
            return "Vision unavailable";
        }
        return m.length() > 220 ? m.substring(0, 220) : m;
    }

    private static String extractJsonObject(String s) {
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return s;
    }

    private static String stripMarkdownFences(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > -1) {
                t = t.substring(firstNl + 1);
            }
            int lastFence = t.lastIndexOf("```");
            if (lastFence > -1) {
                t = t.substring(0, lastFence);
            }
        }
        return t;
    }
}
