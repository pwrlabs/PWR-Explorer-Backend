package Block;

import Main.Settings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class Blocks {

    private static Map<String /*Block Number*/, Block> blockByNumber = new HashMap<>();
    private static long latestBlockNumber = 0;

    public static void add(Block block) {
        blockByNumber.put(block.id, block);

        long blockNumber = Long.parseLong(block.id);
        if(blockNumber > latestBlockNumber) {
            latestBlockNumber = blockNumber;
        }
    }

    public static Block getBlock(Long blockNumber) {
        return blockByNumber.get(blockNumber.toString());
    }
    public static long getLatestBlockNumber() {
        return latestBlockNumber;
    }
    public static double getAverageTps(int numberOfBlocks) {
        long totalTxnCount = 0;
        int blocksCounted = 0;

        long blockNumberToCheck = Blocks.getLatestBlockNumber();
        for(int i = 0; i < numberOfBlocks; i++) {
            Block block = Blocks.getBlock(blockNumberToCheck - i);
            if(block == null) break;

            totalTxnCount += block.getTxnsCount();
            ++blocksCounted;
        }

        if(blocksCounted == 0) return 0;
        return BigDecimal.valueOf(totalTxnCount).divide(BigDecimal.valueOf(blocksCounted), 1, BigDecimal.ROUND_HALF_UP).doubleValue();
    }

    //==================================================================================================================

    private static double networkUtilizationPast24Hours = 0;
    private static int averageBlockSizePast24Hours = 0;
    private static long totalBlockRewardsPast24Hours = 0;

    public static void updateBlock24HourStats() {
        long totalBlockSizePast24Hours = 0;
        long totalBlockRewardsPast24Hours = 0;
        long totalBlockCountPast24Hours = 0;

        long timeNow = Instant.now().getEpochSecond();
        long blockNumberToCheck = Blocks.getLatestBlockNumber();
        while(true) {
            Block block = Blocks.getBlock(blockNumberToCheck--);
            if(block == null) break;
            if(block.getTimeStamp() < timeNow - 24 * 60 * 60) break;

            totalBlockSizePast24Hours += block.getBlockSize();
            totalBlockRewardsPast24Hours += block.getBlockReward();
            totalBlockCountPast24Hours++;
        }
        if(totalBlockCountPast24Hours == 0) averageBlockSizePast24Hours = 0;
        else averageBlockSizePast24Hours = (int) (totalBlockSizePast24Hours / totalBlockCountPast24Hours);

        if(averageBlockSizePast24Hours == 0) networkUtilizationPast24Hours = 0;
        else networkUtilizationPast24Hours = BigDecimal.valueOf( ((double)averageBlockSizePast24Hours / (double)Settings.getBlockSizeLimit()) * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();

        Blocks.totalBlockRewardsPast24Hours = totalBlockRewardsPast24Hours;
    }

    public static double getNetworkUtilizationPast24Hours() {
        return networkUtilizationPast24Hours;
    }

    public static int getAverageBlockSizePast24Hours() {
        return averageBlockSizePast24Hours;
    }

    public static long getTotalBlockRewardsPast24Hours() {
        return totalBlockRewardsPast24Hours;
    }

}
