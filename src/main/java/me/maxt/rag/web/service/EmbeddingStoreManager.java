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

public class EmbeddingStoreManager {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path storePath;
    private final InMemoryEmbeddingStore<TextSegment> embeddingStore;
    private final Map<String, StoredEntry> entries;

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

    public synchronized String add(Embedding embedding, TextSegment textSegment) {
        String id = UUID.randomUUID().toString();
        embeddingStore.add(id, embedding, textSegment);
        entries.put(id, new StoredEntry(embedding.vector(), textSegment.text(),
                textSegment.metadata().toMap()));
        persist();
        return id;
    }

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

    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        return embeddingStore.search(request);
    }

    public List<StoredEntry> listDocuments() {
        return new ArrayList<>(entries.values());
    }

    public int getEntryCount() {
        return entries.size();
    }

    public synchronized void removeAll() {
        entries.clear();
        // Create a fresh store since InMemoryEmbeddingStore doesn't have removeAll with clear semantics
        // We'll just add nothing and persist empty
        persist();
    }

    public InMemoryEmbeddingStore<TextSegment> getStore() {
        return embeddingStore;
    }

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

    public static class StoredEntry {
        public float[] embedding;
        public String text;
        public Map<String, Object> metadata;

        public StoredEntry() {}

        public StoredEntry(float[] embedding, String text, Map<String, Object> metadata) {
            this.embedding = embedding;
            this.text = text;
            this.metadata = metadata;
        }
    }
}
