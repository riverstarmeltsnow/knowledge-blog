package cuit.mygit.entity;

/**
 * 标签实体类 —— 对应数据库的 tag 表。
 * 标签和文章是 M:N 关系：一篇文章可有多个标签，一个标签可贴在多篇文章上。
 * 这个多对多关系由中间表 article_tag 承载，所以本类里看不到 List&lt;Article&gt; 字段。
 */
public class Tag {

    /** 标签ID，主键自增 */
    private Integer tagId;

    /** 标签名称，唯一 */
    private String name;

    // ==================== getter / setter ====================

    public Integer getTagId() {
        return tagId;
    }

    public void setTagId(Integer tagId) {
        this.tagId = tagId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
