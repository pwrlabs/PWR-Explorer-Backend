package User;

import com.github.pwrlabs.dbm.DBM;
import Txn.Txn;
import com.github.pwrlabs.pwrj.protocol.PWRJ;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static Core.Sql.Queries.*;

//public class User {
//    private static final Logger logger = LogManager.getLogger(User.class);
//    private String address;
//    private List<Long> blocksWhereHasTxn = new LinkedList<>();
//    //Latest txns have the smallest index. The latest one is on index 0
//    private List<Txn> txns = new LinkedList<>();
//    private Map<String /*Validator*/, Long /*Delegated Amount*/> initialDelegations;
//    public User(String address) {
//        this.address = address;
//
//        Users.add(this);
//
////        JSONObject initialDelegationsJSON = loadJSON("initialDelegations");
//        JSONObject initialDelegationsJSON = getInitialDeletations(address);
//        initialDelegations = new HashMap<>();
//        for (String validator : initialDelegationsJSON.keySet()) {
//            initialDelegations.put(validator.toLowerCase(), initialDelegationsJSON.getLong(validator));
//        }
//    }
//
//    public void addTxn(Txn txn) {
//        txns.add(txn);
//    }
//
//    public void addDelegation(String validator, long amount) {
//        if(initialDelegations == null) {
//            initialDelegations = new HashMap<>();
//            initialDelegations.put(validator.toLowerCase(), amount);
//            Users.addDelegator();
//        } else {
//            long delegated = initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
//            initialDelegations.put(validator.toLowerCase(), delegated + amount);
//        }
//
//        updateInitialDelegations(address, new JSONObject(initialDelegations).toString());
//    }
//    //Used when a user withdraws PWR, we check if the withdrawn PWR is from rewards only or also delegated PWR
//    //If it is from delegated PWR, we decrease the initial delegation amount
//    public void checkDelegation(String validator) throws IOException {
//        if(initialDelegations == null) return;
//        long delegatedPWR = PWRJ.getDelegatedPWR(getAddress(), validator);
//
//        if(delegatedPWR == 0) {
//            initialDelegations.remove(validator.toLowerCase());
//            if(initialDelegations.size() == 0) {
//                initialDelegations = null;
//                Users.removeDelegator();
//            }
//        }
//        else {
//            long initialDelegation = initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
//            if(delegatedPWR < initialDelegation) initialDelegations.put(validator.toLowerCase(), delegatedPWR);
//        }
//
//        //TODO: is this supposed to be updating the previous validator amount for this address?
////        store("initialDelegations/" + validator.toLowerCase(), initialDelegations.getOrDefault(validator.toLowerCase(), 0L));
//    }
////    public String getAddress() {
////        return id;
////    }
//    public String getAddress() {
//        return address;
//    }
////    public long getBalance() {
////        return loadLong("balance");
////    }
//    public List<Txn> getTxns() {
//        return txns;
//    }
//    public boolean isDelegator() {
//        if(initialDelegations == null) return false;
//        if(initialDelegations.size() == 0) return false;
//
//        return true;
//    }
//
//    public List<String> getDelegatedValidators() {
//        if(initialDelegations == null) return new LinkedList<>();
//        if(initialDelegations.size() == 0) return new LinkedList<>();
//
//        List<String> validators = new LinkedList<>();
//        for(String validator : initialDelegations.keySet()) {
//            validators.add(validator);
//        }
//
//        return validators;
//    }
//
//    public long getDelegatedAmount(String validator) {
//        if(initialDelegations == null) return 0;
//        if(initialDelegations.size() == 0) return 0;
//
//        return initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
//    }

public class User {
    private static final Logger logger = LogManager.getLogger(User.class);
    private String address;
    private byte[] firstSentTxn;
    private byte[] lastSentTxn;
    private byte[][] transactionHashes;
    private int transactionsCount;

    private Map<String /*Validator*/, Long /*Delegated Amount*/> initialDelegations;

    public User(String address, byte[] firstSentTxn, byte[] lastSentTxn, byte[][] transactionHashes, int transactionsCount) {
        this.address = address;
        this.firstSentTxn = firstSentTxn;
        this.lastSentTxn = lastSentTxn;
        this.transactionHashes = transactionHashes;
        this.transactionsCount = transactionsCount;

        Users.add(this);

        initialDelegations = new HashMap<>();
        JSONObject initialDelegationsJSON = getInitialDelegations(address);
        for (String validator : initialDelegationsJSON.keySet()) {
            initialDelegations.put(validator.toLowerCase(), initialDelegationsJSON.getLong(validator));
        }
    }

    public void addTxn(Txn txn) {
        if (transactionHashes == null) {
            transactionHashes = new byte[][]{txn.getHash().getBytes()};
        } else {
            byte[][] newTransactionHashes = Arrays.copyOf(transactionHashes, transactionHashes.length + 1);
            newTransactionHashes[transactionHashes.length] = txn.getHash().getBytes();
            transactionHashes = newTransactionHashes;
        }
        transactionsCount++;
        if (firstSentTxn == null) {
            firstSentTxn = txn.getHash().getBytes();
        }
        lastSentTxn = txn.getHash().getBytes();

        updateUserInDatabase(address, firstSentTxn, lastSentTxn, transactionHashes, transactionsCount);
    }

    public void addDelegation(String validator, long amount) {
        long delegated = initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
        initialDelegations.put(validator.toLowerCase(), delegated + amount);
        updateInitialDelegations(address, validator, delegated + amount);
    }

    public void checkDelegation(PWRJ pwrj, String delegator, String validator) throws IOException {
        if(initialDelegations == null) return;
        long delegatedPWR = pwrj.getDelegatedPWR(delegator,validator);

        if(delegatedPWR == 0) {
            initialDelegations.remove(validator.toLowerCase());
            if(initialDelegations.size() == 0) {
                initialDelegations = null;
            }
        }
        else {
            JSONObject initialDelegations = getInitialDelegations(delegator);
            long initialDelegation = initialDelegations.getLong("initial_delegation");
            if(delegatedPWR < initialDelegation){
                initialDelegations.put(validator.toLowerCase(), delegatedPWR);
                updateInitialDelegations(delegator,validator,delegatedPWR);
            }
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

    public byte[] getFirstSentTxn() {
        return firstSentTxn;
    }

    public byte[] getLastSentTxn() {
        return lastSentTxn;
    }

    public byte[][] getTransactionHashes() {
        return transactionHashes;
    }

    public int getTransactionsCount() {
        return transactionsCount;
    }
}

