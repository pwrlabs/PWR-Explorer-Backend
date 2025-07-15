package Services;

import Core.Cache.CacheManager;
import DataModel.Block;
import DataModel.NewTxn;
import Database.Config;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static Database.Queries.getLastXBlocks;
import static Database.Queries.getTransactions;
import static Utils.Helpers.populateTxnsResponse;
import static Utils.Helpers.returnHexStringWith0x;

enum SubscriptionType {
    LATEST_INFO,
    ALL_BLOCKS,
    ALL_TXNS
}

@WebSocket
public class WebsocketService {
    private static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    private static final Map<Session, SubscriptionType> subscriptions = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final CacheManager cacheManager = new CacheManager(new PWRJ(Config.getPwrRpcUrl()));
    private static final Logger logger = LoggerFactory.getLogger(WebsocketService.class);
    private static final long MAX_IDLE_TIMEOUT = 10 * 60 * 1000; // 10 minutes
    private static volatile boolean started = false;
    private static long latestBlockSent = 0;
    private static long latestTxnTimestamp = 0;

    public WebsocketService() {
        synchronized (WebsocketService.class) {
            if (!started) {
                scheduler.scheduleWithFixedDelay(this::sendLatestBlocks, 3, 3, TimeUnit.SECONDS);
                scheduler.scheduleWithFixedDelay(this::sendLatestTxns, 3, 3, TimeUnit.SECONDS);

                scheduler.scheduleWithFixedDelay(this::sendLastXBlocks, 3, 5, TimeUnit.SECONDS);
                scheduler.scheduleWithFixedDelay(this::sendLastXTxns, 3, 5, TimeUnit.SECONDS);

                started = true;
            }
        }
    }

    @OnWebSocketConnect
    public void onConnect(Session session) {
        try {
            session.setIdleTimeout(MAX_IDLE_TIMEOUT);
            sessions.add(session);
            session.getRemote().sendString("Connection established");
            logger.info("WebSocket connection established from {}", session.getRemoteAddress().getAddress());
        } catch (Exception e) {
            logger.error("Error during WebSocket connection: {}", e.getMessage());
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        subscriptions.remove(session);
        logger.info("WebSocket closed for session {} - Status: {}, Reason: {}",
                session.getRemoteAddress().getAddress(), statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        sessions.remove(session);
        subscriptions.remove(session);
        logger.error("WebSocket error for session {}: {}", session, error.getMessage());
    }

    @OnWebSocketMessage
    public void onMessage(Session session, String message) {
        try {
            JSONObject request = new JSONObject(message);
            String action = request.optString("action");
            String type = request.optString("type");

            if ("subscribe".equalsIgnoreCase(action)) {
                SubscriptionType subType = SubscriptionType.valueOf(type.toUpperCase());
                subscriptions.put(session, subType);
            } else if ("unsubscribe".equalsIgnoreCase(action)) {
                subscriptions.remove(session);
            }
        } catch (Exception e) {
            logger.error("Failed to handle subscription message: {}", e.getMessage());
        }
    }

    private void sendLatestBlocks() {
        try {
            List<Block> blockList = cacheManager.getBlocks(5);
            long blocksCount = cacheManager.getBlocksCount();
            for (Block block : blockList.reversed()) {
                if (Long.parseLong(block.blockNumber()) > latestBlockSent) {
                    JSONObject blockObj = new JSONObject();
                    blockObj.put("blockHeight", Long.parseLong(block.blockNumber()));
                    blockObj.put("timeStamp", block.timeStamp() / 1000);
                    blockObj.put("txnsCount", block.txnCount());
                    blockObj.put("blockReward", block.blockReward());
                    blockObj.put("blockSubmitter", returnHexStringWith0x(block.blockSubmitter()));

                    JSONObject res = new JSONObject();
                    res.put("event", "latest_blocks");
                    res.put("type", "new_block");
                    res.put("block", blockObj);
                    res.put("blocks_count", blocksCount);

                    broadcast(res.toString(), SubscriptionType.LATEST_INFO);

                    latestBlockSent = Math.max(latestBlockSent, Long.parseLong(block.blockNumber()));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send latest blocks: ", e);
        }
    }

    private void sendLatestTxns() {
        try {
            List<NewTxn> txnsList = cacheManager.getRecentTxns(5);
            long txnsCount = cacheManager.getTotalTransactionCount();

            for (NewTxn txn : txnsList.reversed()) {
                if (txn == null) continue;
                if (txn.timestamp() > latestTxnTimestamp) {
                    JSONObject txnObj = new JSONObject();
                    txnObj.put("txnHash", returnHexStringWith0x(txn.hash()));
                    txnObj.put("timeStamp", txn.timestamp() / 1000);
                    txnObj.put("from", returnHexStringWith0x(txn.fromAddress()));
                    txnObj.put("to", returnHexStringWith0x(txn.toAddress()));
                    txnObj.put("value", txn.value());

                    JSONObject res = new JSONObject();
                    res.put("event", "latest_txns");
                    res.put("type", "new_txn");
                    res.put("txn", txnObj);
                    res.put("txns_count", txnsCount);

                    broadcast(res.toString(), SubscriptionType.LATEST_INFO);

                    latestTxnTimestamp = Math.max(txn.timestamp(), latestTxnTimestamp);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send latest txns: ", e);
        }
    }

    private void sendLastXBlocks() {
        try {
            List<Block> blockList = getLastXBlocks(10, 1);

            for (Block block : blockList) {
                if (Long.parseLong(block.blockNumber()) > latestBlockSent) {
                    JSONObject blockObj = new JSONObject();
                    blockObj.put("blockHeight", block.blockNumber());
                    blockObj.put("timeStamp", block.timeStamp() / 1000);
                    blockObj.put("txnsCount", block.txnCount());
                    blockObj.put("blockReward", block.blockReward());
                    blockObj.put("blockSubmitter", returnHexStringWith0x(block.blockSubmitter()));

                    JSONObject res = new JSONObject();
                    res.put("event", "all_blocks");
                    res.put("type", "new_block");
                    res.put("block", blockObj);

                    broadcast(res.toString(), SubscriptionType.ALL_BLOCKS);

                    latestBlockSent = Math.max(latestBlockSent, Long.parseLong(block.blockNumber()));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send last X blocks: ", e);
        }
    }

    private void sendLastXTxns() {
        try {
            List<NewTxn> txns = getTransactions(10, 10);

            for (NewTxn txn : txns) {
                if (txn == null) continue;
                if (txn.timestamp() > latestTxnTimestamp) {
                    JSONObject txnObj = populateTxnsResponse(txn);

                    JSONObject res = new JSONObject();
                    res.put("event", "all_txns");
                    res.put("type", "new_txn");
                    res.put("txn", txnObj);

                    broadcast(res.toString(), SubscriptionType.ALL_TXNS);

                    latestTxnTimestamp = Math.max(txn.timestamp(), latestTxnTimestamp);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to send last X txns: ", e);
        }
    }

    private void broadcast(String message, SubscriptionType targetType) {
        for (Session session : sessions) {
            try {
                SubscriptionType sub = subscriptions.get(session);
                if (sub == targetType) {
                    session.getRemote().sendString(message);
                }
            } catch (Exception e) {
                logger.error("Failed to send WS message to session: ", e);
            }
        }
    }
}
