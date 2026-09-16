package cuit.mygit.entity;

import java.time.LocalDateTime;

/**
 * 评论实体类 —— 对应数据库的 comment 表。
 * 同时关联两个外键：谁评论的（user_id）和评论了哪篇（article_id）。
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
