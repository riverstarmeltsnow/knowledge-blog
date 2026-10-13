package cuit.mygit.service.impl;

import cuit.mygit.common.PermissionChecker;
import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.UserFollowMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户业务实现类。
 * <p>
 * {@code @Service} 告诉 Spring：启动时把这个类创建成对象放进容器，
 * 这样 Controller 里才能注入它。
 */
@Service
public class UserServiceImpl implements UserService {

    /** 用户表的数据访问接口 */
    @Autowired
    private UserMapper userMapper;

    /** 删用户前要数一下他写了几篇文章，所以要用到文章表的查询 */
    @Autowired
    private ArticleMapper articleMapper;

    /** 删用户时要连带删掉他的评论 */
    @Autowired
    private CommentMapper commentMapper;

    /** 删用户时要连带清掉他和别人的关注关系 */
    @Autowired
    private UserFollowMapper userFollowMapper;

    /** 改资料、删账号要判断"是不是本人或管理员" */
    @Autowired
    private PermissionChecker permissionChecker;

    @Override
    public List<User> listAll() {
        List<User> users = userMapper.selectAll();
        // 列表里每个用户的密码都要抹掉，一个都不能漏
        users.forEach(this::hidePassword);
        return users;
    }

    @Override
    public User getById(Integer userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在：" + userId);
        }
        hidePassword(user);
        return user;
    }

    @Override
    public User register(User user) {
        // ---------- 1. 校验必填项 ----------
        if (user == null || isBlank(user.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (isBlank(user.getPassword())) {
            throw new BusinessException("密码不能为空");
        }

        // ---------- 2. 用户名不能和别人重复 ----------
        // 数据库上虽然也有 UNIQUE 约束，但等数据库报错时提示是一串英文 SQL 异常，
        // 用户看不懂。提前查一次，就能给出"用户名已存在"这样的中文提示。
        if (userMapper.selectByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在：" + user.getUsername());
        }

        // ---------- 3. 没指定角色就是普通用户 ----------
        if (user.getRole() == null) {
            user.setRole(0);
        }

        // ---------- 4. 存库 ----------
        // insert 之后，数据库生成的自增ID会被 MyBatis 回填到 user 对象里，
        // 所以下面能直接用 user.getUserId() 拿到新ID。
        userMapper.insert(user);

        // ---------- 5. 把完整数据查回来再返回 ----------
        // 注册时间 create_time 是数据库自动填的，手里这个 user 对象上还是 null。
        // 重查一次就能把数据库真正存的值拿回来，前端拿到手就是完整的一条记录。
        User saved = userMapper.selectById(user.getUserId());

        // 返回给外面之前把密码抹掉
        hidePassword(saved);
        return saved;
    }

    @Override
    public User login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new BusinessException("用户名和密码都不能为空");
        }
        User user = userMapper.selectByUsername(username);

        // 这里故意不区分"用户名不存在"和"密码错误"，统一提示"用户名或密码错误"。
        // 如果分开提示，别人就能靠这个接口一个个试出哪些用户名已经被注册了。
        if (user == null || !user.getPassword().equals(password)) {
            throw new BusinessException("用户名或密码错误");
        }

        hidePassword(user);
        return user;
    }

    @Override
    public void update(User user, Integer operatorId) {
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("缺少用户ID，无法修改");
        }

        // 先把库里的老数据查出来，一是确认这个人存在，二是拿来兜底
        User old = userMapper.selectById(user.getUserId());
        if (old == null) {
            throw new BusinessException("用户不存在：" + user.getUserId());
        }

        // 本人能改自己的资料和密码，改别人的要管理员。
        // 校验放在"确认这个人存在"之后，和 ArticleServiceImpl.update 的顺序一致 ——
        // 先分清"这条数据压根没有"和"这条数据你不能动"，
        // 前者是 404 性质的问题，后者才是权限问题
        permissionChecker.checkOwnerOrAdmin(operatorId, user.getUserId(), "修改这个用户");

        // ---------- 用户名：传了才改，改了要重新查重 ----------
        if (!isBlank(user.getUsername()) && !user.getUsername().equals(old.getUsername())) {
            User same = userMapper.selectByUsername(user.getUsername());
            if (same != null) {
                throw new BusinessException("用户名已被占用：" + user.getUsername());
            }
        } else {
            user.setUsername(old.getUsername());
        }

        // ---------- 密码：不传就表示不改，保留原来的 ----------
        // 这一步很重要。SQL 的 update 是把 password 字段整个覆盖掉的，
        // 如果这里不兜底，前端不传密码就会把用户的密码清成空字符串。
        if (isBlank(user.getPassword())) {
            user.setPassword(old.getPassword());
        }

        // ---------- 角色：不是管理员就一律沿用原值 ----------
        // 上面那句"本人也能改"放宽的是**资料和密码**，不包括身份。
        // 不拦这一步的话，普通用户在请求体里塞一个 role:1 就能把自己变成管理员
        // —— 前端确实已经把角色下拉框藏起来了，但藏起来只是少让人白点一次，
        // 直接发请求照样能绕过去。权限的边界必须在服务端。
        // 顺带把"没传角色"也一并兜底了。
        if (!permissionChecker.isAdmin(operatorId) || user.getRole() == null) {
            user.setRole(old.getRole());
        }

        // 邮箱可以本人改，没传就用原来的值
        if (user.getEmail() == null) {
            user.setEmail(old.getEmail());
        }

        userMapper.update(user);
    }

    @Override
    @Transactional
    public void delete(Integer userId, Integer operatorId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在：" + userId);
        }

        // 能删自己的账号，删别人要管理员。
        // 和上面的 update 一样，先确认这个人存在再谈权限
        permissionChecker.checkOwnerOrAdmin(operatorId, userId, "删除这个用户");

        // 用户写的文章是博客的主要内容，不能因为删个用户就一起没了，
        // 所以这里直接拒绝，让用户先把文章处理掉。
        int articleCount = articleMapper.countByUserId(userId);
        if (articleCount > 0) {
            throw new BusinessException(
                    "该用户还有 " + articleCount + " 篇文章，请先删除或转移这些文章，再删除用户");
        }

        // 评论跟着人走：人没了，他发的评论也一起删掉。
        // 注意顺序 —— comment 表有指向 user 的外键，必须先删评论再删用户，
        // 反过来的话数据库会报"外键约束失败"。
        commentMapper.deleteByUserId(userId);

        // 关注关系也一样：人没了，他关注过的人、关注过他的人，
        // 这些关系都失去意义了，一起清掉。
        // user_follow 的两个字段都有指向 user 的外键，所以要清的是
        // 两个方向，只清一边照样会被外键拦住（Mapper 里用 OR 一次清完）。
        userFollowMapper.deleteByUserId(userId);

        userMapper.deleteById(userId);

        // @Transactional 保证这两步要么都成功要么都不做，
        // 不会出现"评论删了但用户还在"的中间状态。
    }

    /**
     * 把密码抹掉（置为 null）。
     * <p>
     * 凡是往 Controller 方向返回的 User 对象，都要先过一遍这个方法。
     * 密码是敏感信息，绝不能出现在返回给浏览器的 JSON 里。
     *
     * @param user 要处理的用户对象，允许为 null（为 null 时啥也不做）
     */
    private void hidePassword(User user) {
        if (user != null) {
            user.setPassword(null);
        }
    }

    /**
     * 判断字符串是不是空的。
     * <p>
     * 比 {@code str == null} 更严格：像 "   " 这种只有空格的，也算空。
     *
     * @param str 待检查的字符串
     * @return 为 null、或去掉空格后长度为 0 时返回 true
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
