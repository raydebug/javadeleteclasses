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

public class ClassDependencyAnalyzer {
    private final Set<String> allClasses = new HashSet<>();
    private final Map<String, Set<String>> dependencyGraph = new HashMap<>();
    private final Map<String, Path> classToPathMap = new HashMap<>();
    private final Map<String, String> classesToDelete = new HashMap<>();

    public void analyzeAndDeleteClasses(String rootPath, Set<String> targetClassNames) throws IOException {
        // 1. Scan all Java files
        System.out.println("\n=== Scanning Java Files ===");
        scanJavaFiles(new File(rootPath));
        System.out.println("Found classes: " + allClasses);
        
        // 2. Build dependency graph
        System.out.println("\n=== Building Dependency Graph ===");
        buildDependencyGraph(rootPath);
        System.out.println("Dependency graph:");
        dependencyGraph.forEach((k, v) -> System.out.println(k + " -> " + v));
        
        // 3. Find classes to delete
        System.out.println("\n=== Analyzing Classes to Delete ===");
        findClassesToDelete(targetClassNames);
        System.out.println("Classes to delete: " + classesToDelete);
        
        // 4. Execute deletion
        System.out.println("\n=== Executing Deletion ===");
        deleteClasses(rootPath);
    }

    private void scanJavaFiles(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        scanJavaFiles(file);
                    } else if (file.getName().endsWith(".java")) {
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
                    }
                }
            }
        }
    }

    private void buildDependencyGraph(String rootPath) throws IOException {
        Files.walk(Paths.get(rootPath))
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(this::analyzeDependencies);
    }

    private void analyzeDependencies(Path javaFile) {
        try {
            CompilationUnit cu = new JavaParser().parse(javaFile).getResult().orElse(null);
            if (cu != null) {
                DependencyVisitor visitor = new DependencyVisitor();
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
            System.out.println("\nChecking dependency: " + dependency);
            System.out.println("Used by: " + usedBy);
            
            boolean onlyUsedByTargetTrees = true;
            for (String user : usedBy) {
                if (!allDependenciesOfTargets.contains(user) && 
                    !targetClassNames.contains(user) && 
                    !classesToDelete.containsKey(user)) {
                    System.out.println("Found external user: " + user);
                    onlyUsedByTargetTrees = false;
                    break;
                }
            }

            if (onlyUsedByTargetTrees) {
                System.out.println("=> Marked for deletion (only used by target classes or their dependencies)");
                Path filePath = classToPathMap.get(dependency);
                if (filePath != null) {
                    classesToDelete.put(dependency, filePath.toString());
                    System.out.println("   File: " + filePath);
                }
            } else {
                System.out.println("=> Kept (used by classes outside target trees)");
            }
        }

        // Remove tool classes from deletion list
        classesToDelete.keySet().removeIf(className -> 
            className.startsWith("com.example.tools."));
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
        for (Map.Entry<String, String> entry : classesToDelete.entrySet()) {
            Path classFile = Paths.get(entry.getValue());
            if (Files.exists(classFile)) {
                Files.delete(classFile);
                System.out.println("Deleted class file: " + classFile);
            }
        }
    }

    private static class DependencyVisitor extends VoidVisitorAdapter<Void> {
        private final Set<String> dependencies = new HashSet<>();
        private String currentPackage = "";

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

            // If it's a simple class name, add current package name
            if (!typeName.contains(".")) {
                typeName = currentPackage + "." + typeName;
            }

            dependencies.add(typeName);
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
} 