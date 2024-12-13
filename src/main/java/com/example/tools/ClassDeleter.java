package com.example.tools;

public class ClassDeleter {
    public static void main(String[] args) {
        try {
            // Use current project's src/main/java directory
            String projectRoot = System.getProperty("user.dir") + "/src/main/java";
            String classToDelete = "com.example.test.TestClass";
            
            System.out.println("Scanning directory: " + projectRoot);
            ClassDependencyAnalyzer analyzer = new ClassDependencyAnalyzer();
            analyzer.analyzeAndDeleteClass(projectRoot, classToDelete);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 