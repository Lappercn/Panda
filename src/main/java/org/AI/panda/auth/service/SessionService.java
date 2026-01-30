package org.AI.panda.auth.service;

import org.AI.panda.auth.model.UserSession;
import org.AI.panda.auth.repository.UserSessionRepository;
import org.AI.panda.auth.util.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class SessionService {
    private final UserSessionRepository sessionRepository;

    @Value("${panda.auth.session.ttl-days:30}")
    private int ttlDays;

    private final SecureRandom random = new SecureRandom();

    public SessionService(UserSessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public String createSession(String userId, String ip, String userAgent) {
        String token = generateToken();
        String tokenHash = HashUtil.sha256Hex(token);

        Date now = new Date();
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setTokenHash(tokenHash);
        session.setCreatedAt(now);
        session.setExpiresAt(new Date(now.getTime() + Duration.ofDays(ttlDays).toMillis()));
        session.setIp(ip);
        session.setUserAgent(userAgent);

        sessionRepository.save(session);
        return token;
    }

    public String resolveUserId(String token) {
        if (token == null || token.isBlank()) return null;
        String tokenHash = HashUtil.sha256Hex(token);
        Optional<UserSession> sessionOpt = sessionRepository.findByTokenHash(tokenHash);
        if (sessionOpt.isEmpty()) return null;
        UserSession session = sessionOpt.get();
        if (session.getExpiresAt() == null || session.getExpiresAt().before(new Date())) return null;
        return session.getUserId();
    }

    public void deleteSession(String token) {
        if (token == null || token.isBlank()) return;
        String tokenHash = HashUtil.sha256Hex(token);
        sessionRepository.deleteByTokenHash(tokenHash);
    }

    public int getTtlDays() {
        return ttlDays;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
