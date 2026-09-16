package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.entity.Article;
import cuit.mygit.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章控制器（控制层）。
 * <p>
 * {@code @RestController} = {@code @Controller} + {@code @ResponseBody}，
 * 意思是：这个类负责接收 HTTP 请求，方法的返回值直接转成 JSON 写回浏览器，
 * 而不是去找一个页面模板。
 * <p>
 * {@code @RequestMapping("/article")} 是给这个类里所有接口加统一前缀。
 */
@RestController
@RequestMapping("/article")
public class ArticleController {

    /** 注入业务层 */
    @Autowired
    private ArticleService articleService;

    /**
     * 查询文章列表。
     * <p>
     * 浏览器访问：http://localhost:8080/article/list
     *
     * @return 统一格式的 JSON，文章列表装在 data 字段里
     */
    @GetMapping("/list")
    public Result<List<Article>> list() {
        List<Article> articles = articleService.listAll();
        return Result.success(articles);
    }
}
