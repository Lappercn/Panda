package org.AI.panda.auth.repository;

import org.AI.panda.auth.model.EmailVerificationCode;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

@Repository
public interface EmailVerificationCodeRepository extends MongoRepository<EmailVerificationCode, String> {
    Optional<EmailVerificationCode> findFirstByEmailLowerAndPurposeAndUsedOrderByCreatedAtDesc(
            String emailLower,
            EmailVerificationCode.Purpose purpose,
            boolean used
    );

    long countByEmailLowerAndPurposeAndCreatedAtAfter(String emailLower, EmailVerificationCode.Purpose purpose, Date after);
}
