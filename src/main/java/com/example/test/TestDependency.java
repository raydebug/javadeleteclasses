package com.example.test;

public class TestDependency {
    private TestSubDependency subDependency = new TestSubDependency();
    
    public void doSomething() {
        subDependency.subMethod();
    }
} 