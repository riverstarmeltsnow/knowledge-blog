package cuit.mygit.entity;

import cuit.mygit.common.ValidGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 文章分类实体类 —— 对应数据库的 category 表。
 * 分类和文章是 1:N 关系：一个分类下有多篇文章。
 * <p>
 * 属性上的校验注解在 Controller 收到请求时自动执行，
 * 分组规则见 {@link ValidGroup}。
 */
public class Category {

    /** 分类ID，主键自增。只在修改时必填 */
    @NotNull(message = "缺少分类ID", groups = ValidGroup.Update.class)
    private Integer categoryId;

    /** 分类名称，唯一。长度上限跟着数据库的 VARCHAR(50) 走 */
    @NotBlank(message = "分类名称不能为空",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    @Size(max = 50, message = "分类名称不能超过 50 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    private String name;

    /** 分类描述，选填。数据库里是 VARCHAR(255) */
    @Size(max = 255, message = "分类描述不能超过 255 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
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
