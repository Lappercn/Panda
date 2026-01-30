package org.AI.panda.service;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

@Service
public class OcrService {

    @Value("${panda.ocr.baidu.api-key}")
    private String apiKey;

    @Value("${panda.ocr.baidu.secret-key}")
    private String secretKey;

    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    
    // 百度 OCR 限制：Base64 编码后小于 4MB (约等于原始文件 3MB)
    private static final long MAX_FILE_SIZE = 3 * 1024 * 1024;
    // PDF 文件限制 10MB
    private static final long MAX_PDF_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;

    /**
     * 办公文档识别 (支持 PDF, Word, Excel, PPT, OFD 和 图片) - 返回 JSON 原始结果
     */
    public String recognizeOfficeDocJson(File file) throws Exception {
        // 1. 获取 Access Token
        String accessToken = getAccessToken();

        // 2. 判断文件类型
        String fileName = file.getName().toLowerCase();
        boolean isDoc = fileName.endsWith(".pdf") || fileName.endsWith(".ofd") ||
                        fileName.endsWith(".docx") || fileName.endsWith(".doc") ||
                        fileName.endsWith(".xlsx") || fileName.endsWith(".xls") ||
                        fileName.endsWith(".pptx") || fileName.endsWith(".ppt");

        byte[] fileContent;
        String base64;
        
        FormBody.Builder bodyBuilder = new FormBody.Builder();

        if (isDoc) {
            fileContent = Files.readAllBytes(file.toPath());
            if (fileContent.length > MAX_PDF_FILE_SIZE) {
                throw new IllegalArgumentException("文档文件过大，超过 10MB 限制");
            }
            base64 = Base64.getEncoder().encodeToString(fileContent);
            // 百度 API 办公文档识别接口，文档类统一使用 pdf_file 参数 (支持 PDF, OFD, Word, Excel, PPT)
            bodyBuilder.add("pdf_file", base64);
        } else {
            // 图片处理
            fileContent = preprocessImage(file);
            base64 = Base64.getEncoder().encodeToString(fileContent);
            bodyBuilder.add("image", base64);
        }

        // 3. 构建请求 Body
        RequestBody body = bodyBuilder.build();

        // 4. 构建 Request (办公文档识别接口)
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/rest/2.0/ocr/v1/doc_analysis_office?access_token=" + accessToken)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .build();

        // 5. 执行请求并返回 JSON
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("百度 OCR 请求失败: HTTP " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JSONObject json;
            try {
                json = new JSONObject(responseBody);
            } catch (Exception e) {
                String errorPreview = responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
                throw new RuntimeException("百度 OCR 响应解析失败: 非法 JSON. 内容预览: " + errorPreview, e);
            }

            // 检查是否有错误码
            if (json.has("error_code")) {
                throw new RuntimeException("百度 OCR API 返回错误: " + json.optString("error_msg") + " (Code: " + json.optInt("error_code") + ")");
            }
            return responseBody;
        }
    }

    /**
     * 办公文档识别 (支持 PDF 和 图片)
     * 适用于含表格、手写文字等复杂排版的文档
     */
    public String recognizeOfficeDoc(File file) throws Exception {
        String jsonStr = recognizeOfficeDocJson(file);
        JSONObject json = new JSONObject(jsonStr);
        
        // 解析结果
        StringBuilder resultBuilder = new StringBuilder();
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
        return resultBuilder.toString();
    }

    /**
     * 通用文字识别（含位置版） - 返回 JSON 原始结果
     * 适用于图片、PDF、OFD 文件
     */
    public String recognizeGeneralJson(File file) throws Exception {
        // 1. 获取 Access Token
        String accessToken = getAccessToken();

        // 2. 判断文件类型并读取内容
        String fileName = file.getName().toLowerCase();
        byte[] fileContent = Files.readAllBytes(file.toPath());
        
        // 图片预处理 (仅针对图片格式)
        boolean isPdf = fileName.endsWith(".pdf");
        boolean isOfd = fileName.endsWith(".ofd");
        boolean isImage = !isPdf && !isOfd;

        if (isImage) {
            fileContent = preprocessImage(file);
        }
        
        String base64 = Base64.getEncoder().encodeToString(fileContent);

        // 3. 构建请求 Body
        FormBody.Builder bodyBuilder = new FormBody.Builder();
        
        if (isPdf) {
            bodyBuilder.add("pdf_file", base64);
        } else if (isOfd) {
            bodyBuilder.add("ofd_file", base64);
        } else {
            bodyBuilder.add("image", base64);
        }
        
        bodyBuilder.add("detect_direction", "true")      // 检测朝向
                   .add("detect_language", "true")       // 检测语言
                   .add("paragraph", "true")             // 输出段落信息
                   .add("vertexes_location", "true");    // 返回顶点位置

        RequestBody body = bodyBuilder.build();

        // 4. 构建 Request
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/rest/2.0/ocr/v1/general?access_token=" + accessToken)
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Accept", "application/json")
                .build();

        // 5. 执行请求
        return executeAndGetJson(request); 
    }

    /**
     * 使用百度 OCR API 识别图片文字 (通用文字识别 - 标准版) -> 已废弃，请使用 recognizeGeneralJson
     *
     * @param imageFile 图片文件
     * @return 识别结果字符串
     * @throws Exception 异常
     */
    public String recognizeText(File imageFile) throws Exception {
        String jsonStr = recognizeGeneralJson(imageFile);
        JSONObject json = new JSONObject(jsonStr);
        
        StringBuilder resultBuilder = new StringBuilder();
        if (json.has("words_result")) {
            JSONArray wordsResult = json.getJSONArray("words_result");
            for (int i = 0; i < wordsResult.length(); i++) {
                JSONObject word = wordsResult.getJSONObject(i);
                resultBuilder.append(word.getString("words")).append("\n");
            }
        }
        return resultBuilder.toString();
    }

    private String executeAndGetJson(Request request) throws IOException {
        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("百度 OCR 请求失败: HTTP " + response.code() + " " + response.message());
            }

            String responseBody = response.body().string();
            JSONObject json;
            try {
                json = new JSONObject(responseBody);
            } catch (Exception e) {
                String errorPreview = responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
                throw new RuntimeException("百度 OCR 响应解析失败: 非法 JSON. 内容预览: " + errorPreview, e);
            }

            // 检查是否有错误码
            if (json.has("error_code")) {
                throw new RuntimeException("百度 OCR API 返回错误: " + json.optString("error_msg") + " (Code: " + json.optInt("error_code") + ")");
            }
            return responseBody;
        }
    }

    /**
     * 预处理图片：检查大小和尺寸，必要时进行压缩或缩放
     */
    private byte[] preprocessImage(File imageFile) throws IOException {
        long fileSize = imageFile.length();
        BufferedImage image = ImageIO.read(imageFile);
        
        if (image == null) {
            throw new IOException("无法读取图片文件");
        }

        // 检查尺寸
        int width = image.getWidth();
        int height = image.getHeight();
        boolean needsResize = (width > MAX_DIMENSION || height > MAX_DIMENSION);
        
        // 检查文件大小
        boolean needsCompression = (fileSize > MAX_FILE_SIZE);

        if (!needsResize && !needsCompression) {
            return Files.readAllBytes(imageFile.toPath());
        }

        // 如果需要调整
        if (needsResize) {
            // 计算缩放比例
            double scale = Math.min((double) MAX_DIMENSION / width, (double) MAX_DIMENSION / height);
            int newWidth = (int) (width * scale);
            int newHeight = (int) (height * scale);
            
            BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            // 绘制白色背景，防止透明 PNG 变黑
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, newWidth, newHeight);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
            g.dispose();
            image = resized;
        } else if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            // 即使不需要缩放，如果格式不是 RGB (例如 PNG 带透明通道, 或 CMYK)，也必须强制转为 RGB JPEG 兼容格式
            BufferedImage converted = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = converted.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(image, 0, 0, null);
            g.dispose();
            image = converted;
        }

        // 压缩质量直到满足大小限制
        float quality = 0.9f;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // 获取 JPEG Writer (通常 JPEG 压缩效果最好)
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) throw new IllegalStateException("No writers found");
        ImageWriter writer = writers.next();
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            }

            while (quality > 0.1f) {
                baos.reset();
                if (param.canWriteCompressed()) {
                    param.setCompressionQuality(quality);
                }
                writer.write(null, new IIOImage(image, null, null), param);
                
                if (baos.size() < MAX_FILE_SIZE) {
                    break;
                }
                quality -= 0.1f;
            }
        } finally {
            writer.dispose();
        }

        return baos.toByteArray();
    }

    /**
     * 获取百度 AI 开放平台 Access Token
     */
    private String getAccessToken() throws IOException {
        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", apiKey)
                .add("client_secret", secretKey)
                .build();

        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token")
                .post(body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        try (Response response = HTTP_CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("获取百度 Access Token 失败: HTTP " + response.code());
            }
            String responseStr = response.body().string();
            JSONObject json = new JSONObject(responseStr);
            if (json.has("error")) {
                 throw new RuntimeException("获取百度 Access Token 错误: " + json.optString("error_description"));
            }
            return json.getString("access_token");
        }
    }
}
