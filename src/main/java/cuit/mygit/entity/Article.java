package cuit.mygit.entity;

import cuit.mygit.common.ValidGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 文章实体类 —— 对应数据库的 article 表。
 * 这是整个系统的核心表：外键关联作者（user）和分类（category）。
 * <p>
 * 属性上挂的那些校验注解，会在 Controller 收到请求时<b>自动执行</b>，
 * 不合法就直接返回错误，压根进不到 Service 层。
 * 为什么有的注解只写了一个分组、有的写两个，见 {@link ValidGroup} 的说明。
 */
public class Article {

    /**
     * 文章ID，主键自增。
     * <p>
     * 只在修改时要求必填 —— 新增的时候这个值是数据库生成的，提交时还没有。
     */
    @NotNull(message = "缺少文章ID", groups = ValidGroup.Update.class)
    private Integer articleId;

    /** 文章标题。长度上限跟着数据库走，title 字段是 VARCHAR(200) */
    @NotBlank(message = "文章标题不能为空",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    @Size(max = 200, message = "文章标题不能超过 200 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    private String title;

    /**
     * 文章正文。
     * <p>
     * 这里<b>故意不写</b> {@code @Size} —— 数据库里 content 是 LONGTEXT，
     * 能存 4GB，长度限制没有意义；限制得紧了反而让正经的长文发不出来。
     */
    @NotBlank(message = "文章正文不能为空",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    private String content;

    /**
     * 作者ID，外键关联 user 表。
     * <p>
     * 只在新增时必填。修改接口不接受"换作者"，Service 会把作者
     * 强制改回原来的那个人，所以这里不需要 Update 分组。
     */
    @NotNull(message = "缺少作者ID", groups = ValidGroup.Create.class)
    private Integer userId;

    /** 分类ID，外键关联 category 表。可以不填表示"未分类"，所以不做必填校验 */
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
