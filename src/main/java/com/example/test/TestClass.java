package com.example.test;

public class TestClass {
    private TestShareDependency shareDependency;
    private TestDependency dependency = new TestDependency();
    
    public void test() {
        dependency.doSomething();
    }
} 