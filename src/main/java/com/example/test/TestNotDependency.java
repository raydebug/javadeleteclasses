package com.example.test;

public class TestNotDependency {
    // This class should not be deleted as it's used by other classes
    public void independentMethod() {
        System.out.println("Independent");
    }
}
