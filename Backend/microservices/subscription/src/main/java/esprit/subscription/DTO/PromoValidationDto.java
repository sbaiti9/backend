package esprit.subscription.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of {@code GET /sub/promo/validate} — includes price preview when plan context is sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromoValidationDto {
    private boolean valid;
    private String message;
    private String code;
    /** PERCENT or FIXED_AMOUNT */
    private String discountType;
    private Double discountPercent;
    private Double discountAmount;
    private Double originalAmount;
    private Double finalAmount;
    private String currency;
}
