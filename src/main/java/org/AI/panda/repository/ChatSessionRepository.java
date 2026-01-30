package org.AI.panda.repository;

import org.AI.panda.model.entity.ChatSessionEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSessionEntity, String> {
    List<ChatSessionEntity> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<ChatSessionEntity> findByIdAndUserId(String id, String userId);
}
