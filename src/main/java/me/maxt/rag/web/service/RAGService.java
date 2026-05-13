package me.maxt.rag.web.service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.bgesmallenv15q.BgeSmallEnV15QuantizedEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import me.maxt.rag.web.config.AppConfig;
import shared.Assistant;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RAGService {

    private final AppConfig config;
    private final EmbeddingStoreManager storeManager;
    private final EmbeddingModel embeddingModel;
    private final ChatModel chatModel;
    private final ContentRetriever contentRetriever;
    private final Assistant assistant;

    public RAGService(AppConfig config, EmbeddingStoreManager storeManager) {
        this.config = config;
        this.storeManager = storeManager;
        this.embeddingModel = new BgeSmallEnV15QuantizedEmbeddingModel();

        this.chatModel = OpenAiChatModel.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .temperature(config.getTemperature())
                .maxTokens(config.getMaxTokens())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .build();

        this.contentRetriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(storeManager.getStore())
                .embeddingModel(embeddingModel)
                .maxResults(config.getMaxResults())
                .minScore(config.getMinScore())
                .build();

        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .contentRetriever(contentRetriever)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(config.getMemorySize()))
                .build();
    }

    public String answer(String query) {
        return assistant.answer(query);
    }

    public AnswerWithSources answerWithSources(String query) {
        // Retrieve relevant sources manually
        List<Source> sources = new ArrayList<>();

        Embedding queryEmbedding = embeddingModel.embed(query).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(config.getMaxResults())
                .minScore(config.getMinScore())
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = storeManager.search(searchRequest);

        for (EmbeddingMatch<TextSegment> match : searchResult.matches()) {
            String fileName = match.embedded().metadata().getString("absolute_directory_path");
            if (fileName == null) {
                fileName = match.embedded().metadata().getString("file_name");
            } else {
                fileName = fileName + "/" + match.embedded().metadata().getString("file_name");
            }
            if (fileName == null) {
                fileName = "unknown";
            }
            sources.add(new Source(fileName, match.embedded().text(), match.score()));
        }

        // Get answer from assistant
        String answer = assistant.answer(query);

        return new AnswerWithSources(answer, sources);
    }

    public static class AnswerWithSources {
        public String answer;
        public List<Source> sources;

        public AnswerWithSources(String answer, List<Source> sources) {
            this.answer = answer;
            this.sources = sources;
        }
    }

    public static class Source {
        public String fileName;
        public String text;
        public double score;

        public Source(String fileName, String text, double score) {
            this.fileName = fileName;
            this.text = text;
            this.score = score;
        }
    }
}
