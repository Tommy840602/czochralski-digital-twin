package com.twin.furnace.service;
import com.twin.furnace.dto.FurnaceHistoryDto;
import com.twin.furnace.dto.FurnaceLatestDto;
import com.twin.furnace.dto.FurnaceRegistryDto;
import com.twin.furnace.model.FurnaceMetrics;
import com.twin.furnace.model.FurnaceRegistry;
import com.twin.furnace.repository.FurnaceMetricsRepository;
import com.twin.furnace.repository.FurnaceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FurnaceService {
    private static final Logger log = LoggerFactory.getLogger(FurnaceService.class);
    private final FurnaceRegistryRepository registryRepo;
    private final FurnaceMetricsRepository  metricsRepo;
    private final StringRedisTemplate       redis;

    public FurnaceService(FurnaceRegistryRepository r, FurnaceMetricsRepository m, StringRedisTemplate s) {
        this.registryRepo=r; this.metricsRepo=m; this.redis=s;
    }
    public List<FurnaceRegistryDto> listFurnaces() {
        return registryRepo.findAllByOrderByFurnaceIdAsc().stream().map(FurnaceRegistryDto::from).collect(Collectors.toList());
    }
    public List<String> listFurnaceIds() {
        return registryRepo.findAllByOrderByFurnaceIdAsc().stream().map(FurnaceRegistry::getFurnaceId).collect(Collectors.toList());
    }
    public Optional<FurnaceRegistryDto> getFurnace(String id) {
        return registryRepo.findById(id).map(FurnaceRegistryDto::from);
    }
    public void ensureRegistered(String id) { registryRepo.registerIfAbsent(id, "長晶爐 " + id); }
    public void updateStatus(String id, String status) { registryRepo.updateStatus(id, status); }
    public Optional<FurnaceLatestDto> getLatest(String id) {
        Map<Object,Object> m = redis.opsForHash().entries("furnace:" + id);
        if (!m.isEmpty()) return Optional.of(FurnaceLatestDto.fromRedis(id, m));
        return metricsRepo.findLatestByFurnaceId(id)
                .map(metrics -> FurnaceLatestDto.fromMetrics(metrics, registryRepo.findById(id).orElse(null)));
    }
    public List<FurnaceLatestDto> getAllLatest() {
        return listFurnaceIds().stream().map(id -> getLatest(id).orElse(FurnaceLatestDto.offline(id))).collect(Collectors.toList());
    }
    public FurnaceHistoryDto getHistory(String id, OffsetDateTime from, OffsetDateTime to, String resolution) {
        List<FurnaceMetrics> data;
        String actual = resolution;
        long hours = java.time.Duration.between(from, to).toHours();
        if ("raw".equals(resolution) || hours < 3) {
            data = metricsRepo.findHistory(id, from, to, 3600);
            java.util.Collections.reverse(data);
            actual = "raw";
        }
        else if ("1min".equals(resolution) || hours < 72) { data=metricsRepo.findHistory1Min(id,from,to); actual="1min"; }
        else { data=metricsRepo.findHistory1Hour(id,from,to); actual="1hour"; }
        log.debug("history furnace={} resolution={} rows={}", id, actual, data.size());
        return FurnaceHistoryDto.of(id, from, to, actual, data);
    }
}
