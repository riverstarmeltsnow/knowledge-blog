# 个人知识博客系统

基于 **Spring Boot + MyBatis + MySQL** 的个人博客平台，支持文章发布、分类归档、标签标注、评论互动与关键字检索。

前端分两部分：面向访客的阅读前台用原生 HTML/CSS/JS（浏览器调接口后自行渲染），面向管理员的后台用 Thymeleaf 服务端渲染。

## 技术栈

| 层次 | 技术 | 版本 |
|---|---|---|
| 运行环境 | JDK | 21 |
| Web 框架 | Spring Boot | 4.1.1 |
| 持久层 | MyBatis（XML 映射） | starter 4.1.0 |
| 参数校验 | Jakarta Bean Validation | — |
| 模板引擎 | Thymeleaf（后台管理） | 3.1.5 |
| 数据库 | MySQL | 8.4.6 |
| 构建工具 | Maven | 3.9.16 |

> MyBatis starter 的版本号与 Spring Boot 主版本对齐，3.0.x 不兼容 Spring Boot 4.x。

## 功能模块

| 模块 | 功能 |
|---|---|
| 用户 | 注册、登录退出、个人资料与密码修改 |
| 文章 | 发布 / 编辑 / 删除 / 详情浏览（浏览量自动累加） |
| 分类与标签 | 分类（一篇文章归属一个分类）、标签（一篇文章可有多个标签） |
| 评论 | 发表评论、回复评论、删除评论 |
| 检索 | 关键字搜索，按作者 / 分类 / 标签筛选，分页浏览 |
| 后台管理 | 文章、分类、标签、用户四个模块的增删改查 |

后端提供 **28 个 REST 接口**（文章 8 / 分类 5 / 评论 4 / 标签 5 / 用户 6）。

## 项目结构

```
Mygit/
├── docs/                              # 设计文档
│   ├── 系统设计说明书.md
│   ├── 数据库设计文档.md
│   ├── schema.sql                     # 建表脚本
│   ├── test_data.sql                  # 测试数据
│   └── *.png                          # ER 图、用例图、功能模块图
├── src/main/java/cuit/mygit/
│   ├── common/                        # Result 统一响应体、PageResult 分页结果、ValidGroup 校验分组
│   ├── controller/                    # 5 个 REST 控制器
│   ├── entity/                        # 5 个实体类，与数据表一一对应
│   ├── exception/                     # BusinessException + GlobalExceptionHandler
│   ├── mapper/                        # MyBatis Mapper 接口
│   ├── service/impl/                  # 业务逻辑实现
│   └── web/AdminController.java       # 后台管理页面（Thymeleaf）
├── src/main/resources/
│   ├── mapper/*.xml                   # 6 个 MyBatis XML 映射文件
│   ├── static/                        # 前台静态页面
│   └── templates/admin/               # 后台 Thymeleaf 模板
└── src/test/java/cuit/mygit/service/  # 5 个 Service 单元测试
```

## 快速开始

### 1. 准备数据库

```bash
mysql -u root -p < docs/schema.sql                    # 建库建表
mysql -u root -p knowledge_blog < docs/test_data.sql  # 导入测试数据
```

### 2. 配置数据库密码

在 `src/main/resources/` 下新建 `application-local.properties`：

```properties
spring.datasource.password=你的MySQL密码
```

> 该文件已被 `.gitignore` 排除，密码不会进入版本库。

### 3. 启动

```bash
mvn spring-boot:run
```

浏览器打开 http://localhost:8080

## 设计要点

- **分页参数防护**：单页最大 50 条（`MAX_PAGE_SIZE`）防止一次性拉全表；偏移量用 `long` 计算，避免 `int` 溢出
- **参数校验分组**：用 `ValidGroup.Create` / `ValidGroup.Update` 区分新增与更新的校验规则，校验注解的 `@Size` 上限与 `schema.sql` 的字段长度对齐
- **统一异常处理**：`GlobalExceptionHandler` 覆盖参数校验失败、缺少请求参数、参数类型不匹配、参数解码失败四类异常，统一返回 `Result` 格式
- **防 SQL 注入**：模糊查询用 `CONCAT('%', #{keyword}, '%')` 交给 MyBatis 预编译，不做字符串拼接
- **列表查询不取正文**：文章列表 SQL 只查元数据字段，避免 `TEXT` 类型的正文拖慢列表响应
- **评论不可编辑**：`comment` 是唯一没有 Update 操作的表，刻意保持「发出即不可修改」的语义
- **分类与标签的建模范式**：分类是一对多（`article.category_id` 外键），标签是多对多（`article_tag` 中间表），用两种不同的数据结构解决两种内容组织需求

## 文档

- [系统设计说明书](docs/系统设计说明书.md) —— 模块划分、接口清单、数据库设计
- [数据库设计文档](docs/数据库设计文档.md) —— 表结构、字段说明、实体关系
- [实验三开发记录](docs/实验三开发记录.md) —— 增删改查与后台界面的迭代过程
