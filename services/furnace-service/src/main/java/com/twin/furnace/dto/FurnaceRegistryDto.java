package com.twin.furnace.dto;
import com.twin.furnace.model.FurnaceRegistry;
import java.time.OffsetDateTime;
public class FurnaceRegistryDto {
    private String furnaceId, displayName, location, zone, status, description;
    private OffsetDateTime createdAt;
    public static FurnaceRegistryDto from(FurnaceRegistry r) {
        FurnaceRegistryDto d = new FurnaceRegistryDto();
        d.furnaceId   = r.getFurnaceId();
        d.displayName = r.getDisplayName() != null ? r.getDisplayName() : "長晶爐 " + r.getFurnaceId();
        d.location    = r.getLocation();
        d.zone        = r.getZone();
        d.status      = r.getStatus();
        d.description = r.getDescription();
        d.createdAt   = r.getCreatedAt();
        return d;
    }
    public String getFurnaceId()         { return furnaceId; }
    public String getDisplayName()       { return displayName; }
    public String getLocation()          { return location; }
    public String getZone()              { return zone; }
    public String getStatus()            { return status; }
    public String getDescription()       { return description; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
