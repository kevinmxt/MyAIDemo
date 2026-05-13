package me.maxt.rag.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import me.maxt.rag.web.service.RAGService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RAGService ragService;

    public ChatController(RAGService ragService) {
        this.ragService = ragService;
    }

    public void handleChat(Context ctx) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = ctx.bodyAsClass(Map.class);
            String query = (String) body.get("query");

            if (query == null || query.trim().isEmpty()) {
                ctx.status(400).json(Map.of("error", "Query is required"));
                return;
            }

            log.info("Chat query: {}", query);
            RAGService.AnswerWithSources result = ragService.answerWithSources(query.trim());

            ctx.json(result);
        } catch (Exception e) {
            log.error("Chat error", e);
            ctx.status(500).json(Map.of("error", "Failed to process query: " + e.getMessage()));
        }
    }
}
