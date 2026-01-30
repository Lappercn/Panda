package org.AI.panda;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class VolcengineTest {

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("VOLC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Missing VOLC_API_KEY env var");
        }
        String apiUrl = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
        String model = "ep-20260127095308-s599v";

        String promptContent = "请用 3 条要点总结以下内容：\n这是一段用于接口连通性测试的占位文本。";

        // 构造 JSON (手动拼接最稳妥，防止转义问题)
        // 注意：Java 15+ 支持 Text Block (""")，但为了兼容性这里用普通字符串拼接，或者直接转义换行
        String jsonBody = String.format("""
                {
                    "model": "%s",
                    "messages": [
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "temperature": 0.7
                }
                """, model, promptContent.replace("\n", "\\n"));

        System.out.println(">>> 正在直接调用火山引擎 API...");
        System.out.println(">>> 模型: " + model);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n=== 模型原生回答 ===");
        System.out.println(response.body());
        System.out.println("====================");
    }
}
