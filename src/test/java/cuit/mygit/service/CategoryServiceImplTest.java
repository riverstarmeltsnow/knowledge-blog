package cuit.mygit.service;

import cuit.mygit.entity.Category;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CategoryMapper;
import cuit.mygit.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CategoryService 的单元测试。
 * <p>
 * 分类这块两条核心规则：名字不能重复；分类底下还有文章时不能删。
 * 第二条尤其值得测 —— 它是替数据库外键"提前把关"，
 * 没有它的话用户看到的会是一串看不懂的 SQL 报错。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ArticleMapper articleMapper;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    /** 造一个分类对象 */
    private Category buildCategory(Integer categoryId, String name) {
        Category category = new Category();
        category.setCategoryId(categoryId);
        category.setName(name);
        return category;
    }

    @Test
    @DisplayName("查列表：直接返回 Mapper 查到的所有分类")
    void listAll_success() {
        when(categoryMapper.selectAll())
                .thenReturn(Arrays.asList(buildCategory(1, "学习笔记"), buildCategory(2, "踩坑记录")));

        List<Category> result = categoryService.listAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("新增分类：名字没被占用时插入成功，并带回自增ID")
    void add_success() {
        Category category = buildCategory(null, "读书笔记");
        when(categoryMapper.selectByName("读书笔记")).thenReturn(null);
        // 模拟数据库回填自增ID
        when(categoryMapper.insert(any(Category.class))).thenAnswer(invocation -> {
            invocation.<Category>getArgument(0).setCategoryId(3);
            return 1;
        });

        Category result = categoryService.add(category);

        assertEquals(3, result.getCategoryId().intValue());
    }

    @Test
    @DisplayName("新增分类：名称为空时拒绝")
    void add_blankName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.add(buildCategory(null, "  ")));

        assertTrue(ex.getMessage().contains("名称不能为空"));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    @DisplayName("新增分类：名称已存在时拒绝")
    void add_duplicateName() {
        when(categoryMapper.selectByName("学习笔记")).thenReturn(buildCategory(1, "学习笔记"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.add(buildCategory(null, "学习笔记")));

        assertTrue(ex.getMessage().contains("已存在"));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    @DisplayName("删除分类：分类下还有文章时拒绝删除（替数据库外键提前把关）")
    void delete_hasArticles() {
        when(categoryMapper.selectById(1)).thenReturn(buildCategory(1, "学习笔记"));
        when(articleMapper.countByCategoryId(1)).thenReturn(2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.delete(1));

        // 提示里要说清楚有几篇，用户才知道该处理多少
        assertTrue(ex.getMessage().contains("2 篇文章"));
        verify(categoryMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("删除分类：分类下没有文章时正常删除")
    void delete_success() {
        when(categoryMapper.selectById(3)).thenReturn(buildCategory(3, "空分类"));
        when(articleMapper.countByCategoryId(3)).thenReturn(0);

        categoryService.delete(3);

        verify(categoryMapper).deleteById(3);
    }

    @Test
    @DisplayName("删除分类：分类不存在时抛业务异常")
    void delete_notExist() {
        when(categoryMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.delete(999));

        assertTrue(ex.getMessage().contains("分类不存在"));
    }

    @Test
    @DisplayName("修改分类：只改描述不改名字时，不该被自己判成重名")
    void update_keepNameOnlyChangeDescription() {
        Category old = buildCategory(1, "学习笔记");
        old.setDescription("旧描述");
        when(categoryMapper.selectById(1)).thenReturn(old);

        Category toUpdate = buildCategory(1, "学习笔记");
        toUpdate.setDescription("新描述");
        categoryService.update(toUpdate);

        verify(categoryMapper).update(toUpdate);
        // 名字没变，就不该去查重
        verify(categoryMapper, never()).selectByName(any());
    }

    @Test
    @DisplayName("修改分类：改成别人的名字时拒绝")
    void update_duplicateName() {
        when(categoryMapper.selectById(1)).thenReturn(buildCategory(1, "学习笔记"));
        when(categoryMapper.selectByName("踩坑记录")).thenReturn(buildCategory(2, "踩坑记录"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.update(buildCategory(1, "踩坑记录")));

        assertTrue(ex.getMessage().contains("已存在"));
        verify(categoryMapper, never()).update(any(Category.class));
    }
}
