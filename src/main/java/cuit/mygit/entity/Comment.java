package cuit.mygit.entity;

import java.time.LocalDateTime;

/**
 * 评论实体类 —— 对应数据库的 comment 表。
 * <p>
 * 关联的外键：
 * <ul>
 *   <li>谁评论的（user_id）</li>
 *   <li>评论了哪篇（article_id）</li>
 *   <li>挂在哪条顶层评论底下（parent_id，自关联到本表）</li>
 *   <li>在回复谁（reply_to_user_id）</li>
 * </ul>
 * <p>
 * 评论只有两层：顶层评论 + 它下面的回复。
 * 回复的回复也会被归到同一个顶层评论下（Service 层负责"提级"），
 * 所以 parent_id 永远指向顶层评论，不会指到另一条回复上。
 */
public class Comment {

    /** 评论ID，主键自增 */
    private Integer commentId;

    /** 评论内容 */
    private String content;

    /** 评论人ID，外键关联 user 表 */
    private Integer userId;

    /** 所属文章ID，外键关联 article 表 */
    private Integer articleId;

    /**
     * 所属顶层评论ID，外键关联本表的 comment_id。
     * <p>
     * 为 null 表示这条本身就是顶层评论（直接评论文章）；
     * 有值表示这是某条顶层评论下面的回复。
     */
    private Integer parentId;

    /**
     * 被回复人的用户ID，外键关联 user 表。
     * <p>
     * 为 null 表示直接评论文章，没有针对某个具体的人；
     * 有值表示这条是回复，页面可以显示成"回复 @某某"。
     * <p>
     * 这个值由 Service 层从被回复的评论里推出来，不接受前端传，
     * 否则谁都能伪造成"某某回复了你"。
     */
    private Integer replyToUserId;

    /** 评论时间 */
    private LocalDateTime createTime;

    // ==================== getter / setter ====================

    public Integer getCommentId() {
        return commentId;
    }

    public void setCommentId(Integer commentId) {
        this.commentId = commentId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getArticleId() {
        return articleId;
    }

    public void setArticleId(Integer articleId) {
        this.articleId = articleId;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }

    public Integer getReplyToUserId() {
        return replyToUserId;
    }

    public void setReplyToUserId(Integer replyToUserId) {
        this.replyToUserId = replyToUserId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
