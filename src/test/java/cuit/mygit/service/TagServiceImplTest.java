package cuit.mygit.service;

import cuit.mygit.entity.Tag;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleTagMapper;
import cuit.mygit.mapper.TagMapper;
import cuit.mygit.service.impl.TagServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TagService 的单元测试。
 * <p>
 * 重点是删标签这一步：它要动两张表（先删 article_tag 里的关联，
 * 再删 tag 本身），而且顺序不能反。这种"多张表配合"的逻辑
 * 正是最该写测试的地方。
 */
@ExtendWith(MockitoExtension.class)
class TagServiceImplTest {

    @Mock
    private TagMapper tagMapper;

    @Mock
    private ArticleTagMapper articleTagMapper;

    @InjectMocks
    private TagServiceImpl tagService;

    /** 造一个标签对象 */
    private Tag buildTag(Integer tagId, String name) {
        Tag tag = new Tag();
        tag.setTagId(tagId);
        tag.setName(name);
        return tag;
    }

    @Test
    @DisplayName("新增标签：名字没被占用时插入成功")
    void add_success() {
        when(tagMapper.selectByName("Redis")).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(invocation -> {
            invocation.<Tag>getArgument(0).setTagId(4);
            return 1;
        });

        Tag result = tagService.add(buildTag(null, "Redis"));

        assertEquals(4, result.getTagId().intValue());
    }

    @Test
    @DisplayName("新增标签：名称已存在时拒绝")
    void add_duplicateName() {
        when(tagMapper.selectByName("Java")).thenReturn(buildTag(1, "Java"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.add(buildTag(null, "Java")));

        assertTrue(ex.getMessage().contains("已存在"));
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    @DisplayName("删除标签：先清 article_tag 关联，再删标签本身（顺序不能反）")
    void delete_removesRelationsFirst() {
        when(tagMapper.selectById(3)).thenReturn(buildTag(3, "MySQL"));

        tagService.delete(3);

        // 如果先删标签，article_tag 里的外键会拦住，
        // 数据库直接报"外键约束失败"。所以顺序必须验证。
        InOrder inOrder = inOrder(articleTagMapper, tagMapper);
        inOrder.verify(articleTagMapper).deleteByTagId(3);
        inOrder.verify(tagMapper).deleteById(3);
    }

    @Test
    @DisplayName("删除标签：标签不存在时抛业务异常，不去动关联表")
    void delete_notExist() {
        when(tagMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.delete(999));

        assertTrue(ex.getMessage().contains("标签不存在"));
        verify(articleTagMapper, never()).deleteByTagId(anyInt());
        verify(tagMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("查标签：不存在时抛业务异常")
    void getById_notExist() {
        when(tagMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> tagService.getById(999));

        assertTrue(ex.getMessage().contains("标签不存在"));
    }
}
