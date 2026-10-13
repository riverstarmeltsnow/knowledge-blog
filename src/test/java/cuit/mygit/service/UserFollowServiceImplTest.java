package cuit.mygit.service;

import cuit.mygit.entity.User;
import cuit.mygit.entity.UserFollow;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.UserFollowMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.impl.UserFollowServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UserFollowService 的单元测试。
 * <p>
 * 这个类要盯住的东西和别的 Service 不太一样：它几乎全是"拦住不该发生的操作"。
 * 关注这个动作本身很简单（往中间表插一行），麻烦全在插入之前 ——
 * 不能关注自己、不能重复关注、不能关注不存在的人、游客不能关注。
 * 所以下面一大半的测试都在验证"抛了异常，并且真的没往库里写"。
 * <p>
 * "没往库里写"这一半很重要：只断言抛异常是不够的，
 * 万一代码是"先插入再检查"，异常照样抛，脏数据却已经进去了。
 */
@ExtendWith(MockitoExtension.class)
class UserFollowServiceImplTest {

    /** 假的关注关系表访问接口 */
    @Mock
    private UserFollowMapper userFollowMapper;

    /** 假的用户表访问接口（关注前要确认两个人都存在） */
    @Mock
    private UserMapper userMapper;

    /** 被测对象，Mockito 会把上面两个假对象注入进去 */
    @InjectMocks
    private UserFollowServiceImpl userFollowService;

    // ==================== 关注 ====================

    @Test
    @DisplayName("关注：正常情况往中间表插一行")
    void follow_success() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1));
        when(userMapper.selectById(2)).thenReturn(buildUser(2));
        when(userFollowMapper.selectOne(any(UserFollow.class))).thenReturn(null);

        userFollowService.follow(1, 2);

        // 光验证"调了 insert"还不够，还要确认插进去的是 (1, 2) 而不是 (2, 1)。
        // 这两个字段顺序写反的话，功能看起来能用，实际方向完全颠倒 ——
        // 我关注了谁 变成 谁关注了我，而且测试全绿
        ArgumentCaptor<UserFollow> captor = ArgumentCaptor.forClass(UserFollow.class);
        verify(userFollowMapper).insert(captor.capture());
        assertEquals(1, captor.getValue().getFollowerId());
        assertEquals(2, captor.getValue().getFolloweeId());
    }

    @Test
    @DisplayName("关注：不能关注自己，也不会往库里插")
    void follow_self() {
        when(userMapper.selectById(3)).thenReturn(buildUser(3));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.follow(3, 3));

        assertEquals("不能关注自己", ex.getMessage());
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("关注：已经关注过的人不能再关注一次")
    void follow_alreadyFollowing() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1));
        when(userMapper.selectById(2)).thenReturn(buildUser(2));
        // 让 selectOne 返回一条记录，表示"库里已经有这条关系了"
        when(userFollowMapper.selectOne(any(UserFollow.class)))
                .thenReturn(new UserFollow(1, 2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.follow(1, 2));

        assertEquals("已经关注过这个人了", ex.getMessage());
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("关注：要关注的人不存在时拒绝")
    void follow_targetNotExist() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1));
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.follow(1, 999));

        assertTrue(ex.getMessage().contains("要关注的用户不存在"));
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("关注：操作人自己不存在时拒绝")
    void follow_followerNotExist() {
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.follow(999, 1));

        assertTrue(ex.getMessage().contains("操作人不存在"));
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("关注：游客（没传操作人ID）直接被挡住")
    void follow_guest() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.follow(null, 1));

        assertEquals("缺少操作人ID", ex.getMessage());
        // 连用户表都不该查 —— 参数都没齐，查什么都是白费
        verify(userMapper, never()).selectById(any());
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    @Test
    @DisplayName("关注：被关注者ID为 null 时拒绝")
    void follow_targetIdNull() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.follow(1, null));

        assertEquals("缺少要关注的用户ID", ex.getMessage());
        verify(userFollowMapper, never()).insert(any(UserFollow.class));
    }

    // ==================== 取消关注 ====================

    @Test
    @DisplayName("取消关注：关注过的人才能取消")
    void unfollow_success() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1));
        when(userMapper.selectById(2)).thenReturn(buildUser(2));
        when(userFollowMapper.selectOne(any(UserFollow.class)))
                .thenReturn(new UserFollow(1, 2));

        userFollowService.unfollow(1, 2);

        // 同样要确认删的是 (1, 2) 这一条，不是 (2, 1)
        ArgumentCaptor<UserFollow> captor = ArgumentCaptor.forClass(UserFollow.class);
        verify(userFollowMapper).delete(captor.capture());
        assertEquals(1, captor.getValue().getFollowerId());
        assertEquals(2, captor.getValue().getFolloweeId());
    }

    @Test
    @DisplayName("取消关注：本来就没关注时报错，而不是当成成功")
    void unfollow_notFollowing() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1));
        when(userMapper.selectById(2)).thenReturn(buildUser(2));
        when(userFollowMapper.selectOne(any(UserFollow.class))).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.unfollow(1, 2));

        assertEquals("你还没有关注这个人", ex.getMessage());
        verify(userFollowMapper, never()).delete(any(UserFollow.class));
    }

    // ==================== 查关注状态 ====================

    @Test
    @DisplayName("查关注状态：关注了返回 true")
    void isFollowing_true() {
        when(userFollowMapper.selectOne(any(UserFollow.class)))
                .thenReturn(new UserFollow(1, 2));

        assertTrue(userFollowService.isFollowing(1, 2));
    }

    @Test
    @DisplayName("查关注状态：没关注返回 false")
    void isFollowing_false() {
        when(userFollowMapper.selectOne(any(UserFollow.class))).thenReturn(null);

        assertFalse(userFollowService.isFollowing(1, 2));
    }

    @Test
    @DisplayName("查关注状态：游客一律返回 false，而且不查库")
    void isFollowing_guest() {
        // 和上面两个方法不一样，这里**不抛异常** ——
        // 游客问"我关注他了吗"，答案是"没有"，这不是错误。
        // 文章详情页要靠这个返回值决定按钮显示"关注"还是"已关注"，
        // 游客打开页面时整页不能因为这个报错
        assertFalse(userFollowService.isFollowing(null, 2));
        assertFalse(userFollowService.isFollowing(1, null));
        assertFalse(userFollowService.isFollowing(null, null));

        // 参数不全时连库都不用查，直接短路
        verify(userFollowMapper, never()).selectOne(any(UserFollow.class));
    }

    // ==================== 查关注列表 ====================

    @Test
    @DisplayName("查关注列表：原样透传 Mapper 的结果")
    void listFollowingIds_success() {
        when(userFollowMapper.selectFollowingIds(1)).thenReturn(List.of(2, 4, 5));

        List<Integer> ids = userFollowService.listFollowingIds(1);

        assertEquals(List.of(2, 4, 5), ids);
    }

    @Test
    @DisplayName("查关注列表：用户ID为 null 时抛业务异常")
    void listFollowingIds_null() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> userFollowService.listFollowingIds(null));

        assertEquals("缺少用户ID", ex.getMessage());
    }

    // ==================== 测试数据 ====================

    /**
     * 造一个用户对象。
     * <p>
     * 只填 Service 校验时真正会读的字段（user_id），其余留空 ——
     * 这个测试关心的是"人存不存在"，不是"这个人叫什么"。
     *
     * @param userId 用户ID
     * @return 用户对象
     */
    private User buildUser(int userId) {
        User user = new User();
        user.setUserId(userId);
        return user;
    }
}
