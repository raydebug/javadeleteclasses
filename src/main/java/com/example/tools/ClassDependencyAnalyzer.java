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
        System.out.println("\n=== 开始扫描Java文件 ===");
        scanJavaFiles(new File(rootPath));
        System.out.println("找到的所有类: " + allClasses);
        
        // 2. 构建依赖图
        System.out.println("\n=== 构建依赖图 ===");
        buildDependencyGraph(rootPath);
        System.out.println("依赖关系图:");
        dependencyGraph.forEach((k, v) -> System.out.println(k + " -> " + v));
        
        // 3. 找出需要删除的类
        System.out.println("\n=== 分析需要删除的类 ===");
        findClassesToDelete(targetClassName);
        System.out.println("将要删除的类: " + classesToDelete);
        
        // 4. 执行删除操作
        System.out.println("\n=== 执行删除操作 ===");
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

        // 构建反向依赖图
        System.out.println("构建反向依赖图...");
        Map<String, Set<String>> reverseDependencies = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
            String className = entry.getKey();
            for (String dependency : entry.getValue()) {
                reverseDependencies.computeIfAbsent(dependency, k -> new HashSet<>())
                    .add(className);
            }
        }
        System.out.println("反向依赖关系图:");
        reverseDependencies.forEach((k, v) -> System.out.println(k + " 被以下类使用: " + v));

        // 找出目标类的所有依赖
        System.out.println("\n查找目标类 " + targetClassName + " 的所有依赖...");
        Set<String> allDependenciesOfTarget = new HashSet<>();
        findAllDependencies(targetClassName, allDependenciesOfTarget, new HashSet<>());
        System.out.println("目标类的所有依赖: " + allDependenciesOfTarget);

        // 检查每个依赖
        System.out.println("\n检查每个依赖是否只被目标类树使用...");
        for (String dependency : allDependenciesOfTarget) {
            Set<String> usedBy = reverseDependencies.getOrDefault(dependency, new HashSet<>());
            System.out.println("\n检查依赖: " + dependency);
            System.out.println("被以下类使用: " + usedBy);
            
            boolean onlyUsedByTargetTree = true;
            for (String user : usedBy) {
                if (!allDependenciesOfTarget.contains(user) && !user.equals(targetClassName)) {
                    System.out.println("发现外部使用者: " + user);
                    onlyUsedByTargetTree = false;
                    break;
                }
            }

            if (onlyUsedByTargetTree) {
                System.out.println("=> 标记为删除");
                classesToDelete.add(dependency);
            } else {
                System.out.println("=> 保留（被目标类树之外的类使用）");
            }
        }

        // 从删除列表中移除工具类
        classesToDelete.removeIf(className -> 
            className.startsWith("com.example.tools."));
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
        private String currentPackage = "";  // 添加当前包名

        @Override
        public void visit(CompilationUnit n, Void arg) {
            // 获取当前包名
            n.getPackageDeclaration().ifPresent(pkg -> 
                currentPackage = pkg.getNameAsString());
            super.visit(n, arg);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // 收集字段类型
            n.getFields().forEach(field -> {
                String typeName = field.getCommonType().asString();
                addDependency(typeName);
                
                // 检查字段初始化
                field.getVariables().forEach(var -> 
                    var.getInitializer().ifPresent(init -> {
                        // 检查 new 表达式
                        init.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class)
                            .forEach(expr -> addDependency(expr.getType().asString()));
                    }));
            });
            
            // 收集方法中的类型引用
            n.getMethods().forEach(method -> {
                // 返回类型
                addDependency(method.getType().asString());
                
                // 参数类型
                method.getParameters().forEach(param -> 
                    addDependency(param.getType().asString()));
                
                // 方法体中的 new 表达式
                method.getBody().ifPresent(body -> 
                    body.findAll(com.github.javaparser.ast.expr.ObjectCreationExpr.class)
                        .forEach(expr -> addDependency(expr.getType().asString())));
            });
            
            super.visit(n, arg);
        }

        private void addDependency(String typeName) {
            // 忽略基本类型和void
            if (typeName.equals("void") || isPrimitiveType(typeName)) {
                return;
            }

            // 忽略Java标准库类
            if (typeName.startsWith("java.") || typeName.startsWith("javax.")) {
                return;
            }

            // 如果是简单类名，添加当前包名
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