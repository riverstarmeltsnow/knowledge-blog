package cuit.mygit.common;

import java.time.LocalDateTime;

/**
 * 归档查询的一行结果：<b>一篇文章的摘要</b>，外加它属于哪个月。
 * <p>
 * 为什么不直接用 {@code Article}：归档接口返回的每一行都要带上"这是哪个月的文章"，
 * 而 {@code month} 在数据库里没有这一列 —— 它是 SQL 拿 {@code DATE_FORMAT}
 * 现算出来的。硬塞进 Article 会让实体类多出一个纯粹为某一条查询服务的字段，
 * 时间一长就说不清这个字段到底代表什么了。单独开一个类，语义更干净。
 * <p>
 * 它和 {@code Article} 的关系是"投影"：只取了列表页要用的几个字段，
 * <b>不含正文</b>。归档页会把全部文章都列出来，带上正文太浪费。
 */
public class ArchiveItem {

    /** 所属月份，格式 {@code 2026-03}。由 SQL 的 DATE_FORMAT 算出，不是数据库里的列 */
    private String month;

    /** 文章ID */
    private Integer articleId;

    /** 文章标题 */
    private String title;

    /** 作者ID */
    private Integer userId;

    /** 分类ID，可能是 null（没归类的文章） */
    private Integer categoryId;

    /** 浏览量 */
    private Integer viewCount;

    /** 发布时间 */
    private LocalDateTime createTime;

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

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
}
