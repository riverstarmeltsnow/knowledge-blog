package cuit.mygit.service;

import cuit.mygit.entity.Article;

import java.util.List;

/**
 * 文章业务接口（Service 层）。
 * <p>
 * 为什么要"接口 + 实现类"分开写？
 * 因为 Controller 只依赖这个接口，不关心具体实现。
 * 以后要把 MyBatis 换成别的技术，只要换实现类，Controller 一行都不用改。
 */
public interface ArticleService {

    /**
     * 查询文章列表。
     *
     * @return 文章列表
     */
    List<Article> listAll();
}
