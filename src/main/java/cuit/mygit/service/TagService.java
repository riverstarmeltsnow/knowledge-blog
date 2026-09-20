package cuit.mygit.service;

import cuit.mygit.entity.Tag;

import java.util.List;

/**
 * 文章标签业务接口（Service 层）。
 * <p>
 * 和分类的区别在于"标签贴在了哪些文章上"这件事记录在中间表
 * article_tag 里，所以删标签时要多一步：先把中间表里的关联清掉。
 * 这两步必须一起成功或一起失败，所以实现类上加了 {@code @Transactional}。
 */
public interface TagService {

    /**
     * 查询所有标签。
     *
     * @return 标签列表
     */
    List<Tag> listAll();

    /**
     * 按ID查标签。
     *
     * @param tagId 标签ID
     * @return 标签对象
     * @throws cuit.mygit.exception.BusinessException 标签不存在时抛出
     */
    Tag getById(Integer tagId);

    /**
     * 新增标签。
     *
     * @param tag 标签名必填
     * @return 新增后的标签（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 名称为空、名称已存在时抛出
     */
    Tag add(Tag tag);

    /**
     * 修改标签名称。
     *
     * @param tag 必须带 tagId
     * @throws cuit.mygit.exception.BusinessException 标签不存在、新名称被占用时抛出
     */
    void update(Tag tag);

    /**
     * 删除标签，同时清掉它在中间表里的所有关联。
     *
     * @param tagId 标签ID
     * @throws cuit.mygit.exception.BusinessException 标签不存在时抛出
     */
    void delete(Integer tagId);
}
