package cuit.mygit.service.impl;

import cuit.mygit.common.PermissionChecker;
import cuit.mygit.entity.Comment;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评论业务实现类。
 * <p>
 * 评论支持"回复"：一条顶层评论下面可以挂多条回复，回复永远是两层，
 * 具体怎么归一化在 {@link #add} 里说明。
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

    /** 删评论要判断"是不是本人或管理员" */
    @Autowired
    private PermissionChecker permissionChecker;

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

        // ---------- 处理"回复"（parentId 有值就说明这是一条回复）----------
        // replyToUserId 一律由后端从被回复的评论里推出来，先把前端传的清掉，
        // 否则谁都能伪造成"某某回复了你"。
        comment.setReplyToUserId(null);
        if (comment.getParentId() != null) {
            Comment parent = commentMapper.selectById(comment.getParentId());
            if (parent == null) {
                throw new BusinessException("要回复的评论不存在：" + comment.getParentId());
            }
            // 不能跨文章回复，否则评论会挂到另一篇文章的评论树下面
            if (!parent.getArticleId().equals(comment.getArticleId())) {
                throw new BusinessException("不能回复其他文章下的评论");
            }

            // 被回复的这条本身如果也是回复，就把新评论挂到同一个顶层评论下。
            // 这一步让评论永远只有两层："A 的评论"下面一堆回复，
            // 不管谁回复谁都在同一层，不用递归就能查、就能删。
            if (parent.getParentId() != null) {
                comment.setParentId(parent.getParentId());
            }

            // 被回复人 = 被回复那条评论的作者，页面靠它显示"回复 @某某"
            comment.setReplyToUserId(parent.getUserId());
        }

        commentMapper.insert(comment);

        // 重新查一次再返回。
        // 评论时间是数据库自动填的，手里这个对象上还是 null，
        // 回查一次就能拿到真实值。
        return commentMapper.selectById(comment.getCommentId());
    }

    @Override
    @Transactional
    public void delete(Integer commentId, Integer operatorId) {
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException("评论不存在：" + commentId);
        }

        // 只能删自己的评论；管理员例外，能删任何人的
        permissionChecker.checkOwnerOrAdmin(operatorId, comment.getUserId(), "删除这条评论");

        // 删顶层评论时，挂在它下面的回复也要一起删掉，不然会留下没有归属的孤儿回复。
        // 顺序不能反：回复的 parent_id 指着顶层评论，先删顶层评论会被外键拦住。
        // 被删的本身就是一条回复时，这句删除影响 0 行（回复下面不会再有回复），
        // 所以不用先判断它是顶层还是回复，直接删就行。
        commentMapper.deleteByParentId(commentId);
        commentMapper.deleteById(commentId);

        // @Transactional 保证这两步是一个整体：
        // 万一第二步失败，第一步删掉的回复会回滚，不会出现"回复没了但评论还在"。
    }

    /** 判断字符串是不是空的（null 或只有空格） */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
