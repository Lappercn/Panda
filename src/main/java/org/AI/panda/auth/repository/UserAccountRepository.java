package org.AI.panda.auth.repository;

import org.AI.panda.auth.model.UserAccount;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserAccountRepository extends MongoRepository<UserAccount, String> {
    Optional<UserAccount> findByEmailLower(String emailLower);
    boolean existsByEmailLower(String emailLower);
}
