package Txn;

import Block.Blocks;
import Block.Block;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Txns {

    private static Map<String /*Txn Hash*/, Txn> txnByHash = new HashMap<>();

    public static void add(Txn txn) {
        if (txnByHash.get(txn.id.toLowerCase()) != null) {
            System.out.println("Txn already exists: " + txn.id);
        }
        txnByHash.put(txn.id.toLowerCase(), txn);
    }

    public static Txn getTxn(String txnHash) {
        return txnByHash.get(txnHash.toLowerCase());
    }

    public static int getTxnCount() {
        return txnByHash.size();
    }
    //==================================================================================================================

    private static long txnCountPast24Hours = 0;
    private static double txnCountPercentageChangeComparedToPreviousDay = 0;

    private static long totalTxnFeesPast24Hours = 0;
    private static double totalTxnFeesPercentageChangeComparedToPreviousDay = 0;

    private static long averageTxnFeePast24Hours = 0;
    private static double averageTxnFeePercentageChangeComparedToPreviousDay = 0;

    public static void updateTxn24HourStats() {
        long txnCountPast24Hours = 0;
        long totalTxnFeesPast24Hours = 0;
        long averageTxnFeePast24Hours = 0;

        long txnCountThe24HoursBefore = 0;
        long totalTxnFeesThe24HoursBefore = 0;
        long averageTxnFeeThe24HoursBefore = 0;

        long timeNow = Instant.now().getEpochSecond();
        long blockNumberToCheck = Blocks.getLatestBlockNumber();
        while(true) {
            Block block = Blocks.getBlock(blockNumberToCheck--);
            if(block == null) break;
            if(block.getTimeStamp() < timeNow - 24 * 60 * 60) break;

            txnCountPast24Hours += block.getTxnsCount();
            for(Txn txn : block.getTxns()) {
                try {totalTxnFeesPast24Hours += txn.getTxnFee(); } catch (Exception e) {
                    //System.out.println("Block number: " + block.id);
                    //e.printStackTrace();
                    //try { Thread.sleep(1000); } catch (Exception e2) {}
                }
            }
        }
        if(txnCountPast24Hours == 0) averageTxnFeePast24Hours = 0;
        else averageTxnFeePast24Hours = totalTxnFeesPast24Hours / txnCountPast24Hours;

        //Calculate the stats of the 24 hours before the current 24 hours
        while(true) {
            Block block = Blocks.getBlock(blockNumberToCheck--);
            if(block == null) break;
            if(block.getTimeStamp() < timeNow - 24 * 60 * 60 * 2) break;

            txnCountThe24HoursBefore += block.getTxnsCount();
            for(Txn txn : block.getTxns()) {
                try { totalTxnFeesThe24HoursBefore += txn.getTxnFee(); } catch (Exception e) {
                    //System.out.println("Block number: " + block.id);
                    //e.printStackTrace();
                    //try { Thread.sleep(1000); } catch (Exception e2) {}
                }
            }
        }
        if(txnCountThe24HoursBefore == 0) averageTxnFeeThe24HoursBefore = 0;
        else averageTxnFeeThe24HoursBefore = totalTxnFeesThe24HoursBefore / txnCountThe24HoursBefore;

        //Calculate the percentage change
        if(txnCountThe24HoursBefore == 0) txnCountPercentageChangeComparedToPreviousDay = 0;
        else txnCountPercentageChangeComparedToPreviousDay = BigDecimal.valueOf((txnCountPast24Hours - txnCountThe24HoursBefore) / (double) txnCountThe24HoursBefore * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

        if(totalTxnFeesThe24HoursBefore == 0) totalTxnFeesPercentageChangeComparedToPreviousDay = 0;
        else totalTxnFeesPercentageChangeComparedToPreviousDay = BigDecimal.valueOf((totalTxnFeesPast24Hours - totalTxnFeesThe24HoursBefore) / (double) totalTxnFeesThe24HoursBefore * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

        if(averageTxnFeeThe24HoursBefore == 0) averageTxnFeePercentageChangeComparedToPreviousDay = 0;
        else averageTxnFeePercentageChangeComparedToPreviousDay = BigDecimal.valueOf((averageTxnFeePast24Hours - averageTxnFeeThe24HoursBefore) / (double) averageTxnFeeThe24HoursBefore * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

        //Update the static variables
        Txns.txnCountPast24Hours = txnCountPast24Hours;
        Txns.totalTxnFeesPast24Hours = totalTxnFeesPast24Hours;
        Txns.averageTxnFeePast24Hours = averageTxnFeePast24Hours;
    }

    public static long getTxnCountPast24Hours() {
        return txnCountPast24Hours;
    }

    public static double getTxnCountPercentageChangeComparedToPreviousDay() {
        return txnCountPercentageChangeComparedToPreviousDay;
    }

    public static long getTotalTxnFeesPast24Hours() {
        return totalTxnFeesPast24Hours;
    }

    public static double getTotalTxnFeesPercentageChangeComparedToPreviousDay() {
        return totalTxnFeesPercentageChangeComparedToPreviousDay;
    }

    public static long getAverageTxnFeePast24Hours() {
        return averageTxnFeePast24Hours;
    }

    public static double getAverageTxnFeePercentageChangeComparedToPreviousDay() {
        return averageTxnFeePercentageChangeComparedToPreviousDay;
    }

}
