package cuit.mygit.common;

import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PermissionChecker 的单元测试 —— <b>"谁能改什么"这条规则唯一的归属地</b>。
 * <p>
 * 为什么规则要单独拎出来测，而不是散在各个 Service 的测试里？
 * <ul>
 *   <li><b>以前就是散的</b>：ArticleServiceImpl 和 CommentServiceImpl 里
 *       各有一份逐字相同的私有 checkPermission，只能靠注释互相提醒"改一处要记得改另一处"。
 *       后来 CategoryService / TagService / UserService 也要用同一套，
 *       再复制就是五份了。抽成一个类之后，规则本身只需要在这里被证明一遍。</li>
 *   <li><b>各 Service 的测试从此换个关注点</b>：它们改成 mock 掉本类、只验证
 *       「该拒绝时一个字都没写库」和「传给本类的 ownerId 对不对」。
 *       后者尤其重要 —— 把作者的 ID 传成操作人自己的 ID，权限照样能过，
 *       但结果就是谁都能改别人的文章，那种错只有靠 verify 盯参数才照得出来。</li>
 * </ul>
 * <p>
 * 这里用<b>真的</b> PermissionChecker，只把 UserMapper 换成假的 ——
 * 不连数据库，却能精确摆出"操作人不存在""操作人是管理员"这些边界情况。
 */
@ExtendWith(MockitoExtension.class)
class PermissionCheckerTest {

    @Mock
    private UserMapper userMapper;

    /** 被测对象。它只有一个构造参数，Mockito 会把上面那个假 Mapper 传进去 */
    @InjectMocks
    private PermissionChecker permissionChecker;

    /** 造一个用户；role 传 null 表示"这个字段没填" */
    private User buildUser(Integer userId, Integer role) {
        User user = new User();
        user.setUserId(userId);
        user.setUsername("u" + userId);
        user.setRole(role);
        return user;
    }

    // ==================== checkOwnerOrAdmin：本人或管理员 ====================
    //
    // 用在有主人的数据上：文章、评论、用户资料。

    @Test
    @DisplayName("本人或管理员：自己改自己的东西，直接放行")
    void ownerOrAdmin_bySelf() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));   // 普通用户

        // 普通用户，但改的是自己的 —— 该放行。
        // 这正是"注册之后能改自己的资料和密码"那条需求的落点
        assertDoesNotThrow(() -> permissionChecker.checkOwnerOrAdmin(2, 2, "修改这个用户"));
    }

    @Test
    @DisplayName("本人或管理员：管理员改别人的东西，放行")
    void ownerOrAdmin_byAdmin() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 1));   // 管理员

        assertDoesNotThrow(() -> permissionChecker.checkOwnerOrAdmin(1, 7, "删除这篇文章"));
    }

    @Test
    @DisplayName("本人或管理员：既不是本人也不是管理员，拒绝")
    void ownerOrAdmin_denied() {
        when(userMapper.selectById(3)).thenReturn(buildUser(3, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkOwnerOrAdmin(3, 7, "删除这篇文章"));

        // 提示语要能说清"做的是什么事"和"为什么不行"。
        // 这里把整句话说全，是因为 Service 层那些测试已经改成只断传参了，
        // 万一有人顺手改了这个措辞，得有地方立刻红
        assertEquals("没有权限删除这篇文章（只有作者本人和管理员可以操作）", ex.getMessage());
    }

    @Test
    @DisplayName("本人或管理员：role 为 null 时按普通用户处理，不能当管理员放行")
    void ownerOrAdmin_roleNullIsNotAdmin() {
        when(userMapper.selectById(3)).thenReturn(buildUser(3, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkOwnerOrAdmin(3, 7, "修改这篇文章"));

        assertTrue(ex.getMessage().contains("没有权限"));
    }

    @Test
    @DisplayName("本人或管理员：没传操作人ID时拒绝，而且根本不去查库")
    void ownerOrAdmin_noOperator() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkOwnerOrAdmin(null, 7, "删除这篇文章"));

        assertEquals("缺少操作人ID", ex.getMessage());
        // 连操作人都没有，就没必要查用户表了
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("本人或管理员：操作人ID在库里查不到时拒绝")
    void ownerOrAdmin_operatorNotExist() {
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkOwnerOrAdmin(999, 7, "删除这篇文章"));

        assertEquals("操作人不存在：999", ex.getMessage());
    }

    @Test
    @DisplayName("本人或管理员：哪怕目标是自己，操作人不存在也照样拒绝")
    void ownerOrAdmin_operatorNotExistEvenIfOwner() {
        when(userMapper.selectById(5)).thenReturn(null);

        // 这条盯的是**判断顺序**：先确认"操作人这个人是真的"，
        // 再谈"是不是本人"。反过来写的话，传一个库里没有的 ID
        // 只要恰好等于 ownerId 就能混过去
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkOwnerOrAdmin(5, 5, "删除这个用户"));

        assertEquals("操作人不存在：5", ex.getMessage());
    }

    // ==================== checkAdmin：仅管理员 ====================
    //
    // 用在没有主人的全站共享数据上：分类、标签。

    @Test
    @DisplayName("仅管理员：管理员能改分类和标签")
    void admin_passes() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 1));

        assertDoesNotThrow(() -> permissionChecker.checkAdmin(1, "新增分类"));
    }

    @Test
    @DisplayName("仅管理员：普通用户被拒，而且提示语和'本人或管理员'那条不一样")
    void admin_deniedForNormalUser() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkAdmin(2, "新增分类"));

        // 分类没有主人，所以提示里不能出现"作者本人" —— 那句话在这里没有意义，
        // 还会让用户以为"如果这是我建的分类我就能改"
        assertEquals("没有权限新增分类（只有管理员可以操作）", ex.getMessage());
    }

    @Test
    @DisplayName("仅管理员：role 为 null 时被拒")
    void admin_deniedWhenRoleNull() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, null));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkAdmin(2, "删除标签"));

        assertEquals("没有权限删除标签（只有管理员可以操作）", ex.getMessage());
    }

    @Test
    @DisplayName("仅管理员：没传操作人ID时拒绝，也不查库")
    void admin_noOperator() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkAdmin(null, "新增标签"));

        assertEquals("缺少操作人ID", ex.getMessage());
        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("仅管理员：操作人不存在时拒绝")
    void admin_operatorNotExist() {
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> permissionChecker.checkAdmin(999, "删除分类"));

        assertEquals("操作人不存在：999", ex.getMessage());
    }

    // ==================== isAdmin：查一下身份 ====================
    //
    // 前面两组是"能不能做这件事"的完整答案，这一组回答的是更小的问题：
    // 这个人是不是管理员？只有一处用到 —— 用户资料本人可以改，
    // 但其中的 role 字段只有管理员能动，调用方要先知道身份才能决定怎么处理。

    @Test
    @DisplayName("查身份：管理员返回 true")
    void isAdmin_admin() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 1));

        assertTrue(permissionChecker.isAdmin(1));
    }

    @Test
    @DisplayName("查身份：普通用户返回 false —— 这是正常答案，不是错误")
    void isAdmin_normalUser() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));

        // 和 checkAdmin 的区别就在这儿：不是管理员时它抛异常，这里只是返回 false。
        // 因为调用方问的是"他是管理员吗"，答案是"不是"完全正常
        assertFalse(permissionChecker.isAdmin(2));
    }

    @Test
    @DisplayName("查身份：role 为 null 时返回 false，不会拆箱抛空指针")
    void isAdmin_roleNull() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, null));

        assertFalse(permissionChecker.isAdmin(2));
    }

    @Test
    @DisplayName("查身份：操作人本身不合法时仍然抛异常，不悄悄返回 false")
    void isAdmin_invalidOperatorStillThrows() {
        assertEquals("缺少操作人ID", assertThrows(BusinessException.class,
                () -> permissionChecker.isAdmin(null)).getMessage());

        when(userMapper.selectById(999)).thenReturn(null);
        assertEquals("操作人不存在：999", assertThrows(BusinessException.class,
                () -> permissionChecker.isAdmin(999)).getMessage());
    }
}
