package cuit.mygit.service.impl;

import cuit.mygit.entity.Article;
import cuit.mygit.mapper.ArticleMapper;
import cuit.mygit.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文章业务实现类。
 * <p>
 * {@code @Service} 告诉 Spring：启动时把这个类创建成对象放进容器，
 * 这样 Controller 里才能注入它。
 * <p>
 * 目前的业务很简单，只是把请求转给 Mapper。等以后加"发表文章"、
 * "删除文章"这类功能，校验、权限判断等逻辑就写在这一层，
 * 不要写到 Controller 或 Mapper 里。
 */
@Service
public class ArticleServiceImpl implements ArticleService {

    /**
     * 注入 Mapper。
     * 这是我们自己写的接口，实现类由 MyBatis 在启动时动态生成。
     */
    @Autowired
    private ArticleMapper articleMapper;

    @Override
    public List<Article> listAll() {
        return articleMapper.selectAll();
    }
}
