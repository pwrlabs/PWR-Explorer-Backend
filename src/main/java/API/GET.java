package API;


import Block.Block;
import Block.Blocks;
import DailyActivity.Stats;
import Main.Settings;
import Txn.NewTxn;
import Txn.Txns;
import User.User;
import User.Users;
import Validator.Validators;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.delegator.Delegator;
import com.github.pwrlabs.pwrj.record.transaction.PayableVmDataTransaction;
import com.github.pwrlabs.pwrj.record.transaction.Transaction;
import com.github.pwrlabs.pwrj.record.transaction.VmDataTransaction;
import com.github.pwrlabs.pwrj.record.validator.Validator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static Core.Sql.Queries.*;
import static spark.Spark.get;

public class GET {
    private static final Logger logger = LogManager.getLogger(GET.class);

    public static void run(PWRJ pwrj) {
        get("/test/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                return "Server is on";
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        //Explorer Calls
        get("/explorerInfo/", (request, response) -> {
            Instant serverStart = Instant.now();
            try {
                response.header("Content-Type", "application/json");

                CompletableFuture<JSONArray> blocksFuture = CompletableFuture.supplyAsync(() -> {
                    Instant start = Instant.now();
                    JSONArray blocks = new JSONArray();
                    long blockNumberToCheck = Blocks.getLatestBlockNumber();
                    logger.info("------------------- {}" , blockNumberToCheck);
                    List<Block> blockList = getLastXBlocks(5);
                    for (Block block : blockList) {
                        JSONObject object = new JSONObject();
                        object.put("blockNumber", block.getBlockNumber());
                        object.put("blockHeight", blockNumberToCheck);
                        object.put("timeStamp", block.getTimeStamp());
                        object.put("txnsCount", block.getTxnCount());
                        object.put("blockReward", block.getBlockReward());
                        object.put("blockSubmitter", "0x" + block.getBlockSubmitter());
                        blocks.put(object);
                    }
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return new JSONArray().put(blocks).put(duration);
                });

                CompletableFuture<JSONArray> txnsFuture = CompletableFuture.supplyAsync(() -> {
                    Instant start = Instant.now();
                    JSONArray txns = new JSONArray();
                    List<NewTxn> txnsList = getLastXTransactions(5);
                    for (NewTxn txn : txnsList) {
                        if (txn == null) continue;
                        JSONObject object = new JSONObject();
                        object.put("txnHash", txn.hash());
                        object.put("timeStamp", txn.timestamp());
                        object.put("from", "0x" + txn.fromAddress());
                        object.put("to", txn.toAddress());
                        object.put("value", txn.value());
                        txns.put(object);
                    }
                    long duration = Duration.between(start, Instant.now()).toMillis();
                    return new JSONArray().put(txns).put(duration);
                });

                CompletableFuture<JSONObject> otherDataFuture = CompletableFuture.supplyAsync(() -> {
                    JSONObject data = new JSONObject();

                    data.put("fourteenDaysTxn", getFourteenDaysTxn());
                    data.put("totalTransactionsCount", getTotalTransactionCount());

                    try {
                        data.put("validators", pwrj.getActiveValidatorsCount());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    data.put("tps", Blocks.getAverageTps(100));

                    return data;
                });

                JSONArray blocksResult = blocksFuture.get();
                JSONArray txnsResult = txnsFuture.get();
                JSONObject otherData = otherDataFuture.get();
                JSONArray arr = blocksResult != null ? blocksResult.optJSONArray(0) : null;

                long blocksCount = 0;
                if (arr != null && arr.length() > 0) {
                    blocksCount = arr.optJSONObject(0).optLong("blockHeight", 0);
                }

                return getSuccess(
                        "price", Settings.getPrice(),
                        "priceChange", 2.5,
                        "marketCap", 1000000000L,
                        "totalTransactionsCount", otherData.getLong("totalTransactionsCount"),
                        "blocksCount", blocksCount,
                        "validators", otherData.getInt("validators"),
                        "tps", otherData.getDouble("tps"),
                        "txns", txnsResult.getJSONArray(0),
                        "blocks", blocksResult.getJSONArray(0),
                        "fourteenDaysTxn", otherData.get("fourteenDaysTxn"),
                        "serverDuration", Duration.between(serverStart, Instant.now()).toMillis()
                );
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getMessage());
            }
        });

        get("/dailyStats", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                // Initialize JSON objects for the response
                JSONArray blocks = new JSONArray();
                JSONArray txns = new JSONArray();

                // Fetch the latest 5 blocks (assuming a function getLastXBlocks exists)
                List<Block> blockList = getLastXBlocks(5);
                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockNumber", block.getBlockNumber());
                    object.put("blockHeight", Blocks.getLatestBlockNumber());
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + block.getBlockSubmitter());

                    blocks.put(object);
                }

                // Fetch the latest 5 transactions (assuming a function getLastXTransactions exists)
                List<NewTxn> txnsList = getLastXTransactions(5);
                for (NewTxn txn : txnsList) {
                    if (txn == null) continue;
                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.hash());
                    object.put("timeStamp", txn.timestamp());
                    object.put("from", "0x" + txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());

                    txns.put(object);
                }

                // Build the response JSON
                JSONObject responseObject = new JSONObject();
                responseObject.put("price", Settings.getPrice());
                responseObject.put("priceChange", 2.5);  // example value
                responseObject.put("marketCap", 1000000000L);  // example value
                responseObject.put("totalTransactionsCount", getTransactionCountPast24Hours());
                responseObject.put("blocksCount", Blocks.getLatestBlockNumber());
                responseObject.put("validators", pwrj.getActiveValidatorsCount());  // example value
                responseObject.put("tps", Blocks.getAverageTps(100));  // example value
                responseObject.put("txns", txns);
                responseObject.put("blocks", blocks);
                responseObject.put("avgTxnFeeChange", getAverageTransactionFeePercentageChange());
                responseObject.put("totalTxnFeesChange", getTotalTransactionFeesPercentageChange());
                responseObject.put("avgTxnFeePast24Hours", getAverageTransactionFeePast24Hours());
                responseObject.put("totalTxnFeesPast24Hours", getTotalTransactionFeesPast24Hours());
                responseObject.put("txnCountChange", getTransactionCountPercentageChangeComparedToPreviousDay());

                // Return the response as a JSON string
                return responseObject.toString();

            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/latestBlocks/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                int offset = (page - 1) * count;

                long latestBlockNumber = Blocks.getLatestBlockNumber();
                int totalBlockCount = (int) latestBlockNumber;
                int totalPages = (int) Math.ceil((double) totalBlockCount / count);

                List<Block> blockList = getLastXBlocks(count, offset);
                System.out.println(">>>blockList: " + blockList);
                JSONArray blocksArray = new JSONArray();
                for (Block block : blockList) {
                    JSONObject object = new JSONObject();
                    object.put("blockHeight", block.getBlockNumber());
                    object.put("timeStamp", block.getTimeStamp());
                    object.put("txnsCount", block.getTxnCount());
                    object.put("blockReward", block.getBlockReward());
                    object.put("blockSubmitter", "0x" + block.getBlockSubmitter());
                    blocksArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalBlocks", totalBlockCount);
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalBlockCount);
                metadata.put("startIndex", offset + 1);
                metadata.put("endIndex", Math.min(offset + count, totalBlockCount));
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);

                return getSuccess(
                        "networkUtilizationPast24Hours", Blocks.getNetworkUtilizationPast24Hours(),
                        "averageBlockSizePast24Hours", Blocks.getAverageBlockSizePast24Hours(),
                        "totalBlockRewardsPast24Hours", Blocks.getTotalBlockRewardsPast24Hours(),
                        "blocks", blocksArray,
                        "metadata", metadata);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blockDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

                com.github.pwrlabs.pwrj.record.block.Block block = pwrj.getBlockByNumber(blockNumber);
                if (block == null) return getError(response, "Invalid Block Number");

                return getSuccess(
                        "blockHeight", blockNumber,
                        "timeStamp", block.getTimestamp(),
                        "txnsCount", block.getTransactionCount(),
                        "blockSize", block.getSize(),
                        "blockReward", block.getReward(),
                        "blockSubmitter", block.getSubmitter(),
                        "blockConfirmations", Blocks.getLatestBlockNumber() - blockNumber);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/block/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));

                String blockHash = getBlockHash(blockNumber);
                if (blockHash == null) {
                    return getError(response, "Invalid Block Number");
                }

                return getSuccess(
                        "blockNumber", blockNumber,
                        "blockHash", blockHash
                );
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blockTransactions/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                //Metadata variables
                int previousTxnsCount = (page - 1) * count;
                int totalTxnCount, totalPages;

                com.github.pwrlabs.pwrj.record.block.Block block = pwrj.getBlockByNumber(blockNumber);
                if (block == null) return getError(response, "Invalid Block Number");

                int txnsCount = 0;
                JSONArray txns = new JSONArray();
                Transaction[] txnsArray = block.getTransactions();

                if (txnsArray == null) totalTxnCount = 0;
                else totalTxnCount = txnsArray.length;

                totalPages = totalTxnCount / count;
                if (totalTxnCount % count != 0) ++totalPages;

                for (int t = previousTxnsCount; t < txnsArray.length; ++t) {
                    Transaction txn = txnsArray[t];
                    //Txn txn = txnsArray[t];
                    if (txn == null) continue;
                    if (txnsCount == count) break;

                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getHash());
                    object.put("txnType", txn.getType());
                    object.put("blockNumber", blockNumber);
                    object.put("timeStamp", block.getTimestamp());
                    object.put("from", txn.getSender());
                    object.put("to", txn.getReceiver());
                    object.put("value", txn.getValue());

                    txns.put(object);
                    ++txnsCount;
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", previousTxnsCount + 1);
                if (previousTxnsCount + count <= totalTxnCount) metadata.put("endIndex", previousTxnsCount + count);
                else metadata.put("endIndex", totalTxnCount);

                if (page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if (page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess("metadata", metadata, "transactions", txns);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/blocksCreated/", ((request, response) -> {
            response.type("application/json");
            String address = request.queryParams("validatorAddress");
            int count = Integer.parseInt(request.queryParams("count"));
            int page = Integer.parseInt(request.queryParams("page"));
            int offset = (page - 1) * count;

            // Metadata variables
            int previousBlocksCount = (page - 1) * count;
            int totalBlocksCount, totalPages;

            try {
                JSONArray blocks = getBlocksCreated(address.toLowerCase().substring(2), count, offset);
                JSONObject metadata = new JSONObject();

                if (blocks.isEmpty()) totalBlocksCount = 0;
                else totalBlocksCount = getBlocksSubmitted(address.toLowerCase().substring(2));

                totalPages = totalBlocksCount / count;
                if (totalBlocksCount % count != 0) ++totalPages;

                metadata.put("totalBlocksCreatedByValidator", totalBlocksCount);
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalBlocksCount);
                metadata.put("startIndex", previousBlocksCount + 1);

                if (previousBlocksCount + count <= totalBlocksCount)
                    metadata.put("endIndex", previousBlocksCount + count);
                else metadata.put("endIndex", totalBlocksCount);

                if (page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if (page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess("blocks", blocks,
                        "metadata", metadata
                );
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/latestTransactions/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int limit = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));
                int offset = (page - 1) * limit;
                System.out.println(">>hello");

                List<NewTxn> txnsList = getTransactions(limit, offset);

                //Metadata variables
                int previousTxnsCount = (page - 1) * limit;
                int totalTxnCount = getTotalTransactionCount();
                int totalPages = totalTxnCount / limit;
                if (totalTxnCount % limit != 0) ++totalPages;

                JSONArray transactions = new JSONArray();

                BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

                for (NewTxn txn : txnsList) {
                    Transaction subTxn = pwrj.getTransactionByHash(txn.hash());

                    BigDecimal sparks = new BigDecimal(txn.txnFee());
                    BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                    BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);

                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.hash());
                    object.put("txnType", txn.txnType());
                    object.put("block", txn.blockNumber());
                    object.put("timeStamp", txn.timestamp());
                    object.put("from", "0x" + txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());
                    object.put("txnFee", txn.txnFee());
                    object.put("valueInUsd", txn.value());
                    object.put("txnFeeInUsd", feeValueInUSD);
                    object.put("nonceOrValidationHash", subTxn.getNonce());
                    object.put("positionInBlock", txn.positionInBlock());
                    transactions.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", limit);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", previousTxnsCount + 1);
                if (previousTxnsCount + limit <= totalTxnCount) {
                    metadata.put("endIndex", previousTxnsCount + limit);
                } else {
                    metadata.put("endIndex", totalTxnCount);
                }

                if (page < totalPages) {
                    metadata.put("nextPage", page + 1);
                } else {
                    metadata.put("nextPage", -1);
                }

                if (page > 1) {
                    metadata.put("previousPage", page - 1);
                } else {
                    metadata.put("previousPage", -1);
                }

//                JSONObject response = new JSONObject();
//                response.put("metadata", metadata);
//                response.put("transactions", transactions);

                return getSuccess("metadata", metadata,
                        "transactionCountPast24Hours", Txns.getTxnCountPast24Hours(),
                        "transactionCountPercentageChangeComparedToPreviousDay", Txns.getTxnCountPercentageChangeComparedToPreviousDay(),
                        "totalTransactionFeesPast24Hours", Txns.getTotalTxnFeesPast24Hours(),
                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", Txns.getTotalTxnFeesPercentageChangeComparedToPreviousDay(),
                        "averageTransactionFeePast24Hours", Txns.getAverageTxnFeePast24Hours(),
                        "averageTransactionFeePercentageChangeComparedToPreviousDay", Txns.getAverageTxnFeePercentageChangeComparedToPreviousDay(),
                        "transactions", transactions);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/transactionDetails/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                Transaction txn = pwrj.getTransactionByHash(txnHash);
                if (txn == null) return getError(response, "Invalid Txn Hash");

                String data = null;
                if (txn instanceof VmDataTransaction) {
                    data = ((VmDataTransaction) txn).getData();
                } else if (txn instanceof PayableVmDataTransaction) {
                    data = ((PayableVmDataTransaction) txn).getData();
                }

                long txnFee = txn.getFee();

                BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

                BigDecimal sparks = new BigDecimal(txnFee);
                BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);
                return getSuccess(
                        "txnHash", txnHash,
                        "txnType", txn.getType(),
                        "blockNumber", txn.getBlockNumber(),
                        "timeStamp", txn.getTimestamp(),
                        "from", txn.getSender(),
                        "to", txn.getReceiver(),
                        "value", txn.getValue(),
                        "valueInUsd", txn.getValue(),
                        "success", !txn.hasError(),
                        "txnFee", txnFee,
                        "txnFeeInUsd", feeValueInUSD,
                        "data", data == null ? data : data.toLowerCase(),
                        "size", txn.getSize(),
                        "errorMessage", txn.getErrorMessage() != null ? txn.getErrorMessage() : null,
                        "extraData", txn.getExtraData()
                );
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        //Wallet Calls
        get("/balanceOf/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String address = request.queryParams("userAddress").toLowerCase();

                long balance = pwrj.getBalanceOfAddress(address);
                long usdValue = balance * Settings.getPrice();
                BigDecimal usdValueBigDec = new BigDecimal(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));

                return getSuccess("balance", balance, "balanceUsdValue", usdValueBigDec);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/nonceOfUser/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                String userAddress = request.queryParams("userAddress");

                return getSuccess("nonce", pwrj.getNonceOfAddress(userAddress));
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/nodesInfo/", ((request, response) -> {
            Instant overallStart = Instant.now();
            Instant sectionStart;

            int count = Integer.parseInt(request.queryParams("count"));
            int page = Integer.parseInt(request.queryParams("page"));
            int offset = (page - 1) * count;

            int previousNodesCount = (page - 1) * count;
            int totalNodesCount, totalPages;

            try {
                // Section 1: Initial data fetching
                sectionStart = Instant.now();
                JSONArray nodesArray = new JSONArray();
                List<Validator> nodes = pwrj.getActiveValidators();
                totalNodesCount = nodes.size();
                logger.info("Section 1 (Initial data fetching) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                sectionStart = Instant.now();
                BigDecimal activeVotingPower = new BigDecimal(pwrj.getActiveVotingPower());
                int standbyNodesCount = pwrj.getStandbyValidatorsCount();
                int activeNodesCount = pwrj.getActiveValidatorsCount();
                long totalVotingPower = pwrj.getTotalVotingPower();
                logger.info("Section middle (Middle data fetching) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 2: Pagination
                sectionStart = Instant.now();
                List<Validator> paginatedNodes = nodes.subList(
                        Math.min(offset, totalNodesCount),
                        Math.min(offset + count, totalNodesCount)
                );
                logger.info("Section 2 (Pagination) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 3: Node processing
                sectionStart = Instant.now();
                for (Validator node : paginatedNodes) {
                    Instant nodeStart = Instant.now();
                    JSONObject nodeObj = new JSONObject();
                    String address = node.getAddress();
                    long shares = node.getShares();
                    BigDecimal sparks = new BigDecimal(shares);
                    BigDecimal sharesInPwr = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                    BigDecimal votingPowerSparks = new BigDecimal(node.getVotingPower());
                    BigDecimal votingPower = votingPowerSparks.divide(activeVotingPower, 7, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(5, RoundingMode.HALF_UP);

                    nodeObj.put("address", "0x" + address)
                            .put("host", node.getIp())
                            .put("votingPowerInPercentage", votingPower)
                            .put("votingPowerInPwr", node.getVotingPower())
                            .put("earnings", sharesInPwr)
                            .put("blocksSubmitted", getBlocksSubmitted(address.toLowerCase()));

                    nodesArray.put(nodeObj);

                    logger.info("Processing time for node {}: {} ms", address, Duration.between(nodeStart, Instant.now()).toMillis());
                }
                logger.info("Section 3 (Node processing) total duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 4: Metadata calculation
                sectionStart = Instant.now();
                totalPages = totalNodesCount / count;
                if (totalNodesCount % count != 0) ++totalPages;

                JSONObject metadata = new JSONObject()
                        .put("totalNodesCount", nodesArray.length())
                        .put("totalPages", totalPages)
                        .put("currentPage", page)
                        .put("itemsPerPage", count)
                        .put("totalItems", totalNodesCount)
                        .put("startIndex", previousNodesCount + 1)
                        .put("endIndex", Math.min(previousNodesCount + count, totalNodesCount))
                        .put("nextPage", page < totalPages ? page + 1 : -1)
                        .put("previousPage", page > 1 ? page - 1 : -1);
                logger.info("Section 4 (Metadata calculation) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                // Section 5: Response preparation
                sectionStart = Instant.now();
                Object result = getSuccess("nodes", nodesArray,
                        "metadata", metadata,
                        "totalActiveNodes", activeNodesCount,
                        "totalStandbyNodes", standbyNodesCount,
                        "totalVotingPower", totalVotingPower
                );
                logger.info("Section 5 (Response preparation) duration: {} ms", Duration.between(sectionStart, Instant.now()).toMillis());

                logger.info("Overall function duration: {} ms", Duration.between(overallStart, Instant.now()).toMillis());
                return result;
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/nodesStatus/", ((request, response) -> {
            response.header("Content-Type", "application/json");
            String address = request.queryParams("userAddress").toLowerCase();

            try {
                Validator node = pwrj.getValidator(address);

                BigDecimal sparks = new BigDecimal(node.getShares());
                BigDecimal sharesInPwr = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                BigDecimal votingPowerSparks = new BigDecimal(node.getVotingPower());
                BigDecimal votingPowerInPwr = votingPowerSparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                // Section 5: Response preparation
                return getSuccess("address", address,
                        "ipAddress", node.getIp(),
                        "status", node.getStatus(),
                        "votingPower", votingPowerInPwr,
                        "numberOfDelegators", node.getDelegatorsCount(),
                        "totalShares", sharesInPwr,
                        "blocksCreated", getBlocksSubmitted(address),
                        "timeOfLastBlock", getLatestBlockNumberForFeeRecipient(address.substring(2))
                );
            } catch (Exception e) {
                return getError(response, e.getLocalizedMessage());
            }
        }));

        get("/transactionHistory/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String address = request.queryParams("address");
                if (address == null) {
                    return getError(response, "Address is required");
                }
                address = address.substring(2).toLowerCase();

                String countStr = request.queryParams("count");
                if (countStr == null) {
                    return getError(response, "Count is required");
                }
                int count;
                try {
                    count = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    return getError(response, "Invalid count");
                }

                String pageStr = request.queryParams("page");
                if (pageStr == null) {
                    return getError(response, "Page is required");
                }
                int page;
                try {
                    page = Integer.parseInt(pageStr);
                } catch (NumberFormatException e) {
                    return getError(response, "Invalid page");
                }

                long startCountTimeOne = System.currentTimeMillis();
                List<NewTxn> txns = getUserTxns(address, page, count);
                long endCountTimeOne = System.currentTimeMillis();
                logger.info(">>>Time taken for userTxns: " + (endCountTimeOne - startCountTimeOne) + " ms");

                long startCountTimeTwo = System.currentTimeMillis();
                int totalTxnCount = getTotalTxnCount(address);
                System.out.println(">>total txn count: " + totalTxnCount);
                long endCountTimeTwo = System.currentTimeMillis();
                logger.info(">>>Time taken for getTotalTxnCount: " + (endCountTimeTwo - startCountTimeTwo) + " ms");

                int totalPages = (int) Math.ceil((double) totalTxnCount / count);
                System.out.println(">>total pages: " + totalPages);

                JSONArray txnsArray = new JSONArray();
                long start = System.currentTimeMillis();

                BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

                for (NewTxn txn : txns) {
                    Transaction subTxn = pwrj.getTransactionByHash(txn.hash());

                    BigDecimal sparks = new BigDecimal(txn.txnFee());
                    BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

                    BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);

                    JSONObject object = new JSONObject();
                    object.put("txnHash", txn.hash());
                    object.put("block", txn.blockNumber());
                    object.put("positionInBlock", txn.positionInBlock());
                    object.put("timeStamp", txn.timestamp());
                    object.put("from", "0x" + txn.fromAddress());
                    object.put("to", txn.toAddress());
                    object.put("value", txn.value());
                    object.put("txnType", txn.txnType());
                    object.put("success", txn.success());
                    object.put("txnFee", txn.txnFee());
                    object.put("txnFeeInUsd", feeValueInUSD);
                    object.put("nonceOrValidationHash", subTxn.getNonce());

                    txnsArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", page > totalPages ? 0 : count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", (page - 1) * count + 1);
                metadata.put("endIndex", Math.min(page * count, totalTxnCount));
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);
                long end = System.currentTimeMillis();
                logger.info(">>>Time taken for loops: " + (end - start) + " ms");

                // Get first and last transactions
                Pair<NewTxn, NewTxn> firstLastTxns = getFirstAndLastTransactionsByAddress(address);
                JSONObject firstLastTxnsObject = new JSONObject();

                if (firstLastTxns.first != null) {
                    JSONObject firstTxnObject = new JSONObject();
                    firstTxnObject.put("txnHash", firstLastTxns.first.hash());
                    firstTxnObject.put("block", firstLastTxns.first.blockNumber());
                    firstTxnObject.put("timeStamp", firstLastTxns.first.timestamp());
                    firstLastTxnsObject.put("firstTransaction", firstTxnObject);
                }

                if (firstLastTxns.second != null) {
                    JSONObject lastTxnObject = new JSONObject();
                    lastTxnObject.put("txnHash", firstLastTxns.second.hash());
                    lastTxnObject.put("block", firstLastTxns.second.blockNumber());
                    lastTxnObject.put("timeStamp", firstLastTxns.second.timestamp());
                    firstLastTxnsObject.put("lastTransaction", lastTxnObject);
                }

                return getSuccess("transactions", txnsArray, "metadata", metadata, "firstLastTransactions", firstLastTxnsObject);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/isTxnProcessed/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String txnHash = request.queryParams("txnHash").toLowerCase();

                NewTxn txn = Txns.getNewTxn(txnHash);
                if (txn == null) return getSuccess("isProcessed", false);
                else return getSuccess("isProcessed", true);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        //Staking app calls
        get("/staking/homepageInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                List<Validator> activeValidators = pwrj.getActiveValidators();
                for (Validator val : activeValidators) {
                    System.out.println(">>>validatorrr" + val.getAddress());
                }
                List<Validator> standbyValidators = pwrj.getStandbyValidators();
                int totalValidatorsCount = activeValidators.size() + standbyValidators.size();
                int availablePages = totalValidatorsCount / count;
                if (totalValidatorsCount % count != 0) ++availablePages;

                logger.info("Active validators count: {}", activeValidators.size());
                logger.info("Standby validators count: {}", standbyValidators.size());
                logger.info("Total validators count: {}", totalValidatorsCount);
                if (page > availablePages)
                    return getError(response, "Page number is greater than the total available pages: " + availablePages);

                //APY calculation for active validators

                //check APY of active validators for the past 34,560 blocks (2 days)
//                long currentBlockNumber = Blocks.getLatestBlockNumber();
//                long blockNumberToCheck = currentBlockNumber - 34560;
//                if(blockNumberToCheck < 0) blockNumberToCheck = 1;
//                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();

                List<String> activeValidatorsList = new LinkedList<>();
                for (Validator validator : activeValidators) activeValidatorsList.add(validator.getAddress());
//                BigDecimal oldShareValues = PWRJ.getShareValue(activeValidatorsList, blockNumberToCheck);
//                JSONObject currentShareValues = PWRJ.getShareValue(activeValidatorsList, currentBlockNumber);

//                Map<String, BigDecimal> apyByValidator = new HashMap<>();
//                for(String validator: activeValidatorsList) {
//                    BigDecimal oldShareValue;
//                    if(oldShareValues.has(validator)) {
//                        oldShareValue = oldShareValues.getBigDecimal(validator);
//                    } else {
//                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal currentShareValue;
//
//                    if(currentShareValues.has(validator)) {
//                        currentShareValue = currentShareValues.getBigDecimal(validator);
//                    } else {
//                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
//                    apyByValidator.put(validator, apy);
//                }

                //Get the validators for the requested page

                int startingIndex = (page - 1) * count;
                JSONArray validatorsArray = new JSONArray();
                long activeVotingPower = pwrj.getActiveVotingPower();
                for (int t = startingIndex; t < totalValidatorsCount; ++t) {
                    Validator validator;

                    if (t >= activeValidators.size()) {
                        validator = standbyValidators.get(t - activeValidators.size());
                    } else {
                        validator = activeValidators.get(t);
                    }

                    if (validator == null) continue;

                    JSONObject object = new JSONObject();

                    object.put("name", validator.getAddress());
                    object.put("status", validator.getStatus());
                    object.put("votingPower", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(1000000000), 2, BigDecimal.ROUND_HALF_UP));
                    object.put("totalPowerShare", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(activeVotingPower), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("commission", 1);
                    object.put("apy", 5);
                    object.put("hosting", "vps");

                    validatorsArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", availablePages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalValidatorsCount);
                metadata.put("startIndex", startingIndex);
                metadata.put("endIndex", page < availablePages ? startingIndex + count : totalValidatorsCount);
                metadata.put("nextPage", page < availablePages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);

                return getSuccess(
                        "activeValidatorsCount", pwrj.getActiveValidatorsCount(),
                        "activeVotingPower", BigDecimal.valueOf(pwrj.getActiveVotingPower()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP),
                        "delegatorsCount", pwrj.getTotalDelegatorsCount(),
                        "validators", validatorsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/staking/validatorInfo/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String validatorAddress = request.queryParams("validatorAddress");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                Validator v = pwrj.getValidator(validatorAddress.substring(2));
                logger.info(">>Validator address {}", v.getAddress());
                if (v.getAddress() == null) return getError(response, "Invalid Validator Address");

                int totalPages = v.getDelegatorsCount() / count;
                if (v.getDelegatorsCount() % count != 0) ++totalPages;
                int startingIndex = (page - 1) * count;

                List<Delegator> delegators = v.getDelegators(pwrj);
                logger.info("delegators size {}", delegators.size());
                for (Delegator value : delegators) {
                    logger.info("-------- delegator {}", value);
                }
                JSONArray delegatorsArray = new JSONArray();
                try {
                    int delegatorsCount = Math.max(v.getDelegatorsCount(), 0);

                    for (int t = 0; t < delegatorsCount; t++) {
                        logger.info("------------ delegators count {}", delegatorsCount);
                        Delegator delegator = delegators.get(t);
                        if (delegator == null) continue;

                        JSONObject object = new JSONObject();

                        object.put("address", delegator.getAddress());
                        object.put("delegatedPWR", BigDecimal.valueOf(delegator.getDelegatedPWR())
                                .divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP));

                        delegatorsArray.put(object);
                    }
                } catch (Exception e) {
                    delegatorsArray = new JSONArray();
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", v.getDelegatorsCount());
                metadata.put("startIndex", startingIndex);
                metadata.put("endIndex", page < totalPages ? startingIndex + count : v.getDelegatorsCount());
                metadata.put("nextPage", page < totalPages ? page + 1 : -1);
                metadata.put("previousPage", page > 1 ? page - 1 : -1);

                return getSuccess(
                        "name", "Unnamed Validator",
                        "status", v.getStatus(),
                        "joiningDate", Validators.getJoinTime(validatorAddress),
                        "hosting", "vps",
                        "website", "null",
                        "description", "null",
                        "delegators", delegatorsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });
        get("/staking/portfolio/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");

                String userAddress = request.queryParams("userAddress");

                User u = Users.getUser(userAddress);
                if (u == null) return getError(response, "Invalid User Address");

                List<String> delegatedValidators = u.getDelegatedValidators();

                long totalDelegatedPWR = 0;
                long totalRewards = 0;
                for (String validator : delegatedValidators) {
                    long delegatedPWR = u.getDelegatedAmount(validator);
                    totalDelegatedPWR += delegatedPWR;
                    totalRewards += pwrj.getDelegatedPWR(userAddress, validator) - delegatedPWR;
                }

                JSONArray portfolioAllocation = new JSONArray();
                for (String validator : delegatedValidators) {
                    long delegatedPWR = u.getDelegatedAmount(validator);

                    JSONObject object = new JSONObject();
                    object.put("validatorName", validator);
                    object.put("portfolioShare", BigDecimal.valueOf(delegatedPWR).divide(BigDecimal.valueOf(totalDelegatedPWR), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));

                    portfolioAllocation.put(object);
                }

                JSONArray validatorsArray = new JSONArray();
                long currentBlockNumber = Blocks.getLatestBlockNumber();
                long blockNumberToCheck = currentBlockNumber - 34560;
                if (blockNumberToCheck < 0) blockNumberToCheck = 1;
//                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();
//                JSONObject oldShareValues = PWRJ.getShareValue(delegatedValidators, blockNumberToCheck);
//                JSONObject currentShareValues = PWRJ.getShareValue(delegatedValidators, currentBlockNumber);

//                Map<String, BigDecimal> apyByValidator = new HashMap<>();
//                for(String validator: delegatedValidators) {
//                    BigDecimal oldShareValue;
//                    if(oldShareValues.has(validator)) {
//                        oldShareValue = oldShareValues.getBigDecimal(validator);
//                    } else {
//                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal currentShareValue;
//
//                    if(currentShareValues.has(validator)) {
//                        currentShareValue = currentShareValues.getBigDecimal(validator);
//                    } else {
//                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
//                    }
//
//                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
//                    apyByValidator.put(validator, apy);
//                }

                for (String validator : delegatedValidators) {
                    Validator v = pwrj.getValidator(validator);

                    JSONObject object = new JSONObject();
                    object.put("name", validator);
                    object.put("status", v.getStatus());
                    object.put("votingPower", v.getVotingPower());
                    object.put("totalPowerShare", BigDecimal.valueOf(v.getVotingPower()).divide(BigDecimal.valueOf(pwrj.getActiveVotingPower()), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("delegatedPWR", u.getDelegatedAmount(validator));
                    object.put("apy", 5);
                }

                return getSuccess(
                        "totalDelegatedPWR", totalDelegatedPWR,
                        "totalRewards", totalRewards,
                        "portfolioAllocation", portfolioAllocation,
                        "validators", validatorsArray);
            } catch (Exception e) {
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/stats", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                Stats stats = Stats.getInstance();

                return new JSONObject()
                        .put("success", true)
                        .put("totalTransactionCount", stats.getTotalTransactionCount())
                        .put("totalBlockCount", stats.getTotalBlockCount())
                        .put("tps", stats.getTPS())
                        .put("totalFees24h", stats.getTotalFees24h())
                        .put("avgFeePerTransaction24h", stats.getAvgFeePerTransaction24h())
                        .put("transactions24h", stats.getTransactions24h())
                        .put("avgBlockSize24h", stats.getAvgBlockSize24h())
                        .put("totalFees", stats.getTotalFees())
                        .toString();

            } catch (Exception e) {
                e.printStackTrace();
                return new JSONObject()
                        .put("success", false)
                        .put("error", e.getLocalizedMessage())
                        .toString();
            }
        });

    }

    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            // Convert each byte to a 2-digit hexadecimal string.
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString().toLowerCase();
    }

    // There was a problem on the server side
    public static JSONObject getError(spark.Response response, String message) {
        response.status(400);
        JSONObject object = new JSONObject();
        object.put("message", message);

        return object;
    }

    public static JSONObject getSuccess(Object... variables) throws Exception {
        JSONObject object = new JSONObject();

        int size = variables.length;
        if (size % 2 != 0)
            throw new Exception("Provided variables length should be even when using getSuccess");

        for (int t = 0; t < size; t += 2) {
            object.put(variables[t].toString(), variables[t + 1]);
        }

        return object;
    }

    public static void main(String[] args) {
        logger.info(1 / 2);
    }
}

