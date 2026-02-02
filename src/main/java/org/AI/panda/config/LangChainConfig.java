package org.AI.panda.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.AI.panda.model.VolcengineMultimodalEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import java.time.Duration;
import java.net.URI;

@Configuration
public class LangChainConfig {

    @Bean
    @Primary
    public ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature}") Double temperature) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .logRequests(false)
                .logResponses(false)
                .timeout(Duration.ofSeconds(300))
                .listeners(new java.util.ArrayList<>()) // Ensure mutable empty list to avoid any immutable list issues
                .build();
    }

    @Bean
    @Primary
    public StreamingChatLanguageModel streamingChatLanguageModel(
            @Value("${langchain4j.open-ai.streaming-chat-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.streaming-chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.streaming-chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.streaming-chat-model.temperature}") Double temperature) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .logRequests(false)
                .logResponses(false)
                .timeout(Duration.ofSeconds(300))
                .listeners(new java.util.ArrayList<>()) // Ensure mutable empty list
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(
            @Value("${panda.vector-store.table-name}") String tableName,
            @Value("${panda.vector-store.dimension}") int dimension,
            @Value("${spring.datasource.url}") String jdbcUrl,
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {

        String host = "localhost";
        int port = 5432;
        String database = "postgres";
        try {
            String uriString = jdbcUrl != null && jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
            if (uriString != null) {
                URI uri = URI.create(uriString);
                if (uri.getHost() != null && !uri.getHost().isBlank()) host = uri.getHost();
                if (uri.getPort() > 0) port = uri.getPort();
                String path = uri.getPath();
                if (path != null && path.length() > 1) database = path.substring(1);
            }
        } catch (Exception ignored) {
        }

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(username)
                .password(password)
                .table(tableName)
                .dimension(dimension)
                .createTable(true)
                .build();
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(
            @Value("${langchain4j.open-ai.embedding-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.embedding-model.model-name}") String modelName) {
        
        // 使用自定义的 Multimodal Embedding Model
        // 显式指定 Host，避免与 application.yml 中的 /api/v3 后缀冲突
        String host = "https://ark.cn-beijing.volces.com";
        return new VolcengineMultimodalEmbeddingModel(host, apiKey, modelName);
    }
}
