package cuit.mygit.mapper;

import cuit.mygit.entity.Article;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章数据访问接口（DAO 层）。
 * <p>
 * 这里只声明方法，不写实现 —— 具体要执行的 SQL 写在
 * {@code src/main/resources/mapper/ArticleMapper.xml} 里，
 * MyBatis 会在启动时自动生成实现类并注入。
 * <p>
 * {@code @Mapper} 注解是给 Spring 看的：告诉它这个接口需要被扫描到，
 * 并生成代理对象放进容器里供 Service 层注入使用。
 */
@Mapper
public interface ArticleMapper {

    /**
     * 查询所有文章，按发布时间倒序（最新的排最前面）。
     *
     * @return 文章列表，没有数据时返回空集合（不是 null）
     */
    List<Article> selectAll();
}
