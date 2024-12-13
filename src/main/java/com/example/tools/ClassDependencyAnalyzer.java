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

        while (!queue.isEmpty()) {
            String currentClass = queue.poll();
            if (visited.contains(currentClass)) continue;
            visited.add(currentClass);

            // 找出只被当前类使用的类
            for (Map.Entry<String, Set<String>> entry : dependencyGraph.entrySet()) {
                String dependentClass = entry.getKey();
                if (classesToDelete.contains(dependentClass)) continue;

                boolean onlyUsedByDeletedClasses = true;
                for (Map.Entry<String, Set<String>> otherEntry : dependencyGraph.entrySet()) {
                    if (!classesToDelete.contains(otherEntry.getKey()) && 
                        otherEntry.getValue().contains(dependentClass)) {
                        onlyUsedByDeletedClasses = false;
                        break;
                    }
                }

                if (onlyUsedByDeletedClasses) {
                    classesToDelete.add(dependentClass);
                    queue.offer(dependentClass);
                }
            }
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

        public Set<String> getDependencies() {
            return dependencies;
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            // 收集类的依赖关系
            n.getExtendedTypes().forEach(t -> dependencies.add(t.getNameAsString()));
            n.getImplementedTypes().forEach(t -> dependencies.add(t.getNameAsString()));
            super.visit(n, arg);
        }
    }
} 