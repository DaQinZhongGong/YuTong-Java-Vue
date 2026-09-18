package com.yutong.ai.rag.loader;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeLoader 单元测试。
 */
class CodeLoaderTest {

    private final CodeLoader loader = new CodeLoader();

    @Test
    void loaderType_isCode() {
        assertEquals("code", loader.getLoaderType());
    }

    @Test
    void supports_commonCodeExtensions() {
        assertTrue(loader.supports("Main.java"));
        assertTrue(loader.supports("app.py"));
        assertTrue(loader.supports("index.ts"));
        assertTrue(loader.supports("style.css"));
        assertTrue(loader.supports("schema.sql"));
        assertTrue(loader.supports("deploy.sh"));
        assertTrue(loader.supports("config.yaml"));
        assertFalse(loader.supports("doc.pdf"));
        assertFalse(loader.supports("data.xlsx"));
    }

    @Test
    void extract_javaCode() throws Exception {
        String code = """
                package com.example;
                
                /**
                 * 示例类
                 */
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello World");
                    }
                }
                """;
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        String result = loader.extract(is, "Hello.java");
        assertTrue(result.contains("public class Hello"));
        assertTrue(result.contains("Hello World"));
        assertTrue(result.contains("示例类"));
    }

    @Test
    void extract_pythonCode() throws Exception {
        String code = """
                def greet(name):
                    # 打印问候
                    print(f"Hello, {name}!")
                
                if __name__ == "__main__":
                    greet("World")
                """;
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        String result = loader.extract(is, "greet.py");
        assertTrue(result.contains("def greet"));
        assertTrue(result.contains("打印问候"));
    }

    @Test
    void extract_emptyFile_returnsEmpty() throws Exception {
        InputStream is = new ByteArrayInputStream(new byte[0]);
        String result = loader.extract(is, "empty.java");
        assertEquals("", result);
    }

    @Test
    void extract_preservesIndentation() throws Exception {
        String code = "def foo():\n    if True:\n        return 1\n";
        InputStream is = new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8));
        String result = loader.extract(is, "test.py");
        assertTrue(result.contains("    if True:"));
        assertTrue(result.contains("        return 1"));
    }
}
