package cuit.mygit.mapper;

import cuit.mygit.entity.Comment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 评论数据访问接口（DAO 层）—— 对应数据库的 comment 表。
 * <p>
 * SQL 写在 {@code src/main/resources/mapper/CommentMapper.xml}。
 */
@Mapper
public interface CommentMapper {

    /**
     * 按主键查评论 —— 删除评论前要先查出来，看这条评论是不是本人写的。
     *
     * @param commentId 评论ID
     * @return 查到的评论；查不到返回 null
     */
    Comment selectById(Integer commentId);

    /**
     * 查某篇文章下的所有评论，最新的排前面。
     *
     * @param articleId 文章ID
     * @return 评论列表
     */
    List<Comment> selectByArticleId(Integer articleId);

    /**
     * 查某个用户发过的所有评论。
     *
     * @param userId 用户ID
     * @return 评论列表
     */
    List<Comment> selectByUserId(Integer userId);

    /**
     * 新增评论。
     *
     * @param comment 要新增的评论对象
     * @return 影响的行数
     */
    int insert(Comment comment);

    /**
     * 按主键删除评论。
     *
     * @param commentId 评论ID
     * @return 影响的行数
     */
    int deleteById(Integer commentId);

    /**
     * 删除某篇文章下的全部评论 —— 删除文章时配套使用，否则外键约束会拦住。
     *
     * @param articleId 文章ID
     * @return 影响的行数（删掉几条就是几）
     */
    int deleteByArticleId(Integer articleId);

    /**
     * 删除某个用户的全部评论 —— 删除用户时配套使用。
     *
     * @param userId 用户ID
     * @return 影响的行数
     */
    int deleteByUserId(Integer userId);

    /**
     * 统计某篇文章的评论条数。
     *
     * @param articleId 文章ID
     * @return 评论条数
     */
    int countByArticleId(Integer articleId);
}
