package com.twin.alarm.controller;

import com.twin.alarm.entity.SpcBaseline;
import com.twin.alarm.service.SpcBackfillService;
import com.twin.alarm.service.SpcBaselineService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/spc")
@RequiredArgsConstructor
public class SpcController {

    private static final Logger log = LoggerFactory.getLogger(SpcController.class);

    private final SpcBaselineService baselineService;

    /** 拿某爐所有參數的 baseline */
    @GetMapping("/baseline")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public List<SpcBaseline> listBaseline(@RequestParam String furnaceId) {
        return baselineService.listByFurnace(furnaceId);
    }

    /** 拿指定爐 + 指定參數的 baseline */
    @GetMapping("/baseline/one")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public SpcBaseline getBaseline(@RequestParam String furnaceId, @RequestParam String paramName) {
        Optional<SpcBaseline> b = baselineService.get(furnaceId, paramName);
        return b.orElse(null);
    }

    /** 拿所有可用參數清單 */
    @GetMapping("/params")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public Map<String, String> availableParams() {
        return SpcBaselineService.PARAM_COLUMN;
    }

    /** 手動重算所有 baseline（ADMIN/ENGINEER 才能觸發） */
    @PostMapping("/baseline/rebuild")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public ResponseEntity<?> rebuildAll() {
        log.info("Manual baseline rebuild triggered");
        java.util.concurrent.CompletableFuture.runAsync(baselineService::rebuildAll);
        return ResponseEntity.accepted().body(Map.of("status", "ok", "message", "重算已開始，背景執行中"));
    }

    /** 重算單一 baseline */
    @PostMapping("/baseline/rebuild/one")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public SpcBaseline rebuildOne(@RequestParam String furnaceId, @RequestParam String paramName) {
        return baselineService.rebuild(furnaceId, paramName);
    }

// ---- 新增：violation 查詢 ----

    @org.springframework.beans.factory.annotation.Autowired
    private com.twin.alarm.repository.SpcViolationRepository violationRepo;

    @GetMapping("/violation/recent")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public java.util.List<com.twin.alarm.entity.SpcViolation> recent(
            @RequestParam(defaultValue = "60") int minutes) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        return violationRepo.findRecent(since);
    }

    @GetMapping("/violation/byFurnace")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public java.util.List<com.twin.alarm.entity.SpcViolation> byFurnace(
            @RequestParam String furnaceId,
            @RequestParam(defaultValue = "60") int minutes) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        return violationRepo.findByFurnace(furnaceId, since);
    }

    @GetMapping("/violation/statistics")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public java.util.Map<Integer, Long> statistics(
            @RequestParam(defaultValue = "1440") int minutes,
            @RequestParam(required = false) String furnaceId) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        java.util.Map<Integer, Long> map = new java.util.HashMap<>();
        List<Object[]> rows = (furnaceId == null || furnaceId.isBlank())
                ? violationRepo.countByRuleSince(since)
                : violationRepo.countByRuleSinceAndFurnace(since, furnaceId);
        for (Object[] row : rows) {
            map.put((Integer) row[0], (Long) row[1]);
        }
        return map;
    }

    private final SpcBackfillService backfillService;  // 加進建構子注入

    @PostMapping("/violation/backfill")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public ResponseEntity<?> backfillViolations() {
        log.info("Manual SPC violation backfill triggered");
        java.util.concurrent.CompletableFuture.runAsync(backfillService::backfillAll);
        return ResponseEntity.accepted().body(Map.of("status", "ok", "message", "回溯計算已開始，背景執行中"));
    }

    /** 重算單一 (furnace, param) 的 violation backfill，避免一次跑 30 組炸記憶體 */
    @PostMapping("/violation/backfill/one")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public ResponseEntity<?> backfillOne(
            @RequestParam String furnaceId,
            @RequestParam String paramName) {
        log.info("Manual single backfill triggered: furnace={} param={}", furnaceId, paramName);
        backfillService.backfillOne(furnaceId, paramName);
        return ResponseEntity.ok(Map.of("status", "ok", "furnaceId", furnaceId, "paramName", paramName));
    }
}
