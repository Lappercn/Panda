package org.AI.panda.service;

import org.AI.panda.model.entity.FileSystemNode;
import org.AI.panda.repository.FileSystemRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONArray;
import org.json.JSONObject;

// POI Imports
import org.apache.poi.extractor.ExtractorFactory;
import org.apache.poi.extractor.POITextExtractor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.List;

@Service
public class DocumentService {

    private final OcrService ocrService;
    private final MinioService minioService;
    private final EmbeddingService embeddingService;
    private final FileSystemRepository fsRepository;

    public DocumentService(OcrService ocrService, 
                           @Lazy MinioService minioService, 
                           EmbeddingService embeddingService,
                           FileSystemRepository fsRepository) {
        this.ocrService = ocrService;
        this.minioService = minioService;
        this.embeddingService = embeddingService;
        this.fsRepository = fsRepository;
    }

    /**
     * 处理文件系统节点 (异步)
     * 1. 从 MinIO 下载
     * 2. 提取文本
     * 3. 向量化存储
     * 4. 更新状态
     */
    @org.springframework.scheduling.annotation.Async
    public void processFileSystemNode(String nodeId) {
        FileSystemNode node = fsRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Node not found"));

        if (node.getType() == FileSystemNode.NodeType.DIRECTORY) {
            return;
        }
        
        // 更新状态为处理中
        node.setProcessingStatus(FileSystemNode.ProcessingStatus.PROCESSING);
        node.setFailReason(null); // 清除之前的错误
        fsRepository.save(node);

        try {
            // 1. 从 MinIO 下载文件到临时文件
            String extension = "";
            int dotIndex = node.getName().lastIndexOf(".");
            if (dotIndex >= 0) {
                extension = node.getName().substring(dotIndex + 1).toLowerCase();
            }
            
            Path tempPath = Files.createTempFile("node-" + node.getId(), "." + extension);
            File tempFile = tempPath.toFile();
            
            try (InputStream is = minioService.getFile(node.getMinioObjectName())) {
                Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. 提取文本
            String content = extractContentAndOcr(tempFile, extension, node);
            
            // 3. 向量化
            if (content != null && !content.isEmpty()) {
                embeddingService.store(content, node.getUserId(), node.getId());
                
                // 4. 更新状态
                node.setVectorized(true);
                node.setProcessingStatus(FileSystemNode.ProcessingStatus.COMPLETED);
                node.setUpdatedAt(java.time.LocalDateTime.now());
                fsRepository.save(node);
            } else {
                // 内容为空，标记为失败? 或者只是 Completed but empty
                node.setProcessingStatus(FileSystemNode.ProcessingStatus.FAILED);
                node.setFailReason("OCR 提取内容为空");
                fsRepository.save(node);
            }

            // 清理临时文件
            Files.deleteIfExists(tempPath);

        } catch (Exception e) {
            e.printStackTrace();
            // 更新失败状态
            try {
                // 重新获取节点，防止并发覆盖
                node = fsRepository.findById(nodeId).orElse(node);
                node.setProcessingStatus(FileSystemNode.ProcessingStatus.FAILED);
                node.setFailReason(e.getMessage());
                fsRepository.save(node);
            } catch (Exception ex) {
                System.err.println("Failed to update failure status: " + ex.getMessage());
            }
        }
    }

    public String extractText(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) originalFilename = "";
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf(".");
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex + 1).toLowerCase();
        }

        Path tempPath = Files.createTempFile("doc-" + UUID.randomUUID(), "." + extension);
        File tempFile = tempPath.toFile();
        
        try {
            file.transferTo(tempFile);
            return extractTextFromFile(tempFile, extension);
        } finally {
            Files.deleteIfExists(tempPath);
        }
    }

    private String extractTextFromFile(File file, String extension) throws Exception {
        return extractContentAndOcr(file, extension, null);
    }

    private String extractContentAndOcr(File file, String extension, FileSystemNode node) throws Exception {
        // 1. 检测真实文件类型
        String realType = detectFileType(file);
        
        File processFile = file;
        Path tempPath = file.toPath();
        boolean isOffice = isOfficeFile(extension);

        if (realType != null && !isSameType(realType, extension)) {
            // 如果不是 Office 文件，或者虽然扩展名是 Office 但检测出完全不相关的类型(非Zip/OLE)，才重命名
            if (!isOffice) {
                String newName = tempPath.getFileName().toString().replaceFirst("\\.[^.]+$", "") + "." + realType;
                Path newPath = tempPath.resolveSibling(newName);
                Files.move(tempPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                processFile = newPath.toFile();
                extension = realType;
            }
        }

        String text = null;
        String ocrJson = null;

        // 2. 根据类型分发处理
        if (isTextFile(extension)) {
             text = Files.readString(processFile.toPath());
        } else if (isOfficeFile(extension)) {
            // Office 文件 -> 百度 OCR (返回 JSON)
            ocrJson = ocrService.recognizeOfficeDocJson(processFile);
            text = parseTextFromJson(ocrJson);
        } else if ("pdf".equals(extension)) {
            // PDF -> 多页 OCR 处理
            PdfResult result = processPdfWithOcr(processFile);
            text = result.text;
            ocrJson = result.json;
        } else {
            // 其他文件 (图片等) -> 百度 OCR (通用文字识别-含位置版)
            ocrJson = ocrService.recognizeGeneralJson(processFile);
            text = parseTextFromJson(ocrJson);
        }
        
        // 3. 保存 OCR 结果到 MinIO (如果有节点信息)
        if (ocrJson != null && node != null) {
            String jsonPath = "ocr-results/" + node.getUserId() + "/" + node.getId() + ".json";
            try (InputStream is = new ByteArrayInputStream(ocrJson.getBytes(StandardCharsets.UTF_8))) {
                minioService.uploadStream(jsonPath, is, "application/json");
            }
            node.setOcrResultPath(jsonPath);
        }

        return text;
    }

    private boolean isOfficeFile(String ext) {
        return "docx".equals(ext) || "doc".equals(ext) || 
               "xlsx".equals(ext) || "xls".equals(ext) || 
               "pptx".equals(ext) || "ppt".equals(ext);
    }

    private String parseTextFromJson(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return "";
        
        JSONObject json;
        try {
            json = new JSONObject(jsonStr);
        } catch (Exception e) {
            System.err.println("JSON Parsing Error: " + e.getMessage() + " | Content: " + (jsonStr.length() > 100 ? jsonStr.substring(0, 100) + "..." : jsonStr));
            return "";
        }
        
        StringBuilder resultBuilder = new StringBuilder();
        
        // 1. Office 文档格式 (doc_analysis_office)
        if (json.has("results")) {
            JSONArray results = json.getJSONArray("results");
            for (int i = 0; i < results.length(); i++) {
                JSONObject item = results.getJSONObject(i);
                if (item.has("words")) {
                    JSONObject wordsObj = item.optJSONObject("words");
                    if (wordsObj != null && wordsObj.has("word")) {
                        resultBuilder.append(wordsObj.getString("word")).append("\n");
                    }
                }
            }
        } 
        // 2. 通用文字识别格式 (general / general_basic)
        else if (json.has("words_result")) {
            JSONArray wordsResult = json.getJSONArray("words_result");
            for (int i = 0; i < wordsResult.length(); i++) {
                JSONObject item = wordsResult.getJSONObject(i);
                if (item.has("words")) {
                    resultBuilder.append(item.getString("words")).append("\n");
                }
            }
        }
        // 3. 自定义 Pages 格式 (PDF / 前端编辑保存后的格式)
        else if (json.has("pages")) {
            JSONArray pages = json.getJSONArray("pages");
            for (int i = 0; i < pages.length(); i++) {
                JSONObject page = pages.getJSONObject(i);
                if (page.has("boxes")) {
                    JSONArray boxes = page.getJSONArray("boxes");
                    for (int j = 0; j < boxes.length(); j++) {
                        JSONObject box = boxes.getJSONObject(j);
                        if (box.has("text")) {
                            resultBuilder.append(box.getString("text")).append("\n");
                        }
                    }
                }
            }
        }
        
        return resultBuilder.toString();
    }

    private static class PdfResult {
        String text;
        String json;
    }

    private PdfResult processPdfWithOcr(File pdfFile) throws Exception {
        StringBuilder fullText = new StringBuilder();
        JSONArray pagesJson = new JSONArray();

        try (PDDocument document = PDDocument.load(pdfFile)) {
            // 不做页数限制，全量识别
            int pageCount = document.getNumberOfPages();

            for (int i = 0; i < pageCount; i++) {
                // 创建单页 PDF
                try (PDDocument singlePageDoc = new PDDocument()) {
                    singlePageDoc.addPage(document.getPage(i));
                    File tempPdf = File.createTempFile("ocr-split-" + i, ".pdf");
                    try {
                        singlePageDoc.save(tempPdf);
                        // 调用百度 OCR 识别单页 PDF (使用通用文字识别含位置版)
                        String pageJsonStr = ocrService.recognizeGeneralJson(tempPdf);
                        JSONObject pageJson = new JSONObject(pageJsonStr);
                        
                        // 添加页码信息
                        pageJson.put("pageIndex", i);
                        pagesJson.put(pageJson);
                        
                        // 解析文本
                        fullText.append(parseTextFromJson(pageJsonStr)).append("\n");
                    } catch (Exception e) {
                        System.err.println("Page " + (i + 1) + " OCR Failed: " + e.getMessage());
                        JSONObject errorJson = new JSONObject();
                        errorJson.put("pageIndex", i);
                        errorJson.put("error", e.getMessage());
                        pagesJson.put(errorJson);
                    } finally {
                        Files.deleteIfExists(tempPdf.toPath());
                    }
                }
            }
        }

        JSONObject finalJson = new JSONObject();
        finalJson.put("pages", pagesJson);
        finalJson.put("total_pages", pagesJson.length());

        PdfResult result = new PdfResult();
        result.text = fullText.toString();
        result.json = finalJson.toString();
        return result;
    }

    private boolean isTextFile(String extension) {
        return "txt".equals(extension) || "md".equals(extension) || "json".equals(extension) 
                || "xml".equals(extension) || "html".equals(extension) || "csv".equals(extension);
    }

    private boolean isSameType(String type1, String type2) {
        if (type1.equals(type2)) return true;
        if (("jpg".equals(type1) && "jpeg".equals(type2)) || ("jpeg".equals(type1) && "jpg".equals(type2))) return true;
        if (("tif".equals(type1) && "tiff".equals(type2)) || ("tiff".equals(type1) && "tif".equals(type2))) return true;
        return false;
    }

    private String detectFileType(File file) {
        try (InputStream is = new FileInputStream(file)) {
            byte[] header = new byte[8];
            int read = is.read(header);
            if (read < 4) return null;

            // PNG: 89 50 4E 47
            if (header[0] == (byte)0x89 && header[1] == (byte)0x50 && header[2] == (byte)0x4E && header[3] == (byte)0x47) {
                return "png";
            }
            // JPEG: FF D8
            if (header[0] == (byte)0xFF && header[1] == (byte)0xD8) {
                return "jpg";
            }
            // PDF: %PDF (25 50 44 46)
            if (header[0] == (byte)0x25 && header[1] == (byte)0x50 && header[2] == (byte)0x44 && header[3] == (byte)0x46) {
                return "pdf";
            }
            // BMP: BM (42 4D)
            if (header[0] == (byte)0x42 && header[1] == (byte)0x4D) {
                return "bmp";
            }
            // GIF: GIF8 (47 49 46 38)
            if (header[0] == (byte)0x47 && header[1] == (byte)0x49 && header[2] == (byte)0x46 && header[3] == (byte)0x38) {
                return "gif";
            }
            // TIFF: II (49 49 2A 00) or MM (4D 4D 00 2A)
            if ((header[0] == (byte)0x49 && header[1] == (byte)0x49 && header[2] == (byte)0x2A && header[3] == (byte)0x00) ||
                (header[0] == (byte)0x4D && header[1] == (byte)0x4D && header[2] == (byte)0x00 && header[3] == (byte)0x2A)) {
                return "tif";
            }
            
            return null;
        } catch (IOException e) {
            return null;
        }
    }
}
