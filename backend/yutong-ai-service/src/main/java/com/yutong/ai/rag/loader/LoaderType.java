package com.yutong.ai.rag.loader;

/**
 * 装载器类型常量，对齐 V037 CHECK 约束 loader_type。
 */
public final class LoaderType {

    private LoaderType() {}

    public static final String PDF = "pdf";
    public static final String WORD = "word";
    public static final String EXCEL = "excel";
    public static final String CSV = "csv";
    public static final String MD = "md";
    public static final String TXT = "txt";
    /** 通用 json/code 等回退到文本 */
    public static final String JSON = "json";
    public static final String CODE = "code";

    /**
     * 将扩展名归一到 loader_type。
     */
    public static String fromExtension(String ext) {
        if (ext == null) {
            return TXT;
        }
        return switch (ext.toLowerCase()) {
            case "pdf" -> PDF;
            case "doc", "docx" -> WORD;
            case "xls", "xlsx" -> EXCEL;
            case "csv" -> CSV;
            case "md", "markdown" -> MD;
            case "txt", "log" -> TXT;
            case "json" -> JSON;
            // 代码文件
            case "java", "kt", "kts", "scala", "groovy",
                 "py", "pyw", "pyx",
                 "js", "jsx", "ts", "tsx", "mjs", "cjs",
                 "go", "rs",
                 "c", "h", "cpp", "hpp", "cc", "cxx",
                 "cs",
                 "sql",
                 "sh", "bash", "zsh", "ps1", "bat", "cmd",
                 "html", "htm", "css", "scss", "less", "vue", "svelte",
                 "xml", "yaml", "yml", "toml", "ini", "properties", "env",
                 "rb", "php", "swift", "dart", "lua", "r", "pl", "ex", "exs" -> CODE;
            default -> TXT;
        };
    }

    /**
     * 将显式 loaderType 参数归一，未知回退 TXT。
     */
    public static String normalize(String loaderType) {
        if (loaderType == null || loaderType.isBlank()) {
            return null;
        }
        String t = loaderType.trim().toLowerCase();
        return switch (t) {
            case "pdf", "word", "excel", "csv", "md", "txt", "json", "code" -> t;
            case "docx", "doc" -> WORD;
            case "xlsx", "xls" -> EXCEL;
            case "markdown" -> MD;
            default -> null;
        };
    }
}
