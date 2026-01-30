package org.AI.panda.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "user_accounts")
public class UserAccount {
    @Id
    private String id;

    @Indexed(unique = true)
    private String emailLower;

    private String email;

    private String passwordHash;

    private Date createdAt;

    private Date updatedAt;

    private Date lastLoginAt;
}
