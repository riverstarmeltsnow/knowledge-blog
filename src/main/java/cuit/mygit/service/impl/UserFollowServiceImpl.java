package cuit.mygit.service.impl;

import cuit.mygit.entity.UserFollow;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.UserFollowMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户关注业务实现类。
 * <p>
 * 这里<b>没有</b> {@code ArticleServiceImpl} 和 {@code CommentServiceImpl} 里
 * 那个私有的 {@code checkPermission} 方法，这是有意的，不是漏了。
 * <p>
 * 那边需要它，是因为"改一篇文章"这件事有明确的归属：
 * 文章属于某个人，非作者来改就是越权。而"关注"没有这回事 ——
 * 关注这个动作天生只能由关注者本人发起，能被篡改的只有
 * followerId（前端传什么就是什么），而这个项目里所有接口都是这个样子，
 * 不是关注功能独有的问题。所以这里只需要校验参数本身合不合理。
 */
@Service
public class UserFollowServiceImpl implements UserFollowService {

    /** 关注关系表的数据访问接口 */
    @Autowired
    private UserFollowMapper userFollowMapper;

    /** 要确认"人真的存在"，所以要用到用户表的查询 */
    @Autowired
    private UserMapper userMapper;

    @Override
    public void follow(Integer followerId, Integer targetId) {
        // 关注前要做的检查，两边共用
        checkBothUsersExist(followerId, targetId);

        // 第 3 条：不能关注自己。
        // 这条数据库拦不住 —— 联合主键只保证"同一条关系不重复"，
        // follower_id = followee_id 这个组合本身是合法的。
        if (followerId.equals(targetId)) {
            throw new BusinessException("不能关注自己");
        }

        // 第 4 条：不能重复关注。
        // 数据库的联合主键其实也能挡住（会报主键冲突），但报出来是一串
        // 英文 SQL 异常，前端只能显示一堆看不懂的东西。提前查一次，
        // 就能给出"已经关注过这个人了"这样能直接展示给用户的提示。
        if (userFollowMapper.selectOne(new UserFollow(followerId, targetId)) != null) {
            throw new BusinessException("已经关注过这个人了");
        }

        userFollowMapper.insert(new UserFollow(followerId, targetId));
    }

    @Override
    public void unfollow(Integer followerId, Integer targetId) {
        checkBothUsersExist(followerId, targetId);

        // 本来就没关注，不能当成"取消成功"。
        // 如果默默返回成功，用户点了取关、界面变成"未关注"，
        // 但其实什么都没发生 —— 下次刷新又变回"已关注"，很困惑。
        if (userFollowMapper.selectOne(new UserFollow(followerId, targetId)) == null) {
            throw new BusinessException("你还没有关注这个人");
        }

        userFollowMapper.delete(new UserFollow(followerId, targetId));
    }

    @Override
    public boolean isFollowing(Integer followerId, Integer targetId) {
        // 游客（没登录）问"我关注他了吗"，答案就是"没有"，不是错误。
        // 这里直接短路返回，连数据库都不用查。
        if (followerId == null || targetId == null) {
            return false;
        }
        return userFollowMapper.selectOne(new UserFollow(followerId, targetId)) != null;
    }

    @Override
    public List<Integer> listFollowingIds(Integer userId) {
        if (userId == null) {
            throw new BusinessException("缺少用户ID");
        }
        return userFollowMapper.selectFollowingIds(userId);
    }

    /**
     * 关注和取关共用的前置检查：两个人是不是都真的存在。
     * <p>
     * 抽出来的原因不是为了少写几行，而是这两处的规则必须完全一样。
     * 如果哪天要加一条新规则（比如"被封禁的账号不能被关注"），
     * 只改一处、漏了另一处，就会出现"能关注但不能取关"这种很别扭的 bug。
     *
     * @param followerId 关注者ID
     * @param targetId   被关注者ID
     */
    private void checkBothUsersExist(Integer followerId, Integer targetId) {
        // 注意这里判的是 null 而不是"等于某人"。
        // 关注接口的 followerId 是从查询串里取的，前端没登录时不会带上它，
        // 于是就是 null —— 必须先拦住，否则下面 selectById(null) 的行为
        // 是"查不到"还是"查全部"，取决于 SQL 怎么写，很容易出意外。
        if (followerId == null) {
            throw new BusinessException("缺少操作人ID");
        }
        if (targetId == null) {
            throw new BusinessException("缺少要关注的用户ID");
        }

        if (userMapper.selectById(followerId) == null) {
            throw new BusinessException("操作人不存在：" + followerId);
        }
        if (userMapper.selectById(targetId) == null) {
            throw new BusinessException("要关注的用户不存在：" + targetId);
        }
    }
}
