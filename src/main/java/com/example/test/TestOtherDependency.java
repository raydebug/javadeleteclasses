package com.example.test;

public class TestOtherDependency {
    private TestSubDependency subDependency = new TestSubDependency();
    
    public void doSomething() {
        subDependency.subMethod();
    }
} 