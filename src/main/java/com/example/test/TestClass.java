package com.example.test;

public class TestClass {
    private TestDependency dependency = new TestDependency();
    private TestShareDependency shareDependency;  // shared with TestClassTwo
    
    public void test() {
        dependency.doSomething();
    }
} 