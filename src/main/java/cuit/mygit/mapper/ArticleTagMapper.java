package cuit.mygit.mapper;

import cuit.mygit.entity.ArticleTag;
import cuit.mygit.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章-标签关联数据访问接口（DAO 层）—— 对应数据库的 article_tag 表。
 * <p>
 * 这张表是"文章 ↔ 标签"多对多关系拆出来的中间表，只干一件事：
 * 记住"哪篇文章贴了哪个标签"。所以它没有自增主键，也没有 update 方法 ——
 * 要改关联只能先删再插。
 * <p>
 * SQL 写在 {@code src/main/resources/mapper/ArticleTagMapper.xml}。
 */
@Mapper
public interface ArticleTagMapper {

    /**
     * 查某篇文章贴了哪些标签。
     * <p>
     * 注意返回的是 {@link Tag} 而不是 ArticleTag —— 因为页面要显示的是
     * 标签名（"Java"），不是标签ID（3）。SQL 里用 JOIN 把两张表连起来查。
     *
     * @param articleId 文章ID
     * @return 标签列表，一个都没贴时返回空集合
     */
    List<Tag> selectTagsByArticleId(Integer articleId);

    /**
     * 查一条关联记录 —— 贴标签前先查一下，避免重复贴同一个标签。
     *
     * @param articleTag 至少要带上 articleId 和 tagId
     * @return 查到的关联；不存在返回 null
     */
    ArticleTag selectOne(ArticleTag articleTag);

    /**
     * 新增一条关联（给文章贴标签）。
     *
     * @param articleTag 文章ID + 标签ID
     * @return 影响的行数
     */
    int insert(ArticleTag articleTag);

    /**
     * 删除一条指定的关联（给文章取消某个标签）。
     *
     * @param articleTag 要删的文章ID + 标签ID
     * @return 影响的行数
     */
    int delete(ArticleTag articleTag);

    /**
     * 删除某篇文章的全部标签关联 —— 删除文章时配套使用。
     *
     * @param articleId 文章ID
     * @return 影响的行数
     */
    int deleteByArticleId(Integer articleId);

    /**
     * 删除某个标签的全部关联 —— 删除标签时配套使用。
     *
     * @param tagId 标签ID
     * @return 影响的行数
     */
    int deleteByTagId(Integer tagId);

    /**
     * 统计某个标签被贴在了几篇文章上。
     *
     * @param tagId 标签ID
     * @return 关联条数
     */
    int countByTagId(Integer tagId);
}
