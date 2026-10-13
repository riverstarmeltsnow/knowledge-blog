package cuit.mygit.service;

import cuit.mygit.common.PermissionChecker;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CategoryService 的单元测试。
 * <p>
 * 分类这块三条核心规则：<b>只有管理员能增删改</b>；名字不能重复；
 * 分类底下还有文章时不能删。
 * <p>
 * 第二条尤其值得测 —— 它是替数据库外键"提前把关"，
 * 没有它的话用户看到的会是一串看不懂的 SQL 报错。
 * <p>
 * <b>权限那条规则本身不在这里测</b>：它已经抽到 {@link PermissionChecker}，
 * 由 {@code PermissionCheckerTest} 统一负责。这里只验证两件事 ——
 * 该拒绝的时候分类确实一个字都没写库，以及放行时该走的查重逻辑照常走。
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock
    private ArticleMapper articleMapper;

    /** 权限规则由 PermissionCheckerTest 负责，这里只用它来控制"放行 / 拒绝" */
    @Mock
    private PermissionChecker permissionChecker;

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

        Category result = categoryService.add(category, 1);

        assertEquals(3, result.getCategoryId().intValue());
    }

    @Test
    @DisplayName("新增分类：名称为空时拒绝")
    void add_blankName() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.add(buildCategory(null, "  "), 1));

        assertTrue(ex.getMessage().contains("名称不能为空"));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    @DisplayName("新增分类：名称已存在时拒绝")
    void add_duplicateName() {
        when(categoryMapper.selectByName("学习笔记")).thenReturn(buildCategory(1, "学习笔记"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.add(buildCategory(null, "学习笔记"), 1));

        assertTrue(ex.getMessage().contains("已存在"));
        verify(categoryMapper, never()).insert(any(Category.class));
    }

    @Test
    @DisplayName("新增分类：不是管理员时拒绝，且一条记录都不插")
    void add_notAdmin() {
        doThrow(new BusinessException("没有权限新增分类（只有管理员可以操作）"))
                .when(permissionChecker).checkAdmin(2, "新增分类");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.add(buildCategory(null, "读书笔记"), 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        // 权限没过就该原地停下 —— 连"这个名字有没有被占用"都不该去查，
        // 否则这个接口就成了一个不用登录就能用的探针
        verify(categoryMapper, never()).insert(any(Category.class));
        verify(categoryMapper, never()).selectByName(any());
    }

    @Test
    @DisplayName("删除分类：分类下还有文章时拒绝删除（替数据库外键提前把关）")
    void delete_hasArticles() {
        when(categoryMapper.selectById(1)).thenReturn(buildCategory(1, "学习笔记"));
        when(articleMapper.countByCategoryId(1)).thenReturn(2);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.delete(1, 1));

        // 提示里要说清楚有几篇，用户才知道该处理多少
        assertTrue(ex.getMessage().contains("2 篇文章"));
        verify(categoryMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("删除分类：不是管理员时拒绝，而且比'分类不存在'先拦住")
    void delete_notAdmin() {
        doThrow(new BusinessException("没有权限删除分类（只有管理员可以操作）"))
                .when(permissionChecker).checkAdmin(2, "删除分类");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.delete(9, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        // 权限判断排在查库之前，所以连 selectById 都不该发生
        verify(categoryMapper, never()).selectById(anyInt());
        verify(categoryMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("删除分类：分类下没有文章时正常删除")
    void delete_success() {
        when(categoryMapper.selectById(3)).thenReturn(buildCategory(3, "空分类"));
        when(articleMapper.countByCategoryId(3)).thenReturn(0);

        categoryService.delete(3, 1);

        verify(categoryMapper).deleteById(3);
    }

    @Test
    @DisplayName("删除分类：分类不存在时抛业务异常")
    void delete_notExist() {
        when(categoryMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> categoryService.delete(999, 1));

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
        categoryService.update(toUpdate, 1);

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
                () -> categoryService.update(buildCategory(1, "踩坑记录"), 1));

        assertTrue(ex.getMessage().contains("已存在"));
        verify(categoryMapper, never()).update(any(Category.class));
    }
}
