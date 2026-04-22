package esprit.subscription.service;

import esprit.subscription.DTO.CompanyDto;
import esprit.subscription.client.EntrepriseClient;
import esprit.subscription.entity.PromoCode;
import esprit.subscription.entity.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class SubscriptionExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionExpiryScheduler.class);

    private final SubscriptionService subscriptionService;
    private final EmailService emailService;
    private final EntrepriseClient entrepriseClient;
    private final PromoCodeService promoCodeService;

    public SubscriptionExpiryScheduler(
            SubscriptionService subscriptionService,
            EmailService emailService,
            EntrepriseClient entrepriseClient,
            PromoCodeService promoCodeService) {
        this.subscriptionService = subscriptionService;
        this.emailService = emailService;
        this.entrepriseClient = entrepriseClient;
        this.promoCodeService = promoCodeService;
    }

    /** Expiry reminders — daily at 00:36 server time. */
    @Scheduled(cron = "0 36 0 * * ?")
    public void checkAndSendExpiryEmails() {
        subscriptionService.findAll().forEach(subscription -> {

            if (subscription.getEndDate() == null) return;

            long daysLeft = java.time.Duration.between(
                    java.time.LocalDateTime.now(),
                    subscription.getEndDate()).toDays();

            if (daysLeft == 7 || daysLeft == 3 || daysLeft == 1) {
                try {
                    CompanyDto company = entrepriseClient.getCompanyByUserId(subscription.getUserId());
                    if (company != null && company.getEmail() != null) {
                        emailService.sendExpiryNotification(
                                company.getEmail(),
                                company.getName() != null ? company.getName() : "Client",
                                daysLeft
                        );
                    }
                } catch (Exception e) {
                    log.warn("Expiry email skipped for userId={}: {}", subscription.getUserId(), e.getMessage());
                }
            }
        });
    }

    /** Birthday promo — daily at 10:05. */
    @Scheduled(cron = "0 5 10 * * ?")
    public void processBirthdayPromos() {
        for (Subscription subscription : subscriptionService.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(subscription.getStatus())) {
                continue;
            }
            String role = subscription.getUserRole();
            if (role != null && !role.isBlank()
                    && !"ENTERPRISE".equalsIgnoreCase(role.trim())
                    && !"ENTREPRISE".equalsIgnoreCase(role.trim())) {
                continue;
            }
            try {
                CompanyDto company = entrepriseClient.getCompanyByUserId(subscription.getUserId());
                if (company == null || company.getEmail() == null) {
                    continue;
                }
                LocalDate dob = company.getDateOfBirth();
                if (dob == null) {
                    continue;
                }
                LocalDate today = LocalDate.now();
                if (dob.getMonthValue() != today.getMonthValue() || dob.getDayOfMonth() != today.getDayOfMonth()) {
                    continue;
                }
                Optional<PromoCode> created = promoCodeService.generateBirthdayPromoForUser(subscription.getUserId());
                created.ifPresent(p -> emailService.sendBirthdayPromoEmail(
                        company.getEmail(),
                        company.getName() != null ? company.getName() : "Client",
                        p.getCode()));
            } catch (Exception e) {
                log.warn("Birthday promo skipped for subscription {}: {}", subscription.getId(), e.getMessage());
            }
        }
    }

    /** Auto-renewal — daily at 10:06. */
    @Scheduled(cron = "0 6 10 * * ?")
    public void processAutoRenewals() {
        try {
            subscriptionService.processAutoRenewalsDueToday();
        } catch (Exception e) {
            log.error("Auto-renew batch failed", e);
        }
    }
}
