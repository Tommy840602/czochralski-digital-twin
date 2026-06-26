package com.twin.furnace.report;

import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;

/** 用 Apache POI 把 ReportDto 產生 .docx（與前端顯示同一份資料源） */
@Service
public class DocxService {

    public byte[] build(ReportDto r) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 標題
            XWPFParagraph title = doc.createParagraph();
            title.setAlignment(ParagraphAlignment.CENTER);
            run(title, "長晶爐 " + nz(r.furnaceId) + " 製程分析報告", 20, true, "1F2937");

            // 副標 / meta
            XWPFParagraph meta = doc.createParagraph();
            meta.setAlignment(ParagraphAlignment.CENTER);
            run(meta, "INGOT " + nz(r.ingotNo) + "    階段：" + nz(r.stage)
                    + "    產生時間：" + nz(r.generatedAt), 9, false, "6B7280");

            // 判定 verdict
            XWPFParagraph vp = doc.createParagraph();
            vp.setSpacingBefore(160);
            run(vp, "判定結果： ", 12, true, "111827");
            run(vp, nz(r.verdict), 12, true, verdictColor(r.verdict));

            // 摘要
            if (notBlank(r.summary)) {
                heading(doc, "摘要");
                para(doc, r.summary);
            }

            // 章節
            if (r.sections != null) {
                for (ReportDto.Section s : r.sections) {
                    if (s == null) continue;
                    heading(doc, nz(s.heading));
                    para(doc, nz(s.content));
                }
            }

            // 關鍵數值表
            if (r.keyPoints != null && !r.keyPoints.isEmpty()) {
                heading(doc, "關鍵數值");
                XWPFTable t = doc.createTable();
                t.setWidth("100%");
                XWPFTableRow h = t.getRow(0);
                cell(h.getCell(0), "參數", true);
                h.addNewTableCell(); cell(h.getCell(1), "數值", true);
                h.addNewTableCell(); cell(h.getCell(2), "判讀", true);
                for (ReportDto.KeyPoint kp : r.keyPoints) {
                    if (kp == null) continue;
                    XWPFTableRow row = t.createRow();
                    cell(row.getCell(0), nz(kp.param), false);
                    cell(row.getCell(1), nz(kp.value), false);
                    cell(row.getCell(2), nz(kp.note), false);
                }
            }

            // 建議
            if (r.recommendations != null && !r.recommendations.isEmpty()) {
                heading(doc, "建議事項");
                for (String rec : r.recommendations) {
                    XWPFParagraph p = doc.createParagraph();
                    p.setIndentationLeft(360);
                    run(p, "• " + nz(rec), 11, false, "111827");
                }
            }

            // 免責
            XWPFParagraph foot = doc.createParagraph();
            foot.setSpacingBefore(240);
            run(foot, "本報告由 AI 依《NG分析注意事項》就即時快照生成，僅供參考；"
                    + "需歷史逐點資料才能完成的判讀已於內文標註。", 8, false, "9CA3AF");

            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("產生 docx 失敗：" + e.getMessage(), e);
        }
    }

    // ── helpers ──
    private void heading(XWPFDocument doc, String text) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingBefore(200); p.setSpacingAfter(40);
        p.setBorderBottom(Borders.SINGLE);
        run(p, text, 13, true, "0E7490");
    }
    private void para(XWPFDocument doc, String text) {
        for (String line : text.split("\\r?\\n")) {
            XWPFParagraph p = doc.createParagraph();
            p.setSpacingAfter(20);
            run(p, line, 11, false, "1F2937");
        }
    }
    private void run(XWPFParagraph p, String text, int size, boolean bold, String hexColor) {
        XWPFRun r = p.createRun();
        r.setText(text); r.setFontSize(size); r.setBold(bold);
        if (hexColor != null) r.setColor(hexColor);
        r.setFontFamily("Microsoft JhengHei");
    }
    private void cell(XWPFTableCell c, String text, boolean header) {
        XWPFParagraph p = c.getParagraphs().isEmpty() ? c.addParagraph() : c.getParagraphs().get(0);
        run(p, text, header ? 10 : 10, header, header ? "FFFFFF" : "1F2937");
        if (header) c.setColor("0E7490");
    }
    private String verdictColor(String v) {
        if (v == null) return "6B7280";
        return switch (v) {
            case "NG" -> "DC2626";
            case "疑似NG" -> "D97706";
            case "OK" -> "059669";
            default -> "6B7280";
        };
    }
    private boolean notBlank(String s) { return s != null && !s.isBlank(); }
    private String nz(String v) { return v == null ? "—" : v; }
}
