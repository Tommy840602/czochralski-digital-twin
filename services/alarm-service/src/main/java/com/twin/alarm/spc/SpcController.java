package com.twin.alarm.spc;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/spc")
public class SpcController {

    private final SpcService spcService;

    public SpcController(SpcService spcService) {
        this.spcService = spcService;
    }

    @GetMapping("/baseline")
    public List<SpcService.BaselineDto> getBaselines(@RequestParam String furnaceId) {
        return spcService.getBaselines(furnaceId);
    }

    @GetMapping("/baseline/one")
    public ResponseEntity<SpcService.BaselineDto> getBaseline(
            @RequestParam String furnaceId,
            @RequestParam String paramName
    ) {
        return spcService.getBaseline(furnaceId, paramName)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/baseline/rebuild")
    public Map<String, Object> rebuildBaseline() {
        return spcService.rebuildBaseline();
    }

    @GetMapping("/params")
    public List<SpcService.ParamDto> getParams() {
        return spcService.getParams();
    }

    @GetMapping("/timeseries")
    public List<SpcService.TimeseriesPointDto> getTimeseries(
            @RequestParam String furnaceId,
            @RequestParam String paramName,
            @RequestParam(defaultValue = "60") int minutes
    ) {
        return spcService.getTimeseries(furnaceId, paramName, minutes);
    }

    @GetMapping("/violation/recent")
    public List<SpcService.ViolationDto> getRecentViolations(
            @RequestParam(defaultValue = "60") int minutes
    ) {
        return spcService.getRecentViolations(minutes);
    }

    @GetMapping("/violation/byFurnace")
    public List<SpcService.ViolationDto> getViolationsByFurnace(
            @RequestParam String furnaceId,
            @RequestParam(defaultValue = "60") int minutes
    ) {
        return spcService.getViolationsByFurnace(furnaceId, minutes);
    }

    @GetMapping("/violation/statistics")
    public Map<Integer, Long> getStatistics(@RequestParam(defaultValue = "1440") int minutes) {
        return spcService.getStatistics(minutes);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "Bad Request",
                "message", ex.getMessage()
        ));
    }
}
