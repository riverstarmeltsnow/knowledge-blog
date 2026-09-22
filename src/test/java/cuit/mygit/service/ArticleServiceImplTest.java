package cuit.mygit.service;

import cuit.mygit.common.PageResult;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @InjectMocks
    private ArticleServiceImpl articleService;

    // ==================== 工具方法：造测试数据 ====================

    /** 造一篇文章；articleId 传 null 表示"还没入库的新文章" */
    private Article buildArticle(Integer articleId, Integer authorId) {
        Article article = new Article();
        article.setArticleId(articleId);
        article.setTitle("测试标题");
        article.setUserId(authorId);
        article.setViewCount(0);
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

        Article result = articleService.getById(1);

        // 数据库那边加了一次
        verify(articleMapper).increaseViewCount(1);
        // 返回给浏览器的值也要跟着加，否则会比数据库少 1，看着对不上
        assertEquals(6, result.getViewCount().intValue());
    }

    @Test
    @DisplayName("查详情：文章不存在时抛业务异常，不去加浏览量")
    void getById_notFound() {
        when(articleMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.getById(999));

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

    // ==================== 删除文章（权限 + 级联） ====================

    @Test
    @DisplayName("删除文章：既不是作者也不是管理员时拒绝，且一条数据都不删")
    void delete_notOwnerAndNotAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));       // 操作人 2 号是普通用户

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
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));

        articleService.delete(1, 1);

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
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 1));       // 操作人 2 号是管理员

        articleService.delete(1, 2);

        verify(articleMapper).deleteById(1);
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
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
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
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        when(tagMapper.selectById(99)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> articleService.addTag(1, 99, 1));

        assertTrue(ex.getMessage().contains("标签不存在"));
    }

    @Test
    @DisplayName("贴标签：一切正常时插入关联记录")
    void addTag_success() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        when(tagMapper.selectById(3)).thenReturn(new Tag());
        when(articleTagMapper.selectOne(any(ArticleTag.class))).thenReturn(null);

        articleService.addTag(1, 3, 1);

        verify(articleTagMapper).insert(any(ArticleTag.class));
    }

    @Test
    @DisplayName("贴标签：不是作者也不是管理员时拒绝（不能给别人文章乱贴标签）")
    void addTag_notOwnerAndNotAdmin() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));  // 作者是 1 号
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));       // 操作人 2 号是普通用户

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
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 1));       // 管理员
        when(tagMapper.selectById(3)).thenReturn(new Tag());
        when(articleTagMapper.selectOne(any(ArticleTag.class))).thenReturn(null);

        articleService.addTag(1, 3, 2);

        verify(articleTagMapper).insert(any(ArticleTag.class));
    }

    @Test
    @DisplayName("取消标签：这篇没贴过该标签时拒绝")
    void removeTag_notExist() {
        when(articleMapper.selectById(1)).thenReturn(buildArticle(1, 1));
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
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
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));

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
}
