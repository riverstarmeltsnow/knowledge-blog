package cuit.mygit.mapper;

import cuit.mygit.entity.User;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户数据访问接口（DAO 层）—— 对应数据库的 user 表。
 * <p>
 * 和 {@link ArticleMapper} 一样：接口里只声明方法，不写实现，
 * 真正的 SQL 在同名 XML（{@code src/main/resources/mapper/UserMapper.xml}）里，
 * MyBatis 启动时自动生成实现类。
 */
@Mapper
public interface UserMapper {

    /**
     * 查询所有用户，按注册顺序排。
     *
     * @return 用户列表，没有数据时返回空集合
     */
    List<User> selectAll();

    /**
     * 按主键查用户。
     *
     * @param userId 用户ID
     * @return 查到的用户；查不到返回 null
     */
    User selectById(Integer userId);

    /**
     * 按用户名查用户 —— 注册时判断用户名是否重复、登录时按用户名找人都用它。
     * <p>
     * 为什么不用 selectById？因为登录时用户只知道自己的用户名，
     * 不知道数据库里的自增ID是多少。
     *
     * @param username 用户名
     * @return 查到的用户；用户名不存在返回 null
     */
    User selectByUsername(String username);

    /**
     * 新增用户。
     * <p>
     * 注册时间不用传，数据库的默认值会自动填当前时间。
     *
     * @param user 要新增的用户对象
     * @return 影响的行数，插入成功是 1
     */
    int insert(User user);

    /**
     * 按主键修改用户信息（用户名、密码、邮箱、角色）。
     * <p>
     * 不会改 create_time —— 注册时间是一次性的，不该被后续修改覆盖。
     *
     * @param user 带 userId 的用户对象
     * @return 影响的行数，改到东西是 1，ID 不存在是 0
     */
    int update(User user);

    /**
     * 按主键删除用户。
     *
     * @param userId 用户ID
     * @return 影响的行数
     */
    int deleteById(Integer userId);
}
