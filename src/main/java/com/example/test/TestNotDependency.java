package com.example.test;

public class TestNotDependency {
    // 这个类不应该被删除，因为它不仅被测试类使用
    public void independentMethod() {
        System.out.println("Independent");
    }
}
