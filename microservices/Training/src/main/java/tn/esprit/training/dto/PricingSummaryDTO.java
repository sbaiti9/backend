package tn.esprit.training.dto;

public class PricingSummaryDTO {
    private long freeCount;
    private long paidCount;
    private double avgPaidPrice;
    private double minPaidPrice;
    private double maxPrice;

    public PricingSummaryDTO() {}

    public PricingSummaryDTO(long freeCount, long paidCount, double avgPaidPrice, double minPaidPrice, double maxPrice) {
        this.freeCount = freeCount;
        this.paidCount = paidCount;
        this.avgPaidPrice = avgPaidPrice;
        this.minPaidPrice = minPaidPrice;
        this.maxPrice = maxPrice;
    }

    public long getFreeCount() {
        return freeCount;
    }

    public void setFreeCount(long freeCount) {
        this.freeCount = freeCount;
    }

    public long getPaidCount() {
        return paidCount;
    }

    public void setPaidCount(long paidCount) {
        this.paidCount = paidCount;
    }

    public double getAvgPaidPrice() {
        return avgPaidPrice;
    }

    public void setAvgPaidPrice(double avgPaidPrice) {
        this.avgPaidPrice = avgPaidPrice;
    }

    public double getMinPaidPrice() {
        return minPaidPrice;
    }

    public void setMinPaidPrice(double minPaidPrice) {
        this.minPaidPrice = minPaidPrice;
    }

    public double getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(double maxPrice) {
        this.maxPrice = maxPrice;
    }
}

