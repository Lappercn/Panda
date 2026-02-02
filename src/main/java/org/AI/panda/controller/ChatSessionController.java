package org.AI.panda.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.AI.panda.auth.service.ShareLinkService;
import org.AI.panda.auth.web.UserIdResolver;
import org.AI.panda.common.Result;
import org.AI.panda.model.entity.ChatMessageEntity;
import org.AI.panda.model.entity.ChatSessionEntity;
import org.AI.panda.repository.ChatMessageRepository;
import org.AI.panda.service.ChatSessionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatSessionController {

    private final ChatSessionService chatSessionService;
    private final ChatMessageRepository chatMessageRepository;
    private final ShareLinkService shareLinkService;

    public ChatSessionController(ChatSessionService chatSessionService,
                                 ChatMessageRepository chatMessageRepository,
                                 ShareLinkService shareLinkService) {
        this.chatSessionService = chatSessionService;
        this.chatMessageRepository = chatMessageRepository;
        this.shareLinkService = shareLinkService;
    }

    @GetMapping("/sessions")
    public Result<List<ChatSessionEntity>> listSessions(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                                        HttpServletRequest request) {
        String uid = UserIdResolver.resolve(request, userId);
        if (UserIdResolver.isVisitor(request)) {
            String sid = UserIdResolver.resolveSharedSessionId(request);
            if (sid == null || sid.isBlank()) return Result.success(List.of());
            ChatSessionEntity one = chatSessionService.getSession(uid, sid);
            if (one == null) return Result.success(List.of());
            return Result.success(List.of(one));
        }
        return Result.success(chatSessionService.listSessions(uid));
    }

    @PostMapping("/sessions")
    public Result<ChatSessionEntity> createSession(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                                   HttpServletRequest request) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        String uid = UserIdResolver.resolve(request, userId);
        return Result.success(chatSessionService.createSession(uid));
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageEntity>> getMessages(@PathVariable String sessionId,
                                                       @RequestParam(defaultValue = "50") int limit,
                                                       @RequestHeader(value = "X-User-ID", required = false) String userId,
                                                       HttpServletRequest request) {
        String uid = UserIdResolver.resolve(request, userId);
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        if (UserIdResolver.isVisitor(request)) {
            String shared = UserIdResolver.resolveSharedSessionId(request);
            if (shared == null || !shared.equals(sid)) {
                return Result.error(403, "只读分享链接禁止访问该会话");
            }
        }
        int safeLimit = Math.max(1, Math.min(limit, 200));
        List<ChatMessageEntity> msgs = chatMessageRepository.findByUserIdAndSessionIdOrderByCreatedAtAsc(uid, sid, PageRequest.of(0, safeLimit));
        return Result.success(msgs);
    }

    @PostMapping("/sessions/{sessionId}/share")
    public Result<Map<String, String>> createShare(@PathVariable String sessionId,
                                                   @RequestParam(required = false) Integer ttlDays,
                                                   @RequestHeader(value = "X-User-ID", required = false) String userId,
                                                   HttpServletRequest request) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        String uid = UserIdResolver.resolve(request, userId);
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        ChatSessionEntity session = chatSessionService.getSession(uid, sid);
        if (session == null) {
            return Result.error(404, "会话不存在");
        }
        String token = shareLinkService.createChatShare(uid, sid, ttlDays);
        return Result.success(Map.of("shareToken", token));
    }

    @GetMapping("/share/resolve")
    public Result<Map<String, Object>> resolveShare(@RequestParam String shareToken) {
        ShareLinkService.ResolvedShare resolved = shareLinkService.resolve(shareToken);
        if (resolved == null) {
            return Result.error(404, "分享链接无效或已过期");
        }
        ChatSessionEntity session = chatSessionService.getSession(resolved.ownerUserId(), resolved.sessionId());
        String title = session != null ? session.getTitle() : "";
        return Result.success(Map.of(
                "ownerUserId", resolved.ownerUserId(),
                "sessionId", resolved.sessionId(),
                "title", title
        ));
    }
}
