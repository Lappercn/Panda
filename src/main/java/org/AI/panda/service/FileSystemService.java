package org.AI.panda.service;

import org.AI.panda.model.entity.FileSystemNode;
import org.AI.panda.repository.FileSystemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service
public class FileSystemService {

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
        "pdf", "ofd",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "jpg", "jpeg", "png", "bmp", "tif", "tiff"
    ));

    @Autowired
    private FileSystemRepository fsRepository;

    @Autowired
    private MinioService minioService;
    
    @Autowired
    private DocumentService documentService;

    @Autowired
    private EmbeddingService embeddingService;

    /**
     * 列出目录内容
     */
    public List<FileSystemNode> listDirectory(String userId, String parentId) {
        // 如果 parentId 为 null 或 empty，默认为根目录 "0"
        String pid = (parentId == null || parentId.isEmpty()) ? "0" : parentId;
        return fsRepository.findByUserIdAndParentId(userId, pid);
    }

    /**
     * 创建目录
     */
    public FileSystemNode createDirectory(String userId, String parentId, String name) {
        String pid = (parentId == null || parentId.isEmpty()) ? "0" : parentId;
        
        // 检查重名
        if (fsRepository.findByUserIdAndParentIdAndName(userId, pid, name).isPresent()) {
            throw new IllegalArgumentException("Directory already exists: " + name);
        }

        FileSystemNode node = new FileSystemNode();
        node.setUserId(userId);
        node.setParentId(pid);
        node.setName(name);
        node.setType(FileSystemNode.NodeType.DIRECTORY);
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedAt(LocalDateTime.now());
        
        return fsRepository.save(node);
    }

    /**
     * 上传文件
     */
    public FileSystemNode uploadFile(String userId, String parentId, MultipartFile file) {
        String pid = (parentId == null || parentId.isEmpty()) ? "0" : parentId;
        String fileName = file.getOriginalFilename();

        if (fileName == null) {
            throw new IllegalArgumentException("File name cannot be null");
        }

        // Validate file extension
        String extension = "";
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex >= 0) {
            extension = fileName.substring(dotIndex + 1).toLowerCase();
        }

        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension + "。仅支持 PDF, Word, Excel, PPT, OFD 及常见图片格式。");
        }

        // 检查重名 (覆盖还是报错？通常文件系统允许覆盖或重命名。这里先报错)
        if (fsRepository.findByUserIdAndParentIdAndName(userId, pid, fileName).isPresent()) {
            throw new IllegalArgumentException("File already exists: " + fileName);
        }

        // 1. 上传到 MinIO
        // 为了避免 MinIO 中文件名冲突，使用 UUID 命名对象，或者 userId/parentId/fileName
        // 使用 UUID 最安全，文件名保存在 MongoDB 中
        String objectName = userId + "/" + UUID.randomUUID() + "/" + fileName;
        minioService.uploadFile(objectName, file);

        // 2. 保存元数据到 MongoDB
        FileSystemNode node = new FileSystemNode();
        node.setUserId(userId);
        node.setParentId(pid);
        node.setName(fileName);
        node.setType(FileSystemNode.NodeType.FILE);
        node.setMinioObjectName(objectName);
        node.setSize(file.getSize());
        node.setContentType(file.getContentType());
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedAt(LocalDateTime.now());
        node.setVectorized(false);
        node.setProcessingStatus(FileSystemNode.ProcessingStatus.PENDING);

        FileSystemNode savedNode = fsRepository.save(node);
        
        // 触发异步处理 (OCR -> Embedding)
        documentService.processFileSystemNode(savedNode.getId());
        
        return savedNode;
    }

    /**
     * 删除节点 (递归删除)
     */
    @Transactional
    public void deleteNode(String userId, String nodeId) {
        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (!node.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }

        if (node.getType() == FileSystemNode.NodeType.DIRECTORY) {
            // 递归删除子节点
            List<FileSystemNode> children = fsRepository.findByUserIdAndParentId(userId, nodeId);
            for (FileSystemNode child : children) {
                deleteNode(userId, child.getId());
            }
        } else {
            // 删除 MinIO 文件
            if (node.getMinioObjectName() != null) {
                minioService.deleteFile(node.getMinioObjectName());
            }
            
            // 删除 OCR 结果文件 (如果有)
            if (node.getOcrResultPath() != null) {
                try {
                    minioService.deleteFile(node.getOcrResultPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            // 删除向量库中的数据
            try {
                embeddingService.deleteByNodeId(nodeId);
            } catch (Exception e) {
                System.err.println("Failed to delete embeddings for node " + nodeId + ": " + e.getMessage());
            }
        }

        fsRepository.delete(node);
    }

    public void batchDeleteNodes(String userId, List<String> nodeIds) {
        for (String nodeId : nodeIds) {
            try {
                deleteNode(userId, nodeId);
            } catch (Exception e) {
                // 忽略单个删除失败，继续删除其他
                System.err.println("Failed to delete node " + nodeId + ": " + e.getMessage());
            }
        }
    }

    public void saveOcrResult(String userId, String nodeId, String jsonContent) {
        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (!node.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }
        
        if (node.getType() != FileSystemNode.NodeType.FILE) {
             throw new IllegalArgumentException("Only files can have OCR results");
        }

        try {
            String jsonPath = node.getOcrResultPath();
            if (jsonPath == null) {
                 jsonPath = "ocr-results/" + node.getUserId() + "/" + node.getId() + ".json";
                 node.setOcrResultPath(jsonPath);
                 fsRepository.save(node);
            }
            
            // 上传新的 JSON 内容到 MinIO
            try (java.io.InputStream is = new java.io.ByteArrayInputStream(jsonContent.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                minioService.uploadStream(jsonPath, is, "application/json");
            }
            
            // 更新节点的修改时间
            node.setUpdatedAt(java.time.LocalDateTime.now());
            fsRepository.save(node);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to save OCR result: " + e.getMessage());
        }
    }

    /**
     * 获取文件下载链接
     */
    public String getDownloadUrl(String userId, String nodeId) {
        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (!node.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }

        if (node.getType() == FileSystemNode.NodeType.DIRECTORY) {
            throw new IllegalArgumentException("Cannot download a directory");
        }

        return minioService.getPresignedUrl(node.getMinioObjectName(), 3600); // 1 hour
    }
    
    /**
     * 移动节点 (文件/目录)
     */
    public FileSystemNode moveNode(String userId, String nodeId, String newParentId) {
        String targetParentId = (newParentId == null || newParentId.isEmpty()) ? "0" : newParentId;

        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (!node.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }
        
        // 如果目标父目录不是根目录，检查其存在性
        if (!"0".equals(targetParentId)) {
            FileSystemNode parentNode = fsRepository.findById(targetParentId)
                    .orElseThrow(() -> new IllegalArgumentException("Target directory not found"));
            if (parentNode.getType() != FileSystemNode.NodeType.DIRECTORY) {
                throw new IllegalArgumentException("Target is not a directory");
            }
        }

        // 检查目标目录下是否有重名
        if (fsRepository.findByUserIdAndParentIdAndName(userId, targetParentId, node.getName()).isPresent()) {
            throw new IllegalArgumentException("Target directory already contains a node with this name: " + node.getName());
        }

        // 循环移动检查 (不能移动到自己的子目录中)
        if (node.getType() == FileSystemNode.NodeType.DIRECTORY) {
            String current = targetParentId;
            while (!"0".equals(current)) {
                if (current.equals(nodeId)) {
                    throw new IllegalArgumentException("Cannot move a directory into its own subdirectory");
                }
                FileSystemNode p = fsRepository.findById(current).orElse(null);
                if (p == null) break;
                current = p.getParentId();
            }
        }

        node.setParentId(targetParentId);
        node.setUpdatedAt(LocalDateTime.now());
        
        return fsRepository.save(node);
    }
    
    public FileSystemNode getNode(String nodeId) {
        return fsRepository.findById(nodeId).orElse(null);
    }

    public java.util.Map<String, String> getPreviewData(String userId, String nodeId) {
        return getPreviewData(userId, nodeId, true);
    }

    public java.util.Map<String, String> getPreviewData(String userId, String nodeId, boolean includeFileUrl) {
        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (!node.getUserId().equals(userId)) {
            throw new SecurityException("Access denied");
        }

        java.util.Map<String, String> result = new java.util.HashMap<>();
        // Original File URL
        if (includeFileUrl && node.getMinioObjectName() != null) {
            try {
                result.put("fileUrl", minioService.getPresignedUrl(node.getMinioObjectName(), 3600));
            } catch (RuntimeException ignored) {
            }
        }

        // OCR JSON URL
        if (node.getOcrResultPath() != null) {
            result.put("ocrUrl", "/api/fs/ocr?nodeId=" + java.net.URLEncoder.encode(nodeId, java.nio.charset.StandardCharsets.UTF_8));
        }

        result.put("name", node.getName());
        result.put("contentType", node.getContentType());

        return result;
    }

    public byte[] readOcrJsonBytes(String userId, String nodeId) {
        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (!node.getUserId().equals(userId)) {
             // 再次检查：也许 userId 格式不对？或者数据库里存的是 ObjectId 而不是 String？
             // 不，都是 String。
             // 唯一的可能是：分享链接的 ownerId 和这个文件的 ownerId 不一致。
             // 比如：A 分享了一个会话，会话里引用了 B 的文件？
             // 或者：文件系统的结构错乱了。
             
             // 暂时放宽策略：如果 userId 不匹配，但文件是公开的？不支持公开。
             // 只能是：抛出异常，让 Controller 处理 403
             throw new SecurityException("Access denied: nodeUser=" + node.getUserId() + ", requestUser=" + userId);
        }

        String ocrPath = node.getOcrResultPath();
        if (ocrPath == null || ocrPath.isBlank()) {
            return "{\"pages\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        try (InputStream is = minioService.getFile(ocrPath)) {
            return is.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read OCR result", e);
        }
    }
}
