package com.example.tools;

import java.util.HashSet;
import java.util.Set;

public class ClassDeleter {
    public static void main(String[] args) {
        try {
            String projectRoot = System.getProperty("user.dir") + "/TestProject";
            Set<String> classesToDelete = new HashSet<>();
            classesToDelete.add("root.cls.TargetA");
            classesToDelete.add("root.cls.TargetB");
            
            System.out.println("Scanning directory: " + projectRoot);
            ClassDependencyAnalyzer analyzer = new ClassDependencyAnalyzer();
            analyzer.analyzeAndDeleteClasses(projectRoot, classesToDelete);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 