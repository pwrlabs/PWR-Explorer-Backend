package User;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.util.*;

import static Core.Sql.Queries.*;

public class User {
    private static final Logger logger = LogManager.getLogger(User.class);
    private String address;
    private byte[] firstSentTxn;
    private byte[] lastSentTxn;
    private byte[][] transactionHashes;
    private int transactionsCount;
    private final Map<String /*Validator*/, Long /*Delegated Amount*/> initialDelegations;

    public User(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes, int transactionsCount) {
        this.address = address;
        this.firstSentTxn = firstSentTxn;
        this.lastSentTxn = lastSentTxn;
        this.transactionHashes = transactionHashes;
        this.transactionsCount = transactionsCount;

        initialDelegations = new HashMap<>();
        JSONObject initialDelegationsJSON = getInitialDelegations(address);
        for (String validator : initialDelegationsJSON.keySet()) {
            initialDelegations.put(validator.toLowerCase(), initialDelegationsJSON.getLong(validator));
        }
    }

    public String getAddress() {
        return address;
    }

    public List<String> getDelegatedValidators() {
        return new ArrayList<>(initialDelegations.keySet());
    }

    public long getDelegatedAmount(String validator) {
        return initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
    }
}

