package cuit.mygit.service;

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
     * 查文章详情（含正文）。
     * <p>
     * 查到之后会把浏览量加一，所以每调一次这个方法，浏览量就涨一点。
     *
     * @param articleId 文章ID
     * @return 文章详情
     * @throws cuit.mygit.exception.BusinessException 文章不存在时抛出
     */
    Article getById(Integer articleId);

    /**
     * 发表文章。
     *
     * @param article 标题、作者ID必填
     * @return 发表后的文章（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 标题为空、作者不存在、分类不存在时抛出
     */
    Article add(Article article);

    /**
     * 修改文章（标题、正文、分类）。
     *
     * @param article 必须带 articleId
     * @throws cuit.mygit.exception.BusinessException 文章不存在、标题为空、分类不存在时抛出
     */
    void update(Article article);

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
}
