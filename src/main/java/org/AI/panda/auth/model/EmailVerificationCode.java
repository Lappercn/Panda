package org.AI.panda.auth.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@Document(collection = "email_verification_codes")
public class EmailVerificationCode {
    public enum Purpose {
        REGISTER,
        LOGIN,
        RESET_PASSWORD
    }

    @Id
    private String id;

    @Indexed
    private String emailLower;

    @Indexed
    private Purpose purpose;

    private String codeHash;

    private boolean used;

    private Date createdAt;

    @Indexed(expireAfterSeconds = 0)
    private Date expiresAt;
}
