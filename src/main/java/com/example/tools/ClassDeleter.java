package com.example.tools;

public class ClassDeleter {
    public static void main(String[] args) {
        try {
            // 使用当前项目的 src/main/java 目录
            String projectRoot = System.getProperty("user.dir") + "/src/main/java";
            String classToDelete = "com.example.test.TestClass";
            
            System.out.println("扫描目录: " + projectRoot);
            ClassDependencyAnalyzer analyzer = new ClassDependencyAnalyzer();
            analyzer.analyzeAndDeleteClass(projectRoot, classToDelete);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 