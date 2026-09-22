package cuit.mygit.entity;

import cuit.mygit.common.ValidGroup;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 用户实体类 —— 对应数据库的 user 表。
 * <p>
 * 一个 Java 对象 = 表里的一行数据，一个属性 = 表里的一个字段。
 * 属性名用驼峰（userId），数据库字段名用下划线（user_id），
 * 靠 application.properties 里的 map-underscore-to-camel-case 自动转换。
 * <p>
 * 属性上的校验注解会在 Controller 收到请求时自动执行，
 * 分组规则见 {@link ValidGroup}。
 */
public class User {

    /** 用户ID，主键自增。只在修改时必填 —— 新增时它是数据库生成的 */
    @NotNull(message = "缺少用户ID", groups = ValidGroup.Update.class)
    private Integer userId;

    /**
     * 用户名，唯一。长度上限跟着数据库的 VARCHAR(50) 走。
     * <p>
     * {@code @NotBlank} 只挂在新增分组上：修改资料时用户名可以不传，
     * 表示"不改用户名"，这时候强制必填的话，用户改个邮箱
     * 还得把用户名重新打一遍。但 {@code @Size} 两种场景都要管 ——
     * 一旦传了，就得检查长度。
     */
    @NotBlank(message = "用户名不能为空", groups = ValidGroup.Create.class)
    @Size(max = 50, message = "用户名不能超过 50 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    private String username;

    /**
     * 密码（实际项目里应该存加密后的值，本实验为简化直接存明文）。
     * <p>
     * 和用户名同理：注册时必填，改资料时不传表示"不改密码"。
     */
    @NotBlank(message = "密码不能为空", groups = ValidGroup.Create.class)
    @Size(max = 100, message = "密码不能超过 100 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    private String password;

    /**
     * 邮箱，选填。
     * <p>
     * {@code @Email} 管的是"填了的话，格式得像邮箱"，值为 null 时直接放行，
     * 所以不填邮箱不会报错。
     */
    @Email(message = "邮箱格式不正确",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    @Size(max = 100, message = "邮箱不能超过 100 个字符",
            groups = {ValidGroup.Create.class, ValidGroup.Update.class})
    private String email;

    /** 角色：0-普通用户，1-管理员。由后端决定，不接受前端指定，所以不做校验 */
    private Integer role;

    /** 注册时间 */
    private LocalDateTime createTime;

    // ==================== getter / setter ====================

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getRole() {
        return role;
    }

    public void setRole(Integer role) {
        this.role = role;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
