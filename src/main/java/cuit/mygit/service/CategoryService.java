package cuit.mygit.service;

import cuit.mygit.entity.Category;

import java.util.List;

/**
 * 文章分类业务接口（Service 层）。
 * <p>
 * 核心规则：分类名不能重复；分类底下还有文章时不能删。
 * 第二条尤其重要 —— 数据库里 article 表有指向 category 的外键，
 * 硬删会直接被数据库拦住报错，与其抛一句看不懂的 SQL 异常给用户，
 * 不如在 Service 层提前拦住，给一句人话。
 */
public interface CategoryService {

    /**
     * 查询所有分类。
     *
     * @return 分类列表
     */
    List<Category> listAll();

    /**
     * 按ID查分类。
     *
     * @param categoryId 分类ID
     * @return 分类对象
     * @throws cuit.mygit.exception.BusinessException 分类不存在时抛出
     */
    Category getById(Integer categoryId);

    /**
     * 新增分类。
     *
     * @param category 分类名必填
     * @return 新增后的分类（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 名称为空、名称已存在时抛出
     */
    Category add(Category category);

    /**
     * 修改分类。
     *
     * @param category 必须带 categoryId；改名字时会重新查重
     * @throws cuit.mygit.exception.BusinessException 分类不存在、新名称被占用时抛出
     */
    void update(Category category);

    /**
     * 删除分类。
     *
     * @param categoryId 分类ID
     * @throws cuit.mygit.exception.BusinessException 分类不存在、分类下还有文章时抛出
     */
    void delete(Integer categoryId);
}
