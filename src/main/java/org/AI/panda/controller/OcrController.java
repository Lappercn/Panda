package org.AI.panda.controller;

import org.AI.panda.common.Result;
import org.AI.panda.service.OcrService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/ai/ocr")
public class OcrController {

    private final OcrService ocrService;

    public OcrController(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    /**
     * OCR 识别接口
     * POST http://localhost:8080/ai/ocr/recognize
     * Content-Type: multipart/form-data
     * form-data: file=@/path/to/image.png
     */
    @PostMapping("/recognize")
    public Result<Map<String, String>> recognize(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.error("文件为空");
        }

        Path tempFile = null;
        try {
            // 创建临时文件
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            tempFile = Files.createTempFile("ocr-" + UUID.randomUUID(), extension);
            file.transferTo(tempFile.toFile());

            // 调用 OCR 服务
            String result = ocrService.recognizeText(tempFile.toFile());

            return Result.success(Map.of("text", result));

        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
