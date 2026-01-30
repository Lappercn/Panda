package org.AI.panda.service;

import org.AI.panda.model.entity.ChatMessageEntity;
import org.AI.panda.repository.ChatMessageRepository;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatMemoryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatLanguageModel chatModel;
    private final MongoTemplate mongoTemplate;
    
    // Max messages to keep in active history before compression
    // Reduced to 10 to strictly prevent context overflow
    private static final int MAX_HISTORY = 10;
    
    // Token/Char limit threshold
    // Reduced to ~16k chars (approx 10k tokens) to be extremely safe
    private static final int MAX_CONTEXT_CHARS = 16000;
    
    private static final String SUMMARY_COLLECTION = "chat_summaries";

    private final EmbeddingService embeddingService;
    private final ChatSessionService chatSessionService;

    public ChatMemoryService(ChatMessageRepository chatMessageRepository, 
                             ChatLanguageModel chatModel,
                             MongoTemplate mongoTemplate,
                             @org.springframework.context.annotation.Lazy EmbeddingService embeddingService,
                             ChatSessionService chatSessionService) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatModel = chatModel;
        this.mongoTemplate = mongoTemplate;
        this.embeddingService = embeddingService;
        this.chatSessionService = chatSessionService;
    }

    public void saveMessage(String userId, String sessionId, String role, String content) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setUserId(userId);
        entity.setSessionId(sid);
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setVectorized(false);
        ChatMessageEntity saved = chatMessageRepository.save(entity);

        chatSessionService.touchSession(userId, sid, role, content);
        
        // Async vectorization for long-term memory
        vectorizeMessage(saved);
        
        // Remove auto-async compression check, delegate to RagService for better UI control
        // checkAndCompressMemory(userId);
    }
    
    @Async
    public void vectorizeMessage(ChatMessageEntity message) {
        if (message.getContent() == null || message.getContent().length() < 10) return; // Skip short messages
        
        try {
            String text = message.getRole() + ": " + message.getContent();
            embeddingService.storeChat(text, message.getUserId(), message.getSessionId(), message.getId(), message.getRole());
            
            message.setVectorized(true);
            chatMessageRepository.save(message);
        } catch (Exception e) {
            System.err.println("Failed to vectorize chat message: " + e.getMessage());
        }
    }
    
    /**
     * Check if memory needs compression based on smart heuristics
     * @param userId user identifier
     * @return true if compression is needed
     */
    public boolean isCompressionNeeded(String userId, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        long count = chatMessageRepository.countByUserIdAndSessionId(userId, sid);
        if (count <= 5) return false; 

        List<ChatMessageEntity> recentMessages = chatMessageRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(
                userId, sid, PageRequest.of(0, (int) Math.min(count, 50)));
        
        long totalChars = recentMessages.stream()
                .mapToLong(msg -> msg.getContent() != null ? msg.getContent().length() : 0)
                .sum();

        return totalChars > MAX_CONTEXT_CHARS || count > MAX_HISTORY + 5;
    }

    /**
     * Perform synchronous memory compression
     * @param userId user identifier
     */
    public void compressMemorySync(String userId, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        long count = chatMessageRepository.countByUserIdAndSessionId(userId, sid);
        compressHistory(userId, sid, count);
    }

    public List<ChatMessageEntity> getRecentMessages(String userId, String sessionId) {
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        // 1. Get recent messages
        List<ChatMessageEntity> messages = chatMessageRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(
                userId, sid, PageRequest.of(0, MAX_HISTORY));
        Collections.reverse(messages); // Chronological order

        // 2. Get summary if exists
        String summary = getSummary(userId, sid);
        if (summary != null && !summary.isEmpty()) {
            ChatMessageEntity summaryMsg = new ChatMessageEntity();
            summaryMsg.setRole("system");
            String prefix = containsChinese(summary) ? "历史摘要：" : "Previous Conversation Summary: ";
            summaryMsg.setContent(prefix + summary);
            summaryMsg.setCreatedAt(LocalDateTime.MIN); // Oldest
            
            // Insert at the beginning
            List<ChatMessageEntity> combined = new ArrayList<>();
            combined.add(summaryMsg);
            combined.addAll(messages);
            return combined;
        }

        return messages;
    }

    // Removed checkAndCompressMemory in favor of explicit calls

    private void compressHistory(String userId, String sessionId, long totalCount) {
        // Strategy: Keep the last N messages that fit within a safe buffer (e.g. 1000 chars or 10 msgs),
        // and compress everything before that.
        
        // For simplicity in this iteration: We still keep MAX_HISTORY as the "active window",
        // but we ensure we compress if the *content* within that window is too huge, 
        // effectively shrinking the window if needed.
        
        // Actually, to implement "Smart" compression properly:
        // We should identify the "Oldest" messages that need to be moved to summary.
        
        int keep = MAX_HISTORY;
        
        // Dynamic adjustment: If messages are very long, keep fewer messages
        List<ChatMessageEntity> allMessages = chatMessageRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(
                userId, sessionId, PageRequest.of(0, (int) Math.min(totalCount, 50)));
        
        long currentChars = 0;
        int dynamicKeep = 0;
        for (ChatMessageEntity msg : allMessages) {
            int len = msg.getContent() != null ? msg.getContent().length() : 0;
            if (currentChars + len > MAX_CONTEXT_CHARS) {
                break;
            }
            currentChars += len;
            dynamicKeep++;
        }
        
        // Ensure we keep at least a few recent turns even if they are long
        keep = Math.max(dynamicKeep, 4); 
        
        // But also cap at MAX_HISTORY
        keep = Math.min(keep, MAX_HISTORY);

        long toCompressCount = totalCount - keep;
        if (toCompressCount <= 0) return;

        // Get the oldest messages to compress
        List<ChatMessageEntity> oldMessages = chatMessageRepository.findByUserIdAndSessionIdOrderByCreatedAtAsc(
                userId, sessionId, PageRequest.of(0, (int) toCompressCount));
        
        if (oldMessages.isEmpty()) return;

        // Get existing summary
        String existingSummary = getSummary(userId, sessionId);
        
        // Construct prompt
        boolean outputChinese = shouldUseChinese(existingSummary, oldMessages);
        StringBuilder promptBuilder = new StringBuilder();
        if (outputChinese) {
            promptBuilder.append("请将下面的对话历史压缩成一段简洁摘要。\n");
            promptBuilder.append("要求：保留关键事实、用户偏好、重要上下文；合并重复内容；用中文输出。\n");
            if (existingSummary != null && !existingSummary.isEmpty()) {
                promptBuilder.append("已有摘要：").append(existingSummary).append("\n");
            }
            promptBuilder.append("需要合并的新对话：\n");
        } else {
            promptBuilder.append("Please condense the following conversation history into a concise summary.\n");
            promptBuilder.append("Focus on key facts, user preferences, and important context. Output in English.\n");
            if (existingSummary != null && !existingSummary.isEmpty()) {
                promptBuilder.append("Existing Summary: ").append(existingSummary).append("\n");
            }
            promptBuilder.append("New History to Merge:\n");
        }
        for (ChatMessageEntity msg : oldMessages) {
            promptBuilder.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
        }
        if (outputChinese) {
            promptBuilder.append("\n只输出更新后的摘要文本，不要输出标题、列表或多余说明。");
        } else {
            promptBuilder.append("\nOutput ONLY the updated summary text.");
        }

        try {
            String newSummary = chatModel.generate(promptBuilder.toString());
            
            // Update summary in DB
            saveSummary(userId, sessionId, newSummary);
            
            // Delete old messages
            List<String> idsToDelete = oldMessages.stream().map(ChatMessageEntity::getId).collect(Collectors.toList());
            chatMessageRepository.deleteAllById(idsToDelete);
            
            System.out.println("Smart Compressed " + idsToDelete.size() + " messages for user " + userId + ". New Summary Length: " + newSummary.length());
            
        } catch (Exception e) {
            System.err.println("Memory compression failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean shouldUseChinese(String existingSummary, List<ChatMessageEntity> messages) {
        if (containsChinese(existingSummary)) return true;
        for (ChatMessageEntity msg : messages) {
            if (containsChinese(msg.getContent())) return true;
        }
        return false;
    }

    private static boolean containsChinese(String text) {
        if (text == null || text.isEmpty()) return false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) return true;
        }
        return false;
    }
    
    private String getSummary(String userId, String sessionId) {
        Query query = new Query(Criteria.where("_id").is(summaryId(userId, sessionId)));
        var result = mongoTemplate.findOne(query, SummaryDoc.class, SUMMARY_COLLECTION);
        return result != null ? result.summary : null;
    }
    
    private void saveSummary(String userId, String sessionId, String summary) {
        Query query = new Query(Criteria.where("_id").is(summaryId(userId, sessionId)));
        Update update = new Update().set("summary", summary).set("updatedAt", LocalDateTime.now());
        mongoTemplate.upsert(query, update, SUMMARY_COLLECTION);
    }

    private String summaryId(String userId, String sessionId) {
        String uid = userId == null ? "" : userId;
        String sid = (sessionId == null || sessionId.isBlank()) ? "default" : sessionId;
        return uid + ":" + sid;
    }
    
    // Inner helper class for MongoTemplate mapping
    private static class SummaryDoc {
        String id;
        String summary;
    }
}
