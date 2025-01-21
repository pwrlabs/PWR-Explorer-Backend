package Utils;

import DataModel.NewTxn;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Helpers {
    private final static BigDecimal ONE_PWR_TO_USD = BigDecimal.ONE;

    public static int validateAndParsePageParam(String pageStr, spark.Response response) throws Exception {
        try {
            int page = Integer.parseInt(pageStr);
            if (page < 1) {
                throw new Exception("Page number must be positive");
            }
            return page;
        } catch (NumberFormatException e) {
            throw new Exception("Invalid page number format");
        }
    }

    public static int validateAndParseCountParam(String countStr, spark.Response response) throws Exception {
        try {
            int count = Integer.parseInt(countStr);
            if (count < 1) {
                throw new Exception("Count must be positive");
            }
            return count;
        } catch (NumberFormatException e) {
            throw new Exception("Invalid count format");
        }
    }

    public static JSONObject createPaginationMetadata(int totalCount, int currentPage, int itemsPerPage) {
        int totalPages = (int) Math.ceil((double) totalCount / itemsPerPage);
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalCount);

        JSONObject metadata = new JSONObject();
        metadata.put("totalPages", totalPages);
        metadata.put("currentPage", currentPage);
        metadata.put("itemsPerPage", itemsPerPage);
        metadata.put("totalItems", totalCount);
        metadata.put("startIndex", startIndex);
        metadata.put("endIndex", endIndex);
        metadata.put("nextPage", currentPage < totalPages ? currentPage + 1 : -1);
        metadata.put("previousPage", currentPage > 1 ? currentPage - 1 : -1);

        return metadata;
    }

    public static JSONObject populateTxnsResponse(NewTxn txn) {
        long fee = txn.txnFee();
        BigDecimal sparks = BigDecimal.valueOf(fee);
        BigDecimal pwrAmount = sparks.divide(BigDecimal.valueOf(1000000000L), 9, RoundingMode.HALF_EVEN);
        BigDecimal feeValueInUSD = pwrAmount.multiply(ONE_PWR_TO_USD).setScale(9, RoundingMode.HALF_EVEN);

        JSONObject object = new JSONObject();
        object.put("txnHash", txn.hash());
        object.put("txnType", txn.txnType());
        object.put("block", txn.blockNumber());
        object.put("timeStamp", txn.timestamp() / 1000);
        object.put("from", txn.fromAddress());
        object.put("to", txn.toAddress());
        object.put("value", txn.value());
        object.put("txnFee", fee);
        object.put("valueInUsd", txn.value());
        object.put("txnFeeInUsd", feeValueInUSD);
        object.put("positionInBlock", txn.positionInBlock()
        );
        return object;
    }

}
