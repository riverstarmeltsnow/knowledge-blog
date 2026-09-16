package cuit.mygit.entity;

import java.time.LocalDateTime;

/**
 * 用户实体类 —— 对应数据库的 user 表。
 * <p>
 * 一个 Java 对象 = 表里的一行数据，一个属性 = 表里的一个字段。
 * 属性名用驼峰（userId），数据库字段名用下划线（user_id），
 * 靠 application.properties 里的 map-underscore-to-camel-case 自动转换。
 */
public class User {

    /** 用户ID，主键自增 */
    private Integer userId;

    /** 用户名，唯一 */
    private String username;

    /** 密码（实际项目里应该存加密后的值，本实验为简化直接存明文） */
    private String password;

    /** 邮箱 */
    private String email;

    /** 角色：0-普通用户，1-管理员 */
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
