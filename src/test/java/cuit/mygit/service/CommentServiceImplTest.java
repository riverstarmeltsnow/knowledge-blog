package cuit.mygit.service;

import cuit.mygit.entity.Article;
import cuit.mygit.entity.Comment;
import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.impl.CommentServiceImpl;
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
 * CommentService 的单元测试。
 * <p>
 * 重点测两件事：发表评论前的各种校验（内容、用户、文章），
 * 以及删除评论时的权限判断 —— 这是系统里"所有权"规则最典型的体现。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ArticleMapper articleMapper;

    @InjectMocks
    private CommentServiceImpl commentService;

    /** 造一条评论；commentId 传 null 表示"还没入库的新评论" */
    private Comment buildComment(Integer commentId, Integer userId, Integer articleId) {
        Comment comment = new Comment();
        comment.setCommentId(commentId);
        comment.setContent("说得好");
        comment.setUserId(userId);
        comment.setArticleId(articleId);
        return comment;
    }

    /** 造一个用户；role 传 1 表示管理员 */
    private User buildUser(Integer userId, Integer role) {
        User user = new User();
        user.setUserId(userId);
        user.setRole(role);
        return user;
    }

    // ==================== 查询 ====================

    @Test
    @DisplayName("查评论：文章存在时返回它的评论列表")
    void listByArticle_success() {
        when(articleMapper.selectById(1)).thenReturn(new Article());
        when(commentMapper.selectByArticleId(1))
                .thenReturn(Arrays.asList(buildComment(1, 1, 1), buildComment(2, 2, 1)));

        List<Comment> result = commentService.listByArticle(1);

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("查评论：文章不存在时报错，而不是返回空列表")
    void listByArticle_articleNotExist() {
        when(articleMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.listByArticle(999));

        // 返回空列表会让调用方以为"这篇文章没人评论"，其实是文章压根没有，
        // 所以这里必须报错说清楚
        assertTrue(ex.getMessage().contains("文章不存在"));
    }

    // ==================== 发表评论 ====================

    @Test
    @DisplayName("发表评论：内容为空时拒绝")
    void add_blankContent() {
        Comment comment = buildComment(null, 1, 1);
        comment.setContent("   ");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.add(comment));

        assertTrue(ex.getMessage().contains("内容不能为空"));
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("发表评论：评论人不存在时拒绝（否则数据库外键会报错）")
    void add_userNotExist() {
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.add(buildComment(null, 999, 1)));

        assertTrue(ex.getMessage().contains("评论人不存在"));
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("发表评论：文章不存在时拒绝")
    void add_articleNotExist() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        when(articleMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.add(buildComment(null, 1, 999)));

        assertTrue(ex.getMessage().contains("文章不存在"));
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("发表评论：数据合法时插入成功，并返回查回来的完整记录")
    void add_success() {
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));
        when(articleMapper.selectById(1)).thenReturn(new Article());

        // 模拟数据库回填自增ID
        when(commentMapper.insert(any(Comment.class))).thenAnswer(invocation -> {
            invocation.<Comment>getArgument(0).setCommentId(7);
            return 1;
        });

        Comment saved = buildComment(7, 1, 1);
        when(commentMapper.selectById(7)).thenReturn(saved);

        Comment result = commentService.add(buildComment(null, 1, 1));

        assertEquals(7, result.getCommentId().intValue());
    }

    // ==================== 删除评论（所有权） ====================

    @Test
    @DisplayName("删除评论：不是作者也不是管理员时拒绝，且不删数据")
    void delete_notOwnerAndNotAdmin() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));  // 评论是 1 号写的
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));          // 操作人 2 号是普通用户

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.delete(5, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        verify(commentMapper, never()).deleteById(anyInt());
    }

    @Test
    @DisplayName("删除评论：作者本人可以删自己的评论")
    void delete_byAuthor() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));
        when(userMapper.selectById(1)).thenReturn(buildUser(1, 0));

        commentService.delete(5, 1);

        verify(commentMapper).deleteById(5);
    }

    @Test
    @DisplayName("删除评论：管理员可以删别人的评论")
    void delete_byAdmin() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));  // 评论是 1 号写的
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 1));          // 操作人 2 号是管理员

        commentService.delete(5, 2);

        verify(commentMapper).deleteById(5);
    }

    @Test
    @DisplayName("删除评论：评论不存在时抛业务异常")
    void delete_notExist() {
        when(commentMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.delete(999, 1));

        assertTrue(ex.getMessage().contains("评论不存在"));
    }

    @Test
    @DisplayName("删除评论：操作人不存在时拒绝")
    void delete_operatorNotExist() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));
        when(userMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.delete(5, 999));

        assertTrue(ex.getMessage().contains("操作人不存在"));
        verify(commentMapper, never()).deleteById(anyInt());
    }
}
