package cuit.mygit.service.impl;

import cuit.mygit.entity.Comment;
import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 评论业务实现类。
 */
@Service
public class CommentServiceImpl implements CommentService {

    @Autowired
    private CommentMapper commentMapper;

    /** 发表评论前要确认这个人真实存在（comment.user_id 有外键） */
    @Autowired
    private UserMapper userMapper;

    /** 发表评论前要确认这篇文章真实存在（comment.article_id 有外键） */
    @Autowired
    private ArticleMapper articleMapper;

    @Override
    public List<Comment> listByArticle(Integer articleId) {
        if (articleId == null) {
            throw new BusinessException("缺少文章ID");
        }
        // 查评论列表本身不需要文章存在，但如果文章不存在，
        // 返回空列表会让调用方以为"这篇文章没人评论"，其实是文章压根没有。
        // 所以这里查一下，给个明确的提示。
        if (articleMapper.selectById(articleId) == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }
        return commentMapper.selectByArticleId(articleId);
    }

    @Override
    public List<Comment> listByUser(Integer userId) {
        if (userId == null) {
            throw new BusinessException("缺少用户ID");
        }
        return commentMapper.selectByUserId(userId);
    }

    @Override
    public Comment add(Comment comment) {
        if (comment == null || isBlank(comment.getContent())) {
            throw new BusinessException("评论内容不能为空");
        }

        // 两个外键都要先确认存在，否则插入时数据库会报"外键约束失败"，
        // 那串报错对使用者毫无意义
        if (comment.getUserId() == null) {
            throw new BusinessException("缺少评论人ID");
        }
        if (userMapper.selectById(comment.getUserId()) == null) {
            throw new BusinessException("评论人不存在：" + comment.getUserId());
        }
        if (comment.getArticleId() == null) {
            throw new BusinessException("缺少文章ID");
        }
        if (articleMapper.selectById(comment.getArticleId()) == null) {
            throw new BusinessException("文章不存在：" + comment.getArticleId());
        }

        commentMapper.insert(comment);
        return comment;
    }

    @Override
    public void delete(Integer commentId, Integer operatorId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在：" + commentId);
        }

        // 只能删自己的评论；管理员例外，能删任何人的
        checkPermission(operatorId, comment.getUserId(), "删除这条评论");

        commentMapper.deleteById(commentId);
    }

    /**
     * 检查操作人有没有资格动这条数据。
     * <p>
     * 规则和 {@link ArticleServiceImpl} 里删文章时用的完全一样：
     * 本人可以，管理员可以，其他人不行。
     *
     * @param operatorId 操作人ID（当前登录用户）
     * @param ownerId    数据的主人ID（评论作者、文章作者）
     * @param action     要做的动作，用来拼错误提示，如"删除这条评论"
     * @throws BusinessException 操作人不存在或没权限时抛出
     */
    private void checkPermission(Integer operatorId, Integer ownerId, String action) {
        if (operatorId == null) {
            throw new BusinessException("缺少操作人ID");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作人不存在：" + operatorId);
        }

        // 情况一：自己写的内容，自己当然能删
        if (operatorId.equals(ownerId)) {
            return;
        }

        // 情况二：管理员（role = 1）能删任何人的
        if (Integer.valueOf(1).equals(operator.getRole())) {
            return;
        }

        // 情况三：既不是本人也不是管理员，拒绝
        throw new BusinessException("没有权限" + action + "（只有作者本人和管理员可以操作）");
    }

    /** 判断字符串是不是空的（null 或只有空格） */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
