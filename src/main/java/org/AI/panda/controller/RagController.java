package org.AI.panda.controller;

import org.AI.panda.service.RagService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.AI.panda.auth.web.UserIdResolver;

@RestController
@RequestMapping("/ai/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    /**
     * RAG 问答接口
     * 访问: http://localhost:8080/ai/rag/chat?question=熊猫吃什么&chatId=123
     */
    @GetMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chat(@RequestParam String question, 
                             @RequestParam(defaultValue = "default") String chatId,
                             @RequestParam(required = false) String userId,
                             HttpServletRequest request,
                             HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        String uid = UserIdResolver.resolve(request, userId);
        boolean isVisitor = UserIdResolver.isVisitor(request);
        String sharedSessionId = UserIdResolver.resolveSharedSessionId(request);
        return ragService.ask(chatId, question, uid, isVisitor, sharedSessionId);
    }
}
