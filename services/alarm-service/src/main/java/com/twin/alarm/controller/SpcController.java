package com.twin.alarm.controller;

import com.twin.alarm.entity.SpcBaseline;
import com.twin.alarm.service.SpcBaselineService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    public Map<String, Object> rebuildAll() {
        log.info("Manual baseline rebuild triggered");
        baselineService.rebuildAll();
        return Map.of("status", "ok", "message", "baseline rebuild completed");
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
            @RequestParam(defaultValue = "1440") int minutes) {
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofMinutes(minutes));
        java.util.Map<Integer, Long> map = new java.util.HashMap<>();
        for (Object[] row : violationRepo.countByRuleSince(since)) {
            map.put((Integer) row[0], (Long) row[1]);
        }
        return map;
    }
}
