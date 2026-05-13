package me.maxt.rag.web.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class AppConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // LLM
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private String systemPrompt;
    private double temperature;
    private int maxTokens;
    private int timeoutSeconds;

    // Retrieval
    private int maxResults;
    private double minScore;

    // Document
    private String documentDir;
    private int chunkSize;
    private int chunkOverlap;

    // Chat
    private int memorySize;

    // Server
    private int port;

    // Store
    private String storeFilePath;

    public AppConfig() {
        // Set defaults
        this.apiKey = "demo";
        this.baseUrl = "https://api.deepseek.com";
        this.modelName = "deepseek-v4-flash";
        this.systemPrompt = "你是一个基于本地知识库的智能助手，请根据提供的文档内容回答用户问题。如果文档中没有相关信息，请如实告知。";
        this.temperature = 0.7;
        this.maxTokens = 4096;
        this.timeoutSeconds = 120;
        this.maxResults = 3;
        this.minScore = 0.5;
        this.documentDir = "./documents";
        this.chunkSize = 300;
        this.chunkOverlap = 0;
        this.memorySize = 10;
        this.port = 8080;
        this.storeFilePath = "./data/embedding-store.json";
    }

    public static AppConfig load() {
        AppConfig config = new AppConfig();

        // 1. Try loading config.json from working directory
        File configFile = new File(System.getProperty("user.dir"), "config.json");
        if (configFile.exists()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> fileConfig = MAPPER.readValue(configFile, Map.class);
                applyFileConfig(config, fileConfig);
            } catch (IOException e) {
                System.err.println("Warning: Failed to parse config.json, using defaults. " + e.getMessage());
            }
        }

        // 2. Apply environment variable overrides
        applyEnvOverrides(config);

        return config;
    }

    @SuppressWarnings("unchecked")
    private static void applyFileConfig(AppConfig config, Map<String, Object> fileConfig) {
        Map<String, Object> llm = (Map<String, Object>) fileConfig.get("llm");
        if (llm != null) {
            config.apiKey = getString(llm, "apiKey", config.apiKey);
            config.baseUrl = getString(llm, "baseUrl", config.baseUrl);
            config.modelName = getString(llm, "modelName", config.modelName);
            config.systemPrompt = getString(llm, "systemPrompt", config.systemPrompt);
            config.temperature = getDouble(llm, "temperature", config.temperature);
            config.maxTokens = getInt(llm, "maxTokens", config.maxTokens);
            config.timeoutSeconds = getInt(llm, "timeoutSeconds", config.timeoutSeconds);
        }

        Map<String, Object> retrieval = (Map<String, Object>) fileConfig.get("retrieval");
        if (retrieval != null) {
            config.maxResults = getInt(retrieval, "maxResults", config.maxResults);
            config.minScore = getDouble(retrieval, "minScore", config.minScore);
        }

        Map<String, Object> document = (Map<String, Object>) fileConfig.get("document");
        if (document != null) {
            config.documentDir = getString(document, "dir", config.documentDir);
            config.chunkSize = getInt(document, "chunkSize", config.chunkSize);
            config.chunkOverlap = getInt(document, "chunkOverlap", config.chunkOverlap);
        }

        Map<String, Object> chat = (Map<String, Object>) fileConfig.get("chat");
        if (chat != null) {
            config.memorySize = getInt(chat, "memorySize", config.memorySize);
        }

        Map<String, Object> server = (Map<String, Object>) fileConfig.get("server");
        if (server != null) {
            config.port = getInt(server, "port", config.port);
        }

        Map<String, Object> store = (Map<String, Object>) fileConfig.get("store");
        if (store != null) {
            config.storeFilePath = getString(store, "filePath", config.storeFilePath);
        }
    }

    private static void applyEnvOverrides(AppConfig config) {
        config.apiKey = env("RAG_LLM_API_KEY", config.apiKey);
        config.baseUrl = env("RAG_LLM_BASE_URL", config.baseUrl);
        config.modelName = env("RAG_LLM_MODEL_NAME", config.modelName);
        config.systemPrompt = env("RAG_LLM_SYSTEM_PROMPT", config.systemPrompt);
        config.temperature = envDouble("RAG_LLM_TEMPERATURE", config.temperature);
        config.maxTokens = envInt("RAG_LLM_MAX_TOKENS", config.maxTokens);
        config.timeoutSeconds = envInt("RAG_LLM_TIMEOUT", config.timeoutSeconds);
        config.maxResults = envInt("RAG_RETRIEVAL_MAX_RESULTS", config.maxResults);
        config.minScore = envDouble("RAG_RETRIEVAL_MIN_SCORE", config.minScore);
        config.chunkSize = envInt("RAG_CHUNK_SIZE", config.chunkSize);
        config.chunkOverlap = envInt("RAG_CHUNK_OVERLAP", config.chunkOverlap);
        config.memorySize = envInt("RAG_CHAT_MEMORY_SIZE", config.memorySize);
        config.port = envInt("RAG_SERVER_PORT", config.port);
        config.documentDir = env("RAG_DOCUMENT_DIR", config.documentDir);
        config.storeFilePath = env("RAG_STORE_PATH", config.storeFilePath);
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return (val instanceof String) ? (String) val : defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    private static double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Double) return (Double) val;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }

    private static String env(String name, String defaultVal) {
        String val = System.getenv(name);
        return (val != null && !val.isEmpty()) ? val : defaultVal;
    }

    private static int envInt(String name, int defaultVal) {
        String val = System.getenv(name);
        if (val != null && !val.isEmpty()) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    private static double envDouble(String name, double defaultVal) {
        String val = System.getenv(name);
        if (val != null && !val.isEmpty()) {
            try { return Double.parseDouble(val); } catch (NumberFormatException ignored) {}
        }
        return defaultVal;
    }

    // Getters
    public String getApiKey() { return apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public String getModelName() { return modelName; }
    public String getSystemPrompt() { return systemPrompt; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxResults() { return maxResults; }
    public double getMinScore() { return minScore; }
    public String getDocumentDir() { return documentDir; }
    public int getChunkSize() { return chunkSize; }
    public int getChunkOverlap() { return chunkOverlap; }
    public int getMemorySize() { return memorySize; }
    public int getPort() { return port; }
    public String getStoreFilePath() { return storeFilePath; }
}
