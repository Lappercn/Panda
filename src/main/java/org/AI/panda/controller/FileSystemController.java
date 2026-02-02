package org.AI.panda.controller;

import org.AI.panda.common.Result;
import org.AI.panda.model.entity.FileSystemNode;
import org.AI.panda.service.FileSystemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import org.AI.panda.auth.web.UserIdResolver;

import java.util.List;

@RestController
@RequestMapping("/api/fs")
public class FileSystemController {

    @Autowired
    private org.AI.panda.service.DocumentService documentService;

    @Autowired
    private FileSystemService fileSystemService;

    @GetMapping("/list")
    public Result<List<FileSystemNode>> list(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                             HttpServletRequest request,
                                             @RequestParam(required = false, defaultValue = "0") String parentId) {
        return Result.success(fileSystemService.listDirectory(UserIdResolver.resolve(request, userId), parentId));
    }

    @PostMapping("/mkdir")
    public Result<FileSystemNode> mkdir(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                        HttpServletRequest request,
                                        @RequestParam(required = false, defaultValue = "0") String parentId,
                                        @RequestParam String name) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        return Result.success(fileSystemService.createDirectory(UserIdResolver.resolve(request, userId), parentId, name));
    }

    @PostMapping("/upload")
    public Result<FileSystemNode> upload(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                         HttpServletRequest request,
                                         @RequestParam(required = false, defaultValue = "0") String parentId,
                                         @RequestParam("file") MultipartFile file) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        return Result.success(fileSystemService.uploadFile(UserIdResolver.resolve(request, userId), parentId, file));
    }

    @DeleteMapping("/delete")
    public Result<Void> delete(@RequestHeader(value = "X-User-ID", required = false) String userId,
                               HttpServletRequest request,
                               @RequestParam String nodeId) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        fileSystemService.deleteNode(UserIdResolver.resolve(request, userId), nodeId);
        return Result.success(null);
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                    HttpServletRequest request,
                                    @RequestBody List<String> nodeIds) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        fileSystemService.batchDeleteNodes(UserIdResolver.resolve(request, userId), nodeIds);
        return Result.success(null);
    }

    @PostMapping("/save-ocr")
    public Result<Void> saveOcrResult(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                      HttpServletRequest request,
                                      @RequestBody SaveOcrRequest body) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        fileSystemService.saveOcrResult(UserIdResolver.resolve(request, userId), body.getNodeId(), body.getJsonContent());
        return Result.success(null);
    }

    public static class SaveOcrRequest {
        private String nodeId;
        private String jsonContent;
        // getters/setters
        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }
        public String getJsonContent() { return jsonContent; }
        public void setJsonContent(String jsonContent) { this.jsonContent = jsonContent; }
    }

    @GetMapping("/download")
    public Result<String> download(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                   HttpServletRequest request,
                                   @RequestParam String nodeId) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止下载");
        }
        return Result.success(fileSystemService.getDownloadUrl(UserIdResolver.resolve(request, userId), nodeId));
    }

    @PostMapping("/move")
    public Result<FileSystemNode> move(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                       HttpServletRequest request,
                                       @RequestParam String nodeId,
                                       @RequestParam(required = false, defaultValue = "0") String newParentId) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        return Result.success(fileSystemService.moveNode(UserIdResolver.resolve(request, userId), nodeId, newParentId));
    }

    @GetMapping("/preview")
    public Result<java.util.Map<String, String>> preview(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                                         HttpServletRequest request,
                                                         @RequestParam String nodeId) {
        boolean includeFileUrl = !UserIdResolver.isVisitor(request);
        return Result.success(fileSystemService.getPreviewData(UserIdResolver.resolve(request, userId), nodeId, includeFileUrl));
    }

    @GetMapping(value = "/ocr", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> ocr(@RequestHeader(value = "X-User-ID", required = false) String userId,
                                      HttpServletRequest request,
                                      @RequestParam String nodeId) {
        // 优先解析分享 Token (如果存在)
        String uid = UserIdResolver.resolve(request, userId);
        
        // 调试日志
        // System.out.println("OCR Request: nodeId=" + nodeId + ", userIdHeader=" + userId + ", resolvedUid=" + uid);
        
        try {
            // 如果是分享访问，允许读取
            // 但 fileSystemService.readOcrJsonBytes 会严格校验 uid == node.userId
            // 正常情况下，UserIdResolver 已经把 uid 解析为 ownerId
            // 如果还是报错，说明 UserIdResolver 没解析对，或者文件不属于该 owner
            
            // 为了更稳健，我们这里可以做一个特殊的处理：
            // 如果是分享访问 (isVisitor=true)，我们直接获取文件节点，并临时允许读取 (跳过 Service 层的严格校验)
            // 或者，我们在 Service 层增加一个 allowShare 的参数
            
            // 方案二：直接在 Controller 层处理分享逻辑
            if (UserIdResolver.isVisitor(request)) {
                // 验证分享 Token 是否有效（Filter 已验证）
                // 验证该文件是否属于分享的 owner
                // 这里我们直接调用 Service 的内部方法或者新增一个 permissive 方法
                // 但 Service 层通常不暴露内部方法。
                
                // 最好的办法是：UserIdResolver 已经把 uid 变成了 ownerId。
                // 如果还报错，说明文件真的不是这个 owner 的。
                // 或者是 UserIdResolver 解析出的 ownerId 和文件实际 ownerId 不一致。
                
                // 让我们尝试捕获异常，并再次尝试（比如直接用文件的 ownerId 读取，但这有安全风险）
                // 安全的做法：相信 UserIdResolver。如果报错，就是 403。
            }
            
            byte[] bytes = fileSystemService.readOcrJsonBytes(uid, nodeId);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Cache-Control", "no-store")
                    .body(bytes);
        } catch (SecurityException e) {
            // 403 Access denied
            return ResponseEntity.status(403).build();
        }
    }

    @PostMapping("/retry")
    public Result<Void> retry(@RequestHeader(value = "X-User-ID", required = false) String userId,
                              HttpServletRequest request,
                              @RequestParam String nodeId) {
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接禁止该操作");
        }
        String uid = UserIdResolver.resolve(request, userId);
        // Check ownership
        FileSystemNode node = fileSystemService.getNode(nodeId);
        if (node == null) return Result.error(404, "Node not found");
        if (!node.getUserId().equals(uid)) return Result.error(403, "Access denied");
        
        // Trigger processing
        documentService.processFileSystemNode(nodeId);
        return Result.success(null);
    }
}
