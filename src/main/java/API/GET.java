package API;

import Services.*;
import static Utils.ResponseBuilder.*;
import static spark.Spark.get;
import static spark.Spark.path;

public class GET {
    public static void run() {
        get("/", (req, res) -> "Server is running");

        get("/test/", (request, response) -> {
            try {
                response.header("Content-Type", "application/json");
                return "Server is on";
            } catch (Exception e) {
                return getError(response, "Error in test request: " + e.getLocalizedMessage());
            }
        });

        //Explorer Calls
        get("/explorerInfo/", GeneralService::getExplorerInfo);
        get("/dailyStats", GeneralService::getDailyStats);

        // Block calls
        get("/latestBlocks/", BlockService::getLatestBlocks);
        get("/blockDetails/", BlockService::getBlockDetails);
        get("/block/", BlockService::getBlock);
        get("/blockTransactions/", BlockService::getBlockTransactions);
        get("/blocksCreated/", BlockService::getBlocksCreated);

        // Transaction calls
        get("/latestTransactions/", TransactionService::getLatestTransactions);
        get("/transactionDetails/", TransactionService::getTransactionDetails);
        get("/transactionHistory/", TransactionService::getTransactionHistory);
        get("/isTxnProcessed/", TransactionService::isTransactionProcessed);

        // Wallet Calls
        get("/balanceOf/", GeneralService::getBalanceOf);
        get("/nonceOfUser/", GeneralService::getNonceOfUser);

        // Node calls
        get("/nodesInfo/", NodeService::getNodesInfo);
        get("/nodesStatus/", NodeService::getNodesStatus);

        // Staking calls
        path("/staking/", () -> {
            get("homePageInfo/", StakingService::getHomePageInfo);
            get("validatorInfo/", StakingService::getValidatorInfo);
            get("/portfolio/", StakingService::getPortfolio);
        });
        get("/stats", StakingService::getStats);
    }
}

