package cuit.mygit.mapper;

import cuit.mygit.entity.Article;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章数据访问接口（DAO 层）。
 * <p>
 * 这里只声明方法，不写实现 —— 具体要执行的 SQL 写在
 * {@code src/main/resources/mapper/ArticleMapper.xml} 里，
 * MyBatis 会在启动时自动生成实现类并注入。
 * <p>
 * {@code @Mapper} 注解是给 Spring 看的：告诉它这个接口需要被扫描到，
 * 并生成代理对象放进容器里供 Service 层注入使用。
 */
@Mapper
public interface ArticleMapper {

    /**
     * 查询所有文章，按发布时间倒序（最新的排最前面）。
     * <p>
     * 列表页不需要正文，所以这条 SQL **故意不查 content 字段** ——
     * 文章正文可能有几万字，列表页几十篇一起查出来太浪费。
     * 要正文请用 {@link #selectById(Integer)}。
     *
     * @return 文章列表，没有数据时返回空集合（不是 null）
     */
    List<Article> selectAll();

    /**
     * 按主键查文章详情（含正文）。
     *
     * @param articleId 文章ID
     * @return 查到的文章；查不到返回 null
     */
    Article selectById(Integer articleId);

    /**
     * 新增文章。
     * <p>
     * 发布时间、修改时间都不传，交给数据库的默认值填。
     *
     * @param article 要新增的文章对象
     * @return 影响的行数
     */
    int insert(Article article);

    /**
     * 按主键修改文章（标题、正文、分类）。
     * <p>
     * 不会改 view_count —— 浏览量只能由 {@link #increaseViewCount(Integer)} 增加，
     * 否则改一次文章就把浏览量覆盖成前端传来的值了。
     * 也不改 create_time 和 update_time，后者由数据库的
     * {@code ON UPDATE CURRENT_TIMESTAMP} 自动更新。
     *
     * @param article 带 articleId 的文章对象
     * @return 影响的行数
     */
    int update(Article article);

    /**
     * 按主键删除文章。
     * <p>
     * article 表被 comment 和 article_tag 用外键指着，所以删之前要先清掉
     * 评论和标签关联，这个顺序由 Service 层保证。
     *
     * @param articleId 文章ID
     * @return 影响的行数
     */
    int deleteById(Integer articleId);

    /**
     * 浏览量加一。
     * <p>
     * 用 {@code view_count = view_count + 1} 而不是"先查出来再写回去"，
     * 是为了让数据库自己完成加法 —— 两个人同时看同一篇文章时不会互相覆盖。
     *
     * @param articleId 文章ID
     * @return 影响的行数
     */
    int increaseViewCount(Integer articleId);

    /**
     * 统计某个分类下有几篇文章 —— 删分类前用它判断能不能删。
     *
     * @param categoryId 分类ID
     * @return 文章篇数
     */
    int countByCategoryId(Integer categoryId);

    /**
     * 统计某个用户写了几篇文章 —— 删用户前用它判断能不能删。
     *
     * @param userId 用户ID
     * @return 文章篇数
     */
    int countByUserId(Integer userId);
}
