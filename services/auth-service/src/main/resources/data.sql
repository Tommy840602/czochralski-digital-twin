-- 冪等 seed：Hibernate 已用 ddl-auto=update 建好表，這裡只灌參考資料。
-- 用顯式 id 方便 role_permissions 對應；ON CONFLICT 確保重啟不重複。

INSERT INTO roles (id, name) VALUES
                                 (1, 'ADMIN'),
                                 (2, 'ENGINEER'),
                                 (3, 'OPERATOR'),
                                 (4, 'VIEWER')
    ON CONFLICT (id) DO NOTHING;

INSERT INTO permissions (id, name) VALUES
                                       (1, 'FURNACE_VIEW'),
                                       (2, 'FURNACE_CONTROL'),
                                       (3, 'ALARM_VIEW'),
                                       (4, 'ALARM_ACK'),
                                       (5, 'REPORT_GEN'),
                                       (6, 'USER_MANAGE'),
                                       (7, 'SPC_VIEW'),
                                       (8, 'OEE_VIEW')
    ON CONFLICT (id) DO NOTHING;

-- VIEWER: 看爐況 + 看告警
INSERT INTO role_permissions (role_id, permission_id) VALUES
                                                          (4, 1), (4, 3)
    ON CONFLICT DO NOTHING;

-- OPERATOR: VIEWER + 確認告警
INSERT INTO role_permissions (role_id, permission_id) VALUES
                                                          (3, 1), (3, 3), (3, 4)
    ON CONFLICT DO NOTHING;

-- ENGINEER: OPERATOR + 控制爐子 + 產報告 + SPC + OEE
INSERT INTO role_permissions (role_id, permission_id) VALUES
                                                          (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 7), (2, 8)
    ON CONFLICT DO NOTHING;

-- ADMIN: 全部（含 SPC + OEE）
INSERT INTO role_permissions (role_id, permission_id) VALUES
                                                          (1, 1), (1, 2), (1, 3), (1, 4), (1, 5), (1, 6), (1, 7), (1, 8)
    ON CONFLICT DO NOTHING;