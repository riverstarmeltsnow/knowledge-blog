package cuit.mygit.service.impl;

import cuit.mygit.common.PermissionChecker;
import cuit.mygit.entity.Category;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CategoryMapper;
import cuit.mygit.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章分类业务实现类。
 */
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    /** 删分类前要数一下这个分类下有几篇文章 */
    @Autowired
    private ArticleMapper articleMapper;

    /** 分类没有主人，增删改一律只放行管理员 */
    @Autowired
    private PermissionChecker permissionChecker;

    @Override
    public List<Category> listAll() {
        return categoryMapper.selectAll();
    }

    @Override
    public Category getById(Integer categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在：" + categoryId);
        }
        return category;
    }

    @Override
    public Category add(Category category, Integer operatorId) {
        // 权限放在最前面：分类没有主人，只能由管理员维护。
        // 放在参数校验之前还有个好处 —— 不是管理员的人连"这个分类名有没有被占用"
        // 都问不出来，省得有人拿这个接口当探针去猜库里的数据
        permissionChecker.checkAdmin(operatorId, "新增分类");

        if (category == null || isBlank(category.getName())) {
            throw new BusinessException("分类名称不能为空");
        }
        if (categoryMapper.selectByName(category.getName()) != null) {
            throw new BusinessException("分类名称已存在：" + category.getName());
        }
        categoryMapper.insert(category);
        return category;
    }

    @Override
    public void update(Category category, Integer operatorId) {
        permissionChecker.checkAdmin(operatorId, "修改分类");

        if (category == null || category.getCategoryId() == null) {
            throw new BusinessException("缺少分类ID，无法修改");
        }
        Category old = categoryMapper.selectById(category.getCategoryId());
        if (old == null) {
            throw new BusinessException("分类不存在：" + category.getCategoryId());
        }

        // 改了名字才需要查重；没改就跳过，否则会把自己判成"重名"
        if (!isBlank(category.getName()) && !category.getName().equals(old.getName())) {
            if (categoryMapper.selectByName(category.getName()) != null) {
                throw new BusinessException("分类名称已存在：" + category.getName());
            }
        } else {
            category.setName(old.getName());
        }

        // 描述不传就保留原来的，避免被更新成空
        if (category.getDescription() == null) {
            category.setDescription(old.getDescription());
        }

        categoryMapper.update(category);
    }

    @Override
    public void delete(Integer categoryId, Integer operatorId) {
        permissionChecker.checkAdmin(operatorId, "删除分类");

        Category category = categoryMapper.selectById(categoryId);
        if (category == null) {
            throw new BusinessException("分类不存在：" + categoryId);
        }

        // article 表有指向 category 的外键，分类下还有文章时数据库会拒绝删除。
        // 与其让用户看到一串 SQL 报错，不如在这里提前拦住并说清楚原因。
        int articleCount = articleMapper.countByCategoryId(categoryId);
        if (articleCount > 0) {
            throw new BusinessException(
                    "分类【" + category.getName() + "】下还有 " + articleCount
                            + " 篇文章，请先把这些文章移到别的分类，再删除该分类");
        }

        categoryMapper.deleteById(categoryId);
    }

    /** 判断字符串是不是空的（null 或只有空格） */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
