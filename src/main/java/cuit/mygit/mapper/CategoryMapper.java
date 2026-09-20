package cuit.mygit.mapper;

import cuit.mygit.entity.Category;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章分类数据访问接口（DAO 层）—— 对应数据库的 category 表。
 * <p>
 * SQL 写在 {@code src/main/resources/mapper/CategoryMapper.xml}。
 */
@Mapper
public interface CategoryMapper {

    /**
     * 查询所有分类，按ID从小到大排。
     *
     * @return 分类列表，没有数据时返回空集合
     */
    List<Category> selectAll();

    /**
     * 按主键查分类。
     *
     * @param categoryId 分类ID
     * @return 查到的分类；查不到返回 null
     */
    Category selectById(Integer categoryId);

    /**
     * 按名称查分类 —— 新增分类时判断名字有没有被占用。
     *
     * @param name 分类名称
     * @return 查到的分类；名称不存在返回 null
     */
    Category selectByName(String name);

    /**
     * 新增分类。
     *
     * @param category 要新增的分类对象
     * @return 影响的行数
     */
    int insert(Category category);

    /**
     * 按主键修改分类（名称、描述）。
     *
     * @param category 带 categoryId 的分类对象
     * @return 影响的行数
     */
    int update(Category category);

    /**
     * 按主键删除分类。
     * <p>
     * 注意：article 表有指向本表的外键，所以只有"该分类下没有文章"时才能删，
     * 这个判断写在 Service 层。
     *
     * @param categoryId 分类ID
     * @return 影响的行数
     */
    int deleteById(Integer categoryId);
}
