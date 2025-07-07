package Core;

import Services.AdminService;
import Services.DiscordAlertService;
import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.protocol.PWRJ;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import Database.Queries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static Database.Constants.Constants.BLOCK_TIMEOUT_MINUTES;
import static Database.Queries.*;
import static Services.DiscordAlertService.isRpcDown;

public class Synchronizer {
    private static final Logger logger = LoggerFactory.getLogger(Synchronizer.class);
    private static volatile boolean running = false;
    private static volatile boolean rpcHealthy = true;
    private static final boolean blockchainHealthy = true;
    private static long previousBlockTimestamp = -1;
    private static long previousBlockNumber = -1;
    private static final List<Block> blockBuffer = new ArrayList<>();
    private static final int BATCH_SIZE = 10;
    private static final long BATCH_MAX_TIME_MS = 2000; //2 seconds
    private static long batchStartTime = 0;
    private static long sleepBetweenBlockFetches = 100;

    public static void sync(PWRJ pwrj) {
        running = true;
        long blockToCheck = Math.max(getLastBlockNumber() + 1, 1);
//        long blockToCheck = 19758;

        logger.info("Synchronizer starting at block {}", blockToCheck);

        while (running) {
            try {
                long startTime = System.currentTimeMillis();
                long chainLatestBlock = getChainLatestBlock(pwrj);
                if (chainLatestBlock == -1) {
                    Thread.sleep(5000);
                    continue;
                }

                long lastStoredBlock = getLastBlockNumber();
                if (chainLatestBlock < lastStoredBlock) {
                    handleChainReset(lastStoredBlock, chainLatestBlock);
                    blockToCheck = 1;
                    continue;
                }

                if (blockToCheck > chainLatestBlock) {
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime < sleepBetweenBlockFetches) Thread.sleep(sleepBetweenBlockFetches - elapsedTime);
                    continue;
                }

                while (blockToCheck <= chainLatestBlock && running) {
                    if (!processBlock(pwrj, blockToCheck)) {
                        if (isRpcDown.get()) {// Check if we should continue or break
                            break;// Actual RPC error - break the loop
                        } else {
                            // Just a "block not ready" condition - skip this block and try the next one
                            logger.debug("Skipping block {} (not ready), continuing with next block", blockToCheck);
                            blockToCheck++;
                            throttleProcessing();
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

    private static boolean processBlock(PWRJ pwrj, long blockNumber) {
        try {
            Block block = getBlock(pwrj, blockNumber);
            if (block == null) {
                return false;
            }

            checkBlockHealth(block);
            blockBuffer.add(block);

            if (blockBuffer.size() == 1) {
                batchStartTime = System.currentTimeMillis();
            }

            long now = System.currentTimeMillis();

            boolean sizeReached = blockBuffer.size() >= BATCH_SIZE;
            boolean timeReached = (now - batchStartTime) >= BATCH_MAX_TIME_MS;

            if (timeReached) {
                logger.info("time reached {}", true);
            }

            if (sizeReached || timeReached) {
                insertBlockData(blockBuffer);
                Processor.processIncomingBlocks(blockBuffer);
                blockBuffer.clear();
                batchStartTime = 0;
            }

            return true;
        } catch (Exception e) {
            logger.error("Error processing block {}", blockNumber, e);
            return true;
        }
    }

    private static Block getBlock(PWRJ pwrj, long blockNumber) {
        try {
            Block block = pwrj.getBlockByNumber(blockNumber);
            handleRpcRecovery();
            return block;
        } catch (Exception e) {
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("400")) {
                logger.debug("getBlockByNumber error : Block {} not ready yet, skipping: {}", blockNumber, errorMessage);
                return null;
            } else {
                logger.error("RPC error getting block {}: {}", blockNumber, errorMessage, e);
                handleRpcError();
                return null;
            }
        }
    }

    private static void checkBlockHealth(Block block) {
        try {
            long currentBlockNumber = block.getBlockNumber();
            long currentTimestamp = block.getTimestamp();
            if (previousBlockNumber == -1 || previousBlockTimestamp == -1) {
                previousBlockNumber = currentBlockNumber;
                previousBlockTimestamp = currentTimestamp;
                logger.info("Initializing block health check with first block: {}", currentBlockNumber);
                return;
            }
            if (currentBlockNumber <= previousBlockNumber) {
                logger.debug("Skipping block {} because block number is not newer than previous ({})", currentBlockNumber, previousBlockNumber);
                return;
            }
            if (currentTimestamp <= previousBlockTimestamp) {
                logger.debug("Skipping block {} because timestamp is not newer than previous ({} <= {})",
                        currentBlockNumber, currentTimestamp, previousBlockTimestamp);
                return;
            }
            long diffMillis = currentTimestamp - previousBlockTimestamp;
            long diffMinutes = Duration.ofMillis(diffMillis).toMinutes();
            if (diffMinutes >= BLOCK_TIMEOUT_MINUTES) {
                logger.info("Block {} timestamp = {}, previous = {}", currentBlockNumber, Instant.ofEpochMilli(currentTimestamp), Instant.ofEpochMilli(previousBlockTimestamp));
                logger.info("Block {}, time diff with previous = {} minutes", currentBlockNumber, diffMinutes);

                if (!DiscordAlertService.isBlockchainDown.get()) {
                    logger.warn("Blockchain unhealthy: Block {} is {} minutes old", currentBlockNumber, diffMinutes);
//                    DiscordAlertService.handleBlockchainDown(
//                            "Block interval too large",
//                            currentBlockNumber,
//                            Instant.ofEpochMilli(currentTimestamp).toString(),
//                            diffMinutes
//                    );
                }
            } else if (DiscordAlertService.isBlockchainDown.get()) {
                // Log recovery info
                logger.info("Block {} timestamp = {}, previous = {}", currentBlockNumber,
                        Instant.ofEpochMilli(currentTimestamp), Instant.ofEpochMilli(previousBlockTimestamp));
                logger.info("Block {}, time diff with previous = {} minutes", currentBlockNumber, diffMinutes);
                logger.info("Blockchain recovered with block {} at {} ({} mins diff)",
                        currentBlockNumber,
                        Instant.ofEpochMilli(currentTimestamp),
                        diffMinutes);
//                DiscordAlertService.handleBlockchainUp(
//                        currentBlockNumber,
//                        Instant.ofEpochMilli(currentTimestamp).toString(),
//                        diffMinutes
//                );
            }
            previousBlockNumber = currentBlockNumber;
            previousBlockTimestamp = currentTimestamp;
        } catch (Exception e) {
            logger.error("Error checking block health for {}", block.getBlockNumber(), e);
        }
    }

    private static void insertBlockData(List<Block> blocks) {
        try {
            insertBlock(blocks);
            Queries.incrementSubmittedBlocksCount(blocks);
            logger.info("Incremented submitted blocks count");
            Queries.updateLatestBlockNumber(blocks);
            logger.info("Updated latest block number");
        } catch (Exception e) {
            logger.error("Error inserting from block {} -> {}", blocks.getFirst().getBlockNumber(), blocks.getLast().getBlockNumber(), e);
        }
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
