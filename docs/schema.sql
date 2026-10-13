-- ============================================================
-- 个人知识博客系统 - 数据库建表脚本
-- 数据库：knowledge_blog
-- 字符集：utf8mb4（支持中文和 emoji）
-- 引擎：InnoDB（支持事务和外键）
-- ============================================================

-- 如果已存在则先删除（方便反复执行；正式环境请注释掉这行）
DROP DATABASE IF EXISTS knowledge_blog;

CREATE DATABASE knowledge_blog
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE knowledge_blog;

-- ------------------------------------------------------------
-- 1. 用户表
-- 存储注册用户和管理员，用 role 字段区分身份
-- ------------------------------------------------------------
CREATE TABLE `user` (
  `user_id`     INT AUTO_INCREMENT PRIMARY KEY  COMMENT '用户ID，主键自增',
  `username`    VARCHAR(50)  NOT NULL UNIQUE     COMMENT '用户名，唯一',
  `password`    VARCHAR(100) NOT NULL            COMMENT '密码（实际项目应存加密后的值）',
  `email`       VARCHAR(100)                     COMMENT '邮箱',
  `role`        TINYINT DEFAULT 0                COMMENT '角色：0-普通用户 1-管理员',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 分类表
-- 一篇文章归属一个分类，分类和文章是 1:N 关系
-- ------------------------------------------------------------
CREATE TABLE `category` (
  `category_id` INT AUTO_INCREMENT PRIMARY KEY   COMMENT '分类ID，主键自增',
  `name`        VARCHAR(50) NOT NULL UNIQUE      COMMENT '分类名称，唯一',
  `description` VARCHAR(255)                     COMMENT '分类描述'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章分类表';

-- ------------------------------------------------------------
-- 3. 标签表
-- 一篇文章可以有多个标签，标签和文章是 M:N 关系
-- M:N 关系由下面的 article_tag 中间表承载
-- ------------------------------------------------------------
CREATE TABLE `tag` (
  `tag_id` INT AUTO_INCREMENT PRIMARY KEY        COMMENT '标签ID，主键自增',
  `name`   VARCHAR(50) NOT NULL UNIQUE           COMMENT '标签名称，唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签表';

-- ------------------------------------------------------------
-- 4. 文章表
-- 整个系统的核心表，外键关联用户（作者）和分类
-- ------------------------------------------------------------
CREATE TABLE `article` (
  `article_id`  INT AUTO_INCREMENT PRIMARY KEY   COMMENT '文章ID，主键自增',
  `title`       VARCHAR(200) NOT NULL            COMMENT '文章标题',
  `content`     LONGTEXT                         COMMENT '文章正文（长文本）',
  `user_id`     INT NOT NULL                     COMMENT '作者ID，外键关联 user',
  `category_id` INT                              COMMENT '分类ID，外键关联 category',
  `status`      TINYINT NOT NULL DEFAULT 1       COMMENT '状态：0=草稿（只有作者本人和管理员看得到），1=已发布',
  `view_count`  INT DEFAULT 0                    COMMENT '浏览量',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间，自动更新',
  CONSTRAINT fk_article_user     FOREIGN KEY (`user_id`)     REFERENCES `user`(`user_id`),
  CONSTRAINT fk_article_category FOREIGN KEY (`category_id`) REFERENCES `category`(`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章表';

-- ------------------------------------------------------------
-- 5. 文章-标签关联表（M:N 拆解出的中间表）
-- 主键是 (article_id, tag_id) 联合主键，保证同一篇文章不会重复贴同一个标签
-- ------------------------------------------------------------
CREATE TABLE `article_tag` (
  `article_id` INT NOT NULL COMMENT '文章ID，外键关联 article',
  `tag_id`     INT NOT NULL COMMENT '标签ID，外键关联 tag',
  PRIMARY KEY (`article_id`, `tag_id`),
  CONSTRAINT fk_at_article FOREIGN KEY (`article_id`) REFERENCES `article`(`article_id`),
  CONSTRAINT fk_at_tag     FOREIGN KEY (`tag_id`)     REFERENCES `tag`(`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章标签关联表（多对多中间表）';

-- ------------------------------------------------------------
-- 6. 用户关注表（M:N 拆解出的中间表，用户关注用户）
--
-- 这是全库唯一一张"两张外键指向同一张表"的表：
-- follower_id 和 followee_id 都指向 user，含义完全不同，
-- 所以列名必须区分清楚，不能像 article_tag 那样两边都叫得含糊。
--   follower_id = 关注者，也就是"我"
--   followee_id = 被关注者，也就是"我关注的那个人"
--
-- 和 article_tag 的另一个区别：这里多了 create_time。
-- article_tag 记录的是"这篇贴了这个标签"这种静态事实，时间没意义；
-- 关注是个发生在某个时刻的事件，时间有意义 —— 比如"按关注时间倒序"
-- 列出我关注的人，或者显示"你们互相关注"。
--
-- 联合主键 (follower_id, followee_id) 挡住重复关注：
-- 同一个人关注同一个人第二次时，数据库直接拒绝，不靠代码判断。
-- 自我关注（follower_id = followee_id）数据库挡不住，
-- 因为"两个值相等"是行级约束，MySQL 的 CHECK 在 8.0.16 之前会被忽略，
-- 所以这条规则放在 Service 层拦（见 UserFollowServiceImpl）。
-- ------------------------------------------------------------
CREATE TABLE `user_follow` (
  `follower_id` INT NOT NULL COMMENT '关注者ID（我），外键关联 user',
  `followee_id` INT NOT NULL COMMENT '被关注者ID（我关注的人），外键关联 user',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`follower_id`, `followee_id`),
  CONSTRAINT fk_uf_follower FOREIGN KEY (`follower_id`) REFERENCES `user`(`user_id`),
  CONSTRAINT fk_uf_followee FOREIGN KEY (`followee_id`) REFERENCES `user`(`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表（多对多中间表）';

-- ------------------------------------------------------------
-- 7. 评论表
-- 关联三张表：用户（谁评论的）、文章（评论了哪篇）、自己（挂在哪条顶层评论下面）
-- 评论只有两层：parent_id 为 NULL 的是顶层评论，有值的是它下面的回复。
-- 回复的回复也会被归到同一个顶层评论下，所以 parent_id 永远指向顶层评论，
-- 不会指向另一条回复。
-- ------------------------------------------------------------
CREATE TABLE `comment` (
  `comment_id`        INT AUTO_INCREMENT PRIMARY KEY   COMMENT '评论ID，主键自增',
  `content`           VARCHAR(500) NOT NULL            COMMENT '评论内容',
  `user_id`           INT NOT NULL                     COMMENT '评论人ID，外键关联 user',
  `article_id`        INT NOT NULL                     COMMENT '所属文章ID，外键关联 article',
  `parent_id`         INT NULL                         COMMENT '所属顶层评论ID，NULL表示这条本身就是顶层评论',
  `reply_to_user_id`  INT NULL                         COMMENT '被回复人的用户ID，NULL表示直接对文章评论',
  `create_time`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  CONSTRAINT fk_comment_user       FOREIGN KEY (`user_id`)           REFERENCES `user`(`user_id`),
  CONSTRAINT fk_comment_article    FOREIGN KEY (`article_id`)        REFERENCES `article`(`article_id`),
  CONSTRAINT fk_comment_parent     FOREIGN KEY (`parent_id`)         REFERENCES `comment`(`comment_id`),
  CONSTRAINT fk_comment_reply_user FOREIGN KEY (`reply_to_user_id`)  REFERENCES `user`(`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表';

-- ============================================================
-- 建表完成。验证：
--   SHOW TABLES;
--   DESC article;
-- ============================================================
