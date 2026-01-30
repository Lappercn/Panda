package org.AI.panda.model.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "chat_messages")
public class ChatMessageEntity {
    @Id
    private String id;
    
    @Indexed
    private String userId;

    @Indexed
    private String sessionId;
    
    private String role; // user, assistant, system
    private String content;
    private LocalDateTime createdAt;
    
    // Long-term memory
    private boolean isVectorized;
}
