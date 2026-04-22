package tn.esprit.training.analytics.dto;

import java.util.List;

public class AnalyticsOverviewDTO {
    public long totalTrainings;
    public long totalContents;
    public long totalReviews;
    public double globalAverageRating;
    public double globalAvgRating;
    public long freeTrainings;
    public long paidTrainings;

    public List<TopClaimsItem> topByClaims;
    public List<TopRatingItem> topByRating;

    public List<DailyCount> claimsDaily;
    public List<DailyCount> reviewsDaily;

    public static class TopClaimsItem {
        public long id;
        public String title;
        public long claimsCount;
    }

    public static class TopRatingItem {
        public long id;
        public String title;
        public double avgRating;
        public long reviewsCount;
    }

    public static class DailyCount {
        public String date;
        public long count;
    }
}

