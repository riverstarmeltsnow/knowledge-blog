package cuit.mygit.service.impl;

import cuit.mygit.common.PermissionChecker;
import cuit.mygit.entity.Tag;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleTagMapper;
import cuit.mygit.mapper.TagMapper;
import cuit.mygit.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章标签业务实现类。
 */
@Service
public class TagServiceImpl implements TagService {

    @Autowired
    private TagMapper tagMapper;

    /** 删标签时要先清掉它在中间表 article_tag 里的关联 */
    @Autowired
    private ArticleTagMapper articleTagMapper;

    /** 标签和分类一样没有主人，增删改一律只放行管理员 */
    @Autowired
    private PermissionChecker permissionChecker;

    @Override
    public List<Tag> listAll() {
        return tagMapper.selectAll();
    }

    @Override
    public Tag getById(Integer tagId) {
        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("标签不存在：" + tagId);
        }
        return tag;
    }

    @Override
    public Tag add(Tag tag, Integer operatorId) {
        // 和分类同理：标签是全站共用的，只有管理员能新增
        permissionChecker.checkAdmin(operatorId, "新增标签");

        if (tag == null || isBlank(tag.getName())) {
            throw new BusinessException("标签名称不能为空");
        }
        if (tagMapper.selectByName(tag.getName()) != null) {
            throw new BusinessException("标签名称已存在：" + tag.getName());
        }
        tagMapper.insert(tag);
        return tag;
    }

    @Override
    public void update(Tag tag, Integer operatorId) {
        permissionChecker.checkAdmin(operatorId, "修改标签");

        if (tag == null || tag.getTagId() == null) {
            throw new BusinessException("缺少标签ID，无法修改");
        }
        Tag old = tagMapper.selectById(tag.getTagId());
        if (old == null) {
            throw new BusinessException("标签不存在：" + tag.getTagId());
        }

        if (!isBlank(tag.getName()) && !tag.getName().equals(old.getName())) {
            if (tagMapper.selectByName(tag.getName()) != null) {
                throw new BusinessException("标签名称已存在：" + tag.getName());
            }
        } else {
            tag.setName(old.getName());
        }

        tagMapper.update(tag);
    }

    @Override
    @Transactional
    public void delete(Integer tagId, Integer operatorId) {
        permissionChecker.checkAdmin(operatorId, "删除标签");

        Tag tag = tagMapper.selectById(tagId);
        if (tag == null) {
            throw new BusinessException("标签不存在：" + tagId);
        }

        // 和分类不一样，标签这里不是"拒绝删除"，而是"连带清理"。
        // 因为标签只是给文章做标记，删掉标签不影响文章本身，
        // 顶多是那些文章少了一个标记而已。
        //
        // 顺序不能反：article_tag 表有指向 tag 的外键，
        // 先把关联删干净，再删标签，否则数据库会报"外键约束失败"。
        articleTagMapper.deleteByTagId(tagId);
        tagMapper.deleteById(tagId);

        // @Transactional 保证两步同生共死：要么关联和标签都被删掉，
        // 要么都没删。中途出错不会留下"标签还在、关联没了"的烂摊子。
    }

    /** 判断字符串是不是空的（null 或只有空格） */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
