package Core;

import Services.DiscordAlertService;
import com.github.pwrlabs.pwrj.entities.Block;
import com.github.pwrlabs.pwrj.entities.Validator;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import Database.Queries;

import static Database.Queries.*;

public class Synchronizer {
    private static final Logger logger = LogManager.getLogger(Synchronizer.class);
    private static final long BLOCK_TIMEOUT_MINUTES = 10;

    private static volatile boolean running = false;
    private static volatile boolean rpcHealthy = true;
    private static volatile boolean blockchainHealthy = true;
    private static int blockCounter = 0;

    public static void sync(PWRJ pwrj) {
        running = true;
        long blockToCheck = Math.max(getLastBlockNumber() + 1, 1);
        logger.info("Synchronizer starting at block {}", blockToCheck);

        while (running) {
            try {
                initializeValidatorsIfNeeded(pwrj);

                long chainLatestBlock = getChainLatestBlock(pwrj);
                if (chainLatestBlock == -1) {
                    Thread.sleep(5000);
                    continue;
                }

                if (blockToCheck > chainLatestBlock) {
                    handleChainReset(blockToCheck, chainLatestBlock);
                    blockToCheck = 1;
                    continue;
                }

                while (blockToCheck <= chainLatestBlock && running) {
                    if (!processBlock(pwrj, blockToCheck)) {
                        break;
                    }
                    blockToCheck++;
                    throttleProcessing();
                }

                if (running) {
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                logger.error("Error in sync loop", e);
                handleRpcError();
                sleepSafely(5000);
            }
        }

        logger.info("Synchronizer has stopped");
    }

    private static void initializeValidatorsIfNeeded(PWRJ pwrj) {
        try {
            long startBlockNumber = Math.max(Queries.getMaxProcessedBlockNumber(), 1);
            if (startBlockNumber <= 1) {
                Block block = pwrj.getBlockByNumber(1);
                List<Validator> validators = pwrj.getActiveValidators();

                if (validators != null && !validators.isEmpty()) {
                    for (Validator validator : validators) {
                        insertValidator(validator.getAddress(), block.getTimestamp());
                    }
                    logger.info("Inserted {} validators", validators.size());
                }
            }
        } catch (Exception e) {
            logger.error("Error initializing validators", e);
        }
    }

    private static long getChainLatestBlock(PWRJ pwrj) {
        try {
            long latest = pwrj.getLatestBlockNumber();
            handleRpcRecovery();
            return latest;
        } catch (Exception e) {
            logger.error("Error getting latest block number", e);
            handleRpcError();
            return -1;
        }
    }

    private static void handleChainReset(long expected, long actual) {
        logger.warn("Chain reset detected! Expected: {}, Actual: {}", expected, actual);
        DiscordAlertService.handleRpcReset();
    }

    private static boolean processBlock(PWRJ pwrj, long blockNumber) {
        try {
            Block block = getBlock(pwrj, blockNumber);
            if (block == null) {
                return false;
            }

            checkBlockHealth(block);
            insertBlockData(block);
            Processor.processIncomingBlock(block);

            if (++blockCounter % 10 == 0) {
                logger.info("Processed block: {}", blockNumber);
                blockCounter = 0;
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
            logger.error("Error getting block {}", blockNumber, e);
            handleRpcError();
            return null;
        }
    }

    private static void checkBlockHealth(Block block) {
        try {
            LocalDateTime blockTime = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(block.getTimestamp()),
                    ZoneId.systemDefault()
            );

            long minutesSince = Duration.between(blockTime, LocalDateTime.now()).toMinutes();
            boolean isHealthy = minutesSince < BLOCK_TIMEOUT_MINUTES;

            if (!isHealthy && blockchainHealthy) {
                blockchainHealthy = false;
                logger.warn("Blockchain unhealthy: Block {} is {} minutes old",
                        block.getBlockNumber(), minutesSince);
                DiscordAlertService.handleRpcFailure();
            } else if (isHealthy && !blockchainHealthy) {
                blockchainHealthy = true;
                logger.info("Blockchain recovered");
                DiscordAlertService.handleRpcRecovery();
            }
        } catch (Exception e) {
            logger.error("Error checking block health for {}", block.getBlockNumber(), e);
        }
    }

    private static void insertBlockData(Block block) {
        try {
            insertBlock(block.getBlockNumber(), block.getBlockHash().toLowerCase(),
                    block.getProposer().toLowerCase(), block.getTimestamp(),
                    block.getTransactionCount(), block.getBlockReward(),
                    block.getBlockSize(), block.isProcessedWithoutCriticalErrors());

            Queries.updateLifetimeReward(block.getProposer().toLowerCase(), block.getBlockReward());
            Queries.incrementSubmittedBlocksCount(block.getProposer().toLowerCase());
            Queries.updateLatestBlockNumber(block.getProposer(), block.getBlockNumber());
        } catch (Exception e) {
            logger.error("Error inserting block {}", block.getBlockNumber(), e);
        }
    }

    private static void handleRpcError() {
        if (rpcHealthy) {
            rpcHealthy = false;
            DiscordAlertService.handleRpcFailure();
        }
    }

    private static void handleRpcRecovery() {
        if (!rpcHealthy) {
            rpcHealthy = true;
            DiscordAlertService.handleRpcRecovery();
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
