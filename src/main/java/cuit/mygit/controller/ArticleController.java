package cuit.mygit.controller;

import cuit.mygit.common.PageResult;
import cuit.mygit.common.Result;
import cuit.mygit.common.ValidGroup;
import cuit.mygit.entity.Article;
import cuit.mygit.entity.Tag;
import cuit.mygit.service.ArticleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
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

    /*
     * ============================================================
     * 下面五个都是"查"接口，而且路径都是**固定文字**开头的，
     * 所以必须写在这几个方法的前面 —— 尤其要放在 /{id} 前面。
     *
     * 原因：/{id} 是个路径变量，能匹配 /article/ 底下的任何一段。
     * 如果它排在前面，访问 /article/search 时 Spring 会先把 "search"
     * 当成文章ID去匹配，结果就是类型转换失败。
     *
     * （Spring Boot 3 以后用的路径匹配器其实会优先选"更具体"的那个，
     *  所以即使顺序写反了多半也能正常工作。但依赖这个规则容易踩坑，
     *  老老实实把精确路径写在前面最省心。）
     * ============================================================
     */

    /**
     * 分页查询文章列表。
     * <p>
     * GET /article/page?page=1&size=10
     * <p>
     * page 从 1 开始数。两个参数都可以不传，不传就是第 1 页、每页 10 条。
     * 页码超出范围不会报错，返回的是一个空列表，前端照常画页码条就行。
     * <p>
     * 返回的 data 是分页对象，除了本页数据还带总数和总页数：
     * <pre>
     * {"code":200,"data":{"list":[...],"total":87,"page":2,"size":10,
     *                     "totalPages":9,"hasNext":true,"hasPrev":true}}
     * </pre>
     *
     * @param page 第几页，从 1 开始
     * @param size 每页多少条，最多 50 条
     * @return 分页结果
     */
    @GetMapping("/page")
    public Result<PageResult<Article>> page(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return Result.success(articleService.page(page, size));
    }

    /**
     * 按标题关键字搜索。
     * <p>
     * GET /article/search?keyword=MySQL
     * <p>
     * 关键字为空会返回失败提示，而不是把全部文章都查出来 ——
     * 空关键字拼进 SQL 会变成 {@code LIKE '%%'}，等于全表查询。
     *
     * @param keyword 要搜索的文字，匹配标题里包含它的文章
     * @return 匹配到的文章列表
     */
    @GetMapping("/search")
    public Result<List<Article>> search(@RequestParam("keyword") String keyword) {
        return Result.success(articleService.searchByTitle(keyword));
    }

    /**
     * 查某个作者发表的全部文章。
     * <p>
     * GET /article/author/1
     *
     * @param userId 作者ID
     * @return 文章列表；他没写过文章时是空列表
     */
    @GetMapping("/author/{userId}")
    public Result<List<Article>> listByAuthor(@PathVariable("userId") Integer userId) {
        return Result.success(articleService.listByUserId(userId));
    }

    /**
     * 查某个分类下的全部文章。
     * <p>
     * GET /article/category/1
     *
     * @param categoryId 分类ID
     * @return 文章列表；该分类下没有文章时是空列表
     */
    @GetMapping("/category/{categoryId}")
    public Result<List<Article>> listByCategory(@PathVariable("categoryId") Integer categoryId) {
        return Result.success(articleService.listByCategoryId(categoryId));
    }

    /**
     * 查贴了某个标签的全部文章。
     * <p>
     * GET /article/tag/2
     * <p>
     * 和 {@code GET /article/1/tags} 正好相反：那个是"这篇文章有哪些标签"，
     * 这个是"这个标签被哪些文章贴了"。
     *
     * @param tagId 标签ID
     * @return 文章列表；这个标签还没被贴过时是空列表
     */
    @GetMapping("/tag/{tagId}")
    public Result<List<Article>> listByTag(@PathVariable("tagId") Integer tagId) {
        return Result.success(articleService.listByTagId(tagId));
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
    public Result<Article> add(
            @Validated(ValidGroup.Create.class) @RequestBody Article article) {
        return Result.success(articleService.add(article));
    }

    /**
     * 修改文章。
     * <p>
     * PUT /article/update
     * 请求体必须带 articleId。
     * <p>
     * {@code @Validated} 括号里写的是 <b>Update</b> 分组，和上面的 add 不一样：
     * 新增时 articleId 由数据库生成、提交时还没有，不能要求必填；
     * 修改时没 ID 就不知道改哪一篇，必须必填。
     * 两套规则同时写在 Article 那一个类上，靠分组区分这次该用哪套。
     *
     * @param article 要修改的文章
     * @return 不带数据的成功结果
     */
    @PutMapping("/update")
    public Result<Void> update(
            @Validated(ValidGroup.Update.class) @RequestBody Article article) {
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
