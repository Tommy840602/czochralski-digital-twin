package com.twin.furnace.controller;

import com.twin.furnace.dto.FurnaceHistoryDto;
import com.twin.furnace.dto.FurnaceLatestDto;
import com.twin.furnace.dto.FurnaceRegistryDto;
import com.twin.furnace.service.FurnaceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Validated
@RestController
@RequestMapping("/furnaces")
public class FurnaceController {

    private static final Logger log = LoggerFactory.getLogger(FurnaceController.class);

    private static final Set<String> ALLOWED_STATUSES =
            Set.of("idle", "running", "alarm", "maintenance", "offline");

    private static final Set<String> ALLOWED_RESOLUTIONS =
            Set.of("auto", "raw", "minute", "hour");

    private final FurnaceService furnaceService;

    public FurnaceController(FurnaceService s) {
        this.furnaceService = s;
    }

    @PreAuthorize("hasAuthority('FURNACE_VIEW')")
    @GetMapping
    public ResponseEntity<List<FurnaceRegistryDto>> listFurnaces() {
        return ResponseEntity.ok(furnaceService.listFurnaces());
    }

    @PreAuthorize("hasAuthority('FURNACE_VIEW')")
    @GetMapping("/latest")
    public ResponseEntity<List<FurnaceLatestDto>> getAllLatest() {
        return ResponseEntity.ok(furnaceService.getAllLatest());
    }

    @PreAuthorize("hasAuthority('FURNACE_VIEW')")
    @GetMapping("/{id}")
    public ResponseEntity<FurnaceRegistryDto> getFurnace(@PathVariable String id) {
        return furnaceService.getFurnace(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('FURNACE_VIEW')")
    @GetMapping("/{id}/latest")
    public ResponseEntity<FurnaceLatestDto> getLatest(@PathVariable String id) {
        return furnaceService.getLatest(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasAuthority('FURNACE_VIEW')")
    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(
            @PathVariable String id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "auto") String resolution) {

        if (!ALLOWED_RESOLUTIONS.contains(resolution)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid resolution"));
        }

        OffsetDateTime end = to != null ? to : OffsetDateTime.now();

        log.info("history furnace={} from={} to={} resolution={}", id, from, end, resolution);

        return ResponseEntity.ok(furnaceService.getHistory(id, from, end, resolution));
    }

    @PreAuthorize("hasAuthority('FURNACE_CONTROL')")
    @PostMapping("/{id}/register")
    public ResponseEntity<Map<String, String>> register(@PathVariable String id) {
        furnaceService.ensureRegistered(id);

        log.info("registered: {}", id);

        return ResponseEntity.ok(Map.of(
                "furnaceId", id,
                "message", "registered"
        ));
    }

    @PreAuthorize("hasAuthority('FURNACE_CONTROL')")
    @PutMapping("/{id}/status")
    public ResponseEntity<Map<String, String>> updateStatus(
            @PathVariable String id,
            @RequestParam String status) {

        if (!ALLOWED_STATUSES.contains(status)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "invalid status"));
        }

        furnaceService.updateStatus(id, status);

        return ResponseEntity.ok(Map.of(
                "furnaceId", id,
                "status", status
        ));
    }
}