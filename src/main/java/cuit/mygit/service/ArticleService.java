package cuit.mygit.service;

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
     *
     * @param articleId 文章ID
     * @param tagId     标签ID
     * @throws cuit.mygit.exception.BusinessException 文章或标签不存在、该标签已经贴过了时抛出
     */
    void addTag(Integer articleId, Integer tagId);

    /**
     * 取消文章上的某个标签。
     *
     * @param articleId 文章ID
     * @param tagId     标签ID
     * @throws cuit.mygit.exception.BusinessException 这篇根本没有贴过这个标签时抛出
     */
    void removeTag(Integer articleId, Integer tagId);
}
