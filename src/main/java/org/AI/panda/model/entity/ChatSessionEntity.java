package org.AI.panda.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "chat_sessions")
public class ChatSessionEntity {
    @Id
    private String id;

    @Indexed
    private String userId;

    private String title;

    private LocalDateTime createdAt;

    @Indexed
    private LocalDateTime updatedAt;

    private LocalDateTime lastMessageAt;
}
