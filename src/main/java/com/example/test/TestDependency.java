package com.example.test;

public class TestDependency {
    public TestSubDependency getSubDependency() {
        return subDependency;
    }

    private TestSubDependency subDependency= new TestSubDependency();
    public void doSomething() {
        System.out.println("Test");
    }
} 