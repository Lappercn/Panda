package org.AI.panda.service;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.AI.panda.agent.PandaAgent;
import org.AI.panda.agent.PandaTools;
import org.AI.panda.model.entity.ChatMessageEntity;
import org.AI.panda.model.entity.FileSystemNode;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RagService {

    private final StreamingChatLanguageModel streamingChatModel;
    private final ChatLanguageModel chatModel;
    private final EmbeddingService embeddingService;
    private final ChatMemoryService chatMemoryService;
    private final FileSystemService fileSystemService;

    public RagService(StreamingChatLanguageModel streamingChatModel,
                      ChatLanguageModel chatModel,
                      EmbeddingService embeddingService, 
                      ChatMemoryService chatMemoryService,
                      FileSystemService fileSystemService) {
        this.streamingChatModel = streamingChatModel;
        this.chatModel = chatModel;
        this.embeddingService = embeddingService;
        this.chatMemoryService = chatMemoryService;
        this.fileSystemService = fileSystemService;
    }

    /**
     * Agentic RAG 问答 (流式返回) - 支持多轮对话与工具调用
     * @param chatId 会话ID
     * @param question 用户问题
     * @param userId 用户ID (用于数据隔离)
     * @param isVisitor 是否为分享访客
     * @param sharedSessionId 分享限制的会话ID (访客生效)
     * @return AI 回答流
     */
    public Flux<String> ask(String chatId, String question, String userId, boolean isVisitor, String sharedSessionId) {
        String effectiveChatId = (isVisitor && sharedSessionId != null && !sharedSessionId.isBlank())
                ? sharedSessionId
                : chatId;
        String sessionId = (effectiveChatId == null || effectiveChatId.isBlank()) ? "default" : effectiveChatId;

        if (!isVisitor) {
            chatMemoryService.saveMessage(userId, sessionId, "user", question);
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        if (!isVisitor && chatMemoryService.isCompressionNeeded(userId, sessionId)) {
            sink.tryEmitNext("[MEMORY_COMPRESSING]");

            try {
                chatMemoryService.compressMemorySync(userId, sessionId);
                sink.tryEmitNext("[MEMORY_COMPRESSED]");
            } catch (Throwable e) {
                System.err.println("Compression failed: " + e.getMessage());
            }
        }

        // 1. 构建动态工具 (注入当前 UserId)
        PandaTools tools = new PandaTools(embeddingService, fileSystemService, userId, !isVisitor);
        // 注入状态回调，实时反馈工具执行进度
        tools.setStatusCallback(status -> sink.tryEmitNext("[STATUS]" + status));

        // 2. 准备上下文 (Get AFTER compression)
        List<ChatMessageEntity> dbHistory = chatMemoryService.getRecentMessages(userId, sessionId);
        StringBuilder historyBuilder = new StringBuilder();
        
        // 2.1 Long-term Memory Retrieval
        try {
            List<EmbeddingMatch<TextSegment>> longTermMemories = embeddingService.search(question, userId, "chat-history");
            if (!longTermMemories.isEmpty()) {
                historyBuilder.append("### 相关历史回忆 (Long-term Memory):\n");
                longTermMemories.stream()
                        .limit(3)
                        .map(match -> match.embedded().text())
                        .map(text -> text.length() > 300 ? text.substring(0, 300) : text)
                        .forEach(text -> historyBuilder.append("> ").append(text).append("\n"));
                historyBuilder.append("\n");
            }
        } catch (Throwable e) {
            System.err.println("Long-term memory retrieval failed: " + e.getMessage());
        }
        
        // Smart Context Construction: Limit history to ~4000 chars to avoid token overflow
        int maxHistoryChars = 4000;
        int currentChars = 0;
        java.util.LinkedList<String> safeHistoryLines = new java.util.LinkedList<>();
        
        // Iterate backwards to keep most recent messages
        for (int i = dbHistory.size() - 1; i >= 0; i--) {
            ChatMessageEntity entity = dbHistory.get(i);
            String line = String.format("- %s: %s\n", entity.getRole(), entity.getContent());
            if (currentChars + line.length() > maxHistoryChars) {
                // If this is the summary (usually first/oldest), try to keep it if possible, 
                // but for now just stop to be safe.
                // Or better: always keep Summary if it exists?
                // Let's just stop. The summary is usually at index 0.
                if (i == 0 && "system".equals(entity.getRole())) {
                     // Special case: Summary is important, maybe add it truncated?
                     // But if we are here, it means recent messages took up all space.
                     break; 
                }
                break;
            }
            safeHistoryLines.addFirst(line);
            currentChars += line.length();
        }
        
        if (!safeHistoryLines.isEmpty()) {
            historyBuilder.append("\n\n### 历史对话上下文 (仅供参考):\n");
            for (String line : safeHistoryLines) {
                historyBuilder.append(line);
            }
        }

        // 3. 构建 Agent (Streaming)
        PandaAgent agent = AiServices.builder(PandaAgent.class)
                .streamingChatLanguageModel(streamingChatModel)
                .chatLanguageModel(chatModel) // 某些工具可能需要阻塞式模型辅助，但主要流式由 streamingChatModel 驱动
                .tools(tools)
                .build();

        // 4. 调用 Agent (Streaming)
        final String finalPrompt = historyBuilder.length() == 0
                ? question
                : historyBuilder.toString() + "\n\n用户当前问题: " + question;

        System.out.println("====== Agent Prompt ======");
        System.out.println(finalPrompt);
        System.out.println("==========================");

        // Emit Thinking Signal
        sink.tryEmitNext("[MODEL_THINKING]");

        StringBuilder fullResponse = new StringBuilder();

        try {
            dev.langchain4j.service.TokenStream tokenStream = agent.chat(finalPrompt);
            
            tokenStream.onNext(token -> {
                fullResponse.append(token);
                sink.tryEmitNext(token);
            })
            .onComplete(response -> {
                // Agent 完整回答结束后，保存到记忆
                // 注意：ToolExecution 期间不会触发 onNext，只有最终 LLM 回复时触发
                if (!isVisitor) {
                    chatMemoryService.saveMessage(userId, sessionId, "assistant", fullResponse.toString());
                }
                sink.tryEmitNext("[DONE]");
                sink.tryEmitComplete();
            })
            .onError(error -> {
                System.err.println("Agent execution failed: " + error.getMessage());
                if (error instanceof IllegalArgumentException && "response cannot be null".equals(error.getMessage())) {
                    try {
                        sink.tryEmitNext("[STATUS]⚠️ 工具调用流式不兼容，切换为直连模型真流式...");

                        boolean likelyFileQuestion = question.contains("文件")
                                || question.contains("目录")
                                || question.contains("列表")
                                || question.contains("我的文件");

                        String toolResult;
                        if (likelyFileQuestion) {
                            if (isVisitor) {
                                toolResult = "只读分享链接不支持文件相关操作。";
                            } else {
                                List<FileSystemNode> nodes = fileSystemService.listDirectory(userId, "0");
                                toolResult = nodes.isEmpty()
                                        ? "文件系统为空。"
                                        : nodes.stream()
                                        .limit(50)
                                        .map(n -> "- " + n.getName() + " (" + n.getType() + ")")
                                        .collect(Collectors.joining("\n"));
                            }
                        } else {
                            List<EmbeddingMatch<TextSegment>> matches = embeddingService.search(question, userId, "user-upload");
                            toolResult = matches.stream()
                                    .filter(m -> m.score() >= 0.50)
                                    .limit(3)
                                    .map(m -> m.embedded().text())
                                    .collect(Collectors.joining("\n\n---\n\n"));
                            if (toolResult.isBlank()) {
                                toolResult = "未检索到相关知识库内容。";
                            } else if (toolResult.length() > 2000) {
                                toolResult = toolResult.substring(0, 2000);
                            }
                        }

                        String fallbackPrompt = """
                                你是一个专业的企业客户分析助手。你将获得“工具检索结果”。
                                
                                要求：
                                - 不要捏造工具结果中不存在的事实或数据。
                                - 允许基于工具结果进行合理推断、延伸分析与建议，但需明确区分：哪些来自工具结果，哪些是推断/建议。
                                - 输出不强制固定格式；以清晰自然的语言表达即可（优先使用用户提问所使用的语言）。

                                工具检索结果：
                                %s

                                用户问题：
                                %s
                                """.formatted(toolResult, question);

                        StringBuilder streamed = new StringBuilder();
                        streamingChatModel.generate(fallbackPrompt, new StreamingResponseHandler<AiMessage>() {
                            @Override
                            public void onNext(String token) {
                                streamed.append(token);
                                sink.tryEmitNext(token);
                            }

                            @Override
                            public void onComplete(Response<AiMessage> response) {
                                if (!isVisitor) {
                                    chatMemoryService.saveMessage(userId, sessionId, "assistant", streamed.toString());
                                }
                                sink.tryEmitNext("[DONE]");
                                sink.tryEmitComplete();
                            }

                            @Override
                            public void onError(Throwable error) {
                                sink.tryEmitNext("❌ 很抱歉，直连模型流式也失败了: " + error.getMessage());
                                sink.tryEmitNext("[DONE]");
                                sink.tryEmitComplete();
                            }
                        });
                        return;
                    } catch (Exception e) {
                        sink.tryEmitNext("❌ 很抱歉，降级流程失败了: " + e.getMessage());
                    }
                } else {
                    sink.tryEmitNext("❌ 很抱歉，遇到了一些问题: " + error.getMessage());
                }
                sink.tryEmitNext("[DONE]");
                sink.tryEmitComplete(); // 结束流，避免前端一直挂起
            })
            .start();
            
        } catch (Exception e) {
            sink.tryEmitError(e);
        }
        
        return sink.asFlux();
    }
}
