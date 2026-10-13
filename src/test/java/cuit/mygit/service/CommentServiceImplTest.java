package cuit.mygit.service;

import cuit.mygit.common.PermissionChecker;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CommentService 的单元测试。
 * <p>
 * 重点测三件事：
 * <ol>
 *   <li>发表评论前的各种校验（内容、用户、文章）</li>
 *   <li>回复的归一化 —— 回复"一条回复"时要被提到同一个顶层评论下，
 *       被回复人要由后端推出来而不是照抄前端</li>
 *   <li>删除评论时的权限判断，以及删顶层评论要连带删回复</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ArticleMapper articleMapper;

    /**
     * 权限校验器用 mock，规则本身由 {@code PermissionCheckerTest} 负责。
     * 这里要盯的是"删评论时有没有把<b>评论作者</b>的 ID 传下去"。
     * <p>
     * 注意 {@code userMapper} 还是要留着 —— {@code add} 里
     * "评论人存不存在"那一步仍然要查用户表，和权限不是一回事。
     */
    @Mock
    private PermissionChecker permissionChecker;

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

    /** 造一条回复；parentId 表示它挂在哪条顶层评论下面 */
    private Comment buildReply(Integer commentId, Integer userId, Integer articleId, Integer parentId) {
        Comment reply = buildComment(commentId, userId, articleId);
        reply.setParentId(parentId);
        return reply;
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

    /**
     * 取出真正传给 Mapper 的那个评论对象。
     * 归一化的结果（parentId 有没有被改、replyToUserId 被填成谁）
     * 都发生在 insert 之前，所以要拦住 insert 看参数，而不是看方法的返回值。
     */
    private Comment capturedInserted() {
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        return captor.getValue();
    }

    // ==================== 回复评论 ====================

    @Test
    @DisplayName("回复顶层评论：parentId 保持不动，被回复人由后端从评论里推出来")
    void add_replyToTopLevelComment() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));   // 回复人 2 号
        when(articleMapper.selectById(1)).thenReturn(new Article());
        // 5 号评论是 1 号写的顶层评论（它自己没有 parentId）
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);

        // 2 号回复 5 号评论
        commentService.add(buildReply(null, 2, 1, 5));

        Comment inserted = capturedInserted();
        assertEquals(5, inserted.getParentId().intValue());
        // 被回复人是 5 号评论的作者（1 号），不是回复人自己
        assertEquals(1, inserted.getReplyToUserId().intValue());
    }

    @Test
    @DisplayName("回复一条回复：新评论被提到顶层，层级不会越套越深")
    void add_replyToReply_isLiftedToTopLevel() {
        when(userMapper.selectById(3)).thenReturn(buildUser(3, 0));   // 回复人 3 号
        when(articleMapper.selectById(1)).thenReturn(new Article());
        // 6 号是"2 号回复 5 号"的那条回复，它自己挂在顶层评论 5 下面
        Comment existingReply = buildReply(6, 2, 1, 5);
        when(commentMapper.selectById(6)).thenReturn(existingReply);
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);

        // 3 号回复 6 号这条回复
        commentService.add(buildReply(null, 3, 1, 6));

        Comment inserted = capturedInserted();
        // 关键：parentId 从 6 被改成了 5，挂到和 6 号同一个顶层评论下。
        // 不加这一步，评论就会一层套一层，查和删都要递归。
        assertEquals(5, inserted.getParentId().intValue());
        // 被回复人仍然是 6 号的作者（2 号），这样页面才能显示"回复 @2号"
        assertEquals(2, inserted.getReplyToUserId().intValue());
    }

    @Test
    @DisplayName("回复：前端硬传的 replyToUserId 会被忽略，一律由后端推导")
    void add_replyToUserIdIsNotTrustedFromClient() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));
        when(articleMapper.selectById(1)).thenReturn(new Article());
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);

        Comment reply = buildReply(null, 2, 1, 5);
        reply.setReplyToUserId(999);   // 想伪造成"999 号回复了你"

        commentService.add(reply);

        // 被改回 1 号（5 号评论真正的作者），伪造没生效
        assertEquals(1, capturedInserted().getReplyToUserId().intValue());
    }

    @Test
    @DisplayName("回复：顶层评论自己不带 replyToUserId（表示直接评论文章）")
    void add_topLevelComment_hasNoReplyToUser() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));
        when(articleMapper.selectById(1)).thenReturn(new Article());
        when(commentMapper.insert(any(Comment.class))).thenReturn(1);

        // 不带 parentId，就是普通评论
        commentService.add(buildComment(null, 2, 1));

        Comment inserted = capturedInserted();
        assertNull(inserted.getParentId());
        assertNull(inserted.getReplyToUserId());
    }

    @Test
    @DisplayName("回复：被回复的评论不存在时拒绝")
    void add_parentCommentNotExist() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));
        when(articleMapper.selectById(1)).thenReturn(new Article());
        when(commentMapper.selectById(999)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.add(buildReply(null, 2, 1, 999)));

        assertTrue(ex.getMessage().contains("要回复的评论不存在"));
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    @Test
    @DisplayName("回复：不能回复别的文章下面的评论")
    void add_parentCommentInAnotherArticle() {
        when(userMapper.selectById(2)).thenReturn(buildUser(2, 0));
        when(articleMapper.selectById(1)).thenReturn(new Article());
        // 8 号评论属于文章 2，却想挂到文章 1 的评论树下面
        when(commentMapper.selectById(8)).thenReturn(buildComment(8, 1, 2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.add(buildReply(null, 2, 1, 8)));

        assertTrue(ex.getMessage().contains("不能回复其他文章下的评论"));
        verify(commentMapper, never()).insert(any(Comment.class));
    }

    // ==================== 删除评论（所有权） ====================

    @Test
    @DisplayName("删除评论：不是作者也不是管理员时拒绝，且不删数据")
    void delete_notOwnerAndNotAdmin() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));  // 评论是 1 号写的
        doThrow(new BusinessException("没有权限删除这条评论（只有作者本人和管理员可以操作）"))
                .when(permissionChecker).checkOwnerOrAdmin(2, 1, "删除这条评论");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.delete(5, 2));

        assertTrue(ex.getMessage().contains("没有权限"));
        // 权限没过，一条数据都不该删（包括它下面的回复）
        verify(commentMapper, never()).deleteById(anyInt());
        verify(commentMapper, never()).deleteByParentId(anyInt());
    }

    @Test
    @DisplayName("删除顶层评论：按'先删回复、再删自己'的顺序（顺序反了会被外键拦住）")
    void delete_topLevelComment_cascadesRepliesInOrder() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));  // 顶层评论

        commentService.delete(5, 1);

        verify(permissionChecker).checkOwnerOrAdmin(1, 1, "删除这条评论");

        // reply.parent_id 有外键指向 comment.comment_id，
        // 先删顶层评论会被"外键约束失败"拦下，所以顺序必须验证
        InOrder inOrder = inOrder(commentMapper);
        inOrder.verify(commentMapper).deleteByParentId(5);
        inOrder.verify(commentMapper).deleteById(5);
    }

    @Test
    @DisplayName("删除一条回复：同样先清一遍它下面的回复（实际是 0 条），不影响结果")
    void delete_reply_alsoClearsItsOwnReplies() {
        // 6 号是挂在顶层评论 5 下面的回复
        when(commentMapper.selectById(6)).thenReturn(buildReply(6, 2, 1, 5));

        commentService.delete(6, 2);

        // 权限那一关看的是**评论作者的 ID**（回复的 userId 是 2），
        // 不是 parentId 那个 5 —— 传错了就会让被回复的人能删别人的回复
        verify(permissionChecker).checkOwnerOrAdmin(2, 2, "删除这条评论");

        // 回复下面不会再有回复，这句删除影响 0 行；
        // 保留它是为了让删除逻辑不分叉（不用先判断是顶层还是回复）
        verify(commentMapper).deleteByParentId(6);
        verify(commentMapper).deleteById(6);
    }

    @Test
    @DisplayName("删除评论：作者本人可以删自己的评论")
    void delete_byAuthor() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));

        commentService.delete(5, 1);

        verify(commentMapper).deleteById(5);
        verify(permissionChecker).checkOwnerOrAdmin(1, 1, "删除这条评论");
    }

    @Test
    @DisplayName("删除评论：管理员可以删别人的评论")
    void delete_byAdmin() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));  // 评论是 1 号写的

        commentService.delete(5, 2);

        verify(commentMapper).deleteById(5);
        verify(permissionChecker).checkOwnerOrAdmin(2, 1, "删除这条评论");
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
    @DisplayName("删除评论：操作人不存在时拒绝（checker 抛出的异常要能传上来）")
    void delete_operatorNotExist() {
        when(commentMapper.selectById(5)).thenReturn(buildComment(5, 1, 1));
        // "操作人存不存在"现在是 checker 的事，这里只验证它抛出来之后
        // Service 不会硬着头皮往下删
        doThrow(new BusinessException("操作人不存在：999"))
                .when(permissionChecker).checkOwnerOrAdmin(999, 1, "删除这条评论");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> commentService.delete(5, 999));

        assertTrue(ex.getMessage().contains("操作人不存在"));
        verify(commentMapper, never()).deleteById(anyInt());
    }
}
