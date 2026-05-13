package me.maxt.rag.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import me.maxt.rag.web.service.DocumentService;
import me.maxt.rag.web.service.EmbeddingStoreManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocumentService documentService;
    private final EmbeddingStoreManager storeManager;

    public DocumentController(DocumentService documentService, EmbeddingStoreManager storeManager) {
        this.documentService = documentService;
        this.storeManager = storeManager;
    }

    public void handleIngest(Context ctx) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String directory = (String) body.get("directory");

            if (directory == null || directory.trim().isEmpty()) {
                ctx.status(400).json(Map.of("error", "Directory path is required"));
                return;
            }

            log.info("Ingesting documents from: {}", directory);
            DocumentService.IngestResult result = documentService.ingestDirectory(directory.trim());

            ctx.json(result);
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Ingest error", e);
            ctx.status(500).json(Map.of("error", "Failed to ingest documents: " + e.getMessage()));
        }
    }

    public void handleListDocuments(Context ctx) {
        try {
            List<EmbeddingStoreManager.StoredEntry> entries = storeManager.listDocuments();

            // Group by file name to produce document list
            Map<String, List<EmbeddingStoreManager.StoredEntry>> grouped = entries.stream()
                    .collect(Collectors.groupingBy(e -> {
                        String fileName = e.metadata != null ?
                                (String) e.metadata.get("file_name") : null;
                        return fileName != null ? fileName : "unknown";
                    }));

            List<Map<String, Object>> documents = new ArrayList<>();
            for (Map.Entry<String, List<EmbeddingStoreManager.StoredEntry>> group : grouped.entrySet()) {
                Map<String, Object> doc = new LinkedHashMap<>();
                doc.put("fileName", group.getKey());
                doc.put("segmentCount", group.getValue().size());

                // Get directory from metadata of first entry
                Map<String, Object> meta = group.getValue().get(0).metadata;
                String dir = meta != null ? (String) meta.get("absolute_directory_path") : null;
                doc.put("directory", dir != null ? dir : "");

                documents.add(doc);
            }

            ctx.json(documents);
        } catch (Exception e) {
            log.error("List documents error", e);
            ctx.status(500).json(Map.of("error", "Failed to list documents: " + e.getMessage()));
        }
    }
}
