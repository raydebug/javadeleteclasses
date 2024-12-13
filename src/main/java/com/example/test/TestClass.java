package com.example.test;

public class TestClass {
    private TestDependency dependency = new TestDependency();
    
    public void test() {
        dependency.doSomething();
    }
} 