package cuit.mygit.service;

import cuit.mygit.common.ArchiveMonth;
import cuit.mygit.common.PageResult;
import cuit.mygit.entity.Article;
import cuit.mygit.entity.Tag;

import java.util.List;

/**
 * 文章业务接口（Service 层）。
 * <p>
 * 为什么要"接口 + 实现类"分开写？
 * 因为 Controller 只依赖这个接口，不关心具体实现。
 * 以后要把 MyBatis 换成别的技术，只要换实现类，Controller 一行都不用改。
 * <p>
 * 文章是系统的核心，这一层的业务规则也最多：
 * 发文章要校验作者和分类真实存在（否则数据库外键会报错）；
 * 看文章要顺手把浏览量加一；删文章要连带清掉评论和标签关联，
 * 而且只有作者本人或管理员才有权删。
 */
public interface ArticleService {

    /**
     * 查询文章列表（不含正文，列表页用）。
     *
     * @return 文章列表
     */
    List<Article> listAll();

    /**
     * 管理页专用：查"这个人有权管理"的文章，草稿也在内。
     * <p>
     * 和 {@link #listAll()} 的差别只有一条 —— 草稿：
     * 管理员能看见所有人的草稿，普通用户只多看见自己的草稿。
     * 首页、分类页、搜索这些公开入口用的是 {@link #listAll()} 那一系，
     * 它们已经<b>在 SQL 层就把草稿滤掉了</b>，草稿漏不出去。
     *
     * @param operatorId 操作人ID（当前登录用户）
     * @return 文章列表（不含正文），按发布时间倒序
     * @throws cuit.mygit.exception.BusinessException 没传操作人ID、或操作人不存在时抛出
     */
    List<Article> listForManage(Integer operatorId);

    /**
     * 按月归档：把全部已发布文章按"年-月"分好组。
     * <p>
     * 返回的数组<b>按月份从新到旧</b>排列，每个月内部的文章也按发布时间从新到旧。
     * 排序由 SQL 一次排好，Service 分组时只用保持顺序、不再重排，
     * 所以前端拿到什么顺序就画什么顺序。
     * <p>
     * 只看已发布的：草稿不进归档页，和首页、分类页、搜索保持一致。
     * 归档页会把所有文章列出来，所以同样<b>不带正文</b>。
     *
     * @return 按月份分组的文章列表；一篇都没有时返回空集合（不是 null）
     */
    List<ArchiveMonth> listArchive();

    /**
     * 查文章详情（含正文）。
     * <p>
     * 查到之后会把浏览量加一，所以每调一次这个方法，浏览量就涨一点。
     * <p>
     * 草稿只有作者本人和管理员打得开。别人访问一篇草稿时，
     * 收到的提示是"文章不存在"而不是"你没权限" —— 见实现类里的说明。
     *
     * @param articleId  文章ID
     * @param operatorId 操作人ID（当前登录用户）；游客传 null 即可，
     *                   传 null 的后果仅仅是"看不到任何草稿"
     * @return 文章详情
     * @throws cuit.mygit.exception.BusinessException 文章不存在（或是一篇你看不到的草稿）时抛出
     */
    Article getById(Integer articleId, Integer operatorId);

    /**
     * 把一段 Markdown 渲染成 HTML —— 编辑器里点"预览"用。
     * <p>
     * 不落库、不校验权限：它处理的是"还没提交的正文"，
     * 连属于哪篇文章都还不知道。
     * <p>
     * 为什么不让 Controller 直接调 {@code MarkdownRenderer}，而要多走一层？
     * 为了让"把 Markdown 变成 HTML"这件事只有一个入口。将来要换渲染器、
     * 加缓存、改安全配置，都只改这一个地方，不会出现
     * "详情页是这么渲染的、预览是那么渲染的"这种不一致。
     *
     * @param markdown Markdown 原文
     * @return 渲染好的 HTML 片段；传空返回空串
     */
    String renderPreview(String markdown);

    /**
     * 发表文章。
     * <p>
     * 不传 {@code status} 就是直接发布（status = 1）。想存成草稿，
     * 把 status 填 0 —— 草稿不会出现在首页、分类页、搜索结果里。
     *
     * @param article 标题、作者ID必填
     * @return 发表后的文章（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 标题为空、作者不存在、分类不存在、
     *                                               status 不是 0 或 1 时抛出
     */
    Article add(Article article);

    /**
     * 修改文章（标题、正文、分类、发布状态）。
     * <p>
     * 和 {@link #delete(Integer, Integer)} 一样要校验权限：**只有作者本人和
     * 管理员可以改**，别人不能动你的文章。
     * <p>
     * 这个 {@code operatorId} 参数是后补的。原先这个方法只收一个 article，
     * 签名里根本没有"是谁在改"这个信息，于是想校验都没得校验 ——
     * 任何人都能改任何人的文章。凡是"改别人东西"的操作，
     * 参数里必须带上操作人，这一条要记住。
     * <p>
     * {@code status} 可以不传，不传表示"不改发布状态"（沿用数据库里的原值）。
     * 这么设计是为了让老调用方和无表单字段的接口不会把已发布的文章
     * 悄悄变回草稿。
     *
     * @param article    必须带 articleId
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 文章不存在、标题为空、分类不存在、
     *                                               status 不是 0 或 1、
     *                                               操作人既不是作者也不是管理员时抛出
     */
    void update(Article article, Integer operatorId);

    /**
     * 删除文章，连同它的评论和标签关联一起删掉。
     *
     * @param articleId  文章ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 文章不存在、操作人既不是作者也不是管理员时抛出
     */
    void delete(Integer articleId, Integer operatorId);

    /**
     * 查某篇文章贴了哪些标签。
     *
     * @param articleId 文章ID
     * @return 标签列表
     */
    List<Tag> listTags(Integer articleId);

    /**
     * 给文章贴一个标签。
     * <p>
     * 标签属于文章内容的一部分，所以改标签的权限和改文章一样：
     * **只有文章作者本人和管理员可以**，别人不能给你的文章乱贴标签。
     *
     * @param articleId  文章ID
     * @param tagId      标签ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 文章或标签不存在、该标签已经贴过了、
     *                                               操作人既不是作者也不是管理员时抛出
     */
    void addTag(Integer articleId, Integer tagId, Integer operatorId);

    /**
     * 取消文章上的某个标签。
     *
     * @param articleId  文章ID
     * @param tagId      标签ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 文章不存在、这篇根本没有贴过这个标签、
     *                                               操作人既不是作者也不是管理员时抛出
     */
    void removeTag(Integer articleId, Integer tagId, Integer operatorId);

    /**
     * 查某个作者发表的全部文章 —— 点作者名字进个人主页时用。
     * <p>
     * 和 {@link #listAll()} 相比只是多了一个筛选条件，返回的文章同样不含正文。
     *
     * @param userId 作者ID
     * @return 该作者的文章列表；他一篇都没写过时返回空集合（不是报错）
     * @throws cuit.mygit.exception.BusinessException 没传作者ID时抛出
     */
    List<Article> listByUserId(Integer userId);

    /**
     * 查某个分类下的全部文章。
     *
     * @param categoryId 分类ID
     * @return 文章列表；该分类下没有文章时返回空集合
     * @throws cuit.mygit.exception.BusinessException 没传分类ID时抛出
     */
    List<Article> listByCategoryId(Integer categoryId);

    /**
     * 查贴了某个标签的全部文章。
     * <p>
     * 这是 {@link #listTags(Integer)} 的反方向：那个是"看这篇文章有哪些标签"，
     * 这个是"看这个标签被哪些文章贴了"。两个方向都通，多对多关系才算完整。
     *
     * @param tagId 标签ID
     * @return 文章列表；这个标签还没被贴过时返回空集合
     * @throws cuit.mygit.exception.BusinessException 没传标签ID时抛出
     */
    List<Article> listByTagId(Integer tagId);

    /**
     * 按标题关键字模糊搜索。
     * <p>
     * 匹配规则是"标题里包含这段文字"，位置不限，大小写也不敏感。
     *
     * @param keyword 关键字，前后空格会被自动去掉
     * @return 匹配到的文章列表；一条都没匹配上时返回空集合
     * @throws cuit.mygit.exception.BusinessException 关键字为空或只有空格时抛出
     */
    List<Article> searchByTitle(String keyword);

    /**
     * 分页查询文章。
     * <p>
     * 页码和每页条数都会做边界处理：页码小于 1 按第 1 页算，
     * 每页条数超过上限按上限算，<b>都不会报错</b> ——
     * 翻页翻过头、或者手动改地址栏上的页码是很常见的事，
     * 弹个错误提示反而让人以为系统坏了。
     *
     * @param page 第几页，从 1 开始
     * @param size 每页多少条
     * @return 分页结果：当前页数据 + 总条数 + 总页数 + 有没有上一页/下一页
     */
    PageResult<Article> page(int page, int size);

    /**
     * 分页查热门文章 —— 按浏览量从高到低。
     * <p>
     * 和 {@link #page} 是并列的两种排序，不是一个方法的两种参数：
     * 首页那三个标签（热门 / 最新 / 推荐）各自对应一个方法。
     * 边界处理和 {@link #page} 完全一致，同样是"纠正"而不是"报错"。
     * <p>
     * 总条数复用的是全站已发布文章数 —— 热门只是换了个顺序，
     * 参与排序的文章一篇不多一篇不少，所以总数和 {@link #page} 一样。
     *
     * @param page 第几页，从 1 开始
     * @param size 每页多少条，最多 50 条
     * @return 分页结果
     */
    PageResult<Article> hot(int page, int size);

    /**
     * 分页查推荐流 —— 只包含"我关注的人"写的已发布文章。
     * <p>
     * 和另外两个分页方法的区别在于多了个前提：得先有"我"。
     * 所以 followerId 是必填的，没传会报错，而不是退化成查全站 ——
     * 那样游客和没关注任何人的用户会看到和"最新"一模一样的内容，
     * 但界面上却标着"推荐"，反而让人以为关注功能坏了。
     * <p>
     * 没关注任何人（或者关注的人一篇文章都没写）时返回的是空列表，
     * <b>不是错误</b>。前端拿到 total = 0 会显示一段引导文案，
     * 告诉用户怎么让这里的内容出现。
     *
     * @param page       第几页，从 1 开始
     * @param size       每页多少条，最多 50 条
     * @param followerId 关注者ID（当前登录用户）
     * @return 分页结果；一条都没有时 list 是空集合
     * @throws cuit.mygit.exception.BusinessException followerId 为 null 时抛出
     */
    PageResult<Article> recommend(int page, int size, Integer followerId);
}
