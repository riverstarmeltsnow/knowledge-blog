package cuit.mygit.entity;

import cuit.mygit.common.ValidGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 标签实体类 —— 对应数据库的 tag 表。
 * 标签和文章是 M:N 关系：一篇文章可有多个标签，一个标签可贴在多篇文章上。
 * 这个多对多关系由中间表 article_tag 承载，所以本类里看不到 List&lt;Article&gt; 字段。
 * <p>
 * 属性上的校验注解在 Controller 收到请求时自动执行，
 * 分组规则见 {@link ValidGroup}。
 */
public class Tag {

    /** 标签ID，主键自增。只在修改时必填 */
    @NotNull(message = "缺少标签ID", groups = ValidGroup.Update.class)
    private Integer tagId;

    /** 标签名称，唯一。长度上限跟着数据库的 VARCHAR(50) 走 */
    @NotBlank(message = "标签名称不能为空",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    @Size(max = 50, message = "标签名称不能超过 50 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
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
