public class ClassDeleter {
    public static void main(String[] args) {
        try {
            String projectRoot = "/path/to/your/project/src/main/java";
            String classToDelete = "com.example.test.TestClass";
            
            ClassDependencyAnalyzer analyzer = new ClassDependencyAnalyzer();
            analyzer.analyzeAndDeleteClass(projectRoot, classToDelete);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
} 