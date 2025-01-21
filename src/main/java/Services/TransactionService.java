package Services;

import Core.Cache.CacheManager;
import DataModel.NewTxn;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import com.github.pwrlabs.pwrj.record.transaction.PayableVmDataTransaction;
import com.github.pwrlabs.pwrj.record.transaction.Transaction;
import com.github.pwrlabs.pwrj.record.transaction.VmDataTransaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static Database.Queries.*;
import static Utils.Helpers.*;
import static Utils.ResponseBuilder.*;

public class TransactionService {
    private static final Logger logger = LogManager.getLogger(TransactionService.class);
    private static CacheManager cacheManager;
    private static PWRJ pwrj;
    private static final BigDecimal ONE_PWR_TO_USD = new BigDecimal("1.0"); // Adjust value as needed

    public static void initialize(PWRJ pwrjInstance) {
        pwrj = pwrjInstance;
        cacheManager = new CacheManager(pwrj);
    }

    public static Object getLatestTransactions(Request request, Response response) {
        JSONArray transactions = new JSONArray();
        try {
            response.header("Content-Type", "application/json");

            int count = validateAndParseCountParam(request.queryParams("count"), response);
            int page = validateAndParsePageParam(request.queryParams("page"), response);
            int offset = (page - 1) * count;

            List<NewTxn> txns = getTransactions(count, offset);
            int totalTxnCount = cacheManager.getTotalTransactionCount();

            for (NewTxn txn : txns) {
                JSONObject object = populateTxnsResponse(txn);
                transactions.put(object);
            }

            JSONObject metadata = createPaginationMetadata(totalTxnCount, page, count);

            return getSuccess("metadata", metadata,
                    "transactionCountPast24Hours", cacheManager.getTxnsCountPast24Hours(),
                    "transactionCountPercentageChangeComparedToPreviousDay", cacheManager.getTxnCountPercentageChange(),
                    "totalTransactionFeesPast24Hours", cacheManager.getTotalTxnsFeesPast24Hours(),
                    "totalTransactionFeesPercentageChangeComparedToPreviousDay", cacheManager.getTotalTxnFeesPercentageChange(),
                    "averageTransactionFeePast24Hours", cacheManager.getAverageTxnFeePast24Hours(),
                    "averageTransactionFeePercentageChangeComparedToPreviousDay", cacheManager.getAvgTxnFeePercentageChange(),
                    "transactions", transactions
            );
        } catch (Exception e) {
            return getError(response, "Failed to fetch latest transactions: " + e.getLocalizedMessage());
        }
    }

    public static Object getTransactionDetails(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            String txnHash = request.queryParams("txnHash").toLowerCase();

            Transaction txn = pwrj.getTransactionByHash(txnHash);
            if (txn == null) return getError(response, "Invalid DataModel.Block.Txn Hash");

            String data = null;
            if (txn instanceof VmDataTransaction) {
                data = ((VmDataTransaction) txn).getData();
            } else if (txn instanceof PayableVmDataTransaction) {
                data = ((PayableVmDataTransaction) txn).getData();
            }

            long txnFee = txn.getFee();

            BigDecimal sparks = new BigDecimal(txnFee);
            BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);

            BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);
            return getSuccess(
                    "txnHash", txnHash,
                    "txnType", txn.getType(),
                    "blockNumber", txn.getBlockNumber(),
                    "timeStamp", txn.getTimestamp() / 1000,
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
            return getError(response, "Failed to get txn details: " + e.getLocalizedMessage());
        }
    }

    public static Object getTransactionHistory(Request request, Response response) {
        try {
            long serverTime = System.currentTimeMillis();
            response.header("Content-Type", "application/json");

            String address = request.queryParams("address");
            if (address == null) {
                return getError(response, "Address is required");
            }
            address = address.substring(2).toLowerCase();

            int count = validateAndParseCountParam(request.queryParams("count"), response);
            int page = validateAndParsePageParam(request.queryParams("page"), response);

            List<NewTxn> txns = getUserTxns(address, page, count);
            int totalTxnCount = getTotalTxnCount(address);

            JSONArray transactions = new JSONArray();
            for (NewTxn txn : txns) {
                JSONObject object = populateTxnsResponse(txn);
                transactions.put(object);
            }

            JSONObject metadata = createPaginationMetadata(totalTxnCount, page, count);

            Pair<NewTxn, NewTxn> firstLastTxns = new Pair<>(null, null);

            if (totalTxnCount != 0) {
                firstLastTxns = getFirstAndLastTransactionsByAddress(address);
            }

            JSONObject firstLastTxnsObject = new JSONObject();
            if (firstLastTxns.first() != null) {
                JSONObject firstTxnObject = new JSONObject();
                firstTxnObject.put("txnHash", firstLastTxns.first().hash());
                firstTxnObject.put("block", firstLastTxns.first().blockNumber());
                firstTxnObject.put("timeStamp", firstLastTxns.first().timestamp() / 1000);
                firstLastTxnsObject.put("firstTransaction", firstTxnObject);
            }

            if (firstLastTxns.second() != null) {
                JSONObject lastTxnObject = new JSONObject();
                lastTxnObject.put("txnHash", firstLastTxns.second().hash());
                lastTxnObject.put("block", firstLastTxns.second().blockNumber());
                lastTxnObject.put("timeStamp", firstLastTxns.second().timestamp() / 1000);
                firstLastTxnsObject.put("lastTransaction", lastTxnObject);
            }

            return getSuccess(
                    "transactions", transactions,
                    "metadata", metadata,
                    "firstLastTransactions", firstLastTxnsObject,
                    "serverTime", System.currentTimeMillis() - serverTime
            );
        } catch (Exception e) {
            return getError(response, "Failed to get transaction history " + e.getLocalizedMessage());
        }
    }

    public static Object isTransactionProcessed(Request request, Response response) {
        try {
            response.header("Content-Type", "application/json");

            String txnHash = request.queryParams("txnHash").toLowerCase();

            NewTxn txn = getDbTxn(txnHash);
            if (txn == null) return getSuccess("isProcessed", false);
            else return getSuccess("isProcessed", true);
        } catch (Exception e) {
            return getError(response, "Failed to get txn status: " + e);
        }
    }

}