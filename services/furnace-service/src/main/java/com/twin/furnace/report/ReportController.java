package com.twin.furnace.report;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/furnaces")
public class ReportController {

    private final ReportService reportService;
    private final DocxService   docxService;

    public ReportController(ReportService r, DocxService d) {
        this.reportService = r; this.docxService = d;
    }

    /** 生成分析報告（呼叫 LLM，回結構化 JSON 給前端顯示） */
    @PostMapping("/{id}/report")
    @PreAuthorize("hasAuthority('REPORT_GEN')")
    public ResponseEntity<?> generate(@PathVariable String id) {
        try {
            return ResponseEntity.ok(reportService.generate(id));
        } catch (IllegalStateException e) {           // 缺 API key
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(err(e));
        } catch (IllegalArgumentException e) {         // 查無爐子
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(err(e));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(err(e));
        }
    }

    /** 由前端把已生成的報告 JSON 回傳，POI 產 .docx 下載（不再呼叫 LLM，確保與畫面一致） */
    @PostMapping("/reports/docx")
    @PreAuthorize("hasAuthority('REPORT_GEN')")
    public ResponseEntity<byte[]> docx(@RequestBody ReportDto report) {
        byte[] bytes = docxService.build(report);
        String fname = "report_" + safe(report.furnaceId) + "_" + System.currentTimeMillis() + ".docx";
        String cd = "attachment; filename=\"" + fname + "\"; filename*=UTF-8''"
                + URLEncoder.encode(fname, StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd)
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    @PostMapping("/reports/zip")
    @PreAuthorize("hasAuthority('REPORT_GEN')")
    public ResponseEntity<byte[]> downloadZip(@RequestBody List<ReportDto> reports) throws Exception {
        if (reports == null || reports.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ByteArrayOutputStream zipBytes = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipBytes, StandardCharsets.UTF_8)) {
            for (ReportDto dto : reports) {
                // 用你既有的 DocxService 把單份 report 轉成 docx byte[]
                byte[] docx = docxService.build(dto);   // ← 換成你 DocxService 實際的方法名

                String fileName = "report_" + safe(dto.furnaceId) + ".docx";
                zos.putNextEntry(new ZipEntry(fileName));
                zos.write(docx);
                zos.closeEntry();
            }
        }

        byte[] body = zipBytes.toByteArray();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDispositionFormData("attachment", "furnace_reports.zip");
        headers.setContentLength(body.length);
        return new ResponseEntity<>(body, headers, 200);
    }

    // 檔名安全處理（避免 furnaceId 有奇怪字元）
    private static String safe(String s) {
        return s == null ? "unknown" : s.replaceAll("[^A-Za-z0-9_-]", "_");
    }

    private java.util.Map<String, String> err(Exception e) {
        return java.util.Map.of("error", e.getMessage() == null ? e.toString() : e.getMessage());
    }
    //private String safe(String s) { return s == null ? "x" : s.replaceAll("[^A-Za-z0-9_-]", ""); }
}
