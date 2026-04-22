package tn.esprit.user.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import tn.esprit.user.entity.Users;
import tn.esprit.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminSecurityAlertService {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityAlertService.class);

    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:no-reply@skillio.local}")
    private String fromEmail;

    /**
     * Comma-separated list of admin emails.
     * Example: skilio.security.admin-alert-emails=admin@skillio.tn,sec@skillio.tn
     */
    @Value("${skilio.security.admin-alert-emails:}")
    private String adminAlertEmails;

    public AdminSecurityAlertService(UserRepository userRepository,
                                     ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.userRepository = userRepository;
        this.mailSender = mailSenderProvider.getIfAvailable();
    }

    public void notifyUserTemporarilyBlocked(Long userId, int attempts, LocalDateTime lockUntil) {
        if (mailSender == null) {
            log.warn("AdminSecurityAlertService: SMTP not configured, skipping admin alert.");
            return;
        }
        List<String> recipients = parseRecipients(adminAlertEmails);
        if (recipients.isEmpty()) {
            log.warn("AdminSecurityAlertService: skilio.security.admin-alert-emails empty, skipping admin alert.");
            return;
        }

        Users u = userRepository.findById(userId).orElse(null);
        String email = u != null ? u.getEmail() : null;

        String until = lockUntil != null
                ? lockUntil.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                : "N/A";

        String subject = "[Skillio][SECURITY] User temporarily blocked (failed logins)";
        String body = String.join("\n",
                "A user account has been temporarily blocked due to repeated failed login attempts.",
                "",
                "UserId: " + userId,
                "Email: " + (email != null ? email : "N/A"),
                "Failed attempts: " + attempts,
                "Locked until: " + until,
                "",
                "Action: Review geo-security anomalies / suspicious activity, then unlock via admin endpoint if safe."
        );

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(fromEmail);
        msg.setTo(recipients.toArray(new String[0]));
        msg.setSubject(subject);
        msg.setText(body);
        mailSender.send(msg);
    }

    private static List<String> parseRecipients(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .collect(Collectors.toList());
    }
}

