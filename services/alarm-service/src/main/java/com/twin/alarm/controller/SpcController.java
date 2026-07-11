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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/spc")
@RequiredArgsConstructor
public class SpcController {

    private static final Logger log = LoggerFactory.getLogger(SpcController.class);

    private final SpcBaselineService baselineService;
    private final SpcBackfillService backfillService;

    /** 每個爐子各自獨立的忙碌狀態，互不影響 */
    private final Set<String> busyFurnaces = ConcurrentHashMap.newKeySet();

    private boolean tryLock(String furnaceId) {
        return busyFurnaces.add(furnaceId);
    }

    private void unlock(String furnaceId) {
        busyFurnaces.remove(furnaceId);
    }

    @GetMapping("/baseline")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public List<SpcBaseline> listBaseline(@RequestParam String furnaceId) {
        return baselineService.listByFurnace(furnaceId);
    }

    @GetMapping("/baseline/one")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public SpcBaseline getBaseline(@RequestParam String furnaceId,
                                   @RequestParam String paramName,
                                   @RequestParam String operationMode) {
        Optional<SpcBaseline> b = baselineService.get(furnaceId, paramName, operationMode);
        return b.orElse(null);
    }

    @GetMapping("/params")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public Map<String, String> availableParams() {
        return SpcBaselineService.PARAM_COLUMN;
    }

    /** 該爐近 7 天實際出現過的製程階段（baseline 以此為單位建立） */
    @GetMapping("/modes")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public List<String> modes(@RequestParam String furnaceId) {
        return baselineService.listModes(furnaceId);
    }

    /** 重算單一爐子（所有參數），非同步執行，每爐獨立上鎖 */
    @PostMapping("/baseline/rebuild/furnace")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public ResponseEntity<?> rebuildFurnace(@RequestParam String furnaceId) {
        if (!tryLock(furnaceId)) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "conflict",
                    "message", "此爐子已有計算正在進行中，請稍候再試"));
        }
        log.info("Manual furnace baseline rebuild triggered: {}", furnaceId);
        CompletableFuture.runAsync(() -> {
            try {
                // 先重建各 mode 的 baseline，再用新管制界重算歷史違規，
                // 否則畫面上的統計還是舊 baseline 產生的數字
                baselineService.rebuildFurnace(furnaceId);
                violationRepo.deleteByFurnaceId(furnaceId);
                backfillService.backfillFurnace(furnaceId);
            } catch (Exception e) {
                log.error("Furnace baseline rebuild failed: {}", furnaceId, e);
            } finally {
                unlock(furnaceId);
            }
        });
        return ResponseEntity.accepted().body(Map.of("status", "ok", "message", "重算已開始，背景執行中"));
    }

    /** 重算「所有爐子」——初次建立或門檻調整後用，免得一爐一爐點 */
    @PostMapping("/baseline/rebuild/all")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public ResponseEntity<?> rebuildAll() {
        List<String> furnaces = List.of("D1", "D3", "DB", "F7", "FA");
        List<String> locked = new java.util.ArrayList<>();
        for (String f : furnaces) {
            if (tryLock(f)) locked.add(f);
        }
        if (locked.isEmpty()) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "conflict", "message", "所有爐子都有計算進行中，請稍候再試"));
        }
        log.info("Manual rebuild triggered for all furnaces: {}", locked);
        CompletableFuture.runAsync(() -> {
            for (String f : locked) {
                try {
                    baselineService.rebuildFurnace(f);
                    violationRepo.deleteByFurnaceId(f);
                    backfillService.backfillFurnace(f);
                } catch (Exception e) {
                    log.error("Furnace baseline rebuild failed: {}", f, e);
                } finally {
                    unlock(f);
                }
            }
            log.info("All-furnace baseline rebuild finished");
        });
        return ResponseEntity.accepted().body(Map.of(
                "status", "ok", "message", "全部爐子重算已開始", "furnaces", locked));
    }

    /** 查詢某爐子目前是否有計算正在進行（重算或 σ 調整共用） */
    @GetMapping("/baseline/rebuild/status")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public Map<String, Object> rebuildStatus(@RequestParam String furnaceId) {
        return Map.of("inProgress", busyFurnaces.contains(furnaceId));
    }

    @org.springframework.beans.factory.annotation.Autowired
    private com.twin.alarm.repository.SpcViolationRepository violationRepo;

    @GetMapping("/violation/recent")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public List<com.twin.alarm.entity.SpcViolation> recent(
            @RequestParam(defaultValue = "60") int minutes) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        return violationRepo.findRecent(since);
    }

    @GetMapping("/violation/byFurnace")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public List<com.twin.alarm.entity.SpcViolation> byFurnace(
            @RequestParam String furnaceId,
            @RequestParam(defaultValue = "60") int minutes) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        return violationRepo.findByFurnace(furnaceId, since);
    }

    @GetMapping("/violation/statistics")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public Map<Integer, Long> statistics(
            @RequestParam(defaultValue = "1440") int minutes,
            @RequestParam(required = false) String furnaceId,
            @RequestParam(required = false) String paramName) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        Map<Integer, Long> map = new java.util.HashMap<>();
        List<Object[]> rows;
        if (paramName != null && !paramName.isBlank() && furnaceId != null && !furnaceId.isBlank()) {
            rows = violationRepo.countByRuleSinceAndFurnaceAndParam(since, furnaceId, paramName);
        } else if (furnaceId != null && !furnaceId.isBlank()) {
            rows = violationRepo.countByRuleSinceAndFurnace(since, furnaceId);
        } else {
            rows = violationRepo.countByRuleSince(since);
        }
        for (Object[] row : rows) {
            map.put((Integer) row[0], (Long) row[1]);
        }
        return map;
    }

    /** 調整某爐某參數的 σ 寬鬆度，非同步執行（調整 UCL/LCL + 清空舊 violation + 重跑 backfill），與重算共用同一把爐鎖 */
    @PatchMapping("/baseline/sigma-multiplier")
    @PreAuthorize("hasAuthority('SPC_VIEW')")
    public ResponseEntity<?> adjustSigmaMultiplier(
            @RequestParam String furnaceId,
            @RequestParam String paramName,
            @RequestParam String operationMode,
            @RequestParam double multiplier) {
        if (multiplier <= 0 || multiplier > 5) {
            return ResponseEntity.badRequest().body(Map.of("message", "倍數必須介於 0 到 5 之間"));
        }
        if (!tryLock(furnaceId)) {
            return ResponseEntity.status(409).body(Map.of(
                    "status", "conflict",
                    "message", "此爐子已有計算正在進行中，請稍候再試"));
        }
        log.info("Sigma multiplier adjust triggered: furnace={} param={} mode={} multiplier={}",
                furnaceId, paramName, operationMode, multiplier);
        CompletableFuture.runAsync(() -> {
            try {
                baselineService.adjustSigmaMultiplier(furnaceId, paramName, operationMode, multiplier);
                violationRepo.deleteByFurnaceIdAndParamName(furnaceId, paramName);
                backfillService.backfillOne(furnaceId, paramName, operationMode);
            } catch (Exception e) {
                log.error("Sigma multiplier adjust failed: furnace={} param={} mode={}",
                        furnaceId, paramName, operationMode, e);
            } finally {
                unlock(furnaceId);
            }
        });
        return ResponseEntity.accepted().body(Map.of("status", "ok", "message", "調整已開始，背景執行中"));
    }
}
