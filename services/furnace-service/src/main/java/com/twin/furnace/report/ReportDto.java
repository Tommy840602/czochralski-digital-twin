package com.twin.furnace.report;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** AI 分析報告結構化模型：LLM 回傳此 JSON，前端渲染 + POI 產 docx 共用 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReportDto {
    public String furnaceId;
    public String ingotNo;
    public String stage;        // 階段 / operationMode
    public String verdict;      // OK | 疑似NG | NG | 資料不足
    public String summary;      // 2~4 句總結
    public String generatedAt;
    public List<Section>  sections;
    public List<KeyPoint> keyPoints;
    public List<String>   recommendations;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Section {
        public String heading;
        public String content;
    }
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class KeyPoint {
        public String param;
        public String value;
        public String note;
    }
}
