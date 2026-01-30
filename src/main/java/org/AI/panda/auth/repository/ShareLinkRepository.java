package org.AI.panda.auth.repository;

import org.AI.panda.auth.model.ShareLink;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ShareLinkRepository extends MongoRepository<ShareLink, String> {
    Optional<ShareLink> findByTokenHash(String tokenHash);
}
