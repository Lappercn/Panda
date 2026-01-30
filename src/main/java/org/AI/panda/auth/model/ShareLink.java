package org.AI.panda.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "share_links")
public class ShareLink {
    @Id
    private String id;

    @Indexed
    private String ownerUserId;

    @Indexed
    private String sessionId;

    @Indexed(unique = true)
    private String tokenHash;

    private Date createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;
}
