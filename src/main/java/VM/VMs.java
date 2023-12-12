package VM;

import java.util.HashMap;
import java.util.Map;

public class VMs {
    private static Map<Long /*VM ID*/, VM> vmById= new HashMap<>();

    public static void add(VM vm) {
        vmById.put(vm.getId(), vm);
    }

    public static VM getVM(Long vmId) {
        return vmById.get(vmId);
    }
}
