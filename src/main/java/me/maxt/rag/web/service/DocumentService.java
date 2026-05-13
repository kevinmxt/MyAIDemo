package me.maxt.rag.web.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DocumentService {

    private final EmbeddingStoreManager storeManager;
    private final EmbeddingModel embeddingModel;
    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentService(EmbeddingStoreManager storeManager, int chunkSize, int chunkOverlap) {
        this.storeManager = storeManager;
        this.embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public IngestResult ingestDirectory(String directoryPath) {
        Path dir = Paths.get(directoryPath);
        File dirFile = dir.toFile();

        if (!dirFile.exists() || !dirFile.isDirectory()) {
            throw new IllegalArgumentException("Directory not found: " + directoryPath);
        }

        // Find all .txt files
        File[] txtFiles = dirFile.listFiles((d, name) -> name.endsWith(".txt"));
        if (txtFiles == null || txtFiles.length == 0) {
            return new IngestResult(0, 0, "No .txt files found in directory: " + directoryPath);
        }

        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
        TextDocumentParser parser = new TextDocumentParser();

        int filesProcessed = 0;
        int totalSegments = 0;

        for (File txtFile : txtFiles) {
            try {
                Document document = FileSystemDocumentLoader.loadDocument(txtFile.toPath(), parser);
                List<TextSegment> segments = splitter.split(document);
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                storeManager.addAll(embeddings, segments);

                filesProcessed++;
                totalSegments += segments.size();
            } catch (Exception e) {
                System.err.println("Failed to process file: " + txtFile.getName() + " - " + e.getMessage());
            }
        }

        return new IngestResult(filesProcessed, totalSegments,
                "Successfully processed " + filesProcessed + " files, created " + totalSegments + " segments.");
    }

    public static class IngestResult {
        public boolean success;
        public int filesProcessed;
        public int segmentsCreated;
        public String message;

        public IngestResult(int filesProcessed, int segmentsCreated, String message) {
            this.success = filesProcessed > 0;
            this.filesProcessed = filesProcessed;
            this.segmentsCreated = segmentsCreated;
            this.message = message;
        }
    }
}
