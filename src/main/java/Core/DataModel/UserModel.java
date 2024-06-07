package Core.DataModel;

public class UserModel {
    private String address;
    private byte[] firstSentTxn;
    private byte[] lastSentTxn;
    private byte[][] transactionHashes;

    public UserModel(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes) {
        this.address = address;
        this.firstSentTxn = firstSentTxn;
        this.lastSentTxn = lastSentTxn;
        this.transactionHashes = transactionHashes;
    }

    public String getAddress() {
        return address;
    }

    public byte[] getFirstSentTxn() {
        return firstSentTxn;
    }

    public byte[] getLastSentTxn() {
        return lastSentTxn;
    }

    public byte[][] getTransactionHashes() {
        return transactionHashes;
    }
}
