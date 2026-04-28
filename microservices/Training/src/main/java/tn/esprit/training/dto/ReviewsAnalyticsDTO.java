package tn.esprit.training.dto;

import java.util.List;

public class ReviewsAnalyticsDTO {
    private long totalReviews;
    private double globalAverageRating;
    private List<RatingDistributionItem> ratingDistribution;
    private List<TopRatingItem> topByAvgRating;
    private List<TopReviewsItem> topByReviewsCount;

    public ReviewsAnalyticsDTO() {}

    public ReviewsAnalyticsDTO(
            long totalReviews,
            double globalAverageRating,
            List<RatingDistributionItem> ratingDistribution,
            List<TopRatingItem> topByAvgRating,
            List<TopReviewsItem> topByReviewsCount
    ) {
        this.totalReviews = totalReviews;
        this.globalAverageRating = globalAverageRating;
        this.ratingDistribution = ratingDistribution;
        this.topByAvgRating = topByAvgRating;
        this.topByReviewsCount = topByReviewsCount;
    }

    public long getTotalReviews() {
        return totalReviews;
    }

    public void setTotalReviews(long totalReviews) {
        this.totalReviews = totalReviews;
    }

    public double getGlobalAverageRating() {
        return globalAverageRating;
    }

    public void setGlobalAverageRating(double globalAverageRating) {
        this.globalAverageRating = globalAverageRating;
    }

    public List<RatingDistributionItem> getRatingDistribution() {
        return ratingDistribution;
    }

    public void setRatingDistribution(List<RatingDistributionItem> ratingDistribution) {
        this.ratingDistribution = ratingDistribution;
    }

    public List<TopRatingItem> getTopByAvgRating() {
        return topByAvgRating;
    }

    public void setTopByAvgRating(List<TopRatingItem> topByAvgRating) {
        this.topByAvgRating = topByAvgRating;
    }

    public List<TopReviewsItem> getTopByReviewsCount() {
        return topByReviewsCount;
    }

    public void setTopByReviewsCount(List<TopReviewsItem> topByReviewsCount) {
        this.topByReviewsCount = topByReviewsCount;
    }

    public static class RatingDistributionItem {
        private int rating;
        private long count;

        public RatingDistributionItem() {}

        public RatingDistributionItem(int rating, long count) {
            this.rating = rating;
            this.count = count;
        }

        public int getRating() {
            return rating;
        }

        public void setRating(int rating) {
            this.rating = rating;
        }

        public long getCount() {
            return count;
        }

        public void setCount(long count) {
            this.count = count;
        }
    }

    public static class TopRatingItem {
        private long id;
        private String title;
        private double avgRating;
        private long reviewsCount;

        public TopRatingItem() {}

        public TopRatingItem(long id, String title, double avgRating, long reviewsCount) {
            this.id = id;
            this.title = title;
            this.avgRating = avgRating;
            this.reviewsCount = reviewsCount;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public double getAvgRating() {
            return avgRating;
        }

        public void setAvgRating(double avgRating) {
            this.avgRating = avgRating;
        }

        public long getReviewsCount() {
            return reviewsCount;
        }

        public void setReviewsCount(long reviewsCount) {
            this.reviewsCount = reviewsCount;
        }
    }

    public static class TopReviewsItem {
        private long id;
        private String title;
        private long reviewsCount;
        private double avgRating;

        public TopReviewsItem() {}

        public TopReviewsItem(long id, String title, long reviewsCount, double avgRating) {
            this.id = id;
            this.title = title;
            this.reviewsCount = reviewsCount;
            this.avgRating = avgRating;
        }

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public long getReviewsCount() {
            return reviewsCount;
        }

        public void setReviewsCount(long reviewsCount) {
            this.reviewsCount = reviewsCount;
        }

        public double getAvgRating() {
            return avgRating;
        }

        public void setAvgRating(double avgRating) {
            this.avgRating = avgRating;
        }
    }
}

