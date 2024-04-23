package DailyActivity;

import java.util.Date;

public class DailyActivity {
    private Date day;
    private int transactionCount;
    private long totalValue;
    private long totalFee;
    private long totalAmountUsd;
    private long totalFeeUsd;

    public DailyActivity(Date day, int transactionCount, long totalValue, long totalFee, long totalAmountUsd, long totalFeeUsd) {
        this.day = day;
        this.transactionCount = transactionCount;
        this.totalValue = totalValue;
        this.totalFee = totalFee;
        this.totalAmountUsd = totalAmountUsd;
        this.totalFeeUsd = totalFeeUsd;
    }

    public Date getDay() {
        return day;
    }

    public void setDay(Date day) {
        this.day = day;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }

    public long getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(long totalValue) {
        this.totalValue = totalValue;
    }

    public long getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(long totalFee) {
        this.totalFee = totalFee;
    }

    public long getTotalAmountUsd() {
        return totalAmountUsd;
    }

    public void setTotalAmountUsd(long totalAmountUsd) {
        this.totalAmountUsd = totalAmountUsd;
    }

    public long getTotalFeeUsd() {
        return totalFeeUsd;
    }

    public void setTotalFeeUsd(long totalFeeUsd) {
        this.totalFeeUsd = totalFeeUsd;
    }

    @Override
    public String toString() {
        return "DailyActivity{" +
                "day=" + day +
                ", transactionCount=" + transactionCount +
                ", totalValue=" + totalValue +
                ", totalFee=" + totalFee +
                ", totalAmountUsd=" + totalAmountUsd +
                ", totalFeeUsd=" + totalFeeUsd +
                '}';
    }
}
