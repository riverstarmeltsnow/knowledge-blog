package cuit.mygit.mapper;

import cuit.mygit.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户关注关系数据访问接口（DAO 层）—— 对应数据库的 user_follow 表。
 * <p>
 * 和 {@code ArticleTagMapper} 是同一类东西：M:N 中间表的 Mapper。
 * 所以它也没有 update 方法 —— 关注关系只有"建立"和"解除"两种变化，
 * 不存在"把 A 关注的人改成 B"这种操作，要改只能先删再插。
 * <p>
 * SQL 写在 {@code src/main/resources/mapper/UserFollowMapper.xml}。
 */
@Mapper
public interface UserFollowMapper {

    /**
     * 查一条关注关系 —— 关注前先查一下，避免重复关注；取关前也查一下，确认真的关注过。
     *
     * @param userFollow 至少要带上 followerId 和 followeeId
     * @return 查到的那条关系；没关注过则返回 null
     */
    UserFollow selectOne(UserFollow userFollow);

    /**
     * 新增一条关注关系（关注某人）。
     *
     * @param userFollow 关注者ID + 被关注者ID
     * @return 影响的行数
     */
    int insert(UserFollow userFollow);

    /**
     * 删除一条指定的关注关系（取消关注）。
     *
     * @param userFollow 要删的关注者ID + 被关注者ID
     * @return 影响的行数
     */
    int delete(UserFollow userFollow);

    /**
     * 删除和某个用户有关的全部关注关系 —— 删除用户时配套使用。
     * <p>
     * 注意是"全部"，不分方向：这个人关注别人的记录，
     * 和别人关注这个人的记录，都要清掉。
     * 因为 user_follow 的两个字段都有指向 user 的外键，
     * 只清一个方向的话，另一个方向会把删除用户的操作拦下来。
     *
     * @param userId 用户ID
     * @return 影响的行数
     */
    int deleteByUserId(Integer userId);

    /**
     * 查这个人关注了哪些人，只返回对方的用户ID。
     * <p>
     * 前端拿它来判断"这篇文章的作者我关注了没有"，
     * 从而决定要不要在文章卡片上打「已关注」标记 ——
     * 一次把整个关注列表拿回来，就不用每张卡片问一次接口了。
     *
     * @param userId 关注者ID
     * @return 被关注者的用户ID列表；一个都没关注时返回空集合
     */
    List<Integer> selectFollowingIds(Integer userId);
}
