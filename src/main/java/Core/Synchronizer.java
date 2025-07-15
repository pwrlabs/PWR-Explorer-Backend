package Core;

import Database.Queries;
import Services.AdminService;
import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.entities.FalconTransaction;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import io.pwrlabs.util.encoders.BiResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static Database.Queries.*;
import static Services.DiscordAlertService.isRpcDown;

public class Synchronizer {
    private static final Logger logger = LoggerFactory.getLogger(Synchronizer.class);
    private static volatile boolean running = false;
    private static volatile boolean rpcHealthy = true;
    private static final boolean blockchainHealthy = true;
    private static final long sleepBetweenBlockFetches = 100;

    public static void sync(PWRJ pwrj) {
        running = true;
        long blockToCheck = Math.max(getLastStoredBlock() + 1, 1);
        logger.info("Synchronizer starting at block {}", blockToCheck);

        while (running) {
            try {
                long startTime = System.currentTimeMillis();
                long chainLatestBlock = getChainLatestBlock(pwrj);
                if (chainLatestBlock == -1) {
                    Thread.sleep(5000);
                    continue;
                }

                long lastStoredBlock = getLastStoredBlock();
//                if (chainLatestBlock < lastStoredBlock) {
//                    handleChainReset(lastStoredBlock, chainLatestBlock);
//                    blockToCheck = 1;
//                    continue;
//                }

                if (blockToCheck > chainLatestBlock) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime < sleepBetweenBlockFetches) Thread.sleep(sleepBetweenBlockFetches - elapsedTime);
                    continue;
                }

                if (blockToCheck == 1) {
                    initializeValidators(pwrj.getBlockByNumber(blockToCheck));
                }

                int maxRetries = 5;
                int retryCount = 0;
                while (blockToCheck <= chainLatestBlock && running) {
                    try {
                        long fetchStart = System.currentTimeMillis();
                        BiResult<Block, List<FalconTransaction>> blockAndTransactions = pwrj.getBlockAndTransactions(blockToCheck);
                        Block block = blockAndTransactions.getFirst();
                        List<FalconTransaction> txns = blockAndTransactions.getSecond();
                        logger.info("Fetched {} transactions for block {} in {} ms", txns.size(), blockToCheck, System.currentTimeMillis() - fetchStart);

                        insertBlock(block);
                        Queries.incrementSubmittedBlocksCount(block);
                        Queries.updateLastStoredBlock(blockToCheck);

                        Processor.processTxns(blockToCheck, txns);

                        retryCount = 0;
                    }
                    catch (Exception e) {
                        if (isRpcDown.get()) {
                            logger.error("RPC down breaking loop");
                            break;
                        } else {
                            long retryTime = System.currentTimeMillis();
                            logger.error("Error fetching block {}, attempt {}: {}", blockToCheck, retryCount + 1, e.getMessage());
                            e.printStackTrace();

                            retryCount++;
                            if (retryCount >= maxRetries) {
                                logger.warn("Skipping block {} after {} failed attempts", blockToCheck, maxRetries);
                                blockToCheck++;
                                retryCount = 0;
                            }

                            long timeTook = System.currentTimeMillis() - retryTime;
                            Thread.sleep(100 - timeTook);
                            continue;
                        }
                    }

                    blockToCheck++;
                    throttleProcessing();
                }

                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime < sleepBetweenBlockFetches) Thread.sleep(sleepBetweenBlockFetches - elapsedTime);

            } catch (Exception e) {
                logger.error("Error in sync loop", e);
                handleRpcError();
                sleepSafely(1000);
            }
        }

        logger.info("Synchronizer has stopped");
    }

    private static long getChainLatestBlock(PWRJ pwrj) {
        try {
            long latestBlock = pwrj.getLatestBlockNumber();
            if (isRpcDown.get()) {
                handleRpcRecovery();
            }
            return latestBlock;
        } catch (Exception e) {
            logger.error("Error getting latest block number", e);
            handleRpcError();
            return -1;
        }
    }

    private static void handleChainReset(long expected, long actual) {
        logger.warn("Chain reset detected! Expected: {}, Actual: {}", expected, actual);
//        DiscordAlertService.handleRpcReset();
        AdminService.resetSystemInternal("Chain reset detected - Expected: " + expected + ", Actual: " + actual);
    }

    private static void handleRpcError() {
        if (rpcHealthy) {
            rpcHealthy = false;
//            DiscordAlertService.handleRpcFailure();
        }
    }

    private static void handleRpcRecovery() {
        if (!rpcHealthy) {
            rpcHealthy = true;
//            DiscordAlertService.handleRpcRecovery();
        }
    }

    private static void throttleProcessing() {
        try {
            if (Processor.shouldFlushBuffer()) {
                Processor.flushTransactionBuffer();
            }
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    private static void sleepSafely(long millis) {
        try {
            if (running) {
                Thread.sleep(millis);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }

    public static void stop() {
        logger.info("Stopping synchronizer...");
        running = false;
    }

    public static boolean isRunning() {
        return running;
    }

    public static boolean isRpcHealthy() {
        return rpcHealthy;
    }

    public static boolean isBlockchainHealthy() {
        return blockchainHealthy;
    }

}
