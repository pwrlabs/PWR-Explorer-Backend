package Block;

import Main.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigDecimal;
import java.time.Instant;

import static Core.Sql.Queries.getDbBlock;
import static Core.Sql.Queries.getLastBlockNumber;


public class Blocks {
    private static final Logger logger = LogManager.getLogger(Block.class);
    private static long latestBlockNumber = 0;

    public static double getAverageTps(int numberOfBlocks) {
        long totalTxnCount = 0;
        int blocksCounted = 0;

        long blockNumberToCheck = getLastBlockNumber();
        for(int i = 0; i < numberOfBlocks; i++) {
            Block block = getDbBlock(blockNumberToCheck - i);
            if(block == null) break;

            totalTxnCount += block.getTxnCount();
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
        logger.info("UPDATING BLOCK");
        long totalBlockSizePast24Hours = 0;
        long totalBlockRewardsPast24Hours = 0;
        long totalBlockCountPast24Hours = 0;

        long timeNow = Instant.now().getEpochSecond();
        long blockNumberToCheck = getLastBlockNumber();
        while(true) {
            Block block = getDbBlock(blockNumberToCheck);
            if(block == null) {
                logger.info(">>Block is null for block number: {}", (blockNumberToCheck));
                Blocks.latestBlockNumber = ++blockNumberToCheck;
                break;
            }

            long blockTimestamp = block.getTimeStamp();
            if(blockTimestamp < timeNow - 24 * 60 * 60) {
                logger.info(">>Block is older than 24 hours. Stopping iteration.");
                break;
            }

            totalBlockSizePast24Hours += block.getBlockSize();
            totalBlockRewardsPast24Hours += block.getBlockReward();
            totalBlockCountPast24Hours++;
        }

        if(totalBlockCountPast24Hours == 0) {
            logger.info("No blocks found in the past 24 hours.");
            averageBlockSizePast24Hours = 0;
        } else {
            averageBlockSizePast24Hours = (int) (totalBlockSizePast24Hours / totalBlockCountPast24Hours);
        }

        if(averageBlockSizePast24Hours == 0) {
            logger.info("Average block size is zero.");
            networkUtilizationPast24Hours = 0;
        } else {
            networkUtilizationPast24Hours = BigDecimal.valueOf(((double)averageBlockSizePast24Hours / (double)Settings.getBlockSizeLimit()) * 100).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        }

        Blocks.totalBlockRewardsPast24Hours = totalBlockRewardsPast24Hours;
        logger.info("REWARDS 24: {}", totalBlockRewardsPast24Hours);
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
