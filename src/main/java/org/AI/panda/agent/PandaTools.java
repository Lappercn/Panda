package org.AI.panda.agent;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.AI.panda.model.entity.FileSystemNode;
import org.AI.panda.service.EmbeddingService;
import org.AI.panda.service.FileSystemService;

import java.util.List;
import java.util.stream.Collectors;

public class PandaTools {

    private final EmbeddingService embeddingService;
    private final FileSystemService fileSystemService;
    private final String userId;
    private final boolean allowFileTools;
    private java.util.function.Consumer<String> statusCallback;

    public PandaTools(EmbeddingService embeddingService, FileSystemService fileSystemService, String userId, boolean allowFileTools) {
        this.embeddingService = embeddingService;
        this.fileSystemService = fileSystemService;
        this.userId = userId;
        this.allowFileTools = allowFileTools;
    }

    public void setStatusCallback(java.util.function.Consumer<String> callback) {
        this.statusCallback = callback;
    }

    private void reportStatus(String status) {
        if (statusCallback != null) {
            statusCallback.accept(status);
        }
        System.out.println(status);
    }

    @Tool("Search the knowledge base for information relevant to the user's query. Use this when the user asks questions about their uploaded documents or specific domain knowledge.")
    public String search_knowledge_base(String query) {
        reportStatus("🔍 正在检索知识库: " + query);
        try {
            // 只搜索用户上传的文档，不搜索聊天记录 (chat-history)
            List<EmbeddingMatch<TextSegment>> matches = embeddingService.search(query, userId, "user-upload");
            
            if (matches.isEmpty()) {
                reportStatus("⚠️ 未找到相关信息");
                return "No relevant information found in the knowledge base.";
            }

            String result = matches.stream()
                    .filter(match -> match.score() >= 0.50)
                    .limit(3) // Limit to top 3
                    .map(match -> match.embedded().text())
                    .collect(Collectors.joining("\n\n---\n\n"));
            
            // Truncate to avoid context overflow (max 2000 chars)
            if (result.length() > 2000) {
                result = result.substring(0, 2000) + "\n...(truncated)";
            }
            
            reportStatus("✅ 检索到 " + matches.size() + " 条相关信息");
            return result;
        } catch (Exception e) {
            reportStatus("❌ 检索失败: " + e.getMessage());
            return "Error searching knowledge base: " + e.getMessage();
        }
    }

    @Tool("List the files currently stored in the user's file system. Use this when the user asks 'what files do I have?' or 'list my files'.")
    public String list_my_files() {
        if (!allowFileTools) {
            return "Access denied.";
        }
        reportStatus("📂 正在查询文件列表...");
        try {
            // List root directory (parentId = "0")
            List<FileSystemNode> nodes = fileSystemService.listDirectory(userId, "0");
            
            if (nodes.isEmpty()) {
                return "The user has no files currently.";
            }

            return nodes.stream()
                    .map(node -> String.format("- %s (%s)", node.getName(), node.getType()))
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return "Error listing files: " + e.getMessage();
        }
    }
}
