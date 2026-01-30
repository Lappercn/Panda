package org.AI.panda.controller;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.AI.panda.common.Result;
import org.AI.panda.service.DocumentService;
import org.AI.panda.service.EmbeddingService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.AI.panda.auth.web.UserIdResolver;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/embedding")
public class EmbeddingController {

    private final EmbeddingService embeddingService;
    private final DocumentService documentService;

    public EmbeddingController(EmbeddingService embeddingService, DocumentService documentService) {
        this.embeddingService = embeddingService;
        this.documentService = documentService;
    }

    /**
     * 上传文件并自动入库 (支持 PDF, 图片, 文本)
     * POST http://localhost:8080/ai/embedding/upload
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "800") int chunkSize,
            @RequestParam(defaultValue = "100") int overlap,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            HttpServletRequest request) throws Exception {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        String uid = UserIdResolver.resolve(request, userId);

        if (file.isEmpty()) {
            return Result.error("文件为空");
        }

        // 1. 提取文本
        String content = documentService.extractText(file);
        
        if (content == null || content.isBlank()) {
            return Result.error("无法提取到有效文本内容");
        }

        // 2. 向量化入库
        embeddingService.store(content, chunkSize, overlap, uid, "direct-upload-" + System.currentTimeMillis());
        
        return Result.success(Map.of(
                "message", "文件处理成功",
                "extracted_length", content.length()
        ));
    }

    /**
     * 添加知识 (存入向量库)
     * POST http://localhost:8080/ai/embedding/add?content=xxx&chunkSize=500&overlap=50
     */
    @PostMapping("/add")
    public Result<Map<String, String>> add(
            @RequestParam String content,
            @RequestParam(defaultValue = "800") int chunkSize,
            @RequestParam(defaultValue = "100") int overlap,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            HttpServletRequest request) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        String uid = UserIdResolver.resolve(request, userId);

        embeddingService.store(content, chunkSize, overlap, uid, "manual-add-" + System.currentTimeMillis());
        return Result.success(Map.of(
                "message", String.format("已将内容向量化并存入数据库 (切片: %d, 重叠: %d)", chunkSize, overlap)
        ));
    }

    @GetMapping("/add")
    public Result<Map<String, String>> addByGet(
            @RequestParam String content,
            @RequestParam(defaultValue = "800") int chunkSize,
            @RequestParam(defaultValue = "100") int overlap,
            @RequestHeader(value = "X-User-ID", required = false) String userId,
            HttpServletRequest request) {
        return add(content, chunkSize, overlap, userId, request);
    }

    /**
     * 检索知识 (语义搜索)
     * GET http://localhost:8080/ai/embedding/search?query=熊猫吃啥
     */
    @GetMapping("/search")
    public Result<List<String>> search(@RequestParam String query,
                                       @RequestHeader(value = "X-User-ID", required = false) String userId,
                                       HttpServletRequest request) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        String uid = UserIdResolver.resolve(request, userId);
        List<EmbeddingMatch<TextSegment>> matches = embeddingService.search(query, uid);
        
        // 简化返回，只返回文本内容
        List<String> results = matches.stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.toList());
        return Result.success(results);
    }

    /**
     * 清空向量库
     * DELETE/GET http://localhost:8080/ai/embedding/clear
     */
    @RequestMapping(value = "/clear", method = {RequestMethod.GET, RequestMethod.DELETE})
    public Map<String, String> clear(HttpServletRequest request) {
        if (UserIdResolver.isVisitor(request)) {
            return Map.of("status", "error", "message", "只读分享链接禁止该操作");
        }
        embeddingService.clear();
        return Map.of("status", "success", "message", "向量库已清空");
    }
}
