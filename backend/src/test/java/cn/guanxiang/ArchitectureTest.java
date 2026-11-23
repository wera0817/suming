package cn.guanxiang;

import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    private static final Path ROOT = Path.of("src/main/java/cn/guanxiang");

    @Test void sqlAndJdbcStayInsideMappers() throws Exception {
        var sql = Pattern.compile("\\b(SELECT\\s|INSERT\\s+INTO|UPDATE\\s+\\w+\\s+SET|DELETE\\s+FROM)");
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (file.getParent().getFileName().toString().equals("mapper")) continue;
                String source = Files.readString(file);
                assertFalse(source.contains("JdbcTemplate"), file.toString());
                assertFalse(sql.matcher(source).find(), file.toString());
            }
        }
    }
    @Test void controllersDependOnlyOnServices() throws Exception {
        try (var files = Files.list(ROOT.resolve("controller"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Class<?> type = Class.forName("cn.guanxiang.controller." + file.getFileName().toString().replace(".java", ""));
                for (var field : type.getDeclaredFields()) {
                    assertTrue(field.getType().getPackageName().equals("cn.guanxiang.service"), field.toString());
                    assertNotNull(field.getType().getAnnotation(Service.class), field.toString());
                }
            }
        }
    }
    @Test void mappersAreRepositoriesAndNeverDependOnUpperLayers() throws Exception {
        try (var files = Files.list(ROOT.resolve("mapper"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("cn.guanxiang.service"), file.toString());
                assertFalse(source.contains("cn.guanxiang.controller"), file.toString());
                Class<?> type = Class.forName("cn.guanxiang.mapper." + file.getFileName().toString().replace(".java", ""));
                assertNotNull(type.getAnnotation(Repository.class), file.toString());
            }
        }
    }
}
