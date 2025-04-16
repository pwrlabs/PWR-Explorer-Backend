package Core;

import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static Database.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);
    private static int blocks = 1;

    public static void sync(PWRJ pwrj) {
        new Thread(() -> {
            long blockToCheck = getLastBlockNumber();
            if (blockToCheck == 0) blockToCheck = 1;
            while (true) {
                try {
                    long latestBlockNumber = pwrj.getLatestBlockNumber();
                    logger.info("Latest block number {}", latestBlockNumber);
                    while (blockToCheck <= latestBlockNumber) {
                        long startTime = System.currentTimeMillis();
                        try {
                            Block block = pwrj.getBlockByNumber(blockToCheck);
                            try {
                                String proposer = block.getProposer().startsWith("0x") ? block.getProposer() : "0x" + block.getProposer();
                                insertBlock(block.getBlockNumber(), block.getBlockHash().toLowerCase(), proposer,
                                        block.getTimeStamp(), block.getTransactionCount(), block.getBlockReward(), block.getBlockSize(), block.isProcessedWithoutCriticalErrors()
                                );

                                blocks++;
                                if (blocks % 10 == 0) {
                                    logger.info("Scanned block: {}", block.getBlockNumber());
                                    blocks = 0;
                                }
                            } catch (Exception e) {
                                logger.error("Error inserting block: {}", blockToCheck, e);
                            }
                            Processor.processIncomingBlock(block);
                        } catch (Exception e) {
                            logger.error("Error processing block: {} {}", blockToCheck, e);
                        }

                        ++blockToCheck;

                        try {
                            if (Processor.shouldFlushBuffer()) {
                                Processor.flushTransactionBuffer();
                            }

                            long processingTime = System.currentTimeMillis() - startTime;
                            long sleepTime = Math.max(0, 10 - processingTime);
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) {
                            logger.error("Thread sleep interrupted", e);
                        }
                    }
                } catch (Exception e) {
                    logger.error("Error getting latest block number", e);
                }
            }
        }).start();
    }
}
