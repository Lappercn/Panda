package org.AI.panda.service;

import org.AI.panda.model.entity.ChatSessionEntity;
import org.AI.panda.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ChatSessionService {

    private final ChatSessionRepository chatSessionRepository;
    private final SecureRandom random = new SecureRandom();

    public ChatSessionService(ChatSessionRepository chatSessionRepository) {
        this.chatSessionRepository = chatSessionRepository;
    }

    public ChatSessionEntity createSession(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        String id = generateId();
        LocalDateTime now = LocalDateTime.now();

        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle("新对话");
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        session.setLastMessageAt(null);
        return chatSessionRepository.save(session);
    }

    public List<ChatSessionEntity> listSessions(String userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    public ChatSessionEntity getSession(String userId, String sessionId) {
        if (userId == null || userId.isBlank() || sessionId == null || sessionId.isBlank()) return null;
        return chatSessionRepository.findByIdAndUserId(sessionId, userId).orElse(null);
    }

    public void touchSession(String userId, String sessionId, String role, String content) {
        if (userId == null || userId.isBlank()) return;
        if (sessionId == null || sessionId.isBlank()) return;

        LocalDateTime now = LocalDateTime.now();
        Optional<ChatSessionEntity> opt = chatSessionRepository.findByIdAndUserId(sessionId, userId);
        ChatSessionEntity session = opt.orElseGet(() -> {
            ChatSessionEntity s = new ChatSessionEntity();
            s.setId(sessionId);
            s.setUserId(userId);
            s.setTitle("新对话");
            s.setCreatedAt(now);
            return s;
        });

        session.setUpdatedAt(now);
        session.setLastMessageAt(now);
        if ("user".equals(role)) {
            String t = deriveTitle(content);
            if (t != null && !t.isBlank() && (session.getTitle() == null || session.getTitle().isBlank() || "新对话".equals(session.getTitle()))) {
                session.setTitle(t);
            }
        }
        chatSessionRepository.save(session);
    }

    private String generateId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String deriveTitle(String content) {
        if (content == null) return null;
        String s = content.trim().replaceAll("\\s+", " ");
        if (s.isBlank()) return null;
        if (s.length() > 20) return s.substring(0, 20);
        return s;
    }
}
