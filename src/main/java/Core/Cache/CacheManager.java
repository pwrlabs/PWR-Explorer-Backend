package Core.Cache;

import DataModel.Block;
import Database.Queries;
import DataModel.NewTxn;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.benmanes.caffeine.cache.*;
import com.github.pwrlabs.pwrj.record.validator.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static Database.Queries.*;

public class CacheManager {
    private final Logger logger = LogManager.getLogger(CacheManager.class.getName());
    private final AsyncLoadingCache<Integer, List<Block>> blocksCache;
    private final AsyncLoadingCache<Integer, List<NewTxn>> recentTxnCache;
    private final AsyncLoadingCache<String, Integer> activeValidatorsCountCache;
    private final AsyncLoadingCache<String, Long> blocksCountCache;
    private final AsyncLoadingCache<String, Integer> totalTxnCountCache;
    private final AsyncLoadingCache<String, Map<Long, Integer>> fourteenDaysTxnCache;
    private final AsyncLoadingCache<String, Double> averageTpsCache;
    private final AsyncLoadingCache<String, JSONObject> blockStatsCache;
    private final AsyncLoadingCache<String, List<Validator>> validatorsCache;
    private final AsyncLoadingCache<String, Integer> standByValidatorsCountCache;
    private final AsyncLoadingCache<String, Long> activeVotingPowerCache;
    private final AsyncLoadingCache<String, Long> totalVotingPowerCache;
    private final AsyncLoadingCache<String, Integer> txnsCountPast24Hours;
    private final AsyncLoadingCache<String, BigInteger> txnsFeesPast24Hours;
    private final AsyncLoadingCache<String, BigInteger> averageTxnFeesPast24Hours;
    private final AsyncLoadingCache<String, Double> avgTxnFeePercentageChangeCache;
    private final AsyncLoadingCache<String, Double> totalTxnFeesPercentageChangeCache;
    private final AsyncLoadingCache<String, Double> txnCountPercentageChangeCache;


    public CacheManager(PWRJ pwrj) {
        blocksCache = Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .buildAsync(Queries::getLastXBlocks);

        recentTxnCache = Caffeine.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .buildAsync(Queries::getLastXTransactions);

        activeValidatorsCountCache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .buildAsync(key -> pwrj.getActiveValidatorsCount());

        blocksCountCache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .buildAsync(key -> getLastBlockNumber());

        averageTpsCache = Caffeine.newBuilder()
                .maximumSize(2)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .buildAsync(key -> {
                    String[] parts = key.split("_");
                    int numberOfBlocks = Integer.parseInt(parts[1]);
                    int latestBlockNumber = Integer.parseInt(parts[3]);
                    return Queries.getAverageTps(numberOfBlocks, latestBlockNumber);
                });

        blockStatsCache = Caffeine.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .buildAsync(key -> Queries.get24HourBlockStats());

        // special caches with automatics background refetch
        totalTxnCountCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(30, TimeUnit.SECONDS)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> Queries.getTotalTransactionCount());

        fourteenDaysTxnCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(2, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> Queries.getFourteenDaysTxn());

        validatorsCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(2, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> pwrj.getActiveValidators());

        totalVotingPowerCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(2, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> pwrj.getTotalVotingPower());

        activeVotingPowerCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(2, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> pwrj.getActiveVotingPower());

        standByValidatorsCountCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(2, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> pwrj.getStandbyValidatorsCount());

        txnsCountPast24Hours = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> getTransactionCountPast24Hours());

        txnsFeesPast24Hours = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> getTotalTransactionFeesPast24Hours());

        averageTxnFeesPast24Hours = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> getAverageTransactionFeePast24Hours());

        avgTxnFeePercentageChangeCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> getAverageTransactionFeePercentageChange());

        totalTxnFeesPercentageChangeCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> getTotalTransactionFeesPercentageChange());

        txnCountPercentageChangeCache = Caffeine.newBuilder()
                .maximumSize(1)
                .refreshAfterWrite(5, TimeUnit.MINUTES)
                .scheduler(Scheduler.systemScheduler())
                .buildAsync(key -> getTransactionCountPercentageChangeComparedToPreviousDay());
    }

    // Method to retrieve cached or fresh blocks
    public List<Block> getBlocks(int blockCount) {
        try {
            return blocksCache.get(blockCount).get();  // Wait for async result
        } catch (Exception e) {
            logger.error("Failed to fetch last {} blocks: ", blockCount, e);
        }
        return null;
    }

    public List<NewTxn> getRecentTxns(int txnsCount) {
        try {
            return recentTxnCache.get(txnsCount).get();
        } catch (Exception e) {
            logger.error("Failed to fetch last {} blocks: ", txnsCount, e);
        }
        return null;
    }

    public int getActiveValidatorsCount() {
        try {
            return activeValidatorsCountCache.get("count").get();
        } catch (Exception e) {
            logger.error("Failed to fetch active validators count: ", e);
        }
        return 0;
    }

    public long getBlocksCount() {
        try {
            return blocksCountCache.get("count").get();
        } catch (Exception e) {
            logger.error("Failed to fetch blocks count: ", e);
        }
        return 0;
    }

    public int getTotalTransactionCount() {
        try {
            return totalTxnCountCache.get("count").get();
        } catch (Exception e) {
            logger.error("Failed to fetch total transaction count: ", e);
        }
        return 0;
    }

    public Map<Long, Integer> getFourteenDaysTxn() {
        try {
            return fourteenDaysTxnCache.get("txns").get();
        } catch (Exception e) {
            logger.error("Failed to fetch 14 days transaction data: ", e);
        }
        return new HashMap<>();
    }

    public double getAverageTps(int numberOfBlocks, long latestBlockNumber) {
        try {
            String cacheKey = String.format("blocks_%d_latest_%d", numberOfBlocks, latestBlockNumber);
            return averageTpsCache.get(cacheKey).get();
        } catch (Exception e) {
            logger.error("Failed to fetch average TPS: ", e);
        }
        return 0.0;
    }

    public JSONObject get24HourBlockStats() {
        try {
            return blockStatsCache.get("stats").get();
        } catch (Exception e) {
            logger.error("Failed to fetch 24h block stats: ", e);
            return new JSONObject();
        }
    }

    public List<Validator> getActiveValidators() {
        try {
            return validatorsCache.get("validators").get();
        } catch (Exception e) {
            logger.info("Failed to fetch active validators: ", e);
            return new ArrayList<>();
        }
    }

    public long getTotalVotingPower() {
        try {
            return totalVotingPowerCache.get("count").get();
        } catch (Exception e) {
            logger.info("Failed to fetch total voting power: ", e);
            return 0;
        }
    }

    public long getActiveVotingPower() {
        try {
            return activeVotingPowerCache.get("count").get();
        } catch (Exception e) {
            logger.info("Failed to fetch active voting power: {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public int getStandByValidatorsCount() {
        try {
            return standByValidatorsCountCache.get("count").get();
        } catch (Exception e) {
            logger.info("Failed to fetch stand by validators count: {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public int getTxnsCountPast24Hours() {
        try {
            return txnsCountPast24Hours.get("count").get();
        } catch (Exception e) {
            logger.info("Failed to fetch txns count past 24 hrs: {}", e.getLocalizedMessage());
            return 0;
        }
    }

    public BigInteger getAverageTxnFeePast24Hours() {
        try {
            return averageTxnFeesPast24Hours.get("count").get();
        } catch (Exception e) {
            logger.info("Failed to fetch average txn fee past 24 hrs: {}", e.getLocalizedMessage());
            return BigInteger.ZERO;
        }
    }

    public BigInteger getTotalTxnsFeesPast24Hours() {
        try {
            return txnsFeesPast24Hours.get("count").get();
        } catch (Exception e) {
            logger.info("Failed to fetch total txn fees past 24 hrs: {}", e.getLocalizedMessage());
            return BigInteger.ZERO;
        }
    }

    public double getAvgTxnFeePercentageChange() {
        try {
            return avgTxnFeePercentageChangeCache.get("change").get();
        } catch (Exception e) {
            logger.info("Failed to fetch average transaction fee percentage change: {}", e.getLocalizedMessage());
            return 0.0;
        }
    }

    public double getTotalTxnFeesPercentageChange() {
        try {
            return totalTxnFeesPercentageChangeCache.get("change").get();
        } catch (Exception e) {
            logger.info("Failed to fetch total transaction fees percentage change: {}", e.getLocalizedMessage());
            return 0.0;
        }
    }

    public double getTxnCountPercentageChange() {
        try {
            return txnCountPercentageChangeCache.get("change").get();
        } catch (Exception e) {
            logger.info("Failed to fetch transaction count percentage change: {}", e.getLocalizedMessage());
            return 0.0;
        }
    }
}