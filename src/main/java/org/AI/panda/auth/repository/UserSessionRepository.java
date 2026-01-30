package org.AI.panda.auth.repository;

import org.AI.panda.auth.model.UserSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSessionRepository extends MongoRepository<UserSession, String> {
    Optional<UserSession> findByTokenHash(String tokenHash);
    void deleteByTokenHash(String tokenHash);
}
