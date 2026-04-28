package esprit.subscription.entity;

/** How {@link PromoCode} applies its discount. */
public enum PromoDiscountType {
    /** {@link PromoCode#getDiscountPercent()} off (e.g. 15 = 15%) */
    PERCENT,
    /** Fixed amount in EUR subtracted from the line item (same currency as Stripe checkout) */
    FIXED_AMOUNT
}
