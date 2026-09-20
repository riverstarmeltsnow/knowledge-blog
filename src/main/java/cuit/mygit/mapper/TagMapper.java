package cuit.mygit.mapper;

import cuit.mygit.entity.Tag;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章标签数据访问接口（DAO 层）—— 对应数据库的 tag 表。
 * <p>
 * SQL 写在 {@code src/main/resources/mapper/TagMapper.xml}。
 * 标签和文章的多对多关系放在 {@link ArticleTagMapper} 里处理。
 */
@Mapper
public interface TagMapper {

    /**
     * 查询所有标签。
     *
     * @return 标签列表，没有数据时返回空集合
     */
    List<Tag> selectAll();

    /**
     * 按主键查标签。
     *
     * @param tagId 标签ID
     * @return 查到的标签；查不到返回 null
     */
    Tag selectById(Integer tagId);

    /**
     * 按名称查标签 —— 新增标签时判断名字有没有被占用。
     *
     * @param name 标签名称
     * @return 查到的标签；名称不存在返回 null
     */
    Tag selectByName(String name);

    /**
     * 新增标签。
     *
     * @param tag 要新增的标签对象
     * @return 影响的行数
     */
    int insert(Tag tag);

    /**
     * 按主键修改标签名称。
     *
     * @param tag 带 tagId 的标签对象
     * @return 影响的行数
     */
    int update(Tag tag);

    /**
     * 按主键删除标签。
     * <p>
     * 删之前必须先清掉 article_tag 里指向它的关联记录，否则外键约束会拦住，
     * 这个顺序由 Service 层保证。
     *
     * @param tagId 标签ID
     * @return 影响的行数
     */
    int deleteById(Integer tagId);
}
