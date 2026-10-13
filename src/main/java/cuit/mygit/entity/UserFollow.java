package cuit.mygit.entity;

import java.time.LocalDateTime;

/**
 * 用户关注关系实体类 —— 对应数据库的 user_follow 表。
 * <p>
 * 和 {@link ArticleTag} 一样，这是为了表达多对多关系而存在的中间表：
 * 关注关系没法硬塞进 user 表（一个人关注的人数量不固定），
 * 所以拆成"一行代表一次关注"。
 * <p>
 * 但比 article_tag 多两个要注意的地方：
 * <ol>
 *   <li><b>两个字段都指向 user 表，方向相反。</b>
 *       article_tag 的两个字段一个指文章一个指标签，看名字就知道谁是谁；
 *       这里两边都是用户，所以名字必须把方向写清楚 ——
 *       follower 是发起关注的人，followee 是被关注的人。</li>
 *   <li><b>多了 create_time。</b>
 *       article_tag 记的是"这篇贴了这个标签"，是一种状态，什么时候贴的无所谓；
 *       关注是发生在某个时刻的动作，时间有意义。</li>
 * </ol>
 * <p>
 * 联合主键是 (followerId, followeeId)，从数据库层面挡住"重复关注同一个人"。
 * 但"不能关注自己"挡不住（那是"两个值相等"，行级约束，MySQL 表达不了），
 * 这条规则放在 Service 层拦，见 {@code UserFollowServiceImpl}。
 */
public class UserFollow {

    /** 关注者ID（我），外键关联 user 表，联合主键的一部分 */
    private Integer followerId;

    /** 被关注者ID（我关注的人），外键关联 user 表，联合主键的一部分 */
    private Integer followeeId;

    /**
     * 关注时间。
     * <p>
     * 插入时不用管它 —— 数据库的 DEFAULT CURRENT_TIMESTAMP 会自动填。
     * 查询时才会被读出来。
     */
    private LocalDateTime createTime;

    /**
     * 无参构造方法。
     * MyBatis 把查询结果映射成对象时需要先创建空对象再逐个塞值，
     * 所以这个方法是必须的。
     */
    public UserFollow() {
    }

    /**
     * 带参构造方法，写起来方便：
     * {@code new UserFollow(1, 2)} 就表示"1 号用户关注了 2 号用户"。
     *
     * @param followerId 关注者ID
     * @param followeeId 被关注者ID
     */
    public UserFollow(Integer followerId, Integer followeeId) {
        this.followerId = followerId;
        this.followeeId = followeeId;
    }

    // ==================== getter / setter ====================

    public Integer getFollowerId() {
        return followerId;
    }

    public void setFollowerId(Integer followerId) {
        this.followerId = followerId;
    }

    public Integer getFolloweeId() {
        return followeeId;
    }

    public void setFolloweeId(Integer followeeId) {
        this.followeeId = followeeId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
