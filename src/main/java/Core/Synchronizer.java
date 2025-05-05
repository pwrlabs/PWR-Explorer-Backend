package Core;

import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicBoolean;

import static Database.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);
    private static int blocks = 1;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    public static void sync(PWRJ pwrj) {
        running.set(true);
        long blockToCheck = getLastBlockNumber() + 1;
        if (blockToCheck == 0) blockToCheck = 1;
        logger.info("Synchronizer starting at block {}", blockToCheck);
        while (running.get()) {
            try {
                long latestBlockNumber = pwrj.getLatestBlockNumber();
                logger.info("Latest block number {}", latestBlockNumber);
                while (blockToCheck <= latestBlockNumber && running.get()) {
                    long startTime = System.currentTimeMillis();
                    try {
                        Block block = pwrj.getBlockByNumber(blockToCheck);
                        try {
                            insertBlock(block.getBlockNumber(), block.getBlockHash().toLowerCase(), block.getProposer().toLowerCase(),
                                    block.getTimestamp(), block.getTransactionCount(), block.getBlockReward(), block.getBlockSize(), block.isProcessedWithoutCriticalErrors()
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
                        if (running.get() && sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }
                    } catch (InterruptedException e) {
                        logger.info("Synchronizer thread was interrupted, stopping gracefully");
                        running.set(false);
                        return;
                    }
                }
                if (running.get()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        logger.info("Synchronizer thread was interrupted while waiting for new blocks");
                        running.set(false);
                        return;
                    }
                }
            } catch (Exception e) {
                logger.error("Error getting latest block number", e);
                try {
                    if (running.get()) {
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException ie) {
                    logger.info("Synchronizer thread was interrupted while recovering from an error");
                    running.set(false);
                    return;
                }
            }
        }

        logger.info("Synchronizer has stopped");
    }

    public static void stop() {
        logger.info("Stopping synchronizer...");
        running.set(false);
    }

    public static boolean isRunning() {
        return running.get();
    }

}
