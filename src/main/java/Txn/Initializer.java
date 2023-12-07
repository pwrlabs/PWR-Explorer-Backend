package Txn;

import com.github.pwrlabs.dbm.DBM;

public class Initializer {

    public static void init() throws NoSuchMethodException {
        DBM.loadAllObjectsFromDatabase(Txn.class);

        new Thread() {
            public void run() {
                while(true) {
                    try { Txns.updateTxn24HourStats(); } catch (Exception e) { e.printStackTrace(); }
                    try { Thread.sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }
                }
            }
        }.start();
    }
}
