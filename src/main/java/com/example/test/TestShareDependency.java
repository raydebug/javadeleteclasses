package com.example.test;

public class TestShareDependency {
    // This class is shared between TestClass and TestClassTwo
    public void sharedMethod() {
        System.out.println("Shared");
    }
}
