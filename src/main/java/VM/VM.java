package VM;

import Txn.Txn;
import com.github.pwrlabs.dbm.DBM;

import java.util.LinkedList;
import java.util.List;

public class VM extends DBM {
    private List<Txn> txns = new LinkedList<>();
    //TODO: migrate to db
    public VM(String owner, long vmId) {
        super(vmId + "");

        store("owner", owner);

        VMs.add(this);
    }

    public VM(String vmId) {
        super(vmId);

        VMs.add(this);
    }

    public void addTxn(Txn txn) {
        txns.add(txn);
    }

    public List<Txn> getTxns() {
        return txns;
    }

    public String getOwner() {
        return loadString("owner");
    }
    public long getId() {
        return Long.parseLong(id);
    }
}
