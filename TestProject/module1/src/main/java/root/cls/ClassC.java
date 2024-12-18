package root.cls;

import dep.cls.SharedDep;

public class ClassC {
    SharedDep sharedDep;

    private static void createTargetNew(){
        System.out.println(new ClassD());
    }
}
