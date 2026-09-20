package cuit.mygit.entity;

/**
 * 文章-标签关联实体类 —— 对应数据库的 article_tag 表。
 * <p>
 * 这张表是为了表达"文章和标签的多对多关系"而存在的中间表，
 * 里面只有两个外键字段，没有自己的自增主键 ——
 * 主键是 (articleId, tagId) 这两个字段一起组成的**联合主键**，
 * 从数据库层面保证同一篇文章不会重复贴同一个标签。
 */
public class ArticleTag {

    /** 文章ID，外键关联 article 表，联合主键的一部分 */
    private Integer articleId;

    /** 标签ID，外键关联 tag 表，联合主键的一部分 */
    private Integer tagId;

    /**
     * 无参构造方法。
     * MyBatis 把查询结果映射成对象时需要先创建空对象再逐个塞值，
     * 所以这个方法是必须的。
     */
    public ArticleTag() {
    }

    /**
     * 带参构造方法，写起来方便：
     * {@code new ArticleTag(1, 2)} 就表示"给 1 号文章贴 2 号标签"。
     *
     * @param articleId 文章ID
     * @param tagId     标签ID
     */
    public ArticleTag(Integer articleId, Integer tagId) {
        this.articleId = articleId;
        this.tagId = tagId;
    }

    // ==================== getter / setter ====================

    public Integer getArticleId() {
        return articleId;
    }

    public void setArticleId(Integer articleId) {
        this.articleId = articleId;
    }

    public Integer getTagId() {
        return tagId;
    }

    public void setTagId(Integer tagId) {
        this.tagId = tagId;
    }
}
