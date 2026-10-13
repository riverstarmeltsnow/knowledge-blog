package cuit.mygit.service;

import cuit.mygit.entity.User;

import java.util.List;

/**
 * 用户业务接口（Service 层）。
 * <p>
 * 用户这一块最核心的规则是两条：
 * 一是**用户名不能重复**，二是**密码不能泄露给外面**。
 * 这两条都在这层的实现类里保证，Controller 和 Mapper 都不管。
 */
public interface UserService {

    /**
     * 查询所有用户（结果里不含密码）。
     *
     * @return 用户列表
     */
    List<User> listAll();

    /**
     * 按ID查用户（结果里不含密码）。
     *
     * @param userId 用户ID
     * @return 用户对象
     * @throws cuit.mygit.exception.BusinessException 用户不存在时抛出
     */
    User getById(Integer userId);

    /**
     * 注册新用户。
     *
     * @param user 用户名和密码必填，其余可选
     * @return 注册成功后的用户（带上了数据库生成的自增ID，不含密码）
     * @throws cuit.mygit.exception.BusinessException 用户名或密码为空、用户名已被占用时抛出
     */
    User register(User user);

    /**
     * 登录。
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录成功的用户（不含密码）
     * @throws cuit.mygit.exception.BusinessException 用户名不存在或密码错误时抛出
     */
    User login(String username, String password);

    /**
     * 修改用户资料（本人或管理员）。
     * <p>
     * 有两个细节：改用户名时要重新检查有没有和别人撞名；
     * 不传密码就表示"不改密码"，原来的密码会保留。
     * <p>
     * 权限上放宽到"本人也可以"：改自己的资料、改自己的密码是注册之后最基本的需要，
     * 要是只允许管理员，普通用户想换个邮箱都得去找管理员。
     * 但改<b>别人</b>的资料必须管理员 —— 那已经不是"维护自己的账号"了。
     *
     * @param user       要改的用户，必须带 userId
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 操作人既不是本人也不是管理员、用户不存在、新用户名被占用时抛出
     */
    void update(User user, Integer operatorId);

    /**
     * 删除用户（本人或管理员）。
     * <p>
     * 用户写的文章会被保留（文章是博客的主体内容），所以
     * **如果该用户还写过文章，就不允许删除**，必须先处理掉那些文章。
     * 该用户发过的评论会一并删掉（评论跟着人走）。
     *
     * @param userId     用户ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 操作人既不是本人也不是管理员、用户不存在、或该用户还有文章时抛出
     */
    void delete(Integer userId, Integer operatorId);
}
