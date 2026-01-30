package org.AI.panda.repository;

import org.AI.panda.model.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessageEntity, String> {
    List<ChatMessageEntity> findByUserIdAndSessionIdOrderByCreatedAtDesc(String userId, String sessionId, Pageable pageable);
    List<ChatMessageEntity> findByUserIdAndSessionIdOrderByCreatedAtAsc(String userId, String sessionId, Pageable pageable);
    long countByUserIdAndSessionId(String userId, String sessionId);
    void deleteByUserIdAndSessionIdAndIdIn(String userId, String sessionId, List<String> ids);
}
