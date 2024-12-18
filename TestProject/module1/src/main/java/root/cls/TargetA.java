package root.cls;

import dep.cls.SharedDep;
import dep.cls.TargetDep;
import dep.cls.TargetSharedDep;

public class TargetA {
    SharedDep sharedDep;
    TargetDep targetDep;
    TargetSharedDep targetSharedDep;

    private static void createTargetNew(){
        System.out.println(new TargetNew());
    }
}
