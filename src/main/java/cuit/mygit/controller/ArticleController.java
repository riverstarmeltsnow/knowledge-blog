package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.entity.Article;
import cuit.mygit.entity.Tag;
import cuit.mygit.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
 * <p>
 * 最后三个接口（tags / tag）管的是"文章和标签的关系"，
 * 所以放在这里而不是 TagController。
 */
@RestController
@RequestMapping("/article")
public class ArticleController {

    /** 注入业务层 */
    @Autowired
    private ArticleService articleService;

    /**
     * 查询文章列表（不含正文）。
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

    /**
     * 查文章详情（含正文），同时把浏览量加一。
     * <p>
     * GET /article/1
     * <p>
     * 注意：这个接口每被调用一次，数据库里的浏览量就涨 1，
     * 刷新页面就会涨，属于正常现象。
     *
     * @param id 文章ID
     * @return 文章详情
     */
    @GetMapping("/{id}")
    public Result<Article> getById(@PathVariable("id") Integer id) {
        return Result.success(articleService.getById(id));
    }

    /**
     * 发表文章。
     * <p>
     * POST /article/add
     * 请求体：{@code {"title":"标题","content":"正文","userId":1,"categoryId":1}}
     * <p>
     * categoryId 可以不填（表示未分类），userId 必填。
     *
     * @param article 从请求体 JSON 转换来的文章对象
     * @return 发表成功的文章（带自增ID）
     */
    @PostMapping("/add")
    public Result<Article> add(@RequestBody Article article) {
        return Result.success(articleService.add(article));
    }

    /**
     * 修改文章。
     * <p>
     * PUT /article/update
     * 请求体必须带 articleId。
     *
     * @param article 要修改的文章
     * @return 不带数据的成功结果
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Article article) {
        articleService.update(article);
        return Result.success(null);
    }

    /**
     * 删除文章。
     * <p>
     * DELETE /article/3?operatorId=1
     * <p>
     * 会连带删掉这篇文章下的评论和标签关联。
     * operatorId 用来判断权限：只有作者本人或管理员能删。
     *
     * @param id         要删的文章ID
     * @param operatorId 操作人ID（当前登录用户）
     * @return 不带数据的成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id,
                               @RequestParam("operatorId") Integer operatorId) {
        articleService.delete(id, operatorId);
        return Result.success(null);
    }

    /**
     * 查这篇文章贴了哪些标签。
     * <p>
     * GET /article/1/tags
     *
     * @param id 文章ID
     * @return 标签列表
     */
    @GetMapping("/{id}/tags")
    public Result<List<Tag>> listTags(@PathVariable("id") Integer id) {
        return Result.success(articleService.listTags(id));
    }

    /**
     * 给文章贴一个标签。
     * <p>
     * POST /article/1/tag/3?operatorId=1
     * <p>
     * 两条规则：贴过的标签不能重复贴；**只有文章作者本人和管理员能改标签**，
     * 别人给你的文章贴标签会被拒绝。
     *
     * @param id         文章ID
     * @param tagId      标签ID
     * @param operatorId 操作人ID（当前登录用户）
     * @return 不带数据的成功结果
     */
    @PostMapping("/{id}/tag/{tagId}")
    public Result<Void> addTag(@PathVariable("id") Integer id,
                               @PathVariable("tagId") Integer tagId,
                               @RequestParam("operatorId") Integer operatorId) {
        articleService.addTag(id, tagId, operatorId);
        return Result.success(null);
    }

    /**
     * 取消文章上的某个标签。
     * <p>
     * DELETE /article/1/tag/3?operatorId=1
     * <p>
     * 只删"这篇文章和这个标签"之间的关联，标签本身还在，
     * 别的文章贴的也还在。同样只有作者本人和管理员能操作。
     *
     * @param id         文章ID
     * @param tagId      标签ID
     * @param operatorId 操作人ID（当前登录用户）
     * @return 不带数据的成功结果
     */
    @DeleteMapping("/{id}/tag/{tagId}")
    public Result<Void> removeTag(@PathVariable("id") Integer id,
                                  @PathVariable("tagId") Integer tagId,
                                  @RequestParam("operatorId") Integer operatorId) {
        articleService.removeTag(id, tagId, operatorId);
        return Result.success(null);
    }
}
