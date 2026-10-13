package cuit.mygit.service.impl;

import cuit.mygit.common.ArchiveItem;
import cuit.mygit.common.ArchiveMonth;
import cuit.mygit.common.MarkdownRenderer;
import cuit.mygit.common.PageResult;
import cuit.mygit.common.PermissionChecker;
import cuit.mygit.entity.Article;
import cuit.mygit.entity.ArticleTag;
import cuit.mygit.entity.Tag;
import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.ArticleTagMapper;
import cuit.mygit.mapper.CategoryMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.TagMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文章业务实现类。
 * <p>
 * {@code @Service} 告诉 Spring：启动时把这个类创建成对象放进容器，
 * 这样 Controller 里才能注入它。
 * <p>
 * 文章是系统的核心表，牵扯的用户表、分类表、评论表、标签关联表都靠它串起来，
 * 所以这个类里注入的 Mapper 也最多。
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    /**
     * 分页时每页最多允许取多少条。
     * <p>
     * 加上限是为了防住 {@code ?size=100000} 这种请求 —— 前端要是真传了这个数，
     * 一次就把整张表读进内存，数据库和浏览器都受不了。
     * 传超了按上限处理，不报错。
     */
    private static final int MAX_PAGE_SIZE = 50;

    /**
     * 文章状态：已发布。首页、分类页、搜索结果里能看到的都是这一类。
     */
    private static final int STATUS_PUBLISHED = 1;

    /**
     * 文章状态：草稿。只有作者本人和管理员看得到，
     * 公开入口（列表、分页、搜索、按分类/标签/作者查）在 SQL 里就把它滤掉了。
     * <p>
     * 这两个常量为什么用 int 而不是枚举？因为库里 status 就是 TINYINT，
     * 用 int 全程不用转换。真实项目里更该用枚举，那是另一回事了。
     */
    private static final int STATUS_DRAFT = 0;

    /**
     * 注入 Mapper。
     * 这是我们自己写的接口，实现类由 MyBatis 在启动时动态生成。
     */
    @Autowired
    private ArticleMapper articleMapper;

    /** 发表文章要确认作者真实存在（article.user_id 有外键） */
    @Autowired
    private UserMapper userMapper;

    /** 发表文章要确认分类真实存在（article.category_id 有外键） */
    @Autowired
    private CategoryMapper categoryMapper;

    /** 删文章要连带删掉它下面的评论 */
    @Autowired
    private CommentMapper commentMapper;

    /** 删文章要连带清掉它的标签关联 */
    @Autowired
    private ArticleTagMapper articleTagMapper;

    /** 给文章贴标签时要确认标签本身存在 */
    @Autowired
    private TagMapper tagMapper;

    /** 把 Markdown 原文渲染成 HTML，塞进 article.contentHtml 给前端用 */
    @Autowired
    private MarkdownRenderer markdownRenderer;

    /** 改文章、删文章、改标签都要先问一句"这个人有没有资格" */
    @Autowired
    private PermissionChecker permissionChecker;

    @Override
    public List<Article> listAll() {
        // 草稿的过滤在 SQL 里（selectAll 的 WHERE status = 1）。
        // 放在这里 Filter 也行，但那样草稿数据已经被读进内存了 ——
        // 能不让它离开数据库，就别让它离开
        return articleMapper.selectAll();
    }

    @Override
    public List<Article> listForManage(Integer operatorId) {
        if (operatorId == null) {
            throw new BusinessException("缺少操作人ID");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作人不存在：" + operatorId);
        }

        // 是不是管理员，决定"别人的草稿"要不要一起查出来。
        // 关键是这个判断要在**查库之前**做完、并且体现在 SQL 的 where 里：
        // 如果先把所有草稿查出来、再在 Java 里筛掉别人的，
        // 那些数据其实已经到过内存了，哪天有人顺手把中间结果返回出去就漏了。
        // 权限过滤的位置越靠下越安全
        boolean includeAllDrafts = Integer.valueOf(1).equals(operator.getRole());
        return articleMapper.selectForManage(operatorId, includeAllDrafts);
    }

    @Override
    public List<ArchiveMonth> listArchive() {
        // SQL 已经把月份算好、并且按时间倒序排好了，这里只做"分堆"
        List<ArchiveItem> rows = articleMapper.selectArchive();

        /*
         * 用 LinkedHashMap 而不是 HashMap：
         * 前者保持插入顺序，而数据库给的顺序是"从新到旧"，
         * 所以先遇到的就是更新的月份，分完堆自然就是新的在前。
         * 换成 HashMap 的话月份顺序会变得随机 —— 因为 String 的
         * hashCode 是固定的，同样的数据每次跑出来顺序一样，
         * 但那是巧合，不是设计，改天数据变了就会莫名其妙地乱序。
         */
        Map<String, List<ArchiveItem>> byMonth = new LinkedHashMap<>();
        for (ArchiveItem row : rows) {
            // computeIfAbsent：这个月还没出现过就先开一个空列表，有就直接拿
            byMonth.computeIfAbsent(row.getMonth(), m -> new ArrayList<>()).add(row);
        }

        return byMonth.entrySet().stream()
                .map(e -> new ArchiveMonth(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public Article getById(Integer articleId, Integer operatorId) {
        if (articleId == null) {
            throw new BusinessException("缺少文章ID");
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }

        boolean draft = isDraft(article);

        // 草稿只有作者本人和管理员打得开。
        if (draft && !canSeeDraft(operatorId, article.getUserId())) {
            // 注意这里说的是"文章不存在"，不是"你没有权限看"。
            // 说"没权限"等于承认"这个ID上确实躺着一篇草稿"，
            // 对方接着试 1、2、3……就能把你攒了多少篇没发的东西数出来。
            // 对访问者来说，"查不到"和"看不着"应该长得一模一样。
            throw new BusinessException("文章不存在：" + articleId);
        }

        // 权限过了，才把正文渲染出来。
        // 放在这一句之后不是随手写的：渲染是有成本的（虽然不高），
        // 更关键的是没权限的请求不该拿到任何正文内容 —— 哪怕是渲染后的
        article.setContentHtml(markdownRenderer.toHtml(article.getContent()));

        if (draft) {
            // 草稿不加浏览量 —— 作者自己来回预览几遍就把数字刷上去了，没意义。
            // 但 null 得兜住：数据库里默认是 0，转换过程万一丢了，
            // 前端拿到的就是 null，还得自己判空
            if (article.getViewCount() == null) {
                article.setViewCount(0);
            }
            return article;
        }

        // 有人点进来看详情，浏览量加一
        articleMapper.increaseViewCount(articleId);

        // 数据库里的浏览量已经加过了，但手里这个对象还是加之前的值。
        // 不跟着改的话，返回给浏览器的 JSON 会比数据库少 1，看着对不上。
        int currentViewCount = article.getViewCount() == null ? 0 : article.getViewCount();
        article.setViewCount(currentViewCount + 1);

        return article;
    }

    @Override
    public String renderPreview(String markdown) {
        return markdownRenderer.toHtml(markdown);
    }

    @Override
    @Transactional
    public Article add(Article article) {
        if (article == null || isBlank(article.getTitle())) {
            throw new BusinessException("文章标题不能为空");
        }

        // ---------- 校验作者 ----------
        if (article.getUserId() == null) {
            throw new BusinessException("缺少作者ID");
        }
        if (userMapper.selectById(article.getUserId()) == null) {
            throw new BusinessException("作者不存在：" + article.getUserId());
        }

        // ---------- 校验分类（可以不填，表示"未分类"）----------
        // 建表时 category_id 是允许为 NULL 的，所以这里只在填了的时候才检查
        if (article.getCategoryId() != null
                && categoryMapper.selectById(article.getCategoryId()) == null) {
            throw new BusinessException("分类不存在：" + article.getCategoryId());
        }

        // ---------- 发布状态 ----------
        // 不传就当"直接发布"。默认值给已发布，老调用方和只想发篇文章的
        // 调用方都不用关心这个概念，行为跟加这个功能之前一样。
        if (article.getStatus() == null) {
            article.setStatus(STATUS_PUBLISHED);
        }
        checkStatus(article.getStatus());

        articleMapper.insert(article);

        // 重新查一次再返回。
        // 发布时间、修改时间、浏览量都是数据库自动填的，手里这个对象上还是 null，
        // 回查一次就能拿到真实值，前端拿到手就是完整的一条记录。
        return articleMapper.selectById(article.getArticleId());
    }

    @Override
    public void update(Article article, Integer operatorId) {
        if (article == null || article.getArticleId() == null) {
            throw new BusinessException("缺少文章ID，无法修改");
        }

        Article old = articleMapper.selectById(article.getArticleId());
        if (old == null) {
            throw new BusinessException("文章不存在：" + article.getArticleId());
        }

        // 只有作者本人或管理员能改。
        // 这一行是补上的：早先这里什么都没查，任何登录用户都能改别人的文章。
        // 放在最前面校验，失败时连"分类存不存在"这种信息都不透露出去。
        permissionChecker.checkOwnerOrAdmin(operatorId, old.getUserId(), "修改这篇文章");

        if (isBlank(article.getTitle())) {
            throw new BusinessException("文章标题不能为空");
        }
        if (article.getCategoryId() != null
                && categoryMapper.selectById(article.getCategoryId()) == null) {
            throw new BusinessException("分类不存在：" + article.getCategoryId());
        }

        // 不传 status 表示"这次不改发布状态"，沿用库里原来的值。
        // 必须这么兜一下：status 在库里的 NOT NULL，
        // 直接写 null 进去会报错；而"不传就重置成已发布"更糟 ——
        // 编辑一篇草稿时忘了带这个字段，它就自己发出去了。
        if (article.getStatus() == null) {
            article.setStatus(old.getStatus());
        }
        checkStatus(article.getStatus());

        // 作者不允许改（文章不能换人），用原来的人
        article.setUserId(old.getUserId());

        articleMapper.update(article);
    }

    @Override
    @Transactional
    public void delete(Integer articleId, Integer operatorId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }

        // 只有作者本人或管理员能删
        permissionChecker.checkOwnerOrAdmin(operatorId, article.getUserId(), "删除这篇文章");

        // 删除顺序由外键决定，不能乱：
        // comment 和 article_tag 都指着 article，必须先删它们，最后才能删文章本身。
        commentMapper.deleteByArticleId(articleId);
        articleTagMapper.deleteByArticleId(articleId);
        articleMapper.deleteById(articleId);

        // @Transactional 保证这三步是一个整体：
        // 万一删到一半出错，前面的删除会回滚，不会出现"评论没了但文章还在"的脏数据。
    }

    @Override
    public List<Tag> listTags(Integer articleId) {
        if (articleId == null) {
            throw new BusinessException("缺少文章ID");
        }
        return articleTagMapper.selectTagsByArticleId(articleId);
    }

    @Override
    public void addTag(Integer articleId, Integer tagId, Integer operatorId) {
        if (articleId == null || tagId == null) {
            throw new BusinessException("缺少文章ID或标签ID");
        }

        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }

        // 标签是文章内容的一部分，谁能改文章谁才能改它的标签。
        // 不校验的话，别人就能往你的文章上乱贴标签了。
        permissionChecker.checkOwnerOrAdmin(operatorId, article.getUserId(), "修改这篇文章的标签");

        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException("标签不存在：" + tagId);
        }

        // article_tag 的主键是 (article_id, tag_id) 联合主键，
        // 重复插入会直接被数据库拒绝。提前查一次，好给出看得懂的提示。
        ArticleTag relation = new ArticleTag(articleId, tagId);
        if (articleTagMapper.selectOne(relation) != null) {
            throw new BusinessException("这篇文章已经贴过该标签了");
        }

        articleTagMapper.insert(relation);
    }

    @Override
    public void removeTag(Integer articleId, Integer tagId, Integer operatorId) {
        if (articleId == null || tagId == null) {
            throw new BusinessException("缺少文章ID或标签ID");
        }

        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }
        permissionChecker.checkOwnerOrAdmin(operatorId, article.getUserId(), "修改这篇文章的标签");

        ArticleTag relation = new ArticleTag(articleId, tagId);
        if (articleTagMapper.selectOne(relation) == null) {
            throw new BusinessException("这篇文章没有贴过该标签，无法取消");
        }

        articleTagMapper.delete(relation);
    }

    // ==================== 筛选查询与分页 ====================

    @Override
    public List<Article> listByUserId(Integer userId) {
        if (userId == null) {
            throw new BusinessException("缺少作者ID");
        }
        // 这里**不检查这个用户存不存在**。
        // 查询接口的语义是"把符合条件的都给我"，条件匹配不上就是空列表；
        // 查一个不存在的用户返回"用户不存在"，反而让前端的空状态页面
        // 得处理两种失败情况，得不偿失。真正需要存在性的是"写入"类操作。
        return articleMapper.selectByUserId(userId);
    }

    @Override
    public List<Article> listByCategoryId(Integer categoryId) {
        if (categoryId == null) {
            throw new BusinessException("缺少分类ID");
        }
        return articleMapper.selectByCategoryId(categoryId);
    }

    @Override
    public List<Article> listByTagId(Integer tagId) {
        if (tagId == null) {
            throw new BusinessException("缺少标签ID");
        }
        return articleMapper.selectByTagId(tagId);
    }

    @Override
    public List<Article> searchByTitle(String keyword) {
        if (isBlank(keyword)) {
            throw new BusinessException("搜索关键字不能为空");
        }

        // 去掉前后空格再查。用户从别处复制标题过来时经常带个尾随空格，
        // 不处理的话搜" MySQL"会拼成 LIKE '% MySQL%'，
        // 凡是"MySQL"不在标题开头的文章全都搜不到，用起来莫名其妙。
        return articleMapper.selectByTitle(keyword.trim());
    }

    @Override
    public PageResult<Article> page(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);

        // 总条数要先查 —— PageResult 靠它才能算出总页数
        long total = articleMapper.countAll();

        // 页码大得这么离谱，说明对方不是真想翻页，直接给个空页
        Integer offset = offsetOf(safePage, safeSize);
        if (offset == null) {
            return new PageResult<>(List.of(), total, safePage, safeSize);
        }

        List<Article> list = articleMapper.selectPage(offset, safeSize);
        return new PageResult<>(list, total, safePage, safeSize);
    }

    @Override
    public PageResult<Article> hot(int page, int size) {
        int safePage = safePage(page);
        int safeSize = safeSize(size);

        // 热门只是换了排序，参与的文章还是"全部已发布"，
        // 所以总数直接复用 countAll，不用再写一个 countHot。
        long total = articleMapper.countAll();

        Integer offset = offsetOf(safePage, safeSize);
        if (offset == null) {
            return new PageResult<>(List.of(), total, safePage, safeSize);
        }

        List<Article> list = articleMapper.selectHotPage(offset, safeSize);
        return new PageResult<>(list, total, safePage, safeSize);
    }

    @Override
    public PageResult<Article> recommend(int page, int size, Integer followerId) {
        // 推荐流的出发点就是"我关注了谁"，没有"我"就无从谈起。
        // 这里选择报错而不是返回全站文章：后者会让游客看到一屏和"最新"
        // 完全相同的内容，但标签写着"推荐"，看起来像是功能坏了。
        // 前端在调这个接口之前会先判断登录状态，所以正常流程走不到这。
        if (followerId == null) {
            throw new BusinessException("缺少关注人ID");
        }

        int safePage = safePage(page);
        int safeSize = safeSize(size);

        long total = articleMapper.countByFollowerId(followerId);

        // 一篇文章都没有（没关注任何人，或者关注的人还没写过东西）。
        // 提前返回，省掉一次必然查不到数据的 LIMIT 查询。
        // 注意返回的是**空分页结果**而不是抛异常 —— "还没关注人"是
        // 正常状态，不是错误，前端要靠 total = 0 来决定显示引导文案。
        if (total == 0) {
            return new PageResult<>(List.of(), 0, safePage, safeSize);
        }

        Integer offset = offsetOf(safePage, safeSize);
        if (offset == null) {
            return new PageResult<>(List.of(), total, safePage, safeSize);
        }

        List<Article> list = articleMapper.selectRecommendedByFollowerId(followerId, offset, safeSize);
        return new PageResult<>(list, total, safePage, safeSize);
    }

    /**
     * 纠正页码。
     * <p>
     * 前端传上来的值不能直接拿去用。策略是"纠正"而不是"报错"：
     * 翻到第 0 页、或者手动改地址栏上的页码都是很常见的事，
     * 弹个错误提示反而让人以为系统坏了。
     *
     * @param page 原始页码
     * @return 至少为 1 的页码
     */
    private int safePage(int page) {
        return Math.max(page, 1);
    }

    /**
     * 纠正每页条数。
     * <p>
     * 上限是为了防住 {@code ?size=100000} 这种请求 ——
     * 一次把整张表读进内存，数据库和浏览器都受不了。
     *
     * @param size 原始每页条数
     * @return 落在 [1, MAX_PAGE_SIZE] 区间内的条数
     */
    private int safeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /**
     * 把页码换算成 LIMIT 用的偏移量。
     * <p>
     * 算的时候先转成 long：页码传到几十万时 (safePage-1)*safeSize
     * 在 int 里会溢出成负数，拼进 LIMIT 就是一条语法错误的 SQL。
     *
     * @param safePage 已经纠正过的页码
     * @param safeSize 已经纠正过的每页条数
     * @return 偏移量；页码大到超出 int 范围时返回 null（表示"查都不用查"）
     */
    private Integer offsetOf(int safePage, int safeSize) {
        long offset = (long) (safePage - 1) * safeSize;
        return offset > Integer.MAX_VALUE ? null : (int) offset;
    }

    /**
     * 这篇文章是不是草稿。
     * <p>
     * status 为 null 时按"已发布"处理 —— 那是这个字段出现之前的老数据，
     * 把它们当成草稿会让所有老文章一夜之间从首页消失。
     *
     * @param article 文章对象（status 可能为 null）
     * @return 是草稿返回 true
     */
    private boolean isDraft(Article article) {
        return Integer.valueOf(STATUS_DRAFT).equals(article.getStatus());
    }

    /**
     * 这个人能不能看见"别人的草稿"。
     * <p>
     * 规则和 {@link PermissionChecker#checkOwnerOrAdmin} 一样（本人可以，管理员可以），
     * 但这里<b>返回 true/false 而不抛异常</b>：因为调用方拿到"不能看"
     * 之后要抛的是"文章不存在"而不是"没权限" ——
     * 抛什么错是调用方的事，这个方法只负责回答"能不能"。
     *
     * @param operatorId 操作人ID，可以是 null（游客）
     * @param authorId   草稿作者的ID
     * @return 可以看返回 true
     */
    private boolean canSeeDraft(Integer operatorId, Integer authorId) {
        if (operatorId == null) {
            // 没选身份就是游客，什么草稿都看不到
            return false;
        }
        if (operatorId.equals(authorId)) {
            // 自己写的，直接放行。短路在这一步还顺便省了一次查库
            return true;
        }
        User operator = userMapper.selectById(operatorId);
        return operator != null && Integer.valueOf(1).equals(operator.getRole());
    }

    /**
     * 校验发布状态。
     * <p>
     * 只认 0（草稿）和 1（已发布）。这个检查不能靠实体类上的注解 ——
     * 注解只能保证"是数字"，保证不了"是这两个数字之一"，
     * 更管不了"你有没有资格改它"。
     *
     * @param status 待校验的状态
     * @throws BusinessException 不是 0 也不是 1 时抛出
     */
    private void checkStatus(Integer status) {
        if (status == null || (status != STATUS_DRAFT && status != STATUS_PUBLISHED)) {
            throw new BusinessException("文章状态只能是 0（草稿）或 1（已发布）");
        }
    }

    /** 判断字符串是不是空的（null 或只有空格） */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
