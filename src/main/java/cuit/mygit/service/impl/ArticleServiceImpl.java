package cuit.mygit.service.impl;

import cuit.mygit.entity.Article;
import cuit.mygit.entity.ArticleTag;
import cuit.mygit.entity.Tag;
import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.mapper.ArticleTagMapper;
import cuit.mygit.mapper.CategoryMapper;
import cuit.mygit.mapper.CommentMapper;
import cuit.mygit.mapper.TagMapper;
import cuit.mygit.mapper.UserMapper;
import cuit.mygit.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章业务实现类。
 * <p>
 * {@code @Service} 告诉 Spring：启动时把这个类创建成对象放进容器，
 * 这样 Controller 里才能注入它。
 * <p>
 * 文章是系统的核心表，牵扯的用户表、分类表、评论表、标签关联表都靠它串起来，
 * 所以这个类里注入的 Mapper 也最多。
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    /**
     * 注入 Mapper。
     * 这是我们自己写的接口，实现类由 MyBatis 在启动时动态生成。
     */
    @Autowired
    private ArticleMapper articleMapper;

    /** 发表文章要确认作者真实存在（article.user_id 有外键） */
    @Autowired
    private UserMapper userMapper;

    /** 发表文章要确认分类真实存在（article.category_id 有外键） */
    @Autowired
    private CategoryMapper categoryMapper;

    /** 删文章要连带删掉它下面的评论 */
    @Autowired
    private CommentMapper commentMapper;

    /** 删文章要连带清掉它的标签关联 */
    @Autowired
    private ArticleTagMapper articleTagMapper;

    /** 给文章贴标签时要确认标签本身存在 */
    @Autowired
    private TagMapper tagMapper;

    @Override
    public List<Article> listAll() {
        return articleMapper.selectAll();
    }

    @Override
    public Article getById(Integer articleId) {
        if (articleId == null) {
            throw new BusinessException("缺少文章ID");
        }
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }

        // 有人点进来看详情，浏览量加一
        articleMapper.increaseViewCount(articleId);

        // 数据库里的浏览量已经加过了，但手里这个对象还是加之前的值。
        // 不跟着改的话，返回给浏览器的 JSON 会比数据库少 1，看着对不上。
        int currentViewCount = article.getViewCount() == null ? 0 : article.getViewCount();
        article.setViewCount(currentViewCount + 1);

        return article;
    }

    @Override
    @Transactional
    public Article add(Article article) {
        if (article == null || isBlank(article.getTitle())) {
            throw new BusinessException("文章标题不能为空");
        }

        // ---------- 校验作者 ----------
        if (article.getUserId() == null) {
            throw new BusinessException("缺少作者ID");
        }
        if (userMapper.selectById(article.getUserId()) == null) {
            throw new BusinessException("作者不存在：" + article.getUserId());
        }

        // ---------- 校验分类（可以不填，表示"未分类"）----------
        // 建表时 category_id 是允许为 NULL 的，所以这里只在填了的时候才检查
        if (article.getCategoryId() != null
                && categoryMapper.selectById(article.getCategoryId()) == null) {
            throw new BusinessException("分类不存在：" + article.getCategoryId());
        }

        articleMapper.insert(article);
        return article;
    }

    @Override
    public void update(Article article) {
        if (article == null || article.getArticleId() == null) {
            throw new BusinessException("缺少文章ID，无法修改");
        }

        Article old = articleMapper.selectById(article.getArticleId());
        if (old == null) {
            throw new BusinessException("文章不存在：" + article.getArticleId());
        }

        if (isBlank(article.getTitle())) {
            throw new BusinessException("文章标题不能为空");
        }
        if (article.getCategoryId() != null
                && categoryMapper.selectById(article.getCategoryId()) == null) {
            throw new BusinessException("分类不存在：" + article.getCategoryId());
        }

        // 作者不允许改（文章不能换人），用原来的人
        article.setUserId(old.getUserId());

        articleMapper.update(article);
    }

    @Override
    @Transactional
    public void delete(Integer articleId, Integer operatorId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }

        // 只有作者本人或管理员能删
        checkPermission(operatorId, article.getUserId(), "删除这篇文章");

        // 删除顺序由外键决定，不能乱：
        // comment 和 article_tag 都指着 article，必须先删它们，最后才能删文章本身。
        commentMapper.deleteByArticleId(articleId);
        articleTagMapper.deleteByArticleId(articleId);
        articleMapper.deleteById(articleId);

        // @Transactional 保证这三步是一个整体：
        // 万一删到一半出错，前面的删除会回滚，不会出现"评论没了但文章还在"的脏数据。
    }

    @Override
    public List<Tag> listTags(Integer articleId) {
        if (articleId == null) {
            throw new BusinessException("缺少文章ID");
        }
        return articleTagMapper.selectTagsByArticleId(articleId);
    }

    @Override
    public void addTag(Integer articleId, Integer tagId) {
        if (articleId == null || tagId == null) {
            throw new BusinessException("缺少文章ID或标签ID");
        }
        if (articleMapper.selectById(articleId) == null) {
            throw new BusinessException("文章不存在：" + articleId);
        }
        if (tagMapper.selectById(tagId) == null) {
            throw new BusinessException("标签不存在：" + tagId);
        }

        // article_tag 的主键是 (article_id, tag_id) 联合主键，
        // 重复插入会直接被数据库拒绝。提前查一次，好给出看得懂的提示。
        ArticleTag relation = new ArticleTag(articleId, tagId);
        if (articleTagMapper.selectOne(relation) != null) {
            throw new BusinessException("这篇文章已经贴过该标签了");
        }

        articleTagMapper.insert(relation);
    }

    @Override
    public void removeTag(Integer articleId, Integer tagId) {
        if (articleId == null || tagId == null) {
            throw new BusinessException("缺少文章ID或标签ID");
        }

        ArticleTag relation = new ArticleTag(articleId, tagId);
        if (articleTagMapper.selectOne(relation) == null) {
            throw new BusinessException("这篇文章没有贴过该标签，无法取消");
        }

        articleTagMapper.delete(relation);
    }

    /**
     * 检查操作人有没有资格动这篇文章。
     * <p>
     * 规则和 {@link CommentServiceImpl} 里删评论时用的完全一样：
     * 本人可以，管理员可以，其他人不行。
     *
     * @param operatorId 操作人ID（当前登录用户）
     * @param ownerId    文章作者ID
     * @param action     要做的动作，用来拼错误提示，如"删除这篇文章"
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

        // 情况一：自己写的文章，自己当然能删
        if (operatorId.equals(ownerId)) {
            return;
        }

        // 情况二：管理员（role = 1）能删任何人的
        if (Integer.valueOf(1).equals(operator.getRole())) {
            return;
        }

        // 情况三：既不是作者也不是管理员，拒绝
        throw new BusinessException("没有权限" + action + "（只有作者本人和管理员可以操作）");
    }

    /** 判断字符串是不是空的（null 或只有空格） */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
