package com.twin.furnace.repository;

import com.twin.furnace.model.FurnaceRegistry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * furnace_registry 查詢。
 * findAllByOrderByFurnaceId() → 供 WebSocket handler 動態掃爐子清單使用。
 */
@Repository
public interface FurnaceRegistryRepository extends JpaRepository<FurnaceRegistry, String> {

    /** 取所有爐子，依 furnace_id 字母順序 */
    List<FurnaceRegistry> findAllByOrderByFurnaceIdAsc();

    /** 只取運行中或閒置的爐子（過濾 offline / maintenance） */
    @Query("SELECT r FROM FurnaceRegistry r WHERE r.status IN ('running','idle') ORDER BY r.furnaceId")
    List<FurnaceRegistry> findActive();

    /** 更新爐子狀態 */
    @Modifying
    @Transactional
    @Query("UPDATE FurnaceRegistry r SET r.status = :status WHERE r.furnaceId = :furnaceId")
    int updateStatus(@Param("furnaceId") String furnaceId, @Param("status") String status);

    /** 自動注冊爐子（若不存在則 INSERT，若存在則 SKIP） */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO furnace_registry (furnace_id, display_name, status, created_at, updated_at)
        VALUES (:furnaceId, :displayName, 'idle', NOW(), NOW())
        ON CONFLICT (furnace_id) DO NOTHING
        """, nativeQuery = true)
    void registerIfAbsent(@Param("furnaceId") String furnaceId,
                          @Param("displayName") String displayName);
}