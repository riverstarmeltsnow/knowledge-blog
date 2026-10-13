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
| Markdown 渲染 | commonmark-java（服务端） | 0.24.0 |
| 数据库 | MySQL | 8.4.6 |
| 构建工具 | Maven | 3.9.16 |

> MyBatis starter 的版本号与 Spring Boot 主版本对齐，3.0.x 不兼容 Spring Boot 4.x。

## 功能模块

| 模块 | 功能 |
|---|---|
| 用户 | 注册、登录退出、个人资料与密码修改 |
| 文章 | 发布 / 编辑 / 删除 / 详情浏览（浏览量自动累加） |
| 写作 | Markdown 编辑（工具条 + 服务端渲染预览）、草稿 / 发布两种状态 |
| 分类与标签 | 分类（一篇文章归属一个分类）、标签（一篇文章可有多个标签） |
| 评论 | 发表评论、回复评论、删除评论 |
| 检索 | 关键字搜索，按作者 / 分类 / 标签筛选，分页浏览 |
| 归档 | 按「年-月」排布全部文章，左栏按年份索引跳转 |
| 首页三档 | 热门（按浏览量）、最新（按发布时间）、推荐（只看我关注的人） |
| 关注 | 在文章详情页关注作者，他的文章会进推荐流并在卡片上标「已关注」 |
| 后台管理 | 文章、分类、标签、用户四个模块的增删改查；分类与标签仅管理员可写，用户资料本人或管理员可写 |

后端提供 **42 个 REST 接口**（文章 18 / 关注 4 / 分类 5 / 评论 4 / 标签 5 / 用户 6）。

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
│   ├── common/                        # Result 统一响应体、PageResult 分页结果、ValidGroup 校验分组、
│   │                                  # MarkdownRenderer 渲染器、PermissionChecker 权限校验器、
│   │                                  # ArchiveItem/ArchiveMonth 归档结果
│   ├── controller/                    # 6 个 REST 控制器
│   ├── entity/                        # 7 个实体类，与数据表一一对应
│   ├── exception/                     # BusinessException + GlobalExceptionHandler
│   ├── mapper/                        # MyBatis Mapper 接口
│   ├── service/impl/                  # 业务逻辑实现
│   └── web/AdminController.java       # 后台管理页面（Thymeleaf）
├── src/main/resources/
│   ├── mapper/*.xml                   # 7 个 MyBatis XML 映射文件
│   ├── static/                        # 前台静态页面
│   └── templates/admin/               # 后台 Thymeleaf 模板
└── src/test/java/cuit/mygit/
    ├── common/                        # MarkdownRenderer、PermissionChecker 的单元测试
    └── service/                       # 6 个 Service 单元测试
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
- **草稿的可见性在 SQL 层过滤**：公开查询一律带 `WHERE status = 1`，草稿只有作者本人和管理员取得到。没有采用「全部查出再在前端隐藏」的做法——那样草稿的标题和 ID 已经随 JSON 发到浏览器，按 F12 就能看见
- **Markdown 存原文、渲染在服务端**：数据库里存的是 Markdown 源码，显示时才渲染成 HTML。渲染器关掉了原生 HTML 透传（`escapeHtml(true)`）并过滤 `javascript:` 链接（`sanitizeUrls(true)`），避免正文成为 XSS 通道；编辑器预览走同一个渲染接口，保证「预览即所得」
- **越权写的防护在后端**：编辑、删除、改标签都要求请求里带上 `operatorId`，由 Service 校验「作者本人或管理员」。前端的按钮置灰只是少让人白点一次，不是安全边界
- **权限规则只有一份**：`PermissionChecker` 提供 `checkOwnerOrAdmin`（本人或管理员）和 `checkAdmin`（仅管理员）两条规则，文章、评论、用户走前者，分类、标签走后者。规则基于数据的归属方式而定——文章有作者、评论有作者、用户资料有本人，所以能放宽到「本人也可」；`category` 和 `tag` 表没有 owner 列，分类和标签是全站共用的字典数据，不存在「我的分类」，因此只能管理员写。抽取之前 `ArticleServiceImpl` 和 `CommentServiceImpl` 各存了一份逐字相同的私有 `checkPermission`，再加三个模块就是五份；现在规则由 `PermissionCheckerTest` 单独钉住，各 Service 的测试改为验证「拒绝时一个字都没写库」和「传下去的 ownerId 对不对」——后者才是容易出错的地方，ownerId 传成操作人自己的 ID，权限照样能过，但结果是谁都能改别人的文章
- **「改自己」放宽的是资料，不包括身份**：用户资料本人可以改，但 `role` 字段在 `UserServiceImpl.update` 里被单独收紧了——非管理员提交的角色值一律丢弃、沿用药里的原值，只有管理员能授予或撤销管理员身份。放宽到「本人也可」时很容易漏掉这类字段级的例外：`/user/update` 若原样照抄请求体里的 `role`，普通用户改一次自己的资料就能把自己变成管理员，整条规则也就形同虚设
- **归档用「一条 SQL 拉全量 + 内存分组」**：`DATE_FORMAT(create_time, '%Y-%m')` 在 SQL 里算出所属月份并排好序，Java 侧只做「分堆」，一次查询覆盖整页。换成「先查月份列表、再按月份查文章」会变成 N+1 次查询；换成 `GROUP BY` 又得配 `GROUP_CONCAT` 才能把文章带出来，更难读
- **评论不可编辑**：`comment` 是唯一没有 Update 操作的表，刻意保持「发出即不可修改」的语义
- **分类与标签的建模范式**：分类是一对多（`article.category_id` 外键），标签是多对多（`article_tag` 中间表），用两种不同的数据结构解决两种内容组织需求
- **三种排序用三个接口，不加 `sort` 参数**：`/article/page`（最新）、`/article/hot`（热门）、`/article/recommend`（推荐）各自一条 SQL。合并成一个接口就得在 XML 里用 `<choose>` 拼 `ORDER BY`，而排序字段是**列名**不是值，没法用 `#{}` 预编译，只能拼字符串——那正是 SQL 注入的入口。多写两条 SQL 换来的是不用做白名单校验
- **热门排序带第二排序条件**：`ORDER BY view_count DESC, article_id DESC`。浏览量相同的文章如果不定序，翻页时同一篇可能在两页各出现一次，或者谁都查不到。第二条件用主键而不是 `create_time`，因为后者允许为 NULL，MySQL 对 NULL 的排序规则和普通值不同，等于又引入一个不确定因素
- **推荐流用 JOIN 而不是两次查询**：`article INNER JOIN user_follow` 一次取完「我关注了谁写的文章」。分两步走要么多发一次请求，要么在 `IN ()` 里塞几百个 ID。`user_follow` 是联合主键，同一作者不会被关注两次，所以 JOIN 不会产生重复行，也就不需要 `DISTINCT`
- **不能关注自己由 Service 拦，不靠数据库**：联合主键只能挡住"同一条关系重复"，`follower_id = followee_id` 这个组合本身是合法的。「两个值相等」是行级约束，MySQL 的 `CHECK` 在 8.0.16 之前会被静默忽略，所以这条规则放在 `UserFollowServiceImpl`
- **查询关注关系的方法不抛异常**：「关注了没有」返回 `true` / `false`，游客一律得到 `false` 而不是报错——文章详情页要靠它决定按钮文案，游客打开页面时整页不能因为这个挂掉。这和 `canSeeDraft` 的取舍一致

## 文档

- [系统设计说明书](docs/系统设计说明书.md) —— 模块划分、接口清单、数据库设计
- [数据库设计文档](docs/数据库设计文档.md) —— 表结构、字段说明、实体关系
- [实验三开发记录](docs/实验三开发记录.md) —— 增删改查与后台界面的迭代过程
