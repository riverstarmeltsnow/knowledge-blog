package cuit.mygit.service;

import cuit.mygit.entity.Comment;

import java.util.List;

/**
 * 评论业务接口（Service 层）。
 * <p>
 * 评论这块有"所有权"的概念：谁写的评论谁能删，管理员能删任何人的。
 * 这个判断放在 Service 层做，而是不放在 Controller —— 因为
 * 将来不管是从网页、手机 App 还是别的入口调进来，规则都得一样。
 * <p>
 * 评论分两层：顶层评论（{@code parentId} 为 null）和它下面的回复
 * （{@code parentId} 指向顶层评论）。层级最多两层，不会无限套下去。
 */
public interface CommentService {

    /**
     * 查某篇文章下的所有评论。
     * <p>
     * 返回的是一张平铺的列表，顶层评论和回复混在一起，
     * 靠每条评论自己的 {@code parentId} 区分：为 null 的是顶层，有值的是回复。
     * 页面拿到后自己按 parentId 分组就能拼成两层。
     *
     * @param articleId 文章ID
     * @return 评论列表（最新的排前面）
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
     * 发表评论，或者回复某条已有评论。
     * <p>
     * 是不是"回复"由 {@code parentId} 决定：
     * <ul>
     *   <li>{@code parentId} 为 null —— 顶层评论，直接评论文章</li>
     *   <li>{@code parentId} 有值 —— 回复这条评论，值就是被回复的评论ID</li>
     * </ul>
     * 归一化由实现类负责：回复"一条回复"时会被提到同一个顶层评论下，
     * 保证层级不超过两层；{@code replyToUserId} 也由实现类从被回复的评论
     * 推导出来，前端传了也会被忽略（防止伪造成"某某回复了你"）。
     *
     * @param comment 内容、用户ID、文章ID都必填；parentId 选填，填了就是回复
     * @return 新增后的评论（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 内容为空、用户或文章不存在、
     *                                               被回复的评论不存在、跨文章回复时抛出
     */
    Comment add(Comment comment);

    /**
     * 删除评论。
     * <p>
     * 删的如果是一条顶层评论，它下面的所有回复会一起删掉。
     *
     * @param commentId  评论ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 评论不存在、操作人既不是作者也不是管理员时抛出
     */
    void delete(Integer commentId, Integer operatorId);
}
