-- 创建数据库
CREATE DATABASE IF NOT EXISTS `Kankan`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE `Kankan`;

-- 1. 用户表
CREATE TABLE IF NOT EXISTS `用户表` (
    `用户ID` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户唯一标识',
    `用户名` VARCHAR(50) NOT NULL COMMENT '登录用户名',
    `密码` VARCHAR(255) NOT NULL COMMENT '加密后的密码',
    `昵称` VARCHAR(50) NOT NULL COMMENT '显示昵称',
    `头像URL` VARCHAR(500) DEFAULT NULL COMMENT '头像地址',
    `注册时间` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    `最后登录时间` DATETIME DEFAULT NULL COMMENT '最后一次登录时间',
    PRIMARY KEY (`用户ID`),
    UNIQUE KEY `uk_用户名` (`用户名`),
    KEY `idx_昵称` (`昵称`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户基本信息表';

-- 2. 房间表
CREATE TABLE IF NOT EXISTS `房间表` (
    `房间ID` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '房间唯一标识',
    `房间名称` VARCHAR(100) NOT NULL COMMENT '房间名称',
    `创建者ID` INT UNSIGNED NOT NULL COMMENT '房主用户ID',
    `创建时间` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '房间创建时间',
    `房间类型` ENUM('公开', '私有', '加密') NOT NULL DEFAULT '公开' COMMENT '房间类型',
    `房间密码` VARCHAR(100) DEFAULT NULL COMMENT '加密房间的密码（哈希值）',
    `当前人数` SMALLINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前在线/总成员数（冗余字段）',
    `最大人数` SMALLINT UNSIGNED NOT NULL DEFAULT 200 COMMENT '房间人数上限',
    `房间状态` ENUM('正常', '已关闭', '维护中') NOT NULL DEFAULT '正常' COMMENT '房间状态',
    PRIMARY KEY (`房间ID`),
    KEY `idx_创建者ID` (`创建者ID`),
    KEY `idx_创建时间` (`创建时间`),
    CONSTRAINT `fk_房间表_创建者` FOREIGN KEY (`创建者ID`) REFERENCES `用户表` (`用户ID`) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天房间信息表';

-- 3. 房间成员表
CREATE TABLE IF NOT EXISTS `房间成员表` (
    `成员ID` INT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    `房间ID` INT UNSIGNED NOT NULL COMMENT '所属房间',
    `用户ID` INT UNSIGNED NOT NULL COMMENT '成员用户ID',
    `加入时间` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入房间的时间',
    `角色` ENUM('房主', '管理员', '普通成员') NOT NULL DEFAULT '普通成员' COMMENT '成员在该房间的角色',
    `昵称备注` VARCHAR(50) DEFAULT NULL COMMENT '在房间内的自定义昵称备注',
    PRIMARY KEY (`成员ID`),
    UNIQUE KEY `uk_房间_用户` (`房间ID`, `用户ID`),  -- 同一用户不能重复加入同一房间
    KEY `idx_用户ID` (`用户ID`),
    KEY `idx_角色` (`角色`),
    CONSTRAINT `fk_成员_房间` FOREIGN KEY (`房间ID`) REFERENCES `房间表` (`房间ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_成员_用户` FOREIGN KEY (`用户ID`) REFERENCES `用户表` (`用户ID`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房间与成员的关联表';

-- 4. 聊天记录表
CREATE TABLE IF NOT EXISTS `聊天记录` (
    `消息ID` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '消息唯一标识',
    `房间ID` INT UNSIGNED NOT NULL COMMENT '所属房间',
    `用户ID` INT UNSIGNED NOT NULL COMMENT '发送者用户ID',
    `消息类型` ENUM('文本', '图片', '语音', '视频', '系统提示') NOT NULL DEFAULT '文本' COMMENT '消息内容类型',
    `消息内容` TEXT NOT NULL COMMENT '消息正文（文本或资源URL等）',
    `发送时间` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '精确到毫秒的发送时间',
    `引用消息ID` BIGINT UNSIGNED DEFAULT NULL COMMENT '引用的历史消息ID',
    `是否撤回` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已被撤回',
    PRIMARY KEY (`消息ID`),
    KEY `idx_房间ID_时间` (`房间ID`, `发送时间`),   -- 按房间查询聊天记录（常用）
    KEY `idx_用户ID` (`用户ID`),
    KEY `idx_发送时间` (`发送时间`),
    CONSTRAINT `fk_聊天_房间` FOREIGN KEY (`房间ID`) REFERENCES `房间表` (`房间ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_聊天_用户` FOREIGN KEY (`用户ID`) REFERENCES `用户表` (`用户ID`) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT `fk_聊天_引用` FOREIGN KEY (`引用消息ID`) REFERENCES `聊天记录` (`消息ID`) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='房间内的聊天消息记录';

-- ----------------------------
-- 插入示例数据（便于测试）
-- ----------------------------
-- 插入几个用户
INSERT INTO `用户表` (`用户名`, `密码`, `昵称`, `头像URL`, `注册时间`, `最后登录时间`) VALUES
('alice', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', '爱丽丝', 'https://example.com/avatar/alice.jpg', NOW(), NOW()),
('bob', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', '鲍勃', 'https://example.com/avatar/bob.jpg', NOW(), NOW()),
('carol', '5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8', '卡罗尔', NULL, NOW(), NULL);

-- 注意：上面的密码是 "password" 的 SHA-256 哈希值（示例用，实际应使用加盐哈希）

-- 创建两个房间
INSERT INTO `房间表` (`房间名称`, `创建者ID`, `创建时间`, `房间类型`, `房间密码`, `最大人数`) VALUES
('技术闲聊', 1, NOW(), '公开', NULL, 100),
('私密小屋', 2, NOW(), '加密', 'a665a45920422f9d417e4867efdc4fb8a04a1f3fff1fa07e998e86f7f7a27ae3', 10);
-- 密码 "123" 的 SHA-256 哈希值（仅示例）

-- 添加成员关系
INSERT INTO `房间成员表` (`房间ID`, `用户ID`, `角色`) VALUES
(1, 1, '房主'),
(1, 2, '管理员'),
(1, 3, '普通成员'),
(2, 2, '房主'),
(2, 1, '普通成员');

-- 插入一些聊天记录
INSERT INTO `聊天记录` (`房间ID`, `用户ID`, `消息类型`, `消息内容`, `发送时间`, `引用消息ID`) VALUES
(1, 1, '文本', '大家好，欢迎来到技术闲聊！', NOW(3), NULL),
(1, 2, '文本', '请问有人懂数据库优化吗？', DATE_ADD(NOW(3), INTERVAL 2 SECOND), NULL),
(1, 3, '文本', '可以看看索引设计', DATE_ADD(NOW(3), INTERVAL 5 SECOND), 2),
(2, 2, '系统提示', '欢迎进入私密小屋', NOW(3), NULL),
(2, 1, '文本', '这里很安静呀', DATE_ADD(NOW(3), INTERVAL 10 SECOND), NULL);