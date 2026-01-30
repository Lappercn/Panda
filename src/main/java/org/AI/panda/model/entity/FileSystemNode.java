package org.AI.panda.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "file_system_nodes")
@CompoundIndex(def = "{'userId': 1, 'parentId': 1, 'name': 1}", unique = true) // 同一目录下不能同名
public class FileSystemNode {

    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed
    private String parentId; // "0" or null for root

    private String name;

    private NodeType type;

    // File specific fields
    private String minioObjectName; // MinIO 中的存储路径/ID
    private Long size;
    private String contentType;

    // RAG related
    private boolean isVectorized;
    private ProcessingStatus processingStatus; // AI 处理状态
    private String failReason; // 失败原因
    private List<String> vectorIds; // 关联的向量库 ID
    private String ocrResultPath; // MinIO path to the OCR JSON result

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum NodeType {
        FILE,
        DIRECTORY
    }

    public enum ProcessingStatus {
        PENDING,    // 等待处理
        PROCESSING, // 处理中 (OCR/Vectorizing)
        COMPLETED,  // 完成
        FAILED      // 失败
    }
}
