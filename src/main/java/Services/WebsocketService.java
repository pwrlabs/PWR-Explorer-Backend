package Services;

import Core.Cache.CacheManager;
import DataModel.Block;
import DataModel.NewTxn;
import Database.Config;
import Utils.Settings;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static Utils.Helpers.returnHexStringWith0x;
import static Utils.ResponseBuilder.getSuccess;

@WebSocket
public class WebsocketService {
    private static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final CacheManager cacheManager = new CacheManager(new PWRJ(Config.getPwrRpcUrl()));
    private static final Logger logger = LoggerFactory.getLogger(WebsocketService.class);
    private static final long MAX_IDLE_TIMEOUT = 60 * 10 * 1000; // 10 minutes
    private static volatile boolean started = false;
    private static volatile int startCount = 0;
    private static String data = "";

    public WebsocketService() {
        synchronized (WebsocketService.class) {
            if (!started) {
                int period = 10;
                if (startCount == 0) {
                    period = 0;
                    startCount++;
                }
                scheduler.scheduleWithFixedDelay(this::sendExplorerInfo, 0, period, TimeUnit.SECONDS);
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
            session.getRemote().sendString(data);
            logger.info("WebSocket connection established from {}", session.getRemoteAddress().getAddress());
        } catch (Exception e) {
            logger.error("Error during WebSocket connection: {}", e.getMessage());
        }
    }

    @OnWebSocketClose
    public void onClose(Session session, int statusCode, String reason) {
        sessions.remove(session);
        logger.info("WebSocket closed for session {} - Status: {}, Reason: {}",
                session.getRemoteAddress().getAddress(), statusCode, reason);
    }

    @OnWebSocketError
    public void onError(Session session, Throwable error) {
        sessions.remove(session);
        logger.error("WebSocket error for session {}: {}", session, error.getMessage());
    }

    // Builds and sends the explorer info to all sessions
    private void sendExplorerInfo() {
        try {
            CompletableFuture<JSONArray> blocksFuture = CompletableFuture.supplyAsync(() -> {
                JSONArray blocks = new JSONArray();
                List<Block> blockList = cacheManager.getBlocks(5);
                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockHeight", block.blockNumber());
                    object.put("timeStamp", block.timeStamp() / 1000);
                    object.put("txnsCount", block.txnCount());
                    object.put("blockReward", block.blockReward());
                    object.put("blockSubmitter", returnHexStringWith0x(block.blockSubmitter()));
                    blocks.put(object);
                }
                return new JSONArray().put(blocks);
            });

            CompletableFuture<JSONArray> txnsFuture = CompletableFuture.supplyAsync(() -> {
                JSONArray txns = new JSONArray();
                List<NewTxn> txnsList = cacheManager.getRecentTxns(5);
                for (NewTxn txn : txnsList) {
                    if (txn == null) continue;
                    JSONObject object = new JSONObject();
                    object.put("txnHash", returnHexStringWith0x(txn.hash()));
                    object.put("timeStamp", txn.timestamp() / 1000);
                    object.put("from", returnHexStringWith0x(txn.fromAddress()));
                    object.put("to", returnHexStringWith0x(txn.toAddress()));
                    object.put("value", txn.value());
                    txns.put(object);
                }
                return new JSONArray().put(txns);
            });

            CompletableFuture<JSONArray> otherDataFuture = CompletableFuture.supplyAsync(() -> {
                Instant start = Instant.now();
                JSONObject data = new JSONObject();

                data.put("fourteenDaysTxn", cacheManager.getFourteenDaysTxn());
                data.put("totalTransactionsCount", cacheManager.getTotalTransactionCount());
                data.put("validators", cacheManager.getActiveValidatorsCount());
                data.put("tps", cacheManager.getAverageTps(100, cacheManager.getBlocksCount()));

                long duration = Duration.between(start, Instant.now()).toMillis();
                return new JSONArray().put(data).put(duration);
            });

            // Combine all futures
            CompletableFuture.allOf(blocksFuture, txnsFuture, otherDataFuture).thenAccept(v -> {
                try {
                    JSONArray blocksResult = blocksFuture.get();
                    JSONArray txnsResult = txnsFuture.get();
                    JSONArray otherData = otherDataFuture.get();

                    JSONArray arr = blocksResult != null ? blocksResult.optJSONArray(0) : null;
                    JSONObject otherDataObj = (JSONObject) otherData.get(0);

                    long blocksCount = 0;
                    if (arr != null && !arr.isEmpty()) {
                        blocksCount = arr.optJSONObject(0).optLong("blockHeight", 0);
                    }

                    JSONObject message = getSuccess(
                            "price", Settings.getPrice(),
                            "priceChange", 2.5,
                            "marketCap", 1_000_000_000L,
                            "totalTransactionsCount", otherDataObj.getLong("totalTransactionsCount"),
                            "blocksCount", blocksCount,
                            "validators", otherDataObj.getInt("validators"),
                            "tps", otherDataObj.getDouble("tps"),
                            "txns", txnsResult.getJSONArray(0),
                            "blocks", blocksResult != null ? blocksResult.getJSONArray(0) : 0,
                            "fourteenDaysTxn", otherDataObj.get("fourteenDaysTxn")
                    );
                    data = message.toString();

                    broadcast(message.toString());
                } catch (Exception e) {
                    logger.error("Error building explorer info: ", e);
                }
            });

        } catch (Exception e) {
            logger.error("Error scheduling explorer info: ", e);
        }
    }

    // Actually sends the message to all active sessions
    private void broadcast(String message) {
        for (Session session : sessions) {
            try {
                session.getRemote().sendString(message);
            } catch (Exception e) {
                logger.error("Failed to send WS message to session: ", e);
            }
        }
    }
}
