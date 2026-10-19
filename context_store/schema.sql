-- ============================================
-- 上下文存储（context store）MySQL 表结构
-- 类 OpenViking：三级存储 / 权重下沉 / Git 式快照 /
-- 多 Agent 黑板 / 资源版本 / 文档分章摘要
-- ============================================

CREATE TABLE IF NOT EXISTS ctx_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    room            VARCHAR(128) NOT NULL COMMENT '会话/协作室（多 Agent 共享同一 room）',
    agent           VARCHAR(64) DEFAULT 'main' COMMENT '写入者',
    role            VARCHAR(16) DEFAULT 'user' COMMENT 'user/assistant/system/tool',
    content         LONGTEXT NOT NULL,
    layer           VARCHAR(4) NOT NULL DEFAULT 'L1' COMMENT '存储层：L1 热缓存 / L2 内存 / L3 外存',
    weight          DOUBLE NOT NULL DEFAULT 1 COMMENT '权重（被调用次数累积，越高越热）',
    access_count    INT NOT NULL DEFAULT 0 COMMENT '被检索/送入模型的次数',
    last_access_at  DATETIME NULL COMMENT '最近一次被访问时间',
    content_hash    CHAR(64) NOT NULL DEFAULT '' COMMENT '内容哈希（支撑 diff）',
    version         INT NOT NULL DEFAULT 1 COMMENT '条目版本（改写/回滚递增）',
    deleted         TINYINT NOT NULL DEFAULT 0 COMMENT '软删除（快照可恢复）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_room_layer (room, layer),
    KEY idx_room_weight (room, weight),
    KEY idx_room_created (room, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上下文条目（三级存储）';

CREATE TABLE IF NOT EXISTS ctx_snapshot (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    room            VARCHAR(128) NOT NULL,
    commit_hash     CHAR(40) NOT NULL COMMENT '快照提交哈希（类 Git commit）',
    parent_hash     CHAR(40) DEFAULT '' COMMENT '父提交哈希',
    message         VARCHAR(255) DEFAULT '',
    item_count      INT NOT NULL DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_room_hash (room, commit_hash),
    KEY idx_room_created (room, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话快照提交（Git 式版本管理）';

CREATE TABLE IF NOT EXISTS ctx_snapshot_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id     BIGINT NOT NULL,
    item_id         BIGINT NOT NULL COMMENT 'ctx_item.id',
    agent           VARCHAR(64) DEFAULT 'main',
    role            VARCHAR(16) DEFAULT 'user',
    content         LONGTEXT NOT NULL,
    content_hash    CHAR(64) NOT NULL DEFAULT '',
    layer           VARCHAR(4) DEFAULT 'L1',
    weight          DOUBLE DEFAULT 1,
    KEY idx_snapshot (snapshot_id),
    KEY idx_item (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='快照明细（用于 diff / rollback）';

CREATE TABLE IF NOT EXISTS ctx_blackboard (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    room            VARCHAR(128) NOT NULL,
    bkey            VARCHAR(128) NOT NULL,
    bvalue          LONGTEXT,
    updated_by      VARCHAR(64) DEFAULT 'main',
    version         INT NOT NULL DEFAULT 1 COMMENT '写入版本号（即时可见依据）',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_room_key (room, bkey),
    KEY idx_room (room)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='多 Agent 共享黑板（写入即时可见）';

CREATE TABLE IF NOT EXISTS ctx_resource (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    kind            VARCHAR(16) NOT NULL COMMENT 'mcp / skill',
    name            VARCHAR(128) NOT NULL,
    version         INT NOT NULL DEFAULT 1,
    payload         LONGTEXT COMMENT '资源定义 JSON',
    content_hash    CHAR(64) NOT NULL DEFAULT '',
    is_active       TINYINT NOT NULL DEFAULT 0 COMMENT '1=当前生效版本',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_kind_name_version (kind, name, version),
    KEY idx_kind_name (kind, name),
    KEY idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP/Skill 版本库（旧版本保留可回滚）';

CREATE TABLE IF NOT EXISTS ctx_doc (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    source          VARCHAR(255) DEFAULT '',
    section_count   INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='导入文档';

CREATE TABLE IF NOT EXISTS ctx_doc_section (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id          BIGINT NOT NULL,
    parent_id       BIGINT DEFAULT 0 COMMENT '上一级章节 id',
    level           TINYINT NOT NULL DEFAULT 1 COMMENT '1=一级章节 / 2=二级章节',
    title           VARCHAR(255) NOT NULL,
    content         LONGTEXT,
    summary         TEXT COMMENT '章节摘要（检索先命中摘要，再回读原文）',
    chapter_order   INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_doc (doc_id),
    KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文档章节（一级/二级摘要）';
