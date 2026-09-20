package cuit.mygit.service;

import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserService 的单元测试。
 * <p>
 * <b>什么是"单元测试"？</b>只测这一个类自己的逻辑，不启动 Spring、不连数据库。
 * 那数据库怎么办？用 Mockito 造一个假的 Mapper ——
 * {@code @Mock} 生成的假对象里所有方法都返回默认值（对象返回 null，数字返回 0），
 * 我们用 {@code when(...).thenReturn(...)} 告诉它"这个方法被调用时该返回什么"。
 * <p>
 * <b>为什么要这样做？</b>真实查库的测试跑得慢，还会把测试数据写进你的数据库。
 * 用假 Mapper 之后，这些测试跑一遍只要几百毫秒，而且能精确控制
 * "用户名已存在""这个人还有文章"这类边界情况 —— 真实数据库里造这些数据反而麻烦。
 * <p>
 * {@code @InjectMocks} 会把上面那些假 Mapper 塞进 UserServiceImpl 的对应字段里，
 * 相当于手工 new 完再一个个 set 进去，Mockito 帮我们做了。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    /** 假的用户表访问接口 */
    @Mock
    private UserMapper userMapper;

    /** 假的文章表访问接口（删用户时要数文章数量） */
    @Mock
    private ArticleMapper articleMapper;

    /** 假的评论表访问接口（删用户时要连带删评论） */
    @Mock
    private CommentMapper commentMapper;

    /** 被测对象，Mockito 会把上面三个假对象注入进去 */
    @InjectMocks
    private UserServiceImpl userService;

    // ==================== 工具方法：造测试数据 ====================

    /** 造一个用户对象，密码统一是 123456 */
    private User buildUser(Integer userId, String username, Integer role) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername(username);
        user.setPassword("123456");
        user.setRole(role);
        return user;
    }

    // ==================== 注册 ====================

    @Test
    @DisplayName("注册成功：用户名没被占用时插入，返回带自增ID且不含密码的用户")
    void register_success() {
        User newUser = buildUser(null, "hexin", null);

        // 查重时数据库里没有这个用户名
        when(userMapper.selectByUsername("hexin")).thenReturn(null);

        // 模拟数据库的自增主键回填：插入时把 userId 设成 100
        doAnswer(invocation -> {
            User arg = invocation.getArgument(0);
            arg.setUserId(100);
            return 1;
        }).when(userMapper).insert(any(User.class));

        // 模拟"插完再查回来"这一步
        User saved = buildUser(100, "hexin", 0);
        saved.setCreateTime(LocalDateTime.now());
        when(userMapper.selectById(100)).thenReturn(saved);

        User result = userService.register(newUser);

        assertEquals(100, result.getUserId().intValue());
        assertEquals("hexin", result.getUsername());
        assertNotNull(result.getCreateTime());
        // 最关键的一条：返回给外面的对象里绝不能有密码
        assertNull(result.getPassword());
    }

    @Test
    @DisplayName("注册失败：用户名已存在时抛业务异常")
    void register_duplicateUsername() {
        User newUser = buildUser(null, "hexin", null);
        when(userMapper.selectByUsername("hexin")).thenReturn(buildUser(1, "hexin", 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(newUser));

        assertTrue(ex.getMessage().contains("用户名已存在"));
        // 校验没过，就不该往数据库插数据
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败：用户名为空时抛业务异常")
    void register_blankUsername() {
        User newUser = buildUser(null, "   ", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(newUser));

        assertTrue(ex.getMessage().contains("用户名不能为空"));
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("注册失败：密码为空时抛业务异常")
    void register_blankPassword() {
        User newUser = buildUser(null, "hexin", null);
        newUser.setPassword(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.register(newUser));

        assertTrue(ex.getMessage().contains("密码不能为空"));
    }

    // ==================== 登录 ====================

    @Test
    @DisplayName("登录成功：密码对得上时返回用户，且不含密码")
    void login_success() {
        when(userMapper.selectByUsername("hexin")).thenReturn(buildUser(1, "hexin", 1));

        User result = userService.login("hexin", "123456");

        assertEquals(1, result.getUserId().intValue());
        assertNull(result.getPassword());
    }

    @Test
    @DisplayName("登录失败：密码不对时抛业务异常")
    void login_wrongPassword() {
        when(userMapper.selectByUsername("hexin")).thenReturn(buildUser(1, "hexin", 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login("hexin", "错误的密码"));

        assertTrue(ex.getMessage().contains("用户名或密码错误"));
    }

    @Test
    @DisplayName("登录失败：用户不存在时抛业务异常，且提示和密码错误完全一样")
    void login_userNotExist() {
        when(userMapper.selectByUsername("nobody")).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.login("nobody", "123456"));

        // 故意不提示"用户不存在"，避免别人靠这个接口试探出哪些用户名已被注册
        assertEquals("用户名或密码错误", ex.getMessage());
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除用户：该用户还有文章时拒绝删除，也不删评论")
    void delete_userHasArticles() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, "hexin", 1));
        when(articleMapper.countByUserId(1)).thenReturn(2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(1));

        assertTrue(ex.getMessage().contains("2 篇文章"));
        // 拒绝之后，评论和用户都不能被删掉
        verify(commentMapper, never()).deleteByUserId(anyInt());
        verify(userMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("删除用户：没有文章时连带删掉他的评论，再删用户")
    void delete_success() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, "zhangsan", 0));
        when(articleMapper.countByUserId(2)).thenReturn(0);

        userService.delete(2);

        verify(commentMapper).deleteByUserId(2);
        verify(userMapper).deleteById(2);
    }

    @Test
    @DisplayName("删除用户：用户不存在时抛业务异常")
    void delete_notExist() {
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userService.delete(999));

        assertTrue(ex.getMessage().contains("用户不存在"));
    }
}
