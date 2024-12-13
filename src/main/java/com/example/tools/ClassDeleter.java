package com.example.tools;

import java.util.HashSet;
import java.util.Set;

public class ClassDeleter {
    public static void main(String[] args) {
        try {
            String projectRoot = System.getProperty("user.dir") + "/src/main/java";
            Set<String> classesToDelete = new HashSet<>();
            classesToDelete.add("com.example.test.TestClass");
            classesToDelete.add("com.example.test.TestClassTwo");
            
            System.out.println("Scanning directory: " + projectRoot);
            ClassDependencyAnalyzer analyzer = new ClassDependencyAnalyzer();
            analyzer.analyzeAndDeleteClasses(projectRoot, classesToDelete);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 