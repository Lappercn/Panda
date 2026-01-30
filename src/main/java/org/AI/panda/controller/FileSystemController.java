package org.AI.panda.controller;

import org.AI.panda.common.Result;
import org.AI.panda.model.entity.FileSystemNode;
import org.AI.panda.service.FileSystemService;
import org.springframework.beans.factory.annotation.Autowired;
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
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接不支持文件访问");
        }
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
        if (UserIdResolver.isVisitor(request)) {
            return Result.error(403, "只读分享链接不支持文件访问");
        }
        return Result.success(fileSystemService.getPreviewData(UserIdResolver.resolve(request, userId), nodeId));
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
