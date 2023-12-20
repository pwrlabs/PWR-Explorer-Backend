package API;


import Block.Blocks;
import Block.Block;
import DTOs.BlockDTO;
import DTOs.TransactionDTO;
import Main.Settings;
import User.Users;
import User.User;
import Utils.DatabaseUtils;
import Validator.Validators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pwrlabs.pwrj.Delegator.Delegator;
import com.github.pwrlabs.pwrj.Transaction.Transaction;
import com.github.pwrlabs.pwrj.Validator.Validator;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.json.JSONArray;
import org.json.JSONObject;
import Txn.Txns;
import Txn.Txn;

import java.math.BigDecimal;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static spark.Spark.get;

public class GET {

    private static final Logger logger = LogManager.getLogger(GET.class);
    public static void run() {

        //Explorer Calls
        get("/explorerInfo/", (request, response) -> {
            try {
                logger.info("Handling request for /explorerInfo/");
                response.header("Content-Type", "application/json");
                JSONArray blocks = new JSONArray();
                JSONArray txns = new JSONArray();
                long totalBlockCount = DatabaseUtils.getBlockCount();
                long totalTxnCount = DatabaseUtils.getTxnCount();
                //Fetch the latest 5 blocks
                List<BlockDTO> lastFiveAddedBlocksList  = DatabaseUtils.fetchLatestBlocks();
                List<TransactionDTO> lastFiveAddedTransactionsList  = DatabaseUtils.fetchLatestTransactions();
                if (lastFiveAddedBlocksList != null) {
                    for(BlockDTO blockDTO : lastFiveAddedBlocksList){
                        JSONObject object = DatabaseUtils.convertBlockDTOToJson(blockDTO);
                        blocks.put(object);
                    }
                }
                if (lastFiveAddedTransactionsList != null) {
                    for(TransactionDTO transactionDTO : lastFiveAddedTransactionsList){
                        JSONObject transactionJson = DatabaseUtils.convertTransactionDTOToJson(transactionDTO);
                        txns.put(transactionJson);
                    }
                }

                return getSuccess(
                        "blocksCount",totalBlockCount,
                        "price", Settings.getPrice(),
                        "priceChange", 2.5,
                        "marketCap", 1000000000L,
                        "totalTransactionsCount", totalTxnCount,
                        "validators", PWRJ.getActiveValidatorsCount(),
                        "tps", DatabaseUtils.getAverageTps(100),
                        "txns", txns,
                        "blocks", blocks
                );
            } catch (Exception e) {
                logger.error("An error occurred while processing /explorerInfo/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });




        get("/latestBlocks/", (request, response) -> {
            try {
                logger.info("Handling request for /latestBlocks/");
                response.header("Content-Type", "application/json");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                // Metadata variables
                long previousBlocksCount = (page - 1) * count;

                List<BlockDTO> blocks = DatabaseUtils.fetchLatestBlocks(count, page);

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", DatabaseUtils.getTotalPagesWrtBlocks(count));
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("startIndex", previousBlocksCount + 1);

                if (page < DatabaseUtils.getTotalPagesWrtBlocks(count)) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if (page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                JSONArray blocksArray = new JSONArray();
                for (BlockDTO blockDTO : blocks) {
                    blocksArray.put(DatabaseUtils.convertBlockDTOToJson(blockDTO));
                }
                logger.info("Returning latest blocks");
                return getSuccess(
                        "networkUtilizationPast24Hours", "Blocks.getNetworkUtilizationPast24Hours()",
                        "averageBlockSizePast24Hours", "Blocks.getAverageBlockSizePast24Hours()",
                        "totalBlockRewardsPast24Hours", "Blocks.getTotalBlockRewardsPast24Hours()",
                        "blocks", blocksArray,
                        "metadata", metadata);

            } catch (Exception e) {
                logger.error("An error occurred while processing /latestBlocks/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });


        ////////////////////////////////////////////////////

        get("/blockDetails/", (request, response) -> {
            try {
                logger.info("Handling request for /blockDetails/");
                response.header("Content-Type", "application/json");

                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));
                long latestBlock = DatabaseUtils.getLatestBlockNumber();
                com.github.pwrlabs.pwrj.Block.Block block = PWRJ.getBlockByNumber(blockNumber);
                if(block == null) {
                    logger.warn("Invalid Block Number: {}", blockNumber);
                    return getError(response, "Invalid Block Number");
                }
                logger.info("Returning block details for blockNumber: {}", blockNumber);
                return getSuccess(
                        "blockHeight", blockNumber,
                        "timeStamp", block.getTimestamp(),
                        "txnsCount", block.getTransactionCount(),
                        "blockSize", block.getSize(),
                        "blockReward", block.getReward(),
                        "blockSubmitter", block.getSubmitter(),
                        "blockConfirmations", latestBlock - blockNumber);
            } catch (Exception e) {
                logger.error("An error occurred while processing /blockDetails/", e);
                e.printStackTrace();
                return getError(response, e.getLocalizedMessage());
            }
        });

// fixed
        get("/blockTransactions/", (request, response) -> {
            try {
                logger.info("Handling request for /blockTransactions/");
                response.header("Content-Type", "application/json");
                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                //Metadata variables
                int previousTxnsCount = (page - 1) * count;
                int totalTxnCount, totalPages;

                BlockDTO block = DatabaseUtils.fetchBlockDetails(blockNumber);
                if(block == null) return getError(response, "Invalid Block Number");

                int txnsCount = 0;
                JSONArray txns = new JSONArray();
                List<TransactionDTO> txnsArray = block.getTransactions();
                //Txn[] txnsArray = block.getTxns();

                if(txnsArray == null) totalTxnCount = 0;
                else totalTxnCount = txnsArray.size();

                totalPages = totalTxnCount / count;
                if(totalTxnCount % count != 0) ++totalPages;

                for(int t = previousTxnsCount; t < txnsArray.size(); ++t) {
                    TransactionDTO txn = txnsArray.get(t);
                    //Txn txn = txnsArray[t];
                    if(txn == null) continue;
                    if(txnsCount == count) break;

                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getHash());
                    object.put("txnType", txn.getType());
                    object.put("blockNumber", blockNumber);
                    object.put("timeStamp", block.getTimestamp());
                    object.put("from", txn.getFrom());
                    object.put("to", txn.getTo());
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
                if(previousTxnsCount + count <= totalTxnCount) metadata.put("endIndex", previousTxnsCount + count);
                else metadata.put("endIndex", totalTxnCount);

                if(page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if(page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                logger.info("Returning block transactions");
                return getSuccess("metadata", metadata, "transactions", txns);
            } catch (Exception e) {
                logger.error("An error occurred while processing /blockTransactions/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });


        get("/latestTransactions/", (request, response) -> {
            try {
                logger.info("Handling request for /latestTransactions/");
                response.header("Content-Type", "application/json");
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));
                long blockNumber = DatabaseUtils.getLatestBlockNumber();
                int previousTxnsCount = (page - 1) * count;
                int totalTxnCount = 0;
                // Fetch the latest blocks
                List<BlockDTO> latestBlocksList = new ArrayList<>();
                for(int i =0; i< 5;i++){
                    BlockDTO blockDTO = DatabaseUtils.fetchBlockDetails(blockNumber);
                    if(blockDTO != null){
                        latestBlocksList.add(blockDTO);

                    }
                    blockNumber--;
                }

                int fetchedTxns = 0;

                JSONArray txnsArray = new JSONArray();
                DatabaseUtils.updateTxn24HourStats();

                for (BlockDTO block : latestBlocksList) {
                    if (block == null || fetchedTxns >= count + previousTxnsCount) {
                        break;
                    }

                    List<TransactionDTO> txns = block.getTransactions();

                    for (TransactionDTO txn : txns) {
                        if (txn == null) {
                            continue;
                        }

                        if (fetchedTxns < previousTxnsCount) {
                            ++fetchedTxns;
                            continue;
                        }

                        JSONObject object = new JSONObject();

                        object.put("txnHash", txn.getHash());
                        object.put("block", block.getBlockNumber());
                        object.put("txnType", txn.getType());
                        object.put("timeStamp", txn.getTimeStamp());
                        object.put("from", "0x" + txn.getFrom());
                        object.put("to", txn.getTo());
                        object.put("txnFee", txn.getTxnFee());
                        object.put("value", txn.getValue());
                        object.put("valueInUsd", txn.getFeeUsdValue());
                        object.put("txnFeeInUsd", txn.getFeeUsdValue());
                        object.put("nonceOrValidationHash", txn.getNonceOrValidationHash());
                        object.put("positionInBlock", txn.getPositionInTheBlock());


                        txnsArray.put(object);
                        ++fetchedTxns;

                        if (fetchedTxns == count + previousTxnsCount || fetchedTxns >= block.getTransactionCount()) {
                            break;
                        }
                    }
                }


                int totalPages = totalTxnCount / count;
                if (totalTxnCount % count != 0) ++totalPages;

                JSONObject metadata = new JSONObject();
                metadata.put("totalTxns", totalTxnCount);
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
                logger.info("Returning latest transactions");
                return getSuccess(
                        "transactionCountPast24Hours", DatabaseUtils.getTxnCountPast24Hours(),
                        "transactionCountPercentageChangeComparedToPreviousDay", DatabaseUtils.getTxnCountPercentageChangeComparedToPreviousDay(),
                        "totalTransactionFeesPast24Hours", DatabaseUtils.getTotalTxnFeesPast24Hours(),
                        "totalTransactionFeesPercentageChangeComparedToPreviousDay", DatabaseUtils.getTotalTxnFeesPercentageChangeComparedToPreviousDay(),
                        "averageTransactionFeePast24Hours", DatabaseUtils.getAverageTxnFeePast24Hours(),
                        "averageTransactionFeePercentageChangeComparedToPreviousDay", DatabaseUtils.getAverageTxnFeePercentageChangeComparedToPreviousDay(),
                        "transactions", txnsArray,
                        "metadata", metadata);
            } catch (Exception e) {
                logger.error("An error occurred while processing /latestTransactions/", e);

                return getError(response, e.getLocalizedMessage());
            }
        });


        get("/transactionDetails/", (request, response) -> {
            try {
                logger.info("Handling request for /transactionDetails/");
                response.header("Content-Type", "application/json");
                String txnHash = request.queryParams("txnHash").toLowerCase();
                //  Txn txn = Txns.getTxn(txnHash);
                TransactionDTO txn = DatabaseUtils.fetchTransactionFromTransactionHash(txnHash);
                if (txn == null) {
                    logger.error("Invalid Txn Hash");
                    return getError(response, "Invalid Txn Hash");
                }

                String data;
                if(txn.getData() == null) data = "0x";
                else data = "0x" + txn.getData();

                logger.info("Returning transaction details");
                return getSuccess(
                        "txnHash", txnHash,
                        "txnType", txn.getType(),
                        "size", txn.getSize(),
                        "blockNumber", txn.getBlockNumber(),
                        "timeStamp", txn.getTimeStamp().getTime(),
                        "from", "0x" + txn.getFrom(),
                        "to", txn.getTo(),
                        "value", txn.getValue(),
                        "valueInUsd", txn.getAmountUsdValue(),
                        "txnFee", txn.getTxnFee(),
                        "txnFeeInUsd", txn.getFeeUsdValue(),
                        "data", data
                );
            } catch (Exception e) {
                logger.error("An error occurred while processing /transactionDetails/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/transactionDetails/", (request, response) -> {
            try {
                logger.info("Handling request for /transactionDetails/");
                response.header("Content-Type", "application/json");
                String txnHash = request.queryParams("txnHash").toLowerCase();

                TransactionDTO transactionDTO = DatabaseUtils.fetchTransactionFromTransactionHash(txnHash);
                if (transactionDTO != null) {
                    JSONObject transactionJson = DatabaseUtils.convertTransactionDTOToJson(transactionDTO);
                    logger.info("Returning transaction details");
                    // Set the response body
                    response.status(200);
                    return transactionJson;
                }
                else {
                    // Handle case when txn is not found
                    logger.warn("Transaction not found for hash: {}", txnHash);
                    response.status(404);
                    return new JSONObject().put("error", "txn not found");
                }
            } catch (Exception e) {
                logger.error("An error occurred while processing /transactionDetails/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/blockDetails/", (request, response) -> {
            try {
                logger.info("Handling request for /transactionDetails/");
                response.header("Content-Type", "application/json");
                long blockNumber = Long.parseLong(request.queryParams("blockNumber"));
                BlockDTO blockDTO = DatabaseUtils.fetchBlockDetails(blockNumber);
                ObjectMapper objectMapper = new ObjectMapper();

                if (blockDTO != null) {
                    // Use the utility method to convert BlockDTO to JsonObject
                    JSONObject blockJson = DatabaseUtils.convertBlockDTOToJson(blockDTO);
                    logger.info("Returning blocks details");
                    // Set the response body
                    response.status(200);
                    return blockJson;
                } else {
                    // Handle case when block is not found
                    logger.warn("Block not found for block number: {}", blockNumber);
                    response.status(404);
                    return new JSONObject().put("error", "Block not found");
                }
            } catch (Exception e) {
                // Handle exceptions
                logger.error("An error occurred while processing /blockDetails/", e);
                response.status(500);
                return new JSONObject().put("error", "Internal Server Error");
            }
        });


        //Wallet Calls
        get("/balanceOf/", (request, response) -> {
            try {
                logger.info("Handling request for /balanceOf/");
                response.header("Content-Type", "application/json");
                String address = request.queryParams("userAddress").toLowerCase();

                long balance = PWRJ.getBalanceOfAddress(address);
                long usdValue = balance * Settings.getPrice();
                BigDecimal usdValueBigDec = new BigDecimal(usdValue).divide(BigDecimal.valueOf((long) Math.pow(10, 11)));
                logger.info("Returning balance details for address: {}", address);

                return  getSuccess("balance", balance, "balanceUsdValue", usdValueBigDec);
            } catch (Exception e) {
                logger.error("An error occurred while processing /balanceOf/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/nonceOfUser/", (request, response) -> {
            try {
                logger.info("Handling request for /nonceOfUser/");
                response.header("Content-Type", "application/json");
                String userAddress = request.queryParams("userAddress");
                logger.info("Returning nonce for user address: {}", userAddress);
                return getSuccess("nonce", PWRJ.getNonceOfAddress(userAddress));
            } catch (Exception e) {
                logger.error("An error occurred while processing /nonceOfUser/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/transactionHistory/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                logger.info("Handling request for /transactionHistory/");
                String address = request.queryParams("address").toLowerCase();
                int count = Integer.parseInt(request.queryParams("count"));
                int page = Integer.parseInt(request.queryParams("page"));

                 List<TransactionDTO> txnList = DatabaseUtils.fetchLatestTransactionsOfUser(address);
                if(txnList == null || txnList.isEmpty()) {
                    JSONObject metadata = new JSONObject();
                    metadata.put("totalPages", 0);
                    metadata.put("currentPage", page);
                    metadata.put("itemsPerPage", 0);
                    metadata.put("totalItems", 0);
                    metadata.put("startIndex", 0);

                    //System.out.println("No user found");
                    logger.info("Returning transaction history for address: {}", address);
                    return getSuccess("transactions", new JSONArray(),
                            "metadata", metadata,
                            "hashOfFirstTxnSent", "null",
                            "timeOfFirstTxnSent", "null",
                            "hashOfLastTxnSent", "null",
                            "timeOfLastTxnSent", "null");
                }
              //  List<Txn> txns = user.getTxns();
                int totalTxnCount = txnList.size();
                int totalPages = totalTxnCount / count;
                if(totalTxnCount % count != 0) ++totalPages;
                int previousTxnsCount = (page - 1) * count;

                if (page > totalPages) {
                    // Log the error and return an error response
                    logger.error("Page number is greater than address's total pages");
                    return getError(response, "Page number is greater than address's total pages");
                }
                JSONArray txnsArray = new JSONArray();

                int fetchedTxns = 0;
                for(int t = previousTxnsCount; fetchedTxns++ < count && t < totalTxnCount; ++t) {
                    TransactionDTO txn = txnList.get(totalTxnCount - t - 1);
                    JSONObject object = new JSONObject();

                    object.put("txnHash", txn.getHash());
                    object.put("block", txn.getBlockNumber());
                    object.put("txnType", txn.getType());
                    object.put("timeStamp", txn.getTimeStamp().getTime());
                    object.put("from", "0x" + txn.getFrom());
                    object.put("to", txn.getTo());
                    object.put("txnFee", txn.getTxnFee());
                    object.put("value", txn.getValue());
                    object.put("valueInUsd", txn.getAmountUsdValue());
                    object.put("txnFeeInUsd", txn.getFeeUsdValue());
                    object.put("nonceOrValidationHash", txn.getNonceOrValidationHash());


                    txnsArray.put(object);
                }

                JSONObject metadata = new JSONObject();
                metadata.put("totalPages", totalPages);
                metadata.put("currentPage", page);
                metadata.put("itemsPerPage", count);
                metadata.put("totalItems", totalTxnCount);
                metadata.put("startIndex", previousTxnsCount + 1);

                if(previousTxnsCount + count <= totalTxnCount) metadata.put("endIndex", previousTxnsCount + count);
                else metadata.put("endIndex", totalTxnCount);

                if(page < totalPages) metadata.put("nextPage", page + 1);
                else metadata.put("nextPage", -1);

                if(page > 1) metadata.put("previousPage", page - 1);
                else metadata.put("previousPage", -1);

                return getSuccess("transactions", txnsArray, "metadata", metadata, "hashOfFirstTxnSent", txnList.get(0).getHash(), "timeOfFirstTxnSent", txnList.get(0).getTimeStamp(),
                        "hashOfLastTxnSent", txnList.get(totalTxnCount - 1).getHash(), "timeOfLastTxnSent", txnList.get(totalTxnCount - 1).getTimeStamp());
            } catch (Exception e) {
                logger.error("An error occurred while processing /transactionHistory/", e);
                return getError(response, e.getLocalizedMessage());
            }
        });

        get("/isTxnProcessed/", (request, response) -> {
            try {
                logger.info("Handling request for /isTxnProcessed/");
                response.header("Content-Type", "application/json");
                String txnHash = request.queryParams("txnHash").toLowerCase();
                logger.info("Checking if transaction is processed for hash: {}", txnHash);
                TransactionDTO txn = DatabaseUtils.fetchTransactionFromTransactionHash(txnHash) ;
                if (txn == null) {
                    logger.info("Transaction with hash {} not processed", txnHash);
                    return getSuccess("isProcessed", false);
                } else {
                    logger.info("Transaction with hash {} processed", txnHash);
                    return getSuccess("isProcessed", true);
                }
            } catch (Exception e) {
                logger.error("An error occurred while processing /isTxnProcessed/", e);
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

                List<Validator> activeValidators = PWRJ.getAllValidators();
                List<Validator> standbyValidators = PWRJ.getStandbyValidators();
                int totalValidatorsCount = activeValidators.size() + standbyValidators.size();
                int availablePages = totalValidatorsCount / count;
                if(totalValidatorsCount % count != 0) ++availablePages;

                if(page > availablePages) return getError(response, "Page number is greater than the total available pages");

                //APY calculation for active validators

                //check APY of active validators for the past 34,560 blocks (2 days)
                long currentBlockNumber = DatabaseUtils.getLatestBlockNumber();
                long blockNumberToCheck = currentBlockNumber - 34560;
                if(blockNumberToCheck < 0) blockNumberToCheck = 1;
                long timeDifference = DatabaseUtils.fetchBlockDetails(currentBlockNumber).getTimestamp() - DatabaseUtils.fetchBlockDetails(blockNumberToCheck).getTimestamp();

                List<String> activeValidatorsList = new LinkedList<>();
                for(Validator validator : activeValidators) activeValidatorsList.add(validator.getAddress());
                JSONObject oldShareValues = PWRJ.getShareValue(activeValidatorsList, blockNumberToCheck);
                JSONObject currentShareValues = PWRJ.getShareValue(activeValidatorsList, currentBlockNumber);

                Map<String, BigDecimal> apyByValidator = new HashMap<>();
                for(String validator: activeValidatorsList) {
                    BigDecimal oldShareValue;
                    if(oldShareValues.has(validator)) {
                        oldShareValue = oldShareValues.getBigDecimal(validator);
                    } else {
                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal currentShareValue;

                    if(currentShareValues.has(validator)) {
                        currentShareValue = currentShareValues.getBigDecimal(validator);
                    } else {
                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
                    apyByValidator.put(validator, apy);
                }

                //Get the validators for the requested page

                int startingIndex = (page - 1) * count;
                JSONArray validatorsArray = new JSONArray();
                long activeVotingPower = PWRJ.getActiveVotingPower();
                for(int t = startingIndex; t < totalValidatorsCount; ++t) {
                    Validator validator;

                    if(t >= activeValidators.size()) {
                        validator = standbyValidators.get(t - activeValidators.size());
                    }
                    else {
                        validator = activeValidators.get(t);
                    }

                    if(validator == null) continue;

                    JSONObject object = new JSONObject();

                    object.put("name", validator.getAddress());
                    object.put("status", validator.getStatus());
                    object.put("votingPower", validator.getVotingPower());
                    object.put("totalPowerShare", BigDecimal.valueOf(validator.getVotingPower()).divide(BigDecimal.valueOf(activeVotingPower), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("commission", 1);
                    object.put("apy", apyByValidator.getOrDefault(validator.getAddress(), BigDecimal.ZERO));
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
                        "activeValidatorsCount", PWRJ.getActiveValidatorsCount(),
                        "activeVotingPower", BigDecimal.valueOf(PWRJ.getActiveVotingPower()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP),
                        "delegatorsCount", Users.getDelegatorsCount(),
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

                Validator v = PWRJ.getValidator(validatorAddress);
                if(v == null) return getError(response, "Invalid Validator Address");

                int totalPages = v.getDelegatorsCount() / count;
                if(v.getDelegatorsCount() % count != 0) ++totalPages;
                int startingIndex = (page - 1) * count;

                List<Delegator> delegators = v.getDelegators();
                JSONArray delegatorsArray = new JSONArray();
                for (int t = startingIndex; t < v.getDelegatorsCount(); ++t) {
                    Delegator delegator = delegators.get(t);
                    if(delegator == null) continue;

                    JSONObject object = new JSONObject();

                    object.put("address", delegator.getAddress());
                    object.put("delegatedPWR", BigDecimal.valueOf(delegator.getDelegatedPWR()).divide(BigDecimal.TEN.pow(9), 0, BigDecimal.ROUND_HALF_UP));

                    delegatorsArray.put(object);
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
                if(u == null) return getError(response, "Invalid User Address");

                List<String> delegatedValidators = u.getDelegatedValidators();

                long totalDelegatedPWR = 0;
                long totalRewards = 0;
                for(String validator: delegatedValidators) {
                    long delegatedPWR = u.getDelegatedAmount(validator);
                    totalDelegatedPWR += delegatedPWR;
                    totalRewards += PWRJ.getDelegatedPWR(userAddress, validator) - delegatedPWR;
                }

                JSONArray portfolioAllocation = new JSONArray();
                for(String validator: delegatedValidators) {
                    long delegatedPWR = u.getDelegatedAmount(validator);

                    JSONObject object = new JSONObject();
                    object.put("validatorName", validator);
                    object.put("portfolioShare", BigDecimal.valueOf(delegatedPWR).divide(BigDecimal.valueOf(totalDelegatedPWR), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));

                    portfolioAllocation.put(object);
                }

                JSONArray validatorsArray = new JSONArray();
                long currentBlockNumber = Blocks.getLatestBlockNumber();
                long blockNumberToCheck = currentBlockNumber - 34560;
                if(blockNumberToCheck < 0) blockNumberToCheck = 1;
                long timeDifference = Blocks.getBlock(currentBlockNumber).getTimeStamp() - Blocks.getBlock(blockNumberToCheck).getTimeStamp();
                JSONObject oldShareValues = PWRJ.getShareValue(delegatedValidators, blockNumberToCheck);
                JSONObject currentShareValues = PWRJ.getShareValue(delegatedValidators, currentBlockNumber);

                Map<String, BigDecimal> apyByValidator = new HashMap<>();
                for(String validator: delegatedValidators) {
                    BigDecimal oldShareValue;
                    if(oldShareValues.has(validator)) {
                        oldShareValue = oldShareValues.getBigDecimal(validator);
                    } else {
                        oldShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal currentShareValue;

                    if(currentShareValues.has(validator)) {
                        currentShareValue = currentShareValues.getBigDecimal(validator);
                    } else {
                        currentShareValue = BigDecimal.ONE.divide(BigDecimal.valueOf(3600), 18, BigDecimal.ROUND_HALF_UP);
                    }

                    BigDecimal apy = currentShareValue.subtract(oldShareValue).divide(oldShareValue, 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(31536000)).divide(BigDecimal.valueOf(timeDifference), 2, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100));
                    apyByValidator.put(validator, apy);
                }

                for(String validator: delegatedValidators) {
                    Validator v = PWRJ.getValidator(validator);

                    JSONObject object = new JSONObject();
                    object.put("name", validator);
                    object.put("status", v.getStatus());
                    object.put("votingPower", v.getVotingPower());
                    object.put("totalPowerShare", BigDecimal.valueOf(v.getVotingPower()).divide(BigDecimal.valueOf(PWRJ.getActiveVotingPower()), 4, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(100)));
                    object.put("delegatedPWR", u.getDelegatedAmount(validator));
                    object.put("apy", apyByValidator.getOrDefault(validator, BigDecimal.ZERO));
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
        System.out.println(1 / 2);
    }
}

