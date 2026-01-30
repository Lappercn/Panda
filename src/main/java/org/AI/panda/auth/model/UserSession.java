package org.AI.panda.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "user_sessions")
public class UserSession {
    @Id
    private String id;

    @Indexed
    private String userId;

    @Indexed(unique = true)
    private String tokenHash;

    private Date createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;

    private String ip;

    private String userAgent;
}
