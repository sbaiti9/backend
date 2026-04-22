package tn.esprit.user.dto;

import java.util.List;

public record ChurnBatchReport(
        int daysWindow,
        String summaryFr,
        List<ChurnUserPrediction> predictions
) {}
