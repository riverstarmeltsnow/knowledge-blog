package cuit.mygit.mapper;

import cuit.mygit.common.ArchiveItem;
import cuit.mygit.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
     * 查某个作者发表的全部文章（个人主页用）。
     *
     * @param userId 作者ID
     * @return 文章列表，该作者没写过文章时返回空集合
     */
    List<Article> selectByUserId(Integer userId);

    /**
     * 查某个分类下的全部文章。
     *
     * @param categoryId 分类ID
     * @return 文章列表，分类下没有文章时返回空集合
     */
    List<Article> selectByCategoryId(Integer categoryId);

    /**
     * 查贴了某个标签的全部文章。
     * <p>
     * 这是 {@code ArticleTagMapper.selectTagsByArticleId} 的反方向：
     * 那个是"文章 → 标签"，这个是"标签 → 文章"。两者合起来，
     * 多对多关系才算两个方向都能查。
     * <p>
     * SQL 里要 JOIN 中间表 article_tag，所以放在这里而不是 ArticleTagMapper ——
     * 查出来的每一条都是完整的文章，用 ArticleTagMapper 的话还得再查一次文章表。
     *
     * @param tagId 标签ID
     * @return 文章列表，这个标签还没被贴过时返回空集合
     */
    List<Article> selectByTagId(Integer tagId);

    /**
     * 按标题关键字模糊搜索。
     * <p>
     * 匹配规则是"标题里包含这段文字"，不区分位置，也不区分大小写
     * （MySQL 默认的排序规则就是大小写不敏感的）。
     *
     * @param keyword 关键字，已由 Service 层去过首尾空格
     * @return 匹配到的文章列表，一条都没有时返回空集合
     */
    List<Article> selectByTitle(String keyword);

    /**
     * 分页查询文章。
     * <p>
     * offset 是"跳过前面多少条"，不是页码 —— 第 1 页传 0，第 2 页传 size，
     * 第 n 页传 (n-1) × size。这个换算故意放在 Service 层做，
     * DAO 层只认 offset，保持"传什么查什么"的单纯语义。
     *
     * @param offset 跳过多少条
     * @param size   取多少条
     * @return 这一页的文章列表
     */
    List<Article> selectPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 分页查热门文章 —— 按浏览量从高到低。
     * <p>
     * 和 {@link #selectPage} 的区别只有排序：那个按发布时间排（最新），
     * 这个按浏览量排（最热）。参数含义完全一样，offset 仍然是"跳过多少条"。
     * <p>
     * 浏览量一样多的时候按文章ID倒序，保证结果稳定 ——
     * 排序不确定的话，翻页时会出现同一篇文章在前后两页各出现一次，
     * 或者干脆谁都查不到。
     *
     * @param offset 跳过多少条
     * @param size   取多少条
     * @return 这一页的文章列表
     */
    List<Article> selectHotPage(@Param("offset") int offset, @Param("size") int size);

    /**
     * 分页查推荐流 —— 只查"这个人关注的人"写的已发布文章。
     * <p>
     * 一次 JOIN 完成两件事：找出我关注了谁、再取出他们写的文章。
     * 排序按发布时间倒序，所以关注的人一发新文章就会出现在最前面。
     *
     * @param followerId 关注者ID（当前登录用户）
     * @param offset     跳过多少条
     * @param size       取多少条
     * @return 这一页的文章列表；没关注任何人时是空列表
     */
    List<Article> selectRecommendedByFollowerId(@Param("followerId") Integer followerId,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    /**
     * 推荐流的总条数 —— 分页时算总页数要用。
     * <p>
     * 和 {@link #countAll()} 的区别：那个数的是全站已发布文章，
     * 这个只数"我关注的人写的"。它必须和
     * {@link #selectRecommendedByFollowerId} 用同一套 WHERE 条件，
     * 否则算出来的总页数对不上实际能取到的数据。
     *
     * @param followerId 关注者ID（当前登录用户）
     * @return 推荐流里的文章总篇数
     */
    long countByFollowerId(@Param("followerId") Integer followerId);

    /**
     * 管理页专用：查"这个人有权管理"的全部文章，草稿也在内。
     * <p>
     * 和 {@link #selectAll()} 的区别就是草稿：
     * <ul>
     *   <li>管理员（includeAllDrafts = true）：所有人的文章都要，草稿也要 —— 管理员有责任管住全站内容</li>
     *   <li>普通用户：已发布的全都给，另外加上<b>自己的</b>草稿 ——
     *       自己的草稿当然要能在后台看见、能接着改，别人的草稿则一条都不给</li>
     * </ul>
     * 不能简单地"把草稿全查出来在前端过滤"：那样草稿的标题和ID已经发到
     * 别人浏览器里了，随手按 F12 就能看见。过滤必须发生在 SQL 这一层。
     *
     * @param operatorId      操作人ID（当前登录用户）
     * @param includeAllDrafts 是不是能看到所有人的草稿（管理员为 true）
     * @return 文章列表，按发布时间倒序
     */
    List<Article> selectForManage(@Param("operatorId") Integer operatorId,
                                  @Param("includeAllDrafts") boolean includeAllDrafts);

    /**
     * 统计文章总数 —— 分页时算总页数要用。
     *
     * @return 文章总篇数
     */
    long countAll();

    /**
     * 归档页专用：查全部已发布文章的"月份 + 摘要"。
     * <p>
     * 和 {@link #selectAll()} 的关系：过滤条件一样（只要已发布的），
     * 排序也一样（按发布时间倒序），区别只有一个 —— 多返回一列
     * {@code month}。这一列不是表里的字段，是 SQL 用 {@code DATE_FORMAT}
     * 从 {@code create_time} 算出来的 {@code 'YYYY-MM'} 字符串。
     * <p>
     * 同样<b>不查 content</b>：归档页会把所有文章一次性列出来，
     * 正文塞进去就太重了。
     * <p>
     * 另外会排掉 {@code create_time} 为空的记录 —— 没有时间的文章没法往
     * 时间线上放，而 {@code DATE_FORMAT(NULL, ...)} 得到 NULL，会让
     * {@code month} 变成 null 一路传到前端。详见 XML 里的说明。
     * <p>
     * 返回的是平铺的一行行数据，"哪几篇属于同一个月"由 Service 层分组 ——
     * 因为它拿到的已经是有序的，一次遍历就能分完，SQL 里不需要 GROUP BY。
     *
     * @return 已发布文章的月份与摘要，按发布时间倒序；一篇都没有时返回空集合
     */
    List<ArchiveItem> selectArchive();

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
