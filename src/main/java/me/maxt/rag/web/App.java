package me.maxt.rag.web;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import me.maxt.rag.web.config.AppConfig;
import me.maxt.rag.web.controller.ChatController;
import me.maxt.rag.web.controller.DocumentController;
import me.maxt.rag.web.service.DocumentService;
import me.maxt.rag.web.service.EmbeddingStoreManager;
import me.maxt.rag.web.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        // Load configuration
        AppConfig config = AppConfig.load();
        log.info("Starting RAG Web Application on port {}", config.getPort());

        // Initialize services
        EmbeddingStoreManager storeManager = new EmbeddingStoreManager(config.getStoreFilePath());
        RAGService ragService = new RAGService(config, storeManager);
        DocumentService documentService = new DocumentService(storeManager, config.getChunkSize(), config.getChunkOverlap());

        // Initialize controllers
        ChatController chatController = new ChatController(ragService);
        DocumentController documentController = new DocumentController(documentService, storeManager);

        // Check if default document directory exists and auto-ingest
        java.io.File defaultDocDir = new java.io.File(config.getDocumentDir());
        if (defaultDocDir.exists() && defaultDocDir.isDirectory() && storeManager.getEntryCount() == 0) {
            log.info("Auto-ingesting documents from default directory: {}", config.getDocumentDir());
            documentService.ingestDirectory(config.getDocumentDir());
        }

        // Start Javalin
        Javalin app = Javalin.create(jc -> {
                    jc.staticFiles.add(staticFileConfig -> {
                        staticFileConfig.directory = "webapp";
                        staticFileConfig.location = Location.CLASSPATH;
                    });
                })
                .start(config.getPort());

        // Register API routes
        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));

        app.post("/api/chat", chatController::handleChat);

        app.post("/api/ingest", documentController::handleIngest);

        app.get("/api/documents", documentController::handleListDocuments);

        // Global exception handler
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled exception", e);
            ctx.status(500).json(Map.of("error", "Internal server error: " + e.getMessage()));
        });

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            app.stop();
        }));

        log.info("Application started at http://localhost:{}", config.getPort());
    }
}
