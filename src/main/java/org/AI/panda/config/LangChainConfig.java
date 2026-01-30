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
            @Value("${spring.datasource.username}") String username,
            @Value("${spring.datasource.password}") String password) {
        
        // 假设本地运行，硬编码 host/port/db，实际生产应解析 jdbc url
    
        return PgVectorEmbeddingStore.builder()
                .host("localhost")
                .port(5432)
                .database("postgres")
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
