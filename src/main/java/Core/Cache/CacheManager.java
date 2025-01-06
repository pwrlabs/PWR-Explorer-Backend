package Core.Cache;

import Block.Block;
import Core.Sql.Queries;
import Txn.NewTxn;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static Core.Sql.Queries.*;
import static Core.Sql.Queries.getTotalTransactionCount;

public class CacheManager {
    private final Logger logger = LogManager.getLogger(CacheManager.class.getName());
    private final LoadingCache<Integer, List<Block>> blocksCache;
    private final LoadingCache<Integer, List<NewTxn>> recentTxnCache;
    private final LoadingCache<String, Integer> activeValidatorsCountCache;
    private final LoadingCache<String, Long> blocksCountCache;
    private final LoadingCache<String, Integer> totalTxnCountCache;
    private final LoadingCache<String, Map<Long, Integer>> fourteenDaysTxnCache;

    public CacheManager(PWRJ pwrj) {
        blocksCache = CacheBuilder.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public List<Block> load(Integer blocksCount) throws Exception {
                        return getLastXBlocks(blocksCount);
                    }
                });

        recentTxnCache = CacheBuilder.newBuilder()
                .maximumSize(4)
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public List<NewTxn> load(Integer txnsCount) throws Exception {
                        return getLastXTransactions(txnsCount);
                    }
                });

        activeValidatorsCountCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Override
                    public Integer load(String key) throws Exception {
                        return pwrj.getActiveValidatorsCount();
                    }
                });

        blocksCountCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(20, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public Long load(String key) throws Exception {
                        return getLastBlockNumber();
                    }
                });

        totalTxnCountCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .build(new CacheLoader<>() {
                    @Override
                    public Integer load(String key) {
                        return Queries.getTotalTransactionCount();
                    }
                });

        fourteenDaysTxnCache = CacheBuilder.newBuilder()
                .maximumSize(1)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build(new CacheLoader<>() {
                    @Override
                    public Map<Long, Integer> load(String key) {
                        return Queries.getFourteenDaysTxn();
                    }
                });
    }

    // Method to retrieve cached or fresh blocks
    public List<Block> getBlocks(int blockCount) {
        try {
            return blocksCache.get(blockCount);
        } catch (Exception e) {
            logger.error("Failed to fetch last {} blocks: ", blockCount, e);
        }
        return null;
    }

    public List<NewTxn> getRecentTxns(int txnsCount) {
        try {
            return recentTxnCache.get(txnsCount);
        } catch (Exception e) {
            logger.error("Failed to fetch last {} blocks: ", txnsCount, e);
        }
        return null;
    }

    public int getActiveValidatorsCount() {
        try {
            return activeValidatorsCountCache.get("count");
        } catch (Exception e) {
            logger.error("Failed to fetch active validators count: ", e);
        }
        return 0;
    }

    public long getBlocksCount() {
        try {
            return blocksCountCache.get("count");
        } catch (Exception e) {
            logger.error("Failed to fetch blocks count: ", e);
        }
        return 0;
    }

    public int getTotalTransactionCount() {
        try {
            return totalTxnCountCache.get("count");
        } catch (Exception e) {
            logger.error("Failed to fetch total transaction count: ", e);
        }
        return 0;
    }

    public Map<Long, Integer> getFourteenDaysTxn() {
        try {
            return fourteenDaysTxnCache.get("txns");
        } catch (Exception e) {
            logger.error("Failed to fetch 14 days transaction data: ", e);
        }
        return new HashMap<>();
    }
}