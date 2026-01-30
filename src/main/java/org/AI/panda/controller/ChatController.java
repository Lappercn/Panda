package org.AI.panda.controller;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.AI.panda.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingChatModel;

    public ChatController(ChatLanguageModel chatModel, StreamingChatLanguageModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    /**
     * 简单的阻塞式对话接口
     * 访问: http://localhost:8080/ai/chat?message=你好
     */
    @GetMapping("/chat")
    public Result<Map<String, String>> chat(@RequestParam(defaultValue = "讲个笑话") String message) {
        String response = chatModel.generate(message);
        return Result.success(Map.of("response", response));
    }

    /**
     * 流式对话接口 (打字机效果)
     * 访问: http://localhost:8080/ai/stream?message=你好
     */
    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> streamChat(@RequestParam(defaultValue = "讲个笑话") String message,
                                   HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        streamingChatModel.generate(message, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                sink.tryEmitNext(token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                sink.tryEmitComplete();
            }

            @Override
            public void onError(Throwable error) {
                sink.tryEmitError(error);
            }
        });

        return sink.asFlux();
    }
}
