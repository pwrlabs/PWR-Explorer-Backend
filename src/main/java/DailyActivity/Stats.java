package DailyActivity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class Stats {
    private static Stats instance = null;

    private HashMap<String, Long> validatorRewards;
    private long txnCount;
    private long blockCount;
    private Queue<BlockInfo> recentBlocks;
    private double tps;
    private long totalFees24h;
    private long transactions24h;
    private long totalBlockSize24h;
    private static final int BLOCKS_24H = 7200; // Assuming 12s block time
    private static final int TPS_BLOCK_WINDOW = 1000;

    private static class BlockInfo {
        long timestamp;
        int transactions;
        long blockSize;
        long fees;

        BlockInfo(long timestamp, int transactions, long blockSize, long fees) {
            this.timestamp = timestamp;
            this.transactions = transactions;
            this.blockSize = blockSize;
            this.fees = fees;
        }
    }

    private Stats() {
        this.validatorRewards = new HashMap<>();
        this.txnCount = 0;
        this.blockCount = 0;
        this.recentBlocks = new LinkedList<>();
        this.tps = 0.0;
        this.totalFees24h = 0;
        this.transactions24h = 0;
        this.totalBlockSize24h = 0;
    }

    public static synchronized Stats getInstance() {
        if (instance == null) {
            instance = new Stats();
        }
        return instance;
    }

    public void processBlock(String validatorAddress, int transactionsInBlock, long reward, long timestamp, long blockSize) {
        txnCount += transactionsInBlock;
        blockCount++;
        updateValidatorRewards(validatorAddress, reward);

        BlockInfo newBlock = new BlockInfo(timestamp, transactionsInBlock, blockSize, reward);
        recentBlocks.offer(newBlock);

        if (recentBlocks.size() > BLOCKS_24H) {
            BlockInfo oldBlock = recentBlocks.poll();
            transactions24h -= oldBlock.transactions;
            totalBlockSize24h -= oldBlock.blockSize;
            totalFees24h -= oldBlock.fees;
        }

        transactions24h += transactionsInBlock;
        totalBlockSize24h += blockSize;
        totalFees24h += reward;

        updateTPS();
    }

    private void updateValidatorRewards(String validatorAddress, long reward) {
        validatorRewards.merge(validatorAddress, reward, Long::sum);
    }

    private void updateTPS() {
        if (recentBlocks.size() < 2) {
            tps = 0.0;
            return;
        }

        long oldestTimestamp = ((LinkedList<BlockInfo>) recentBlocks).getFirst().timestamp;
        long newestTimestamp = ((LinkedList<BlockInfo>) recentBlocks).getLast().timestamp;
        long totalTransactions = recentBlocks.stream().mapToInt(b -> b.transactions).sum();

        double timeSpanSeconds = (newestTimestamp - oldestTimestamp) / 1000.0;
        if (timeSpanSeconds == 0) {
            tps = 0.0;
        } else {
            tps = totalTransactions / timeSpanSeconds;
        }
    }

    public long getTotalTransactionCount() {
        return txnCount;
    }

    public long getTotalBlockCount() {
        return blockCount;
    }

    public long getTotalFees() {
        return validatorRewards.values().stream().mapToLong(Long::longValue).sum();
    }

    public long getValidatorRewards(String validatorAddress) {
        return validatorRewards.getOrDefault(validatorAddress, 0L);
    }

    public HashMap<String, Long> getAllValidatorRewards() {
        return new HashMap<>(validatorRewards);
    }

    public double getTPS() {
        return tps;
    }

    public long getTotalFees24h() {
        return totalFees24h;
    }

    public double getAvgFeePerTransaction24h() {
        return transactions24h > 0 ? (double) totalFees24h / transactions24h : 0;
    }

    public long getTransactions24h() {
        return transactions24h;
    }

    public double getAvgBlockSize24h() {
        return recentBlocks.size() > 0 ? (double) totalBlockSize24h / recentBlocks.size() : 0;
    }

    public void reset() {
        this.validatorRewards.clear();
        this.txnCount = 0;
        this.blockCount = 0;
        this.recentBlocks.clear();
        this.tps = 0.0;
        this.totalFees24h = 0;
        this.transactions24h = 0;
        this.totalBlockSize24h = 0;
    }

    @Override
    public String toString() {
        return "Stats{" +
                "totalTransactions=" + txnCount +
                ", totalBlocks=" + blockCount +
                ", totalFees=" + getTotalFees() +
                ", validatorRewards=" + validatorRewards +
                ", TPS=" + String.format("%.2f", tps) +
                ", TOTAL FEES (24h)=" + totalFees24h +
                ", AVG. FEE PER TRANSACTION (24h)=" + String.format("%.2f", getAvgFeePerTransaction24h()) +
                ", TRANSACTIONS (24h)=" + transactions24h +
                ", AVERAGE BLOCK SIZE (24h)=" + String.format("%.2f", getAvgBlockSize24h()) +
                '}';
    }
}