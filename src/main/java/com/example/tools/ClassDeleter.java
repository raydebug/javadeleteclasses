package com.example.tools;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ClassDeleter {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    
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

    public void deleteClasses(List<String> classNames, String projectPath) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        try {
            List<Path> javaFiles = Files.walk(Paths.get(projectPath))
                    .filter(path -> path.toString().endsWith(".java"))
                    .collect(Collectors.toList());

            for (Path file : javaFiles) {
                executor.submit(() -> {
                    try {
                        processFile(file, classNames);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void processFile(Path file, List<String> classNames) throws IOException {
        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        boolean shouldDelete = classNames.stream()
                .anyMatch(className -> content.contains(className));
        
        if (shouldDelete) {
            Files.delete(file);
            System.out.println("Deleted file: " + file);
        }
    }
} 