package User;

import com.github.pwrlabs.dbm.DBM;
import Txn.Txn;
import com.github.pwrlabs.pwrj.protocol.PWRJ;

import java.io.File;
import java.util.*;

public class User extends DBM {
    private List<Long> blocksWhereHasTxn = new LinkedList<>();
    //Latest txns have the smallest index. The latest one is on index 0
    private List<Txn> txns = new LinkedList<>();
    private Map<String /*Validator*/, Long /*Delegated Amount*/> initialDelegations;
    public User(String address) {
        super(address.toLowerCase().trim());

       // System.out.println("New user created: " + address);

        Users.add(this);

        File initialDelegationsFolder = new File(rootPath + "initialDelegations/");
        if(initialDelegationsFolder.exists()) {
            initialDelegations = new HashMap<>();
            for(File validatorFile : initialDelegationsFolder.listFiles()) {
                String validator = validatorFile.getName();
                long amount = loadLong("initialDelegations/" + validator);
                initialDelegations.put(validator, amount);
            }
        }
    }

    public void addTxn(Txn txn) {
        txns.add(txn);

        //System.out.println("New txn added to user: " + getAddress());
        //System.out.println("Txn count: " + txns.size());

//        if(txns.size() == 0) txns.add(txn);
//        else if (txn.getTimeStamp() > txns.get(0).getTimeStamp()) txns.add(0, txn);
//        else if (txn.getTimeStamp() < txns.get(txns.size() - 1).getTimeStamp()) txns.add(txns.size(), txn);
//        else {
//            for(int i = 1; i < txns.size(); i++) {
//                if(txn.getTimeStamp() > txns.get(i).getTimeStamp()) {
//                    txns.add(i, txn);
//                    return;
//                }
//            }
//
//            txns.add(txn);
//        }
    }

    public void addDelegation(String validator, long amount) {
        if(initialDelegations == null) {
            initialDelegations = new HashMap<>();
            initialDelegations.put(validator.toLowerCase(), amount);
            Users.addDelegator();
        } else {
            long delegated = initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
            initialDelegations.put(validator.toLowerCase(), delegated + amount);
        }

        store("initialDelegations/" + validator.toLowerCase(), initialDelegations.getOrDefault(validator.toLowerCase(), 0L));
    }
    //Used when a user withdraws PWR, we check if the withdrawn PWR is from rewards only or also delegated PWR
    //If it is from delegated PWR, we decrease the initial delegation amount
    public void checkDelegation(String validator) {
        if(initialDelegations == null) return;
        long delegatedPWR = PWRJ.getDelegatedPWR(getAddress(), validator);

        if(delegatedPWR == 0) {
            initialDelegations.remove(validator.toLowerCase());
            if(initialDelegations.size() == 0) {
                initialDelegations = null;
                Users.removeDelegator();
            }
        }
        else {
            long initialDelegation = initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
            if(delegatedPWR < initialDelegation) initialDelegations.put(validator.toLowerCase(), delegatedPWR);
        }

        store("initialDelegations/" + validator.toLowerCase(), initialDelegations.getOrDefault(validator.toLowerCase(), 0L));
    }
    public String getAddress() {
        return id;
    }
    public long getBalance() {
        return loadLong("balance");
    }
    public List<Txn> getTxns() {
        return txns;
    }
    public boolean isDelegator() {
        if(initialDelegations == null) return false;
        if(initialDelegations.size() == 0) return false;

        return true;
    }

    public List<String> getDelegatedValidators() {
        if(initialDelegations == null) return new LinkedList<>();
        if(initialDelegations.size() == 0) return new LinkedList<>();

        List<String> validators = new LinkedList<>();
        for(String validator : initialDelegations.keySet()) {
            validators.add(validator);
        }

        return validators;
    }

    public long getDelegatedAmount(String validator) {
        if(initialDelegations == null) return 0;
        if(initialDelegations.size() == 0) return 0;

        return initialDelegations.getOrDefault(validator.toLowerCase(), 0L);
    }
}
