-- ============================================
-- farming_harness 电商辅助办公系统 数据库初始化脚本
-- 本地化部署：仅需在 MySQL 中执行一次
-- ============================================

CREATE DATABASE IF NOT EXISTS farming_harness DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE farming_harness;

-- ----------------------------
-- 1. 系统设置表（API Key、模型配置等）
-- ----------------------------
DROP TABLE IF EXISTS t_settings;
CREATE TABLE t_settings (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    setting_key   VARCHAR(100) NOT NULL COMMENT '配置键',
    setting_value TEXT COMMENT '配置值（加密存储）',
    description   VARCHAR(255) COMMENT '配置说明',
    is_secret     TINYINT DEFAULT 0 COMMENT '是否敏感（密钥）',
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_setting_key (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统设置';

-- ----------------------------
-- 2. 任务图（s12 长任务持久化）
-- ----------------------------
DROP TABLE IF EXISTS t_task;
CREATE TABLE t_task (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_key    VARCHAR(64) NOT NULL COMMENT '任务唯一键',
    title       VARCHAR(255) COMMENT '任务标题',
    description TEXT COMMENT '任务描述',
    status      VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/in_progress/completed/failed',
    blocked_by  VARCHAR(255) COMMENT '依赖的任务key，逗号分隔',
    owner       VARCHAR(64) COMMENT '认领的 teammate',
    progress    INT DEFAULT 0 COMMENT '进度 0-100',
    result      TEXT COMMENT '任务结果',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_key (task_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='任务图';

-- ----------------------------
-- 3. Teammate 进程表（s15-s18 多Agent协作）
-- ----------------------------
DROP TABLE IF EXISTS t_teammate;
CREATE TABLE t_teammate (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(64) NOT NULL COMMENT '队友名称',
    role          VARCHAR(64) COMMENT '角色/职责',
    status        VARCHAR(20) DEFAULT 'WORK' COMMENT 'WORK/IDLE/SHUTDOWN',
    current_task  VARCHAR(255) COMMENT '当前任务',
    progress      INT DEFAULT 0 COMMENT '进度 0-100',
    log_summary   TEXT COMMENT '最近日志摘要',
    heartbeat_at  DATETIME COMMENT '心跳时间',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_teammate_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Teammate进程';

-- ----------------------------
-- 4. 知识库文档表
-- ----------------------------
DROP TABLE IF EXISTS t_knowledge_doc;
CREATE TABLE t_knowledge_doc (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_key     VARCHAR(64) NOT NULL COMMENT '文档唯一key',
    file_name   VARCHAR(255) COMMENT '原始文件名',
    file_type   VARCHAR(32) COMMENT '文件类型 txt/pdf/docx/xlsx/pptx/csv',
    file_size   BIGINT COMMENT '文件大小(字节)',
    file_path   VARCHAR(255) COMMENT '存储路径',
    content     LONGTEXT COMMENT '解析出的文本内容',
    parse_status VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/parsing/done/failed',
    summary     TEXT COMMENT '内容摘要',
    tags        VARCHAR(255) COMMENT '标签',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_key (doc_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档';

-- ----------------------------
-- 5. 记忆库表（s09 记忆系统）
-- ----------------------------
DROP TABLE IF EXISTS t_memory;
CREATE TABLE t_memory (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    memory_key  VARCHAR(64) NOT NULL COMMENT '记忆键',
    content     TEXT COMMENT '记忆内容',
    importance  INT DEFAULT 5 COMMENT '重要性 1-10',
    category    VARCHAR(64) COMMENT '分类',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_memory_key (memory_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆库';

-- ----------------------------
-- 6. 监控日志表
-- ----------------------------
DROP TABLE IF EXISTS t_monitor_log;
CREATE TABLE t_monitor_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    source      VARCHAR(64) COMMENT '来源 teammate/系统',
    level       VARCHAR(16) DEFAULT 'INFO',
    message     TEXT COMMENT '日志内容',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_source (source),
    KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='监控日志';

-- ----------------------------
-- 7. AI 模型配置表（多模型管理/切换）
-- ----------------------------
DROP TABLE IF EXISTS t_ai_model;
CREATE TABLE t_ai_model (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '模型显示名',
    provider    VARCHAR(50) COMMENT '提供商',
    base_url    VARCHAR(255) COMMENT '服务地址',
    model       VARCHAR(100) COMMENT '模型名',
    api_key     VARCHAR(255) COMMENT 'API 密钥（敏感）',
    active      TINYINT DEFAULT 0 COMMENT '是否激活（当前使用）',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI模型配置';

-- ----------------------------
-- 8. 聊天记录表（对话持久化/回滚）
-- ----------------------------
DROP TABLE IF EXISTS t_chat_message;
CREATE TABLE t_chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_key VARCHAR(64) NOT NULL COMMENT '会话标识',
    role        VARCHAR(16) NOT NULL COMMENT 'user/assistant',
    content     TEXT COMMENT '消息内容',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session (session_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天记录';

-- ----------------------------
-- 8. MCP 服务表（外部工具服务管理）
-- ----------------------------
DROP TABLE IF EXISTS t_mcp_server;
CREATE TABLE t_mcp_server (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL COMMENT '服务名称',
    server_type   VARCHAR(20) DEFAULT 'http' COMMENT '类型 stdio/http/sse',
    endpoint      VARCHAR(255) COMMENT '服务地址/命令',
    description   VARCHAR(255) COMMENT '功能说明',
    config        TEXT COMMENT '配置参数(JSON)',
    status        VARCHAR(20) DEFAULT 'inactive' COMMENT 'active/inactive',
    last_checked  DATETIME COMMENT '最近测试时间',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_mcp_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='MCP服务';

-- ----------------------------
-- 8. Skill 技能表（自定义技能/指令管理）
-- ----------------------------
DROP TABLE IF EXISTS t_skill;
CREATE TABLE t_skill (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL COMMENT '技能名称',
    category    VARCHAR(64) COMMENT '分类',
    description VARCHAR(255) COMMENT '技能说明',
    content     TEXT COMMENT '技能指令/内容',
    enabled     TINYINT DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_skill_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Skill技能';

-- ----------------------------
-- 初始化系统设置
-- ----------------------------
INSERT INTO t_settings (setting_key, setting_value, description, is_secret) VALUES
('api.provider', 'deepseek', 'AI 服务提供商：deepseek/openai', 0),
('api.baseUrl', 'https://api.deepseek.com', 'API 服务地址', 0),
('api.model', 'deepseek-v4-flash', '默认对话模型', 0),
('api.key', '', 'API 密钥（敏感）', 1),
('search.provider', 'bing', '联网搜索提供方 bing/duckduckgo', 0),
('search.enabled', '1', '是否启用联网搜索', 0),
('system.name', '电商辅助办公系统 Harness', '系统名称', 0),
('system.workerCount', '3', '默认 Teammate 工作进程数', 0),
('agent.autoAssign', '1', '是否启用自动任务认领', 0);

-- 示例 Skill 技能
INSERT INTO t_skill (name, category, description, content, enabled) VALUES
('商品文案生成', '内容创作', '根据商品信息生成电商文案',
 '当用户提供商品名称/卖点/目标人群时，输出一段适用于详情页或推广位的营销文案，包含标题、卖点罗列和行动号召。', 1),
('竞品分析', '运营分析', '分析竞品的价格、卖点与优劣势',
 '当用户提及某个竞品或品类时，从价格、销量、卖点、评价、营销策略角度输出对比分析，并给出可执行的运营建议。', 1);

-- ----------------------------
-- 9. 定时提醒表（后台定时任务，应用启动时会自动 CREATE TABLE IF NOT EXISTS 兜底）
-- ----------------------------
CREATE TABLE IF NOT EXISTS t_reminder (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    instruct      TEXT COMMENT '提醒内容',
    title         VARCHAR(255) DEFAULT '⏰ 定时提醒' COMMENT '提醒标题',
    delay_minutes INT DEFAULT 0 COMMENT '延迟分钟数',
    session_key   VARCHAR(64) DEFAULT 'default' COMMENT '会话标识',
    fire_at       DATETIME COMMENT '触发时间',
    status        VARCHAR(16) DEFAULT 'pending' COMMENT 'pending/fired/cancelled',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_fire (status, fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='定时提醒';
