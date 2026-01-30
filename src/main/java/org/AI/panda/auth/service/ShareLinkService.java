package org.AI.panda.auth.service;

import org.AI.panda.auth.model.ShareLink;
import org.AI.panda.auth.repository.ShareLinkRepository;
import org.AI.panda.auth.util.HashUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class ShareLinkService {

    public record ResolvedShare(String ownerUserId, String sessionId) {
    }

    private final ShareLinkRepository shareLinkRepository;

    @Value("${panda.share.ttl-days:30}")
    private int ttlDays;

    private final SecureRandom random = new SecureRandom();

    public ShareLinkService(ShareLinkRepository shareLinkRepository) {
        this.shareLinkRepository = shareLinkRepository;
    }

    public String createChatShare(String ownerUserId, String sessionId) {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("ownerUserId 不能为空");
        }
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }

        String token = generateToken();
        String tokenHash = HashUtil.sha256Hex(token);

        Date now = new Date();
        ShareLink link = new ShareLink();
        link.setOwnerUserId(ownerUserId);
        link.setSessionId(sessionId);
        link.setTokenHash(tokenHash);
        link.setCreatedAt(now);
        link.setExpiresAt(new Date(now.getTime() + Duration.ofDays(ttlDays).toMillis()));

        shareLinkRepository.save(link);
        return token;
    }

    public ResolvedShare resolve(String token) {
        if (token == null || token.isBlank()) return null;
        String tokenHash = HashUtil.sha256Hex(token);
        Optional<ShareLink> opt = shareLinkRepository.findByTokenHash(tokenHash);
        if (opt.isEmpty()) return null;
        ShareLink link = opt.get();
        if (link.getExpiresAt() != null && link.getExpiresAt().before(new Date())) return null;
        if (link.getOwnerUserId() == null || link.getOwnerUserId().isBlank()) return null;
        if (link.getSessionId() == null || link.getSessionId().isBlank()) return null;
        return new ResolvedShare(link.getOwnerUserId(), link.getSessionId());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
