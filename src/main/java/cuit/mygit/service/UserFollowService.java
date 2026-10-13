package cuit.mygit.service;

import java.util.List;

/**
 * 用户关注业务接口。
 * <p>
 * 这一层要管的是"规则"，不是"存取"：
 * 不能关注自己、不能重复关注、不能关注不存在的人 ——
 * 这些约束数据库表达不了（或者说表达起来很别扭），所以放在这里。
 */
public interface UserFollowService {

    /**
     * 关注某个人。
     * <p>
     * 会依次检查：操作人ID有没有传、被关注的人存不存在、是不是在关注自己、
     * 是不是已经关注过了。任何一条不满足都会抛 {@code BusinessException}，
     * 而不是默默返个 false —— 前端需要知道"为什么没成功"才能给出有用的提示。
     *
     * @param followerId 关注者ID（当前登录用户）
     * @param targetId   被关注者ID
     */
    void follow(Integer followerId, Integer targetId);

    /**
     * 取消关注。
     * <p>
     * 和关注一样会做前面几项检查，另外多一条：如果本来就没关注，
     * 会报"你还没有关注这个人"而不是当成成功。
     * <p>
     * 这样做而不是"没关注也算成功"（幂等），是为了和已有的
     * {@code ArticleService.removeTag} 保持一致 —— 那里取消一个没贴过的标签
     * 也是报错。两边行为一致，前端才好统一处理。
     *
     * @param followerId 关注者ID（当前登录用户）
     * @param targetId   被关注者ID
     */
    void unfollow(Integer followerId, Integer targetId);

    /**
     * 查"关注了没有"。
     * <p>
     * 和上面两个方法不一样，这个<b>不抛异常</b>，而是老老实实返回 true / false。
     * 因为它是给页面渲染用的：文章详情页要决定那个按钮显示
     * "关注"还是"已关注"，游客（没登录）看到的就是"没关注"，
     * 这不算错误，不需要报错信息。
     * <p>
     * 这和 {@code ArticleService.canSeeDraft} 是同一个思路：
     * 判断类的方法返回布尔值，至于拿这个"否"怎么办，是调用方的事。
     *
     * @param followerId 关注者ID，允许为 null（表示游客）
     * @param targetId   被关注者ID，允许为 null
     * @return 确实关注了返回 true；任一参数为 null 也返回 false
     */
    boolean isFollowing(Integer followerId, Integer targetId);

    /**
     * 查这个人关注了谁，只返回对方的用户ID。
     * <p>
     * 前端在首页一次拿全，用来给"我关注的作者"的文章打标记。
     *
     * @param userId 关注者ID
     * @return 被关注者的用户ID列表；一个都没关注时返回空集合
     */
    List<Integer> listFollowingIds(Integer userId);
}
