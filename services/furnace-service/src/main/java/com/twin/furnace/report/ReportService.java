package com.twin.furnace.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.twin.furnace.dto.FurnaceLatestDto;
import com.twin.furnace.service.FurnaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final ObjectMapper M = new ObjectMapper();
    private static final String API = "https://api.openai.com/v1/chat/completions";

    private final FurnaceService furnaceService;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    @Value("${openai.api-key:}")          private String apiKey;
    @Value("${openai.model:gpt-4o}")      private String model;

    private volatile String ngDoc;         // 技術文件（快取）

    public ReportService(FurnaceService furnaceService) {
        this.furnaceService = furnaceService;
    }

    public ReportDto generate(String furnaceId) {
        if (apiKey == null || apiKey.isBlank())
            throw new IllegalStateException("未設定 ANTHROPIC_API_KEY，無法生成報告");

        FurnaceLatestDto live = furnaceService.getLatest(furnaceId)
                .orElseThrow(() -> new IllegalArgumentException("查無爐子即時資料：" + furnaceId));

        String system = buildSystemPrompt();
        String user   = buildSnapshot(furnaceId, live);

        String raw = callOpenAI(system, user);
        ReportDto dto = parseReport(raw);
        dto.furnaceId   = furnaceId;
        dto.ingotNo     = live.getIngotNo();
        dto.stage       = live.getOperationMode();
        dto.generatedAt = OffsetDateTime.now().toString();
        return dto;
    }

    // ── 載入 NG 技術文件當知識庫 ──
    private String loadNgDoc() {
        if (ngDoc != null) return ngDoc;
        try {
            var res = new ClassPathResource("knowledge/ng-analysis.txt");
            ngDoc = new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("載入 NG 技術文件失敗，改用空知識庫：{}", e.getMessage());
            ngDoc = "(技術文件缺失)";
        }
        return ngDoc;
    }

    private String buildSystemPrompt() {
        return """
            你是資深長晶（Czochralski）製程 NG 分析師。以下三引號內為公司的《NG分析注意事項》技術文件，\
            請『嚴格』依其判讀邏輯、術語與輸出格式進行分析，不可臆測未提供的資料。

            \"\"\"
            """ + loadNgDoc() + """
            \"\"\"

            重要限制：本次僅提供『單點即時快照』（無歷史逐點資料、無每100mm代表點、無OK/NG比較段）。\
            因此：
            - 僅能做即時狀態評估與風險提示。
            - 凡文件要求以歷史/逐點/OK對照才能完成的判讀（如每100mm取點、Dia震盪歷時、OK vs NG代表點比較），\
              一律在該項標註「需歷史資料，本次快照無法判定」，不可編造數值或結論。
            - verdict 僅能是 OK / 疑似NG / NG / 資料不足 其中之一；快照不足以斷定時用「資料不足」。

            只輸出一個 JSON 物件，不要 markdown 圍欄、不要任何多餘文字。JSON schema：
            {
              "verdict": "OK|疑似NG|NG|資料不足",
              "summary": "2~4句總結",
              "sections": [ { "heading": "章節標題", "content": "內容（可多行）" } ],
              "keyPoints": [ { "param": "參數名", "value": "數值", "note": "判讀說明" } ],
              "recommendations": [ "建議1", "建議2" ]
            }
            sections 請涵蓋：階段判定、GR_mean、Crucible Position vs RW、CP/Power/BPUL-BPLL、Dia/temp29 即時狀態、\
            以及該階段（Neck/Crown/Shoulder/Body）對應的注意事項。所有內容用繁體中文。
            """;
    }

    private String buildSnapshot(String id, FurnaceLatestDto live) {
        StringBuilder sb = new StringBuilder();
        sb.append("爐號：").append(id).append('\n');
        sb.append("INGOT：").append(nz(live.getIngotNo())).append('\n');
        sb.append("Operation Mode / 階段：").append(nz(live.getOperationMode())).append('\n');
        sb.append("即時快照（全感測欄位）：\n");
        Map<String, Double> m = live.getMetrics();
        if (m != null) {
            for (Map.Entry<String, Double> e : m.entrySet())
                sb.append("  - ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        sb.append("\n請依《NG分析注意事項》分析此即時快照並輸出指定 JSON。");
        return sb.toString();
    }

    private String callOpenAI(String system, String user) {
        final int MAX_RETRY = 4;
        RuntimeException last = null;

        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("model", model);
                body.put("max_tokens", 2000);
                body.put("messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user",   "content", user)
                ));
                body.put("response_format", Map.of("type", "json_object"));

                HttpRequest req = HttpRequest.newBuilder(URI.create(API))
                        .timeout(Duration.ofSeconds(90))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("content-type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                M.writeValueAsString(body), StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

                // ── 429：限流，讀建議等待秒數後退避重試 ──
                if (resp.statusCode() == 429) {
                    long waitMs = parseRetryWaitMs(resp);
                    if (attempt < MAX_RETRY) {
                        log.warn("OpenAI 429 限流，第 {} 次，等待 {} ms 後重試", attempt, waitMs);
                        sleep(waitMs);
                        continue;                       // 重試
                    }
                    // 重試用盡，丟出可讀錯誤（前端會看到這段）
                    JsonNode err = M.readTree(resp.body());
                    String msg = err.path("error").path("message").asText(resp.body());
                    throw new RuntimeException("OpenAI API 錯誤(429)：" + msg);
                }

                JsonNode root = M.readTree(resp.body());
                if (resp.statusCode() >= 300 || root.has("error")) {
                    String msg = root.path("error").path("message").asText(resp.body());
                    throw new RuntimeException("OpenAI API 錯誤(" + resp.statusCode() + ")：" + msg);
                }
                return root.path("choices").get(0).path("message").path("content").asText();

            } catch (RuntimeException e) {
                throw e;                                // 非 429 的錯誤直接往外丟，不重試
            } catch (Exception e) {
                // 網路類暫時性錯誤：記下來，下一輪重試
                last = new RuntimeException("呼叫 OpenAI 失敗：" + e.getMessage(), e);
                log.warn("呼叫 OpenAI 連線異常，第 {} 次：{}", attempt, e.getMessage());
                if (attempt < MAX_RETRY) { sleep(2000L * attempt); continue; }
            }
        }
        throw (last != null) ? last : new RuntimeException("呼叫 OpenAI 失敗：重試用盡");
    }

    // 從 429 回應解出建議等待毫秒：優先讀 Retry-After header，其次解 body 的 "try again in Xs"，都沒有則預設 20 秒
    private long parseRetryWaitMs(HttpResponse<String> resp) {
        // 1) Retry-After header（秒）
        Optional<String> ra = resp.headers().firstValue("retry-after");
        if (ra.isPresent()) {
            try { return (long) (Double.parseDouble(ra.get().trim()) * 1000) + 1000; }
            catch (NumberFormatException ignore) { /* 往下 fallback */ }
        }
        // 2) body 的 "Please try again in 29.522s"
        try {
            String b = resp.body();
            var m = java.util.regex.Pattern
                    .compile("try again in ([\\d.]+)s", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(b);
            if (m.find()) return (long) (Double.parseDouble(m.group(1)) * 1000) + 1000;
        } catch (Exception ignore) { /* 往下 fallback */ }
        // 3) 預設
        return 20_000L;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("重試等待被中斷");
        }
    }

    private ReportDto parseReport(String raw) {
        String s = raw == null ? "" : raw.trim();
        // 去除可能的 ```json 圍欄
        if (s.startsWith("```")) s = s.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("```\\s*$", "").trim();
        int a = s.indexOf('{'), b = s.lastIndexOf('}');
        if (a >= 0 && b > a) s = s.substring(a, b + 1);
        try {
            return M.readValue(s, ReportDto.class);
        } catch (Exception e) {
            throw new RuntimeException("LLM 回傳非合法 JSON：" + e.getMessage());
        }
    }

    private static String nz(String v) { return v == null ? "—" : v; }
}
