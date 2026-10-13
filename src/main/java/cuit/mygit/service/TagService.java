package cuit.mygit.service;

import cuit.mygit.entity.Tag;

import java.util.List;

/**
 * 文章标签业务接口（Service 层）。
 * <p>
 * 和分类的区别在于"标签贴在了哪些文章上"这件事记录在中间表
 * article_tag 里，所以删标签时要多一步：先把中间表里的关联清掉。
 * 这两步必须一起成功或一起失败，所以实现类上加了 {@code @Transactional}。
 * <p>
 * 和分类一样，标签也是全站共用的，{@code tag} 表里没有"谁建的"这一列，
 * 所以<b>增删改都只允许管理员</b>。区别只在于"给某篇文章贴标签"——
 * 那属于文章的内容，由文章的作者本人或管理员决定，
 * 相关接口在 {@link cuit.mygit.service.ArticleService} 上。
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
     * 新增标签（仅管理员）。
     *
     * @param tag        标签名必填
     * @param operatorId 操作人ID（当前登录用户）
     * @return 新增后的标签（带自增ID）
     * @throws cuit.mygit.exception.BusinessException 操作人不是管理员、名称为空、名称已存在时抛出
     */
    Tag add(Tag tag, Integer operatorId);

    /**
     * 修改标签名称（仅管理员）。
     *
     * @param tag        必须带 tagId
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 操作人不是管理员、标签不存在、新名称被占用时抛出
     */
    void update(Tag tag, Integer operatorId);

    /**
     * 删除标签（仅管理员），同时清掉它在中间表里的所有关联。
     *
     * @param tagId      标签ID
     * @param operatorId 操作人ID（当前登录用户）
     * @throws cuit.mygit.exception.BusinessException 操作人不是管理员、标签不存在时抛出
     */
    void delete(Integer tagId, Integer operatorId);
}
