package esprit.subscription.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import esprit.subscription.entity.UnlockChatSession;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send HTML email to " + to, e);
        }
    }

    public void sendBirthdayPromoEmail(String companyEmail, String companyName, String promoCode) {
        String subj = "Joyeux anniversaire " + companyName + " — votre cadeau Skillio 🎁";
        String cta = frontendUrl.replaceAll("/$", "") + "/enterprise/subscription";
        String html = buildBirthdayPromoHtml(companyName, promoCode, cta);
        sendHtmlEmail(companyEmail, subj, html);
    }

    public void sendAutoRenewalEmail(String email, String companyName, String planName, LocalDateTime newEndDate) {
        String subj = "Votre abonnement Skillio a été renouvelé";
        String cta = frontendUrl.replaceAll("/$", "") + "/enterprise/subscription";
        String endStr = newEndDate != null
                ? newEndDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH))
                : "";
        String html = buildAutoRenewalHtml(companyName, planName, endStr, cta);
        sendHtmlEmail(email, subj, html);
    }

    public void sendLoyaltyMilestoneEmail(
            String to,
            String displayName,
            String tierName,
            int milestoneMonth,
            int discountPercent,
            String promoCode
    ) {
        String cta = frontendUrl.replaceAll("/$", "") + "/enterprise/subscription";
        String subject = "Bravo " + displayName + " — Palier " + tierName + " débloqué";
        String html = """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"></head>
                <body style="margin:0;background:#f8fafc;font-family:Segoe UI,Arial,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="padding:28px 12px;">
                <tr><td align="center">
                <table width="620" style="max-width:620px;background:#ffffff;border-radius:14px;border:1px solid #e2e8f0;">
                <tr><td style="padding:28px 34px;">
                <h1 style="margin:0 0 10px;color:#0f172a;">Félicitations %s</h1>
                <p style="margin:0 0 14px;color:#475569;line-height:1.6;">
                Vous venez d’atteindre le palier <strong>%s</strong> après <strong>%d mois</strong> de fidélité.
                </p>
                <div style="border:2px dashed #2563eb;border-radius:12px;padding:18px;text-align:center;background:#eff6ff;">
                <div style="font-size:11px;text-transform:uppercase;color:#64748b;">Code promo fidélité</div>
                <div style="font-family:Consolas,monospace;font-size:24px;font-weight:700;color:#1d4ed8;margin-top:6px;">%s</div>
                <div style="margin-top:8px;font-size:14px;color:#0f172a;">%d%% de réduction</div>
                </div>
                <p style="margin:14px 0 18px;color:#64748b;font-size:13px;">
                Ce code est personnel et utilisable une seule fois.
                </p>
                <a href="%s" style="display:inline-block;background:#2563eb;color:#fff;text-decoration:none;font-weight:600;padding:12px 22px;border-radius:8px;">
                Utiliser mon avantage
                </a>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(
                escapeHtml(displayName),
                escapeHtml(tierName),
                milestoneMonth,
                escapeHtml(promoCode),
                discountPercent,
                cta
        );
        sendHtmlEmail(to, subject, html);
    }

    private String buildBirthdayPromoHtml(String companyName, String promoCode, String subscriptionUrl) {
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"></head>
                <body style="margin:0;background:#0f172a;font-family:Segoe UI,Arial,sans-serif;">
                <table width="100%" cellpadding="0" cellspacing="0" style="padding:32px 12px;">
                <tr><td align="center">
                <table width="600" style="max-width:600px;background:#fff;border-radius:16px;overflow:hidden;">
                <tr><td style="background:linear-gradient(135deg,#6366f1,#8b5cf6);padding:28px;text-align:center;color:#fff;">
                <div style="font-size:22px;font-weight:800;letter-spacing:0.06em;">★ Skillio ★</div>
                <div style="margin-top:8px;font-size:14px;opacity:0.95;">✦ Joyeux anniversaire ✦</div>
                </td></tr>
                <tr><td style="padding:28px 32px;color:#0f172a;">
                <p style="margin:0 0 16px;font-size:16px;line-height:1.5;">Bonjour <strong>%s</strong>, en ce jour spécial nous vous offrons <strong>20%%</strong> de réduction sur votre prochain abonnement.</p>
                <div style="border:2px dashed #6366f1;border-radius:12px;padding:20px;text-align:center;margin:20px 0;background:#f8fafc;">
                <div style="font-size:11px;text-transform:uppercase;color:#64748b;letter-spacing:.1em;">Votre code promo</div>
                <div style="font-family:Consolas,Monaco,monospace;font-size:26px;font-weight:700;color:#0f172a;margin-top:8px;">%s</div>
                </div>
                <p style="margin:0 0 20px;font-size:13px;color:#64748b;">Valable 30 jours — usage unique</p>
                <div style="text-align:center;">
                <a href="%s" style="display:inline-block;background:linear-gradient(135deg,#6366f1,#8b5cf6);color:#fff;text-decoration:none;font-weight:700;padding:12px 28px;border-radius:10px;">Utiliser mon code</a>
                </div>
                </td></tr>
                <tr><td style="padding:16px 32px 28px;font-size:12px;color:#94a3b8;text-align:center;">L'équipe Skillio</td></tr>
                </table></td></tr></table></body></html>
                """.formatted(escapeHtml(companyName), escapeHtml(promoCode), subscriptionUrl);
    }

    private String buildAutoRenewalHtml(String companyName, String planName, String endDateStr, String subscriptionUrl) {
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"></head>
                <body style="margin:0;background:#f1f5f9;font-family:Segoe UI,Arial,sans-serif;">
                <table width="100%" cellpadding="0" cellspacing="0" style="padding:32px 12px;">
                <tr><td align="center">
                <table width="600" style="max-width:600px;background:#fff;border-radius:12px;padding:28px 32px;">
                <tr><td>
                <h1 style="margin:0 0 12px;font-size:20px;color:#0f172a;">Votre abonnement Skillio a été renouvelé</h1>
                <p style="color:#475569;line-height:1.6;">Bonjour <strong>%s</strong>,</p>
                <p style="color:#475569;line-height:1.6;">Votre abonnement <strong>%s</strong> a bien été renouvelé. Prochaine échéance : <strong>%s</strong>.</p>
                <div style="text-align:center;margin-top:24px;">
                <a href="%s" style="display:inline-block;background:#2563eb;color:#fff;text-decoration:none;font-weight:600;padding:12px 24px;border-radius:8px;">Gérer mon abonnement</a>
                </div>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(escapeHtml(companyName), escapeHtml(planName), escapeHtml(endDateStr), subscriptionUrl);
    }

    public void sendAiTargetingPromoEmail(String companyEmail, String companyName, String promoCode, int discountPercent) {
        String subj = "Skillio — votre code promo " + discountPercent + "%";
        String cta = frontendUrl.replaceAll("/$", "") + "/enterprise/subscription";
        String html = buildAiTargetingPromoHtml(companyName, promoCode, discountPercent, cta);
        sendHtmlEmail(companyEmail, subj, html);
    }

    private String buildAiTargetingPromoHtml(String companyName, String promoCode, int discountPercent, String subscriptionUrl) {
        return """
                <!DOCTYPE html><html lang="fr"><head><meta charset="UTF-8"></head>
                <body style="margin:0;background:#f8fafc;font-family:Segoe UI,Arial,sans-serif;">
                <table width="100%" cellpadding="0" cellspacing="0" style="padding:28px 12px;">
                <tr><td align="center">
                <table width="600" style="max-width:600px;background:#fff;border-radius:12px;border:1px solid #e2e8f0;">
                <tr><td style="padding:28px 32px;">
                <p style="margin:0 0 12px;font-size:16px;color:#0f172a;">Bonjour <strong>%s</strong>,</p>
                <p style="margin:0 0 16px;font-size:14px;color:#475569;line-height:1.6;">Voici un code promotionnel personnalisé pour votre abonnement Skillio.</p>
                <div style="border:2px dashed #7c3aed;border-radius:12px;padding:18px;text-align:center;background:#faf5ff;">
                <div style="font-size:11px;text-transform:uppercase;color:#64748b;">Code</div>
                <div style="font-family:Consolas,monospace;font-size:24px;font-weight:700;color:#5b21b6;margin-top:6px;">%s</div>
                <div style="margin-top:8px;font-size:14px;color:#0f172a;"><strong>%d%%</strong> de réduction</div>
                </div>
                <div style="text-align:center;margin-top:22px;">
                <a href="%s" style="display:inline-block;background:#7c3aed;color:#fff;text-decoration:none;font-weight:600;padding:12px 24px;border-radius:8px;">Utiliser mon code</a>
                </div>
                </td></tr></table></td></tr></table></body></html>
                """.formatted(escapeHtml(companyName), escapeHtml(promoCode), discountPercent, subscriptionUrl);
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public void sendSimpleMail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email to " + to, e);
        }
    }

    /**
     * Alerte admin lorsqu'une session unlock-chat passe en NEEDS_HUMAN_REVIEW (échec vérification présence / IA).
     * Ne doit pas faire échouer le flux appelant — capturer les exceptions côté appelant si besoin.
     */
    public void sendHumanReviewAlertEmail(String adminEmail, UnlockChatSession session, long messageCount, String currentRotatingCode) {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }
        Long sid = session.getId();
        String userId = session.getUserId() != null ? session.getUserId() : "—";
        String decision = session.getAiDecision() != null ? session.getAiDecision().name() : "—";
        int conf = session.getAiConfidence() != null ? session.getAiConfidence() : 0;
        String reasoning = session.getAiReasoning() != null ? session.getAiReasoning() : "—";
        String analyzed = session.getAiAnalyzedAt() != null
                ? session.getAiAnalyzedAt().toString()
                : "—";
        String linkBase = frontendUrl.replaceAll("/$", "");
        String body = """
                Un utilisateur a échoué à la vérification de présence automatique.
                L'agent IA n'a pas pu confirmer son identité humaine.

                Détails de la session:
                - Session ID: %s
                - Utilisateur ID: %s
                - Décision IA: %s (%d%% confiance)
                - Raisonnement: %s
                - Analysé à: %s
                - Nombre de messages: %d

                Action requise: Connectez-vous au tableau de bord admin et examinez cette session manuellement.
                Lien direct: %s/admin/unlock-chat?sessionId=%s

                Code d'accès admin actuel: %s
                """
                .formatted(
                        sid,
                        userId,
                        decision,
                        conf,
                        reasoning,
                        analyzed,
                        messageCount,
                        linkBase,
                        sid,
                        currentRotatingCode != null ? currentRotatingCode : "—");
        String subject = "🚨 [Skillio] Intervention requise — Session unlock-chat #" + sid;
        sendSimpleMail(adminEmail, subject, body);
    }

    public void sendExpiryNotification(String to, String companyName, long daysLeft) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String plural = daysLeft > 1 ? "s" : "";
            helper.setTo(to);
            helper.setSubject("\u23F3 Votre abonnement Skillio expire dans " + daysLeft + " jour" + plural);
            helper.setText(buildExpiryHtml(companyName, daysLeft), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send expiry notification to " + to, e);
        }
    }

    private String buildExpiryHtml(String companyName, long daysLeft) {

        String accent, badgeBg, urgencyLabel, emoji;

        if (daysLeft == 1) {
            accent = "#EF4444"; badgeBg = "#FEF2F2";
            urgencyLabel = "URGENT &mdash; Expire demain&nbsp;!";
            emoji = "&#x1F6A8;";
        } else if (daysLeft == 3) {
            accent = "#F97316"; badgeBg = "#FFF7ED";
            urgencyLabel = "Action requise";
            emoji = "&#x26A0;&#xFE0F;";
        } else {
            accent = "#F59E0B"; badgeBg = "#FFFBEB";
            urgencyLabel = "Rappel d&rsquo;expiration";
            emoji = "&#x1F514;";
        }

        String plural = daysLeft > 1 ? "s" : "";

        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html lang=\"fr\"><head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>Expiration abonnement Skillio</title>")
                .append("</head>")
                .append("<body style=\"margin:0;padding:0;background-color:#F1F5F9;font-family:'Segoe UI',Arial,sans-serif;\">")

                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:#F1F5F9;padding:40px 16px;\">")
                .append("<tr><td align=\"center\">")

                // Card
                .append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\"")
                .append(" style=\"max-width:600px;width:100%;background:#FFFFFF;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);\">")

                // Top bar
                .append("<tr><td style=\"height:4px;background:").append(accent).append(";\"></td></tr>")

                // Header row
                .append("<tr><td style=\"background:#FFFFFF;padding:32px 40px 24px;border-bottom:1px solid #F1F5F9;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")

                // Logo
                .append("<td><table cellpadding=\"0\" cellspacing=\"0\"><tr>")
                .append("<td style=\"background:linear-gradient(135deg,#3B82F6,#2563EB);border-radius:10px;width:40px;height:40px;text-align:center;vertical-align:middle;\">")
                .append("<span style=\"color:#fff;font-size:18px;font-weight:800;line-height:40px;\">S</span></td>")
                .append("<td style=\"padding-left:12px;vertical-align:middle;\">")
                .append("<span style=\"font-size:18px;font-weight:700;color:#0F172A;\">Skillio</span>")
                .append("<span style=\"display:inline-block;margin-left:8px;font-size:10px;font-weight:600;background:#EFF6FF;color:#2563EB;border:1px solid #BFDBFE;border-radius:4px;padding:1px 7px;\">Platform</span>")
                .append("</td></tr></table></td>")

                // Badge
                .append("<td align=\"right\" style=\"vertical-align:middle;\">")
                .append("<span style=\"display:inline-block;background:").append(badgeBg).append(";color:").append(accent)
                .append(";font-size:11px;font-weight:700;text-transform:uppercase;padding:5px 12px;border-radius:20px;border:1px solid ").append(accent).append(";\">")
                .append(urgencyLabel).append("</span>")
                .append("</td></tr></table></td></tr>")

                // Hero section
                .append("<tr><td style=\"padding:36px 40px 28px;\">")

                // Icon
                .append("<div style=\"text-align:center;margin-bottom:24px;\">")
                .append("<div style=\"display:inline-block;width:72px;height:72px;background:").append(badgeBg)
                .append(";border-radius:50%;line-height:72px;font-size:36px;text-align:center;\">").append(emoji).append("</div></div>")

                // Title
                .append("<h1 style=\"margin:0 0 12px;font-size:24px;font-weight:700;color:#0F172A;text-align:center;\">")
                .append("Votre abonnement expire bient&ocirc;t</h1>")

                // Greeting
                .append("<p style=\"margin:0 0 28px;font-size:15px;color:#64748B;text-align:center;line-height:1.6;\">")
                .append("Bonjour <strong style=\"color:#0F172A;\">").append(companyName).append("</strong>,<br>")
                .append("nous vous informons que votre abonnement Skillio arrivera &agrave; expiration.</p>")

                // Countdown card
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
                .append(" style=\"background:").append(badgeBg).append(";border:2px solid ").append(accent).append(";border-radius:12px;margin-bottom:28px;\">")
                .append("<tr><td style=\"padding:24px;text-align:center;\">")
                .append("<p style=\"margin:0 0 4px;font-size:13px;font-weight:600;color:").append(accent).append(";letter-spacing:.08em;text-transform:uppercase;\">Temps restant</p>")
                .append("<p style=\"margin:0;font-size:48px;font-weight:800;color:").append(accent).append(";line-height:1;\">")
                .append(daysLeft).append("<span style=\"font-size:20px;font-weight:600;\"> jour").append(plural).append("</span></p>")
                .append("</td></tr></table>")

                // Info table
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"")
                .append(" style=\"background:#F8FAFC;border:1px solid #E2E8F0;border-radius:10px;margin-bottom:28px;\">")
                // row 1
                .append("<tr><td style=\"padding:14px 20px;border-bottom:1px solid #E2E8F0;\">")
                .append("<table width=\"100%\"><tr>")
                .append("<td style=\"font-size:13px;color:#64748B;\">&#x1F4CB; Compte</td>")
                .append("<td align=\"right\" style=\"font-size:13px;color:#0F172A;font-weight:600;\">").append(companyName).append("</td>")
                .append("</tr></table></td></tr>")
                // row 2
                .append("<tr><td style=\"padding:14px 20px;border-bottom:1px solid #E2E8F0;\">")
                .append("<table width=\"100%\"><tr>")
                .append("<td style=\"font-size:13px;color:#64748B;\">&#x26A1; Statut</td>")
                .append("<td align=\"right\"><span style=\"background:#FEF9C3;color:#92400E;font-size:11px;font-weight:700;padding:3px 10px;border-radius:20px;\">")
                .append("Expire dans ").append(daysLeft).append(" jour").append(plural).append("</span></td>")
                .append("</tr></table></td></tr>")
                // row 3
                .append("<tr><td style=\"padding:14px 20px;\">")
                .append("<table width=\"100%\"><tr>")
                .append("<td style=\"font-size:13px;color:#64748B;\">&#x1F6E1;&#xFE0F; Service</td>")
                .append("<td align=\"right\" style=\"font-size:13px;color:#0F172A;font-weight:600;\">Skillio Platform</td>")
                .append("</tr></table></td></tr>")
                .append("</table>")

                // CTA
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr><td align=\"center\">")
                .append("<a href=\"https://skillio.com/pricing\"")
                .append(" style=\"display:inline-block;background:linear-gradient(135deg,#3B82F6,#2563EB);color:#FFFFFF;")
                .append("text-decoration:none;font-size:15px;font-weight:700;padding:14px 36px;border-radius:10px;\">")
                .append("Renouveler mon abonnement &rarr;</a>")
                .append("</td></tr></table>")
                .append("</td></tr>")

                // Info banner
                .append("<tr><td style=\"background:#F8FAFC;border-top:1px solid #E2E8F0;padding:18px 40px;\">")
                .append("<p style=\"margin:0;font-size:13px;color:#64748B;text-align:center;line-height:1.6;\">")
                .append("&#x1F4A1; <strong style=\"color:#475569;\">Sans renouvellement</strong>, ")
                .append("votre acc&egrave;s aux offres d&rsquo;emploi, formations et outils Skillio sera suspendu &agrave; l&rsquo;expiration.</p>")
                .append("</td></tr>")

                // Footer
                .append("<tr><td style=\"padding:24px 40px;background:#FFFFFF;border-top:1px solid #F1F5F9;\">")
                .append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>")
                .append("<td style=\"font-size:12px;color:#94A3B8;line-height:1.6;\">")
                .append("Cet email a &eacute;t&eacute; envoy&eacute; automatiquement par <strong>Skillio</strong>.<br>")
                .append("Pour toute question : <a href=\"mailto:support@skillio.com\" style=\"color:#3B82F6;text-decoration:none;\">support@skillio.com</a>")
                .append("</td>")
                .append("<td align=\"right\" style=\"vertical-align:top;font-size:11px;color:#CBD5E1;\">&copy; 2025 Skillio</td>")
                .append("</tr></table></td></tr>")

                .append("</table>") // /card
                .append("</td></tr></table>") // /wrapper
                .append("</body></html>");

        return sb.toString();
    }
}