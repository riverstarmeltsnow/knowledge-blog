package cuit.mygit.entity;

/**
 * 文章分类实体类 —— 对应数据库的 category 表。
 * 分类和文章是 1:N 关系：一个分类下有多篇文章。
 */
public class Category {

    /** 分类ID，主键自增 */
    private Integer categoryId;

    /** 分类名称，唯一 */
    private String name;

    /** 分类描述 */
    private String description;

    // ==================== getter / setter ====================

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
