package cuit.mygit.service;

import cuit.mygit.entity.Comment;

import java.util.List;

/**
 * 评论业务接口（Service 层）。
 * <p>
 * 评论这块有"所有权"的概念：谁写的评论谁能删，管理员能删任何人的。
 * 这个判断放在 Service 层做，而是不放在 Controller —— 因为
 * 将来不管是从网页、手机 App 还是别的入口调进来，规则都得一样。
 */
public interface CommentService {

    /**
     * 查某篇文章下的所有评论。
     *
     * @param articleId 文章ID
     * @return 评论列表
     * @throws cuit.mygit.exception.BusinessException 文章不存在时抛出
     */
    List<Comment> listByArticle(Integer articleId);

    /**
     * 查某个用户发过的所有评论。
     *
     * @param userId 用户ID
     * @return 评论列表
     */
    List<Comment> listByUser(Integer userId);

    /**
     * 发表评论。
     *
     * @param comment 内容、用户ID、文章ID都必填
     * @return 新增后的评论（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 内容为空、用户不存在、文章不存在时抛出
     */
    Comment add(Comment comment);

    /**
     * 删除评论。
     *
     * @param commentId  评论ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 评论不存在、操作人既不是作者也不是管理员时抛出
     */
    void delete(Integer commentId, Integer operatorId);
}
