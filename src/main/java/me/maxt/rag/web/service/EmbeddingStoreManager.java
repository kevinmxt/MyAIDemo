package me.maxt.rag.web.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 嵌入向量持久化存储管理器，封装内存向量存储并提供 JSON 文件级别的持久化。
 *
 * <p>内部使用 {@link InMemoryEmbeddingStore} 作为向量检索引擎，同时将向量数据和元数据
 * 以 JSON 格式原子写入磁盘文件。启动时自动从文件恢复已有的向量数据。</p>
 *
 * <p>线程安全：所有写操作（add/addAll/removeAll）均使用 {@code synchronized} 方法保护。</p>
 *
 * @since 1.0
 */
public class EmbeddingStoreManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 持久化文件路径 */
    private final Path storePath;

    /** 内存向量存储引擎 */
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;

    /** 向量条目映射（ID → 条目），用于持久化和恢复 */
    private final Map<String, StoredEntry> entries;

    /**
     * 创建存储管理器，如持久化文件已存在则自动加载。
     *
     * @param storeFilePath 向量存储持久化文件的路径（JSON 格式）
     */
    public EmbeddingStoreManager(String storeFilePath) {
        this.storePath = Paths.get(storeFilePath);
        this.embeddingStore = new InMemoryEmbeddingStore<>();
        this.entries = new ConcurrentHashMap<>();

        // Try to load existing store
        File file = storePath.toFile();
        if (file.exists()) {
            try {
                loadFromFile();
                System.out.println("Loaded embedding store from: " + storePath + " (" + entries.size() + " entries)");
            } catch (IOException e) {
                System.err.println("Failed to load embedding store: " + e.getMessage() + ". Starting fresh.");
            }
        }
    }

    /**
     * 添加单个嵌入向量条目，立即持久化。
     *
     * @param embedding   嵌入向量
     * @param textSegment 文本片段
     * @return 生成的条目唯一标识符
     */
    public synchronized String add(Embedding embedding, TextSegment textSegment) {
        String id = UUID.randomUUID().toString();
        embeddingStore.add(id, embedding, textSegment);
        entries.put(id, new StoredEntry(embedding.vector(), textSegment.text(),
                textSegment.metadata().toMap()));
        persist();
        return id;
    }

    /**
     * 批量添加嵌入向量条目，立即持久化。
     *
     * @param embeddings   嵌入向量列表
     * @param textSegments 文本片段列表（与 embedding 一一对应）
     * @return 生成的条目 ID 列表
     */
    public synchronized List<String> addAll(List<Embedding> embeddings, List<TextSegment> textSegments) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < embeddings.size(); i++) {
            ids.add(UUID.randomUUID().toString());
        }
        embeddingStore.addAll(ids, embeddings, textSegments);
        for (int i = 0; i < embeddings.size(); i++) {
            TextSegment seg = textSegments.get(i);
            entries.put(ids.get(i), new StoredEntry(embeddings.get(i).vector(), seg.text(),
                    seg.metadata().toMap()));
        }
        persist();
        return ids;
    }

    /**
     * 执行向量相似度搜索。
     *
     * @param request 搜索请求，包含查询向量、最大返回数和最低分数阈值
     * @return 搜索结果，包含匹配的文本片段和相似度分数
     */
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return embeddingStore.search(request);
    }

    /**
     * 列出所有已存储的文档条目。
     *
     * @return 所有条目的副本列表
     */
    public List<StoredEntry> listDocuments() {
        return new ArrayList<>(entries.values());
    }

    /**
     * 获取已存储条目总数。
     *
     * @return 条目数量
     */
    public int getEntryCount() {
        return entries.size();
    }

    /**
     * 清空所有存储的条目并持久化空状态。
     */
    public synchronized void removeAll() {
        entries.clear();
        persist();
    }

    /**
     * 获取底层的 {@link InMemoryEmbeddingStore} 实例，供 LangChain4j 的 RAG 组件使用。
     *
     * @return 内存向量存储实例
     */
    public InMemoryEmbeddingStore<TextSegment> getStore() {
        return embeddingStore;
    }

    /**
     * 原子写入持久化文件：先写入临时文件，再重命名替换。
     */
    private void persist() {
        try {
            // Ensure parent directory exists
            Files.createDirectories(storePath.getParent());

            // Atomic write: write to temp file first, then rename
            Path tempPath = Paths.get(storePath.toString() + ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(tempPath.toFile(), entries);
            Files.move(tempPath, storePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Failed to persist embedding store: " + e.getMessage());
        }
    }

    /**
     * 从 JSON 文件加载向量数据并重建内存索引。
     *
     * @throws IOException 如果文件读取或解析失败
     */
    private void loadFromFile() throws IOException {
        Map<String, StoredEntry> loaded = MAPPER.readValue(storePath.toFile(),
                new TypeReference<Map<String, StoredEntry>>() {});
        entries.putAll(loaded);

        // Rebuild InMemoryEmbeddingStore from loaded entries
        List<String> ids = new ArrayList<>();
        List<Embedding> embeddings = new ArrayList<>();
        List<TextSegment> segments = new ArrayList<>();

        for (Map.Entry<String, StoredEntry> entry : entries.entrySet()) {
            ids.add(entry.getKey());
            embeddings.add(Embedding.from(entry.getValue().embedding));
            segments.add(TextSegment.from(entry.getValue().text,
                    dev.langchain4j.data.document.Metadata.from(entry.getValue().metadata)));
        }

        if (!ids.isEmpty()) {
            embeddingStore.addAll(ids, embeddings, segments);
        }
    }

    /**
     * 持久化存储条目 DTO，包含向量、文本和元数据。
     */
    public static class StoredEntry {
        /** 嵌入向量数组 */
        public float[] embedding;
        /** 文本内容 */
        public String text;
        /** 元数据（如文件名、目录路径等） */
        public Map<String, Object> metadata;

        public StoredEntry() {}

        public StoredEntry(float[] embedding, String text, Map<String, Object> metadata) {
            this.embedding = embedding;
            this.text = text;
            this.metadata = metadata;
        }
    }
}
