package tn.esprit.training.dto;

import java.util.List;

public class TrainingTrendsDTO {

    public List<CountPoint> counts;
    public List<PricePoint> prices;

    public static class CountPoint {
        public String date; // yyyy-MM-dd
        public long free;
        public long paid;
    }

    public static class PricePoint {
        public String date; // yyyy-MM-dd
        public double avgPaid;
        public double minPaid;
        public double maxAll;
    }
}

