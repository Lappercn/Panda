package org.AI.panda.model;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.output.TokenUsage;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VolcengineMultimodalEmbeddingModel implements EmbeddingModel {

    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final RestClient restClient;

    public VolcengineMultimodalEmbeddingModel(String baseUrl, String apiKey, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        List<Embedding> embeddings = new ArrayList<>();
        int totalPromptTokens = 0;
        int totalTokens = 0;

        for (TextSegment segment : textSegments) {
            List<Map<String, String>> inputList = List.of(
                    Map.of("type", "text", "text", segment.text())
            );

            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "input", inputList
            );

            VolcengineResponse responseBody = restClient.post()
                    .uri("/api/v3/embeddings/multimodal")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(VolcengineResponse.class);

            if (responseBody == null) {
                throw new RuntimeException("Failed to get embeddings from Volcengine: Empty response");
            }

            if (responseBody.error != null) {
                throw new RuntimeException("Volcengine error: " + responseBody.error.message);
            }

            if (responseBody.data == null) {
                throw new RuntimeException("Failed to get embeddings from Volcengine: Empty data");
            }

            embeddings.add(new Embedding(responseBody.data.embedding));

            if (responseBody.usage != null) {
                totalPromptTokens += responseBody.usage.prompt_tokens;
                totalTokens += responseBody.usage.total_tokens;
            }
        }

        return Response.from(embeddings, new TokenUsage(totalPromptTokens, totalTokens));
    }

    private static class VolcengineResponse {
        public DataItem data;
        public Usage usage;
        public Error error;

        public static class DataItem {
            public int index;
            public float[] embedding;
        }

        public static class Usage {
            public int prompt_tokens;
            public int total_tokens;
        }

        public static class Error {
            public String message;
            public String type;
        }
    }
}
