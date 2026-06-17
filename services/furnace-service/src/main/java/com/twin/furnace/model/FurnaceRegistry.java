package com.twin.furnace.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * furnace_registry — 爐子主登錄表
 * 彈性多爐：所有爐子資訊的唯一真相來源。
 * 新增爐子只需 INSERT 一筆，前後端自動跟進。
 */
@Data
@Entity
@Table(name = "furnace_registry")
public class FurnaceRegistry {

    @Id
    @Column(name = "furnace_id", length = 20, nullable = false)
    private String furnaceId;           // D1 / D3 / DB / F7 / FA / ...

    @Column(name = "display_name", length = 50)
    private String displayName;         // 顯示名稱，例如「長晶爐 D1」

    @Column(name = "location", length = 100)
    private String location;            // 廠區位置

    @Column(name = "zone", length = 20)
    private String zone;                // 區域代碼

    @Column(name = "status", length = 20)
    private String status = "idle";     // running / idle / maintenance / offline

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}