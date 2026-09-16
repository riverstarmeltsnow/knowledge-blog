package cuit.mygit.entity;

import java.time.LocalDateTime;

/**
 * 文章实体类 —— 对应数据库的 article 表。
 * 这是整个系统的核心表：外键关联作者（user）和分类（category）。
 */
public class Article {

    /** 文章ID，主键自增 */
    private Integer articleId;

    /** 文章标题 */
    private String title;

    /** 文章正文 */
    private String content;

    /** 作者ID，外键关联 user 表 */
    private Integer userId;

    /** 分类ID，外键关联 category 表 */
    private Integer categoryId;

    /** 浏览量 */
    private Integer viewCount;

    /** 发布时间 */
    private LocalDateTime createTime;

    /** 最后修改时间 */
    private LocalDateTime updateTime;

    // ==================== getter / setter ====================

    public Integer getArticleId() {
        return articleId;
    }

    public void setArticleId(Integer articleId) {
        this.articleId = articleId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
