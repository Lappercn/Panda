package org.AI.panda.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Service
public class EmbeddingService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    @Value("${panda.vector-store.table-name}")
    private String tableName;

    public EmbeddingService(EmbeddingStore<TextSegment> embeddingStore, 
                            EmbeddingModel embeddingModel,
                            JdbcTemplate jdbcTemplate) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 将文本转为向量并存入数据库 (支持长文本切片)
     */
    public void store(String content, String userId, String nodeId) {
        store(content, 800, 100, userId, nodeId);
    }

    public void store(String content, int maxSegmentSizeInChars, int maxOverlapSizeInChars, String userId, String nodeId) {
        // 1. 封装为 Document 对象
        Document document = Document.from(content, Metadata.from(java.util.Map.of(
                "source", "user-upload",
                "userId", userId,
                "nodeId", nodeId,
                "create_time", String.valueOf(System.currentTimeMillis())
        )));
        
        // 2. 文本切片 (Chunking)
        DocumentSplitter splitter = DocumentSplitters.recursive(maxSegmentSizeInChars, maxOverlapSizeInChars);
        List<TextSegment> segments = splitter.split(document);

        // 3. 批量生成向量
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        
        // 4. 存入向量数据库
        embeddingStore.addAll(embeddings, segments);
        
        System.out.println("成功存入 " + segments.size() + " 个切片片段 (User: " + userId + ")");
    }

    /**
     * 保持向后兼容的默认方法 (Deprecated)
     */
    public void store(String content) {
        store(content, "default-user", "unknown");
    }

    /**
     * 语义搜索 (带用户隔离)
     */
    public List<EmbeddingMatch<TextSegment>> search(String query, String userId) {
        return search(query, userId, "user-upload");
    }

    public List<EmbeddingMatch<TextSegment>> search(String query, String userId, String source) {
        // 1. 将 query 转为向量
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        
        // 2. 构建过滤条件
        Filter filter = metadataKey("userId").isEqualTo(userId)
                .and(metadataKey("source").isEqualTo(source));

        // 3. 搜索
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .filter(filter)
                .maxResults(5)
                .minScore(0.6) // 稍微提高一点门槛
                .build();
                
        EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
        return result.matches();
    }
    
    /**
     * 存储聊天记录向量
     */
    public void storeChat(String content, String userId, String sessionId, String msgId, String role) {
        Document document = Document.from(content, Metadata.from(java.util.Map.of(
                "source", "chat-history",
                "userId", userId,
                "sessionId", sessionId == null ? "" : sessionId,
                "msgId", msgId,
                "role", role,
                "create_time", String.valueOf(System.currentTimeMillis())
        )));
        
        TextSegment segment = TextSegment.from(content, document.metadata());
        Embedding embedding = embeddingModel.embed(segment).content();
        embeddingStore.add(embedding, segment);
    }
    
    /**
     * 语义搜索 (旧接口，不推荐)
     */
    public List<EmbeddingMatch<TextSegment>> search(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return embeddingStore.findRelevant(queryEmbedding, 5);
    }

    /**
     * 根据 nodeId 删除向量
     */
    public void deleteByNodeId(String nodeId) {
        // PGVector (metadata 是 JSONB)
        String sql = "DELETE FROM " + tableName + " WHERE metadata ->> 'nodeId' = ?";
        int deleted = jdbcTemplate.update(sql, nodeId);
        System.out.println("已删除 NodeId=" + nodeId + " 的向量记录，共 " + deleted + " 条");
    }

    /**
     * 清空向量库 (TRUNCATE TABLE)
     */
    public void clear() {
        String sql = "TRUNCATE TABLE " + tableName;
        jdbcTemplate.execute(sql);
        System.out.println("已清空向量库表: " + tableName);
    }
}
