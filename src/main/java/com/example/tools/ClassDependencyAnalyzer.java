package com.example.tools;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ClassDependencyAnalyzer {
    private final Set<String> allClasses = new HashSet<>();
    private final Map<String, Set<String>> dependencyGraph = new HashMap<>();
    private final Set<String> classesToDelete = new HashSet<>();

    public void analyzeAndDeleteClass(String rootPath, String targetClassName) throws IOException {
        // 1. 扫描所有Java文件
        scanJavaFiles(new File(rootPath));
        
        // 2. 构建依赖图
        buildDependencyGraph(rootPath);
        
        // 3. 找出需要删除的类
        findClassesToDelete(targetClassName);
        
        // 4. 执行删除操作
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
                                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(c -> 
                                    allClasses.add(c.getFullyQualifiedName().orElse("")));
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
        Files.walk(Path.of(rootPath))
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
                    .map(type -> type.asClassOrInterfaceDeclaration())
                    .map(c -> c.getFullyQualifiedName().orElse(""))
                    .orElse("");
                
                dependencyGraph.put(className, visitor.getDependencies());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void findClassesToDelete(String targetClassName) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.offer(targetClassName);
        classesToDelete.add(targetClassName);

        // 第一步：找出所有直接和间接依赖
        Map<String, Set<String>> reverseDependencies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
            String className = entry.getKey();
            Set<String> dependencies = entry.getValue();
            
            for (String dependency : dependencies) {
                reverseDependencies.computeIfAbsent(dependency, k -> new HashSet<>())
                    .add(className);
            }
        }

        // 第二步：递归检查每个依赖是否只被目标类树使用
        while (!queue.isEmpty()) {
            String currentClass = queue.poll();
            if (visited.contains(currentClass)) continue;
            visited.add(currentClass);

            Set<String> dependencies = dependencyGraph.getOrDefault(currentClass, new HashSet<>());
            for (String dependency : dependencies) {
                if (classesToDelete.contains(dependency)) continue;

                // 检查这个依赖是否只被已标记删除的类使用
                Set<String> usedBy = reverseDependencies.getOrDefault(dependency, new HashSet<>());
                boolean onlyUsedByDeletedClasses = true;
                
                for (String user : usedBy) {
                    if (!classesToDelete.contains(user)) {
                        onlyUsedByDeletedClasses = false;
                        break;
                    }
                }

                if (onlyUsedByDeletedClasses) {
                    classesToDelete.add(dependency);
                    queue.offer(dependency);
                }
            }
        }

        // 从删除列表中移除工具类
        classesToDelete.removeIf(className -> 
            className.startsWith("com.example.tools."));
    }

    private void deleteClasses(String rootPath) throws IOException {
        for (String className : classesToDelete) {
            String relativePath = className.replace('.', '/') + ".java";
            Path classFile = Path.of(rootPath, relativePath);
            if (Files.exists(classFile)) {
                Files.delete(classFile);
                System.out.println("已删除类文件: " + classFile);
            }
        }
    }

    private static class DependencyVisitor extends VoidVisitorAdapter<Void> {
        private final Set<String> dependencies = new HashSet<>();

        public Set<String> getDependencies() {
            return dependencies;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // 收集继承和实现的接口
            n.getExtendedTypes().forEach(t -> dependencies.add(t.getNameAsString()));
            n.getImplementedTypes().forEach(t -> dependencies.add(t.getNameAsString()));
            
            // 收集字段类型
            n.getFields().forEach(field -> 
                field.getVariables().forEach(var -> 
                    dependencies.add(var.getType().asString())));
            
            // 收集方法参数和返回类型
            n.getMethods().forEach(method -> {
                dependencies.add(method.getType().asString());
                method.getParameters().forEach(param -> 
                    dependencies.add(param.getType().asString()));
            });
            
            super.visit(n, arg);
        }
    }
} 