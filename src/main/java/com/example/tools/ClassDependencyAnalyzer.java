package com.example.tools;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClassDependencyAnalyzer {
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private final PrintWriter logWriter;
    private final ConcurrentHashMap<String, Set<String>> dependencyGraph = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Path> classToPathMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> classesToDelete = new ConcurrentHashMap<>();
    private final Set<String> allClasses = ConcurrentHashMap.newKeySet();

    public ClassDependencyAnalyzer() throws IOException {
        String logFile = "deletion_analysis_" + 
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".log";
        this.logWriter = new PrintWriter(new FileWriter(logFile, true));
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String logMessage = String.format("[%s] %s", timestamp, message);
        System.out.println(logMessage);
        logWriter.println(logMessage);
        logWriter.flush();
    }

    private void logProgress(String message) {
        System.out.print("\r" + message);
        logWriter.println(message);
        logWriter.flush();
    }

    public void analyzeAndDeleteClasses(String rootPath, Set<String> targetClassNames) throws IOException {
        try {
            log("Starting analysis...");
            
            log("Scanning files... ");
            scanJavaFiles(new File(rootPath));
            log("Done");
            
            log("Analyzing dependencies... ");
            buildDependencyGraph(rootPath);
            log("Done");
            
            log("Finding classes to delete... ");
            findClassesToDelete(targetClassNames);
            log("Done");
            
            int deleteCount = classesToDelete.size();
            if (deleteCount > 0) {
                log(String.format("Deleting %d classes...", deleteCount));
                deleteClasses(rootPath);
                log("Done");
            } else {
                log("No classes to delete");
            }
        } finally {
            logWriter.close();
        }
    }

    private void scanJavaFiles(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
                try {
                    Arrays.stream(files)
                        .parallel()
                        .forEach(file -> {
                            if (file.isDirectory()) {
                                scanJavaFiles(file);
                            } else if (file.getName().endsWith(".java")) {
                                executor.submit(() -> {
                                    try {
                                        CompilationUnit cu = new JavaParser().parse(file).getResult().orElse(null);
                                        if (cu != null) {
                                            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                                                String className = c.getFullyQualifiedName().orElse("");
                                                if (!className.isEmpty()) {
                                                    allClasses.add(className);
                                                    classToPathMap.put(className, file.toPath());
                                                }
                                            });
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        });
                } finally {
                    shutdownExecutor(executor);
                }
            }
        }
    }

    private void buildDependencyGraph(String rootPath) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            List<Path> javaFiles = Files.walk(Paths.get(rootPath))
                .filter(p -> p.toString().endsWith(".java"))
                .collect(Collectors.toList());

            AtomicInteger processedFiles = new AtomicInteger(0);
            int totalFiles = javaFiles.size();

            // First pass: Scan all classes
            CompletableFuture<?>[] futures = javaFiles.stream()
                .map(path -> CompletableFuture.runAsync(() -> {
                    try {
                        CompilationUnit cu = new JavaParser().parse(path).getResult().orElse(null);
                        if (cu != null) {
                            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> {
                                String className = c.getFullyQualifiedName().orElse("");
                                if (!className.isEmpty()) {
                                    allClasses.add(className);
                                    classToPathMap.put(className, path);
                                }
                            });
                        }
                        updateProgress(processedFiles.incrementAndGet(), totalFiles);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, executor))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();

            // Second pass: Analyze dependencies
            processedFiles.set(0);
            futures = javaFiles.stream()
                .map(path -> CompletableFuture.runAsync(() -> {
                    analyzeDependencies(path);
                    updateProgress(processedFiles.incrementAndGet(), totalFiles);
                }, executor))
                .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(futures).join();
            logProgress("Done");
        } finally {
            shutdownExecutor(executor);
        }
    }

    private void analyzeDependencies(Path javaFile) {
        try {
            CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
            if (cu != null) {
                DependencyVisitor visitor = new DependencyVisitor(allClasses);
                visitor.visit(cu, null);
                
                String className = cu.getPrimaryType()
                    .filter(type -> type.isClassOrInterfaceDeclaration())
                    .map(type -> type.asClassOrInterfaceDeclaration())
                    .map(c -> c.getFullyQualifiedName().orElse(""))
                    .orElse("");
                
                if (!className.isEmpty()) {
                    dependencyGraph.put(className, visitor.getDependencies());
                    classToPathMap.put(className, javaFile);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void findClassesToDelete(Set<String> targetClassNames) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        // Initialize with all target classes
        for (String targetClassName : targetClassNames) {
            queue.offer(targetClassName);
            Path targetPath = classToPathMap.get(targetClassName);
            if (targetPath != null) {
                classesToDelete.put(targetClassName, targetPath.toString());
                log("Target class: " + targetClassName);
            }
        }

        // Build reverse dependency graph
        Map<String, Set<String>> reverseDependencies = buildReverseDependencyGraph();

        // Find all dependencies of target classes
        Set<String> allDependenciesOfTargets = new HashSet<>();
        for (String targetClassName : targetClassNames) {
            findAllDependencies(targetClassName, allDependenciesOfTargets, new HashSet<>());
        }

        // Check each dependency
        for (String dependency : allDependenciesOfTargets) {
            Set<String> usedBy = reverseDependencies.getOrDefault(dependency, new HashSet<>());
            
            boolean onlyUsedByTargetTrees = true;
            Set<String> externalUsers = new HashSet<>();
            
            for (String user : usedBy) {
                if (!allDependenciesOfTargets.contains(user) && 
                    !targetClassNames.contains(user) && 
                    !classesToDelete.containsKey(user)) {
                    onlyUsedByTargetTrees = false;
                    externalUsers.add(user);
                }
            }

            if (onlyUsedByTargetTrees) {
                Path filePath = classToPathMap.get(dependency);
                if (filePath != null) {
                    classesToDelete.put(dependency, filePath.toString());
                    log("\nDependent class: " + dependency);
                    log("Deletion paths:");
                    findDeletionPaths(dependency, targetClassNames, reverseDependencies, new ArrayList<>());
                }
            }
        }

        // Remove tool classes from deletion list
        classesToDelete.keySet().removeIf(className -> {
            if (className.startsWith("com.example.tools.")) {
                log("Keeping tool class: " + className);
                return true;
            }
            return false;
        });
    }

    private void findDeletionPaths(String className, Set<String> targetClasses, 
            Map<String, Set<String>> reverseDependencies, List<String> currentPath) {
        currentPath.add(className);
        
        Set<String> users = reverseDependencies.getOrDefault(className, new HashSet<>());
        if (users.isEmpty() || targetClasses.contains(className)) {
            if (targetClasses.contains(className)) {
                log("  " + String.join(" -> ", currentPath));
            }
        } else {
            for (String user : users) {
                if (!currentPath.contains(user)) {  // Avoid cycles
                    findDeletionPaths(user, targetClasses, reverseDependencies, currentPath);
                }
            }
        }
        
        currentPath.remove(currentPath.size() - 1);
    }

    private Map<String, Set<String>> buildReverseDependencyGraph() {
        Map<String, Set<String>> reverseDependencies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
            String className = entry.getKey();
            for (String dependency : entry.getValue()) {
                reverseDependencies.computeIfAbsent(dependency, k -> new HashSet<>())
                    .add(className);
            }
        }
        return reverseDependencies;
    }

    private void findAllDependencies(String className, Set<String> allDependencies, Set<String> visited) {
        if (visited.contains(className)) return;
        visited.add(className);

        Set<String> directDependencies = dependencyGraph.getOrDefault(className, new HashSet<>());
        allDependencies.addAll(directDependencies);

        for (String dependency : directDependencies) {
            findAllDependencies(dependency, allDependencies, visited);
        }
    }

    private void deleteClasses(String rootPath) throws IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        try {
            AtomicInteger processedFiles = new AtomicInteger(0);
            int totalFiles = classesToDelete.size();

            List<CompletableFuture<Void>> deleteFutures = classesToDelete.entrySet().stream()
                .map(entry -> CompletableFuture.runAsync(() -> {
                    try {
                        Path classFile = Paths.get(entry.getValue());
                        if (Files.exists(classFile)) {
                            Files.delete(classFile);
                            updateProgress(processedFiles.incrementAndGet(), totalFiles);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }, executor))
                .collect(Collectors.toList());

            CompletableFuture.allOf(deleteFutures.toArray(new CompletableFuture[0])).join();
            logProgress("Done");
        } finally {
            shutdownExecutor(executor);
        }
    }

    private synchronized void updateProgress(int current, int total) {
        int percentage = (int) ((current * 100.0) / total);
        logProgress(String.format("Progress: %d%% (%d/%d)", percentage, current, total));
    }

    private void shutdownExecutor(ExecutorService executor) {
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

    private static class DependencyVisitor extends VoidVisitorAdapter<Void> {
        private final Set<String> dependencies = new HashSet<>();
        private String currentPackage = "";
        private final Set<String> allClasses;

        public DependencyVisitor(Set<String> allClasses) {
            this.allClasses = allClasses;
        }

        @Override
        public void visit(CompilationUnit n, Void arg) {
            n.getPackageDeclaration().ifPresent(pkg -> 
                currentPackage = pkg.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // Collect field declarations
            n.getFields().forEach(field -> {
                // Get field type
                String fieldType = field.getElementType().asString();
                addDependency(fieldType);

                // Get types from field initialization
                field.getVariables().forEach(var -> {
                    // Add variable type
                    addDependency(var.getType().asString());
                    
                    // Check initialization expressions
                    var.getInitializer().ifPresent(init -> {
                        init.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class)
                            .forEach(expr -> addDependency(expr.getType().asString()));
                        init.findAll(com.github.javaparser.ast.expr.NameExpr.class)
                            .forEach(expr -> addDependency(expr.getNameAsString()));
                    });
                });
            });

            // Collect method dependencies
            n.getMethods().forEach(method -> {
                // Method return type
                addDependency(method.getType().asString());
                
                // Method parameter types
                method.getParameters().forEach(param -> 
                    addDependency(param.getType().asString()));

                // Type references in method body
                method.getBody().ifPresent(body -> {
                    // Collect types from method calls
                    body.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class)
                        .forEach(call -> {
                            if (call.getScope().isPresent()) {
                                addDependency(call.getScope().get().toString());
                            }
                        });

                    // Collect object creation expressions
                    body.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class)
                        .forEach(expr -> addDependency(expr.getType().asString()));

                    // Collect types from variable declarations
                    body.findAll(com.github.javaparser.ast.expr.VariableDeclarationExpr.class)
                        .forEach(var -> var.getVariables().forEach(v -> 
                            addDependency(v.getType().asString())));
                });
            });

            super.visit(n, arg);
        }

        private void addDependency(String typeName) {
            if (typeName == null || typeName.isEmpty() || 
                typeName.equals("void") || isPrimitiveType(typeName)) {
                return;
            }

            if (typeName.startsWith("java.") || typeName.startsWith("javax.")) {
                return;
            }

            if (!typeName.contains(".")) {
                for (String fullClassName : allClasses) {
                    if (fullClassName.endsWith("." + typeName)) {
                        dependencies.add(fullClassName);
                        return;
                    }
                }
                return;
            }

            // Only add if class exists in project
            if (allClasses.contains(typeName)) {
                dependencies.add(typeName);
            }
        }

        private boolean isPrimitiveType(String typeName) {
            return typeName.equals("int") || typeName.equals("long") || 
                   typeName.equals("double") || typeName.equals("float") ||
                   typeName.equals("boolean") || typeName.equals("byte") ||
                   typeName.equals("char") || typeName.equals("short");
        }

        public Set<String> getDependencies() {
            return dependencies;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            if (logWriter != null) {
                logWriter.close();
            }
        } finally {
            super.finalize();
        }
    }
} 