package com.example.test;

public class TestClassTwo {
    private TestOtherDependency otherDependency = new TestOtherDependency();
    private TestShareDependency shareDependency;  // shared with TestClass
    
    public void test() {
        otherDependency.doSomething();
    }
}
