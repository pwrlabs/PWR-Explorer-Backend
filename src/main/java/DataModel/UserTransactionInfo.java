package DataModel;

public record UserTransactionInfo(
        String firstTxnHash,
        long firstTxnTimestamp,
        String lastTxnHash,
        long lastTxnTimestamp,
        int count,
        boolean isNewUser
) {
    // Constructor
    public UserTransactionInfo(String hash, long timestamp, boolean isNewUser) {
        this(hash, timestamp, hash, timestamp, 1, isNewUser);
    }

    // Updates user info based on if user is new or not
    public UserTransactionInfo updateTransaction(String hash, long timestamp) {
        if (isNewUser) {
            return new UserTransactionInfo(
                    hash,
                    timestamp,
                    hash,
                    timestamp,
                    count + 1,
                    true
            );
        } else {
            return new UserTransactionInfo(
                    firstTxnHash,
                    firstTxnTimestamp,
                    hash,
                    timestamp,
                    count + 1,
                    false
            );
        }
    }
}