package com.example.test;

public class TestDependency {
    private TestSubDependency subDependency = new TestSubDependency();
    
    public TestSubDependency getSubDependency() {
        return subDependency;
    }

    public void doSomething() {
        System.out.println("Test");
    }
} 