package tn.esprit.user.dto;

/**
 * Prédiction de désengagement (churn) pour un compte Skillio.
 */
public record ChurnUserPrediction(
        long userId,
        String email,
        /** LOW, MEDIUM, HIGH */
        String churnRisk,
        /** 0–100 : probabilité estimée de quitter / réduire fortement l’usage. */
        int leaveProbabilityPercent,
        String messageFr,
        /** GROQ, HEURISTIC, ou HYBRID si Groq a complété l’heuristique. */
        String source
) {}
