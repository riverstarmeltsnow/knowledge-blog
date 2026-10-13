package cuit.mygit.service;

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
import cuit.mygit.service.impl.ArticleServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ArticleService 的单元测试。
 * <p>
 * 文章是系统的核心，业务规则最多，所以这里的测试也最全：
 * 浏览量加一、发文章时的各种校验、删文章时的权限判断和级联删除顺序。
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    @Mock
    private ArticleMapper articleMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private ArticleTagMapper articleTagMapper;

    @Mock
    private TagMapper tagMapper;

    /**
     * Markdown 渲染器用<b>真的</b>，不用 mock。
     * <p>
     * 它不依赖数据库、不依赖 Spring，构造完就是个纯函数（见 MarkdownRenderer）。
     * mock 掉的话，"contentHtml 到底有没有被填上、填得对不对"就测不出来了 ——
     * 而 Service 里对它的调用恰恰是这一层唯一需要保证的事。
     * <p>
     * 它自己的渲染规则由 {@code MarkdownRendererTest} 单独负责，两边不重复。
     */
    @Spy
    private MarkdownRenderer markdownRenderer = new MarkdownRenderer();

    /**
     * 权限校验器用<b>mock</b>，不用真的。
     * <p>
     * "谁能改谁的东西"这条规则本身由 {@code PermissionCheckerTest} 单独负责，
     * 这里关心的是另一件事：Service 有没有<b>把正确的 ownerId 传下去</b>。
     * 传错了（比如把自己的 ID 当成作者 ID）照样能过权限，
     * 但结果是任何人都能改别人的文章 —— 那种错只有靠 {@code verify} 盯参数才照得出来。
     * <p>
     * 用真的反而测不准：真 checker 要先查一遍用户表才能判断角色，
     * 每个用例都得额外铺 {@code userMapper} 的桩，而铺出来的桩又和规则本身纠缠在一起。
     */
    @Mock
    private PermissionChecker permissionChecker;

    @InjectMocks
    private ArticleServiceImpl articleService;

    // ==================== 工具方法：造测试数据 ====================

    /** 造一篇<b>已发布</b>的文章；articleId 传 null 表示"还没入库的新文章" */
    private Article buildArticle(Integer articleId, Integer authorId) {
        Article article = new Article();
        article.setArticleId(articleId);
        article.setTitle("测试标题");
        article.setContent("测试正文");
        article.setUserId(authorId);
        article.setStatus(1);
        article.setViewCount(0);
        return article;
    }

    /** 造一篇<b>草稿</b>：status = 0，只有作者本人和管理员看得见 */
    private Article buildDraft(Integer articleId, Integer authorId) {
        Article article = buildArticle(articleId, authorId);
        article.setStatus(0);
        return article;
    }

    /** 造一个用户；role 传 1 表示管理员 */
    private User buildUser(Integer userId, Integer role) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(role);
        return user;
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("查列表：直接返回 Mapper 查到的所有文章")
    void listAll_returnsWhatMapperReturns() {
        when(articleMapper.selectAll())
                .thenReturn(Arrays.asList(buildArticle(1, 1), buildArticle(2, 1)));

        List<Article> result = articleService.listAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("查详情：浏览量加一，返回的对象里也是加过之后的值")
    void getById_increasesViewCount() {
        Article article = buildArticle(1, 1);
        article.setViewCount(5);
        when(articleMapper.selectById(1)).thenReturn(article);

        // 第二个参数是"谁在看"。已发布的文章人人都能看，所以传 null（游客）也行
        Article result = articleService.getById(1, null);

        // 数据库那边加了一次
        verify(articleMapper).increaseViewCount(1);
        // 返回给浏览器的值也要跟着加，否则会比数据库少 1，看着对不上
        assertEquals(6, result.getViewCount().intValue());
        // 详情接口除了原文，还要把渲染好的 HTML 一起给前端，
        // 不然前端就得自己再实现一遍 Markdown —— 那就是两套渲染，迟早不一致
        assertTrue(result.getContentHtml().contains("<p>测试正文</p>"),
                "contentHtml 没填上：" + result.getContentHtml());
    }

    @Test
    @DisplayName("查详情：文章不存在时抛业务异常，不去加浏览量")
    void getById_notFound() {
        when(articleMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.getById(999, null));

        assertTrue(ex.getMessage().contains("文章不存在"));
        verify(articleMapper, never()).increaseViewCount(anyInt());
    }

    // ==================== 发表文章 ====================

    @Test
    @DisplayName("发表文章：标题为空时拒绝，不插库")
    void add_blankTitle() {
        Article article = buildArticle(null, 1);
        article.setTitle("  ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.add(article));

        assertTrue(ex.getMessage().contains("标题不能为空"));
        verify(articleMapper, never()).insert(any(Article.class));
    }

    @Test
    @DisplayName("发表文章：作者不存在时拒绝（否则数据库外键会报错）")
    void add_authorNotExist() {
        Article article = buildArticle(null, 999);
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.add(article));

        assertTrue(ex.getMessage().contains("作者不存在"));
        verify(articleMapper, never()).insert(any(Article.class));
    }

    @Test
    @DisplayName("发表文章：填了分类但分类不存在时拒绝")
    void add_categoryNotExist() {
        Article article = buildArticle(null, 1);
        article.setCategoryId(88);
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        when(categoryMapper.selectById(88)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.add(article));

        assertTrue(ex.getMessage().contains("分类不存在"));
        verify(articleMapper, never()).insert(any(Article.class));
    }

    @Test
    @DisplayName("发表文章：不填分类也能发（表示未分类），成功后返回查回来的完整记录")
    void add_successWithoutCategory() {
        Article article = buildArticle(null, 1);
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));

        // 模拟数据库回填自增ID
        doAnswer(invocation -> {
            Article arg = invocation.getArgument(0);
            arg.setArticleId(10);
            return 1;
        }).when(articleMapper).insert(any(Article.class));

        Article saved = buildArticle(10, 1);
        when(articleMapper.selectById(10)).thenReturn(saved);

        Article result = articleService.add(article);

        assertEquals(10, result.getArticleId().intValue());
        // 没填分类时不该去查分类表
        verify(categoryMapper, never()).selectById(any());
    }

    // ==================== 预览渲染 ====================

    @Test
    @DisplayName("预览：把 Markdown 渲染成 HTML，一个字都不落库")
    void renderPreview_rendersWithoutTouchingDb() {
        String html = articleService.renderPreview("**粗体**");

        assertTrue(html.contains("<strong>粗体</strong>"));
        // 预览处理的是"还没提交的正文"，连属于哪篇文章都不知道，
        // 所以它一个数据库操作都不该有
        verifyNoInteractions(articleMapper);
    }

    @Test
    @DisplayName("预览：正文为空时返回空串，不抛异常")
    void renderPreview_blank() {
        // 编辑器里刚全选删掉、还没开始写就点了预览，属于正常操作，
        // 不该弹一个红叉出来
        assertEquals("", articleService.renderPreview(null));
        assertEquals("", articleService.renderPreview("   \n  "));
    }

    // ==================== 草稿与发布状态 ====================

    /*
     * 一条"草稿"的定义：status = 0，只有作者本人和管理员看得见。
     * 公开入口（列表、分页、搜索、按分类/标签/作者查）的过滤写在 SQL 里，
     * 属于 Mapper 的职责，单测里 mock 掉了查不到 —— 那部分靠下面的
     * getById 和 listForManage 来保证：它们是"绕过列表、直接按ID拿数据"
     * 的入口，也最容易漏。
     */

    @Test
    @DisplayName("查详情：草稿作者本人能看，但草稿不涨浏览量")
    void getById_draft_authorCanSee() {
        when(articleMapper.selectById(1)).thenReturn(buildDraft(1, 1));

        Article result = articleService.getById(1, 1);

        assertEquals(0, result.getStatus().intValue());
        // 作者来回预览几遍就把浏览量刷上去了，没意义。
        // 这里也顺便验证了"是自己的稿子"是在查用户表之前就短路掉的
        verify(articleMapper, never()).increaseViewCount(anyInt());
        // 草稿也要能渲染 —— 作者写稿子的时候当然想看排版效果
        assertTrue(result.getContentHtml().contains("<p>测试正文</p>"));
    }

    @Test
    @DisplayName("查详情：没权限看草稿时连渲染都不做，正文一个字符都不处理")
    void getById_draft_strangerDoesNotRender() {
        when(articleMapper.selectById(1)).thenReturn(buildDraft(1, 1));
        when(userMapper.selectById(3)).thenReturn(buildUser(3, 0));

        assertThrows(BusinessException.class, () -> articleService.getById(1, 3));

        // "渲染放在权限判断之后"是有意的：没权限的请求不该拿到任何正文内容，
        // 哪怕是已经转成 HTML 的。这条断言守住这个顺序
        verify(markdownRenderer, never()).toHtml(anyString());
    }

    @Test
    @DisplayName("查详情：管理员能看别人的草稿")
    void getById_draft_adminCanSee() {
        when(articleMapper.selectById(1)).thenReturn(buildDraft(1, 1));  // 作者是 1 号
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 1));      // 2 号是管理员

        Article result = articleService.getById(1, 2);

        assertEquals(0, result.getStatus().intValue());
    }

    @Test
    @DisplayName("查详情：别人的草稿，对外只说'文章不存在'，不承认这篇稿子存在")
    void getById_draft_strangerCannotSee() {
        when(articleMapper.selectById(1)).thenReturn(buildDraft(1, 1));  // 作者是 1 号
        when(userMapper.selectById(3)).thenReturn(buildUser(3, 0));      // 3 号是普通用户

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.getById(1, 3));

        // 关键在这个措辞：说的是"不存在"，不是"没权限"。
        // 说"没权限"等于承认这个ID上确实有东西，对方接着试 1、2、3……
        // 就能把你攒了几篇没发的稿子数出来
        assertTrue(ex.getMessage().contains("文章不存在"));
        assertFalse(ex.getMessage().contains("权限"));
        verify(articleMapper, never()).increaseViewCount(anyInt());
    }

    @Test
    @DisplayName("查详情：没登录（operatorId 为 null）看不到草稿")
    void getById_draft_visitorCannotSee() {
        when(articleMapper.selectById(1)).thenReturn(buildDraft(1, 1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.getById(1, null));

        assertTrue(ex.getMessage().contains("文章不存在"));
    }

    @Test
    @DisplayName("查详情：已发布的文章，游客照样能看")
    void getById_published_visitorCanSee() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));

        Article result = articleService.getById(1, null);

        assertEquals(1, result.getStatus().intValue());
        // 已发布的不受影响，浏览量照加
        verify(articleMapper).increaseViewCount(1);
    }

    @Test
    @DisplayName("发表文章：不传 status 就是直接发布，行为和老版本一致")
    void add_defaultsToPublished() {
        Article article = buildArticle(null, 1);
        article.setStatus(null);   // 调用方压根没提这个概念
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        doAnswer(invocation -> {
            Article arg = invocation.getArgument(0);
            arg.setArticleId(10);
            return 1;
        }).when(articleMapper).insert(any(Article.class));
        when(articleMapper.selectById(10)).thenReturn(buildArticle(10, 1));

        articleService.add(article);

        // 不能默认成草稿 —— 那会让所有不传 status 的老调用方发出去的文章
        // 全都从首页消失，用户还以为发失败了
        assertEquals(1, article.getStatus().intValue());
    }

    @Test
    @DisplayName("发表文章：status 传 0 就存成草稿")
    void add_asDraft() {
        Article article = buildArticle(null, 1);
        article.setStatus(0);
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        doAnswer(invocation -> {
            Article arg = invocation.getArgument(0);
            arg.setArticleId(10);
            return 1;
        }).when(articleMapper).insert(any(Article.class));
        when(articleMapper.selectById(10)).thenReturn(buildArticle(10, 1));

        articleService.add(article);

        // 断言"插进去的那一篇"确实是草稿，而不是内存里改了、写库时又变回已发布
        assertEquals(0, article.getStatus().intValue());
        verify(articleMapper).insert(article);
    }

    @Test
    @DisplayName("发表文章：status 不是 0 也不是 1 时拒绝，不插库")
    void add_invalidStatus() {
        Article article = buildArticle(null, 1);
        article.setStatus(2);
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.add(article));

        assertTrue(ex.getMessage().contains("文章状态"));
        verify(articleMapper, never()).insert(any(Article.class));
    }

    @Test
    @DisplayName("修改文章：不传 status 时沿用库里原值，草稿不会因为编辑一下就发出去")
    void update_keepsOldStatusWhenNotProvided() {
        Article old = buildDraft(1, 1);
        when(articleMapper.selectById(1)).thenReturn(old);

        Article edited = buildArticle(1, 1);
        edited.setTitle("改过的标题");
        edited.setStatus(null);   // 编辑表单里没带这个字段

        articleService.update(edited, 1);

        // 要是这里兜底写成"null 就当作已发布"，那编辑一篇草稿、
        // 顺手点个保存，稿子就自己发出去了 —— 而且不会有任何提示
        assertEquals(0, edited.getStatus().intValue());
    }

    @Test
    @DisplayName("修改文章：带了 status 就按带来的改（发布 or 转回草稿）")
    void update_changesStatusWhenProvided() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));

        Article edited = buildArticle(1, 1);
        edited.setTitle("改过的标题");
        edited.setStatus(0);   // 撤回，变回草稿

        articleService.update(edited, 1);

        assertEquals(0, edited.getStatus().intValue());
        verify(articleMapper).update(edited);
    }

    @Test
    @DisplayName("修改文章：status 不是 0 也不是 1 时拒绝，不写库")
    void update_invalidStatus() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));

        Article edited = buildArticle(1, 1);
        edited.setTitle("改过的标题");
        edited.setStatus(7);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.update(edited, 1));

        assertTrue(ex.getMessage().contains("文章状态"));
        verify(articleMapper, never()).update(any(Article.class));
    }

    @Test
    @DisplayName("管理页列表：普通用户查不到别人的草稿")
    void listForManage_regularUser() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));
        when(articleMapper.selectForManage(2, false)).thenReturn(Collections.emptyList());

        articleService.listForManage(2);

        // includeAllDrafts 必须是 false。一旦传成 true，
        // 全站所有人的草稿就都发到普通用户的浏览器里了
        verify(articleMapper).selectForManage(2, false);
    }

    @Test
    @DisplayName("管理页列表：管理员能查到所有人的草稿")
    void listForManage_admin() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 1));
        when(articleMapper.selectForManage(1, true)).thenReturn(Collections.emptyList());

        articleService.listForManage(1);

        verify(articleMapper).selectForManage(1, true);
    }

    @Test
    @DisplayName("管理页列表：没传操作人ID时拒绝")
    void listForManage_noOperator() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.listForManage(null));

        assertTrue(ex.getMessage().contains("缺少操作人ID"));
    }

    // ==================== 修改文章（权限） ====================

    /*
     * 下面这一组是补的。
     *
     * 原先 update 的签名是 update(Article)，里面只收一个文章对象 ——
     * 参数里根本没有"是谁在改"这个信息，想判权限都没得判。
     * 结果就是任何登录用户都能把别人的文章改掉，
     * 而删除那边（下面那一组）一直是有权限判断的，两边不一致。
     *
     * 测试也是零：update 一个用例都没有，所以这个洞一直没被照出来。
     */

    @Test
    @DisplayName("修改文章：既不是作者也不是管理员时拒绝，且一个字都不写库")
    void update_notOwnerAndNotAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号
        // 让权限校验像真的一样拒掉 2 号。规则本身在 PermissionCheckerTest 里测，
        // 这里只关心"拒了之后 Service 有没有真的停手"
        doThrow(new BusinessException("没有权限修改这篇文章（只有作者本人和管理员可以操作）"))
                .when(permissionChecker).checkOwnerOrAdmin(2, 1, "修改这篇文章");

        Article edited = buildArticle(1, 1);
        edited.setTitle("被篡改的标题");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.update(edited, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        // 权限没过，update 一次都不该被调用。
        // 这条断言就是这个 bug 的"看门人"：权限判断要是被谁删了，这里立刻红
        verify(articleMapper, never()).update(any(Article.class));
    }

    @Test
    @DisplayName("修改文章：作者本人可以改")
    void update_byAuthor() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));

        Article edited = buildArticle(1, 1);
        edited.setTitle("改过的标题");

        articleService.update(edited, 1);

        verify(articleMapper).update(edited);
        // 作者 ID 得是从库里查出来的 1（作者），不能是别的东西
        verify(permissionChecker).checkOwnerOrAdmin(1, 1, "修改这篇文章");
    }

    @Test
    @DisplayName("修改文章：管理员可以改别人的文章")
    void update_byAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号

        Article edited = buildArticle(1, 1);
        edited.setTitle("管理员改的");

        articleService.update(edited, 2);

        verify(articleMapper).update(edited);
        // 操作人 2、作者 1 —— 两个参数分得清，才说明"管理员能改别人的"走的确实是这条路
        verify(permissionChecker).checkOwnerOrAdmin(2, 1, "修改这篇文章");
    }

    @Test
    @DisplayName("修改文章：请求体里塞别人的 userId 也没用，作者以库里原值为准")
    void update_cannotChangeAuthor() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 原作者是 1 号

        Article edited = buildArticle(1, 999);  // 想把这篇"过继"给 999 号
        edited.setTitle("改过的标题");

        articleService.update(edited, 1);

        // 不覆盖的话，改完文章就从自己列表里消失、跑到别人名下了 ——
        // 等于绕过权限把自己不想要的文章塞给别人
        assertEquals(1, edited.getUserId().intValue());
    }

    @Test
    @DisplayName("修改文章：文章不存在时抛业务异常")
    void update_articleNotExist() {
        when(articleMapper.selectById(999)).thenReturn(null);

        Article edited = buildArticle(999, 1);
        edited.setTitle("改过的标题");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.update(edited, 1));

        assertTrue(ex.getMessage().contains("文章不存在"));
        verify(articleMapper, never()).update(any(Article.class));
    }

    // ==================== 删除文章（权限 + 级联） ====================

    @Test
    @DisplayName("删除文章：既不是作者也不是管理员时拒绝，且一条数据都不删")
    void delete_notOwnerAndNotAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号
        doThrow(new BusinessException("没有权限删除这篇文章（只有作者本人和管理员可以操作）"))
                .when(permissionChecker).checkOwnerOrAdmin(2, 1, "删除这篇文章");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.delete(1, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        // 权限没过，三个删除动作一个都不该发生
        verify(commentMapper, never()).deleteByArticleId(anyInt());
        verify(articleTagMapper, never()).deleteByArticleId(anyInt());
        verify(articleMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("删除文章：作者本人可以删，且按'评论→标签关联→文章'的顺序级联删除")
    void delete_byAuthor_cascadesInOrder() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));

        articleService.delete(1, 1);

        verify(permissionChecker).checkOwnerOrAdmin(1, 1, "删除这篇文章");

        // 顺序很重要：comment 和 article_tag 都有指向 article 的外键，
        // 先删文章会触发"外键约束失败"，所以要验证调用顺序
        InOrder inOrder = inOrder(commentMapper, articleTagMapper, articleMapper);
        inOrder.verify(commentMapper).deleteByArticleId(1);
        inOrder.verify(articleTagMapper).deleteByArticleId(1);
        inOrder.verify(articleMapper).deleteById(1);
    }

    @Test
    @DisplayName("删除文章：管理员可以删别人的文章")
    void delete_byAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号

        articleService.delete(1, 2);

        verify(articleMapper).deleteById(1);
        verify(permissionChecker).checkOwnerOrAdmin(2, 1, "删除这篇文章");
    }

    @Test
    @DisplayName("删除文章：文章不存在时抛业务异常")
    void delete_articleNotExist() {
        when(articleMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.delete(999, 1));

        assertTrue(ex.getMessage().contains("文章不存在"));
    }

    @Test
    @DisplayName("删除文章：没传操作人ID时抛业务异常")
    void delete_noOperator() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        // 操作人为空时 checker 会抛"缺少操作人ID"。
        // 一旦用了 isNull() 这种匹配器，同一个调用里的其它参数也都得用匹配器（eq），
        // 不能混着写裸值 —— 混着写 Mockito 会直接报 InvalidUseOfMatchersException
        doThrow(new BusinessException("缺少操作人ID"))
                .when(permissionChecker).checkOwnerOrAdmin(isNull(), eq(1), eq("删除这篇文章"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.delete(1, null));

        assertTrue(ex.getMessage().contains("缺少操作人ID"));
    }

    // ==================== 文章和标签的关联 ====================

    @Test
    @DisplayName("查标签：返回这篇贴的所有标签")
    void listTags_success() {
        Tag java = new Tag();
        java.setTagId(1);
        java.setName("Java");
        when(articleTagMapper.selectTagsByArticleId(1)).thenReturn(Arrays.asList(java));

        List<Tag> tags = articleService.listTags(1);

        assertEquals(1, tags.size());
        assertEquals("Java", tags.get(0).getName());
    }

    @Test
    @DisplayName("贴标签：同一个标签重复贴时拒绝")
    void addTag_duplicate() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号
        when(tagMapper.selectById(3)).thenReturn(new Tag());
        // 模拟"这条路已经贴过了"
        when(articleTagMapper.selectOne(any(ArticleTag.class)))
                .thenReturn(new ArticleTag(1, 3));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.addTag(1, 3, 1));

        assertTrue(ex.getMessage().contains("已经贴过"));
        verify(articleTagMapper, never()).insert(any(ArticleTag.class));
    }

    @Test
    @DisplayName("贴标签：标签不存在时拒绝")
    void addTag_tagNotExist() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        when(tagMapper.selectById(99)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.addTag(1, 99, 1));

        assertTrue(ex.getMessage().contains("标签不存在"));
    }

    @Test
    @DisplayName("贴标签：一切正常时插入关联记录")
    void addTag_success() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        when(tagMapper.selectById(3)).thenReturn(new Tag());
        when(articleTagMapper.selectOne(any(ArticleTag.class))).thenReturn(null);

        articleService.addTag(1, 3, 1);

        verify(articleTagMapper).insert(any(ArticleTag.class));
        verify(permissionChecker).checkOwnerOrAdmin(1, 1, "修改这篇文章的标签");
    }

    @Test
    @DisplayName("贴标签：不是作者也不是管理员时拒绝（不能给别人文章乱贴标签）")
    void addTag_notOwnerAndNotAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号
        doThrow(new BusinessException("没有权限修改这篇文章的标签（只有作者本人和管理员可以操作）"))
                .when(permissionChecker).checkOwnerOrAdmin(2, 1, "修改这篇文章的标签");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.addTag(1, 3, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        assertTrue(ex.getMessage().contains("标签"));
        // 权限没过，连标签存不存在都不该去查，更不该插数据
        verify(articleTagMapper, never()).insert(any(ArticleTag.class));
    }

    @Test
    @DisplayName("贴标签：管理员可以改别人文章的标签")
    void addTag_byAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        when(tagMapper.selectById(3)).thenReturn(new Tag());
        when(articleTagMapper.selectOne(any(ArticleTag.class))).thenReturn(null);

        articleService.addTag(1, 3, 2);

        verify(articleTagMapper).insert(any(ArticleTag.class));
        verify(permissionChecker).checkOwnerOrAdmin(2, 1, "修改这篇文章的标签");
    }

    @Test
    @DisplayName("取消标签：这篇没贴过该标签时拒绝")
    void removeTag_notExist() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        when(articleTagMapper.selectOne(any(ArticleTag.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.removeTag(1, 3, 1));

        assertTrue(ex.getMessage().contains("没有贴过"));
        verify(articleTagMapper, never()).delete(any(ArticleTag.class));
    }

    @Test
    @DisplayName("取消标签：不是作者也不是管理员时拒绝")
    void removeTag_notOwnerAndNotAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        doThrow(new BusinessException("没有权限修改这篇文章的标签（只有作者本人和管理员可以操作）"))
                .when(permissionChecker).checkOwnerOrAdmin(2, 1, "修改这篇文章的标签");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.removeTag(1, 3, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        verify(articleTagMapper, never()).delete(any(ArticleTag.class));
    }

    // ==================== 按作者 / 分类 / 标签筛选 ====================

    @Test
    @DisplayName("按作者查：把作者ID原样交给 Mapper，返回它给的结果")
    void listByUserId_success() {
        when(articleMapper.selectByUserId(2))
                .thenReturn(Arrays.asList(buildArticle(5, 2), buildArticle(6, 2)));

        List<Article> result = articleService.listByUserId(2);

        assertEquals(2, result.size());
        verify(articleMapper).selectByUserId(2);
    }

    @Test
    @DisplayName("按作者查：没传作者ID时拒绝，不查库")
    void listByUserId_nullId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.listByUserId(null));

        assertTrue(ex.getMessage().contains("缺少作者ID"));
        verify(articleMapper, never()).selectByUserId(any());
    }

    @Test
    @DisplayName("按分类查：把分类ID原样交给 Mapper")
    void listByCategoryId_success() {
        when(articleMapper.selectByCategoryId(3))
                .thenReturn(Collections.singletonList(buildArticle(7, 1)));

        assertEquals(1, articleService.listByCategoryId(3).size());
        verify(articleMapper).selectByCategoryId(3);
    }

    @Test
    @DisplayName("按分类查：没传分类ID时拒绝，不查库")
    void listByCategoryId_nullId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.listByCategoryId(null));

        assertTrue(ex.getMessage().contains("缺少分类ID"));
        verify(articleMapper, never()).selectByCategoryId(any());
    }

    @Test
    @DisplayName("按标签查：这个标签还没被贴过时返回空列表，而不是报错")
    void listByTagId_emptyResult() {
        when(articleMapper.selectByTagId(99)).thenReturn(Collections.emptyList());

        // 查询接口查不到东西属于正常情况。这里要真抛异常的话，
        // 用户点一个还没人用过的标签就会看到错误提示，像是系统坏了
        assertTrue(articleService.listByTagId(99).isEmpty());
    }

    @Test
    @DisplayName("按标签查：没传标签ID时拒绝，不查库")
    void listByTagId_nullId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.listByTagId(null));

        assertTrue(ex.getMessage().contains("缺少标签ID"));
        verify(articleMapper, never()).selectByTagId(any());
    }

    // ==================== 关键字搜索 ====================

    @Test
    @DisplayName("搜索：关键字前后有空格时，去掉空格再拿去查")
    void searchByTitle_trimKeyword() {
        when(articleMapper.selectByTitle("MySQL")).thenReturn(Collections.emptyList());

        articleService.searchByTitle("  MySQL  ");

        // 不 trim 的话会拼成 LIKE '% MySQL%'，
        // 凡是"MySQL"不出现在标题开头的文章，一条都搜不到
        verify(articleMapper).selectByTitle("MySQL");
    }

    @Test
    @DisplayName("搜索：关键字为空或只有空格时拒绝，不查库")
    void searchByTitle_blankKeyword() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.searchByTitle("   "));

        assertTrue(ex.getMessage().contains("关键字不能为空"));
        // 空关键字会拼成 LIKE '%%'，等于把整张表都查出来
        verify(articleMapper, never()).selectByTitle(any());
    }

    // ==================== 分页 ====================

    @Test
    @DisplayName("分页：第3页每页10条，跳过的应该是20条")
    void page_offsetCalculatedFromPageNumber() {
        when(articleMapper.countAll()).thenReturn(100L);
        when(articleMapper.selectPage(20, 10)).thenReturn(Collections.emptyList());

        articleService.page(3, 10);

        // 页码是给用户看的（从1数起），偏移量是给 SQL 用的（从0数起），
        // 两者差一位。算错的表现是"第2页和第1页内容一样"或者莫名跳过一整页
        verify(articleMapper).selectPage(20, 10);
    }

    @Test
    @DisplayName("分页：总页数向上取整 —— 87条每页10条是9页，不是8页")
    void page_totalPagesRoundsUp() {
        when(articleMapper.countAll()).thenReturn(87L);
        when(articleMapper.selectPage(0, 10)).thenReturn(Collections.emptyList());

        PageResult<Article> result = articleService.page(1, 10);

        // 87 ÷ 10 整除得 8，但第 9 页还有 7 条数据。
        // 用整除的话页码条会少画一页，最后那几篇永远点不到
        assertEquals(9, result.getTotalPages());
        assertEquals(87L, result.getTotal());
        // 第 1 页后面还有，前面当然没有
        assertTrue(result.isHasNext());
        assertFalse(result.isHasPrev());
    }

    @Test
    @DisplayName("分页：最后一页没有下一页")
    void page_lastPageHasNoNext() {
        when(articleMapper.countAll()).thenReturn(87L);
        when(articleMapper.selectPage(80, 10)).thenReturn(Collections.emptyList());

        PageResult<Article> result = articleService.page(9, 10);

        assertFalse(result.isHasNext());
        assertTrue(result.isHasPrev());
    }

    @Test
    @DisplayName("分页：每页条数超过上限时按上限截断，防止一次拉走整张表")
    void page_sizeClampedToMax() {
        when(articleMapper.countAll()).thenReturn(100L);
        when(articleMapper.selectPage(0, 50)).thenReturn(Collections.emptyList());

        articleService.page(1, 100000);

        // 前端传 10 万进来，实际只给 50 条。
        // 不设上限的话，一个 ?size=999999 就能把整张表读进内存
        verify(articleMapper).selectPage(0, 50);
    }

    @Test
    @DisplayName("分页：页码小于1时按第1页处理，不报错")
    void page_pageNumberTooSmall() {
        when(articleMapper.countAll()).thenReturn(5L);
        when(articleMapper.selectPage(0, 10)).thenReturn(Collections.emptyList());

        PageResult<Article> result = articleService.page(-3, 10);

        // 翻页翻过头、手动改地址栏上的页码都很常见，
        // 按边界值处理比弹个错误提示更自然
        assertEquals(1, result.getPage());
        verify(articleMapper).selectPage(0, 10);
    }

    @Test
    @DisplayName("分页：页码大到偏移量装不下时直接返回空页，不查数据库")
    void page_offsetOverflow_returnsEmptyPage() {
        when(articleMapper.countAll()).thenReturn(5L);

        PageResult<Article> result = articleService.page(Integer.MAX_VALUE, 50);

        // (MAX_VALUE-1)×50 早就超出 int 的范围了，硬算会溢出成负数，
        // 拼进 LIMIT 就成了一条语法错误的 SQL。
        // 页码大得这么离谱，说明对方不是真想翻页，给个空页就行
        assertTrue(result.getList().isEmpty());
        verify(articleMapper, never()).selectPage(anyInt(), anyInt());
    }

    // ==================== 热门 ====================

    @Test
    @DisplayName("热门：走的是按浏览量排序的那条 SQL，不是按时间的")
    void hot_usesHotQuery() {
        when(articleMapper.countAll()).thenReturn(30L);
        when(articleMapper.selectHotPage(0, 10)).thenReturn(Collections.emptyList());

        articleService.hot(1, 10);

        verify(articleMapper).selectHotPage(0, 10);
        // 这里特意反过来验一次：如果实现里不小心复制粘贴成了 selectPage，
        // 光验"selectHotPage 被调了"是发现不了的（它压根没被调，测试会失败）
        verify(articleMapper, never()).selectPage(anyInt(), anyInt());
    }

    @Test
    @DisplayName("热门：总数复用全站文章数，不再单独查一次")
    void hot_reusesTotalCount() {
        when(articleMapper.countAll()).thenReturn(30L);
        when(articleMapper.selectHotPage(0, 10)).thenReturn(Collections.emptyList());

        PageResult<Article> result = articleService.hot(1, 10);

        // 热门只是换了个排序，参与的文章一篇不多一篇不少，
        // 所以总数和"最新"完全一样，没必要再写一个 countHot
        assertEquals(30, result.getTotal());
        verify(articleMapper).countAll();
    }

    @Test
    @DisplayName("热门：每页条数同样受 50 条上限约束")
    void hot_sizeClampedToMax() {
        when(articleMapper.countAll()).thenReturn(100L);
        when(articleMapper.selectHotPage(0, 50)).thenReturn(Collections.emptyList());

        articleService.hot(1, 100000);

        // 上限这条约束在三个分页方法里必须一致。
        // 只给 page() 加上限、漏了 hot() 的话，热门页就成了绕过限制的口子
        verify(articleMapper).selectHotPage(0, 50);
    }

    @Test
    @DisplayName("热门：页码小于 1 时按第 1 页算")
    void hot_pageNumberTooSmall() {
        when(articleMapper.countAll()).thenReturn(5L);
        when(articleMapper.selectHotPage(0, 10)).thenReturn(Collections.emptyList());

        PageResult<Article> result = articleService.hot(0, 10);

        assertEquals(1, result.getPage());
        verify(articleMapper).selectHotPage(0, 10);
    }

    @Test
    @DisplayName("热门：偏移量溢出时直接返回空页，不查数据库")
    void hot_offsetOverflow_returnsEmptyPage() {
        when(articleMapper.countAll()).thenReturn(5L);

        PageResult<Article> result = articleService.hot(Integer.MAX_VALUE, 50);

        assertTrue(result.getList().isEmpty());
        verify(articleMapper, never()).selectHotPage(anyInt(), anyInt());
    }

    // ==================== 推荐 ====================

    @Test
    @DisplayName("推荐：按 followerId 查我关注的人写的文章")
    void recommend_success() {
        when(articleMapper.countByFollowerId(1)).thenReturn(2L);
        when(articleMapper.selectRecommendedByFollowerId(1, 0, 10))
                .thenReturn(List.of(buildArticle(11, 2), buildArticle(12, 3)));

        PageResult<Article> result = articleService.recommend(1, 10, 1);

        assertEquals(2, result.getList().size());
        assertEquals(2, result.getTotal());
        verify(articleMapper).selectRecommendedByFollowerId(1, 0, 10);
    }

    @Test
    @DisplayName("推荐：没传关注人ID时报错，不会退化成查全站")
    void recommend_nullFollowerId() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.recommend(1, 10, null));

        assertEquals("缺少关注人ID", ex.getMessage());
        // 关键的一条：不能"算了，就当没传，查全部吧"。
        // 那样游客会看到一屏和"最新"一模一样的内容，标签却写着"推荐"，
        // 看起来像是关注功能坏了
        verify(articleMapper, never()).selectRecommendedByFollowerId(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("推荐：没关注任何人时返回空分页结果，而不是抛异常")
    void recommend_emptyWhenFollowingNobody() {
        when(articleMapper.countByFollowerId(1)).thenReturn(0L);

        PageResult<Article> result = articleService.recommend(1, 10, 1);

        // "还没关注人"是正常状态，不是错误 —— 前端要靠 total = 0
        // 来决定显示那段引导文案，抛异常的话前端只能弹个错误框
        assertTrue(result.getList().isEmpty());
        assertEquals(0, result.getTotal());
        // 总数已经是 0 了，再发一次必然查不到数据的 LIMIT 查询纯属浪费
        verify(articleMapper, never()).selectRecommendedByFollowerId(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("推荐：偏移量按页码算")
    void recommend_offsetCalculatedFromPageNumber() {
        when(articleMapper.countByFollowerId(1)).thenReturn(100L);
        when(articleMapper.selectRecommendedByFollowerId(1, 20, 10))
                .thenReturn(Collections.emptyList());

        articleService.recommend(3, 10, 1);

        // 第 3 页，每页 10 条 → 跳过 20 条。
        // followerId 是第一个参数，别和 offset 传串了 ——
        // 两个都是 Integer，传反了编译器不会报警，只会查到一片空
        verify(articleMapper).selectRecommendedByFollowerId(1, 20, 10);
    }

    @Test
    @DisplayName("推荐：每页条数同样受 50 条上限约束")
    void recommend_sizeClampedToMax() {
        when(articleMapper.countByFollowerId(1)).thenReturn(100L);
        when(articleMapper.selectRecommendedByFollowerId(1, 0, 50))
                .thenReturn(Collections.emptyList());

        articleService.recommend(1, 100000, 1);

        verify(articleMapper).selectRecommendedByFollowerId(1, 0, 50);
    }

    // ==================== 按月归档 ====================

    /**
     * 造一行归档数据。
     * <p>
     * 这是个**投影**对象（{@code ArchiveItem}），不是实体：它只带归档页
     * 用得上的那几个字段，没有正文。所以这里不从 {@code Article} 转，
     * 直接建。
     */
    private ArchiveItem buildArchiveItem(String month, Integer articleId) {
        ArchiveItem item = new ArchiveItem();
        item.setMonth(month);
        item.setArticleId(articleId);
        item.setTitle("归档测试 " + articleId);
        item.setUserId(1);
        item.setViewCount(0);
        return item;
    }

    @Test
    @DisplayName("归档：按月份分成若干组，同一个月的文章归到一起")
    void listArchive_groupsByMonth() {
        when(articleMapper.selectArchive()).thenReturn(Arrays.asList(
                buildArchiveItem("2026-09", 1),
                buildArchiveItem("2026-09", 2),
                buildArchiveItem("2025-12", 3)));

        List<ArchiveMonth> archive = articleService.listArchive();

        assertEquals(2, archive.size());

        assertEquals("2026-09", archive.get(0).getMonth());
        assertEquals(2, archive.get(0).getArticles().size());

        assertEquals("2025-12", archive.get(1).getMonth());
        assertEquals(1, archive.get(1).getArticles().size());
    }

    @Test
    @DisplayName("归档：Service 不重新排序，月份顺序完全照搬 SQL 给的顺序")
    void listArchive_keepsSqlOrder() {
        // 故意给一个"升序"的输入：如果 Service 自己按月份排过序，
        // 输出就会变成 2026-01 在前，这个断言立刻失败
        when(articleMapper.selectArchive()).thenReturn(Arrays.asList(
                buildArchiveItem("2025-12", 1),
                buildArchiveItem("2026-09", 2)));

        List<ArchiveMonth> archive = articleService.listArchive();

        // 排序规则只写在 SQL 一处（ORDER BY create_time DESC），
        // Java 侧只负责"分堆"、不插嘴。两边各排一次的话，
        // 哪天改动了一边，另一边就会在背后把顺序改回去，很难查
        assertEquals("2025-12", archive.get(0).getMonth());
        assertEquals("2026-09", archive.get(1).getMonth());
    }

    @Test
    @DisplayName("归档：同一个月的文章保持 SQL 给的先后顺序")
    void listArchive_keepsOrderInsideMonth() {
        when(articleMapper.selectArchive()).thenReturn(Arrays.asList(
                buildArchiveItem("2026-09", 17),
                buildArchiveItem("2026-09", 6),
                buildArchiveItem("2026-09", 3)));

        List<ArchiveItem> articles = articleService.listArchive().get(0).getArticles();

        assertEquals(Arrays.asList(17, 6, 3),
                articles.stream().map(ArchiveItem::getArticleId).toList());
    }

    @Test
    @DisplayName("归档：一篇文章都没有时返回空集合，不是 null")
    void listArchive_empty() {
        when(articleMapper.selectArchive()).thenReturn(Collections.emptyList());

        List<ArchiveMonth> archive = articleService.listArchive();

        // 返回 null 的话，Controller 包出来就是 "data": null，
        // 前端 months.length 会直接报错。空集合才是能安全遍历的"没有数据"
        assertNotNull(archive);
        assertTrue(archive.isEmpty());
    }

    @Test
    @DisplayName("归档：同一个月分开出现时并回原来那组，不新开一组")
    void listArchive_sameMonthAppearingTwiceMergesIntoOneGroup() {
        // 按时间倒序的正常数据里，同一个月必然是连着的。
        // 这里故意拆开（2026-01, 2025-12, 2026-01），验证第二次遇到
        // 2026-01 时是"并回原来那组"——分组靠的是 Map 的键，
        // 不是"相邻的才算一组"，所以中间隔着别的月份也不影响
        when(articleMapper.selectArchive()).thenReturn(Arrays.asList(
                buildArchiveItem("2026-01", 1),
                buildArchiveItem("2025-12", 2),
                buildArchiveItem("2026-01", 3)));

        List<ArchiveMonth> archive = articleService.listArchive();

        assertEquals(2, archive.size());
        assertEquals("2026-01", archive.get(0).getMonth());
        assertEquals(2, archive.get(0).getArticles().size());   // 1 号和 3 号都在这一组
        assertEquals(3, archive.get(0).getArticles().get(1).getArticleId());
    }

    @Test
    @DisplayName("归档：只查一次数据库，不会为了每个月再补一次查询")
    void listArchive_queriesDatabaseOnce() {
        when(articleMapper.selectArchive()).thenReturn(Arrays.asList(
                buildArchiveItem("2026-09", 1),
                buildArchiveItem("2025-12", 2),
                buildArchiveItem("2025-06", 3)));

        articleService.listArchive();

        // 这是"一条 SQL 拉全量 + 内存分组"和"N 个月查 N 次"的分水岭。
        // 后者在月份多了以后会把数据库打成筛子，且没有任何好处
        verify(articleMapper).selectArchive();
        verifyNoInteractions(userMapper, categoryMapper, commentMapper,
                articleTagMapper, tagMapper);
    }
}
