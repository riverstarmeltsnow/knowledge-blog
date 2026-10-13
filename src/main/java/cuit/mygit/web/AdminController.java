package cuit.mygit.web;

import cuit.mygit.common.ValidGroup;
import cuit.mygit.entity.Article;
import cuit.mygit.entity.Category;
import cuit.mygit.entity.Tag;
import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.service.ArticleService;
import cuit.mygit.service.CategoryService;
import cuit.mygit.service.TagService;
import cuit.mygit.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台控制器 —— 用 Thymeleaf 模板在服务端拼好整页 HTML 再发出去。
 * <p>
 * <b>和 {@code cuit.mygit.controller} 下那批控制器的区别</b>：
 * <ul>
 *   <li>注解是 {@code @Controller} 而不是 {@code @RestController} ——
 *       返回值是"模板名字符串"，Spring 拿它去找 {@code templates/} 下的文件去渲染，
 *       而不是把返回值当 JSON 写回去。这是两者最关键的一处不同。</li>
 *   <li>页面上的数据在服务器上就拼好了，浏览器拿到的是成品 HTML，
 *       不跑 JS 也能看见内容。</li>
 * </ul>
 * <p>
 * <b>为什么所有"提交"都用 POST，没有 PUT/DELETE</b>：
 * 浏览器的 {@code <form>} 只认 GET 和 POST，写 {@code method="put"} 是无效的，
 * 会被降级成 GET。JSON 接口那边能用 PUT/DELETE，是因为那些请求是 JS 发的，
 * 不受这个限制。绕过去的办法有三个：开 HiddenHttpMethodFilter、用 JS 发请求、
 * 或者干脆把动作写进路径。这里选了第三种 —— 路径最直白，
 * 看 URL 就知道这个表单是干什么的：
 * <pre>
 * POST /admin/articles/3/delete   删除3号文章
 * POST /admin/articles/3          保存对3号文章的修改
 * </pre>
 * <p>
 * <b>身份是怎么记的</b>：静态版后台把"当前登录的是谁"存在浏览器的 localStorage 里，
 * 服务端读不到；这里改用 {@link HttpSession}，选一次身份存进会话，
 * 后续每次请求服务器都知道你是谁。
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    /** 当前身份存在会话里的键名。取名字时加个前缀，免得和框架自己的东西撞上 */
    private static final String SESSION_USER = "currentUser";

    /** 文章列表每页显示几条 */
    private static final int PAGE_SIZE = 8;

    @Autowired
    private ArticleService articleService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TagService tagService;

    @Autowired
    private UserService userService;

    // ============================================================
    // 公共部分
    // ============================================================

    /**
     * 每个页面都要用的数据：身份下拉框里的候选用户。
     * <p>
     * 写成 {@code @ModelAttribute} 方法，Spring 会在调用任何请求处理方法
     * <b>之前</b>先执行它，把返回值塞进模型。省得下面十几个方法各写一遍
     * {@code model.addAttribute("allUsers", ...)}。
     * <p>
     * 每次请求都查一次用户表，看着有点浪费，但用户表就几条记录，
     * 换来的是每个方法都清爽 —— 这个取舍在课程实验里是划算的。
     *
     * @return 全部用户（Service 层已经把密码清掉了）
     */
    @ModelAttribute("allUsers")
    public List<User> allUsers() {
        return userService.listAll();
    }

    /**
     * 读出会话里存的当前身份。
     *
     * @return 当前用户；没选过身份时返回 null（注意是 null 而不是抛异常，
     *         因为"没选身份"是正常状态 —— 这时候只能浏览，不能删改）
     */
    private User currentUser(HttpSession session) {
        return (User) session.getAttribute(SESSION_USER);
    }

    /**
     * 校验登录后要跳回的地址，不合法就退回文章列表。
     * <p>
     * 表单里的 {@code back} 字段是页面带过来的，理论上可以被改成任意地址。
     * 要是拿它直接 redirect，别人就能构造一个"本站跳到钓鱼站"的链接发出去 ——
     * 地址栏开头是正经域名，跳过去却是别人的页面，这种手法叫开放重定向。
     * 只放行本站管理后台下的路径，就堵死了这条路。
     *
     * @param back 表单里传来的目标地址
     * @return 一个确定安全的地址
     */
    private String safeBack(String back) {
        return (back != null && back.startsWith("/admin")) ? back : "/admin/articles";
    }

    /**
     * 把用户列表转成「ID → 用户名」的映射，方便模板按 ID 取名字。
     * <p>
     * 为什么不直接让模板遍历列表去找？因为 Thymeleaf 里没有"查找"这种写法，
     * 得套两层循环加判断，可读性很差。在 Java 这边先做成 Map，
     * 模板里写 {@code ${userNames[article.userId]}} 一行就够了。
     */
    private Map<Integer, String> userNameMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (User u : userService.listAll()) {
            map.put(u.getUserId(), u.getUsername());
        }
        return map;
    }

    /** 把分类列表转成「ID → 分类名」的映射，用途同上 */
    private Map<Integer, String> categoryNameMap() {
        Map<Integer, String> map = new LinkedHashMap<>();
        for (Category c : categoryService.listAll()) {
            map.put(c.getCategoryId(), c.getName());
        }
        return map;
    }

    // ============================================================
    // 身份切换
    // ============================================================

    /**
     * 访问 /admin 时直接进文章管理。
     */
    @GetMapping
    public String index() {
        return "redirect:/admin/articles";
    }

    /**
     * 切换当前身份（右上角那个下拉框）。
     * <p>
     * 存进会话之后**必须重定向**，不能直接返回模板。因为提交身份是个 POST 请求，
     * 如果这个时候直接渲染页面，用户一按 F5 就会"重复提交表单"，
     * 浏览器会弹一个莫名其妙的确认框。重定向之后浏览器地址栏变成了 GET 的地址，
     * 怎么刷新都没事 —— 这个套路叫 PRG（Post-Redirect-Get）。
     *
     * @param userId 选中的用户ID；传空表示"退出身份"
     * @param back   提交完跳回哪个页面，由页面上的 JS 填当前地址
     */
    @PostMapping("/identity")
    public String switchIdentity(
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "back", required = false) String back,
            HttpSession session,
            RedirectAttributes flash) {

        if (userId == null) {
            session.removeAttribute(SESSION_USER);
            flash.addFlashAttribute("okMsg", "已清除身份，现在只能浏览");
        } else {
            User user = userService.getById(userId);
            session.setAttribute(SESSION_USER, user);
            flash.addFlashAttribute("okMsg", "当前身份：" + user.getUsername()
                    + (user.getRole() != null && user.getRole() == 1 ? "（管理员）" : ""));
        }
        return "redirect:" + safeBack(back);
    }

    // ============================================================
    // 文章
    // ============================================================

    /**
     * 文章列表，分页显示。
     *
     * @param page 第几页，从 1 开始；翻过头了 Service 会自己纠正，不会报错
     */
    @GetMapping("/articles")
    public String listArticles(
            @RequestParam(value = "page", defaultValue = "1") int page,
            Model model) {

        model.addAttribute("pageData", articleService.page(page, PAGE_SIZE));
        // 列表里要显示"作者"和"分类"的名字，而 Article 对象里只存着它们的 ID。
        // 这里把两张表读出来做成映射发给页面，模板按 ID 一查就有。
        // （表里数据少，这么写最省事；真实项目里应该让 SQL 直接 JOIN 出来。）
        model.addAttribute("userNames", userNameMap());
        model.addAttribute("categoryNames", categoryNameMap());
        return "admin/articles";
    }

    /**
     * 打开"写新文章"表单。
     */
    @GetMapping("/articles/new")
    public String newArticleForm(Model model, HttpSession session) {
        Article article = new Article();

        // 已经选了身份的话，作者默认就是他 —— 填表时少点一次
        User me = currentUser(session);
        if (me != null) {
            article.setUserId(me.getUserId());
        }

        model.addAttribute("article", article);
        model.addAttribute("allCategories", categoryService.listAll());
        model.addAttribute("allTags", tagService.listAll());
        model.addAttribute("tagIds", List.of());   // 新文章当然还没贴标签
        return "admin/article-form";
    }

    /**
     * 打开"修改文章"表单。
     * <p>
     * <b>注意</b>：{@code getById} 这个接口有副作用 —— 它会把浏览量加一。
     * 所以每次打开编辑页，那篇文章的浏览量都会涨 1，这不是 bug，
     * 是详情接口本身的设计（见 {@code ArticleController#getById} 的说明）。
     */
    @GetMapping("/articles/{id}/edit")
    public String editArticleForm(@PathVariable("id") Integer id, Model model,
                                  HttpSession session) {
        // 详情接口现在要知道"你是谁"：草稿只有作者本人和管理员能打开。
        // 没选身份就传 null，当成游客 —— 已发布的文章照样编辑，草稿会提示不存在
        User me = currentUser(session);
        model.addAttribute("article",
                articleService.getById(id, me == null ? null : me.getUserId()));
        model.addAttribute("allCategories", categoryService.listAll());
        model.addAttribute("allTags", tagService.listAll());
        model.addAttribute("tagIds", articleService.listTags(id).stream()
                .map(Tag::getTagId)
                .toList());
        return "admin/article-form";
    }

    /**
     * 提交新文章。
     * <p>
     * <b>参数校验在这里的写法，和 JSON 接口那边有一处关键区别</b>：
     * <pre>
     * JSON 接口：  @Validated(Create.class) @RequestBody Article article
     * 表单页面：   @Validated(Create.class) @ModelAttribute("article") Article article,
     *              BindingResult binding      ← 必须紧跟其后，中间不能插别的参数
     * </pre>
     * 那个 {@code BindingResult} 参数是**必须**的。少了它，校验失败时 Spring
     * 会直接抛异常，页面根本轮不到渲染，{@code th:errors} 里也就永远是空的。
     * 有了它，校验结果被"接住"放进 binding 里，方法才能照常执行到下面，
     * 由我们来决定"出错了该显示哪个页面"。
     * <p>
     * 另外，失败时**直接返回模板名而不是重定向** —— 重定向会让浏览器重新发一个
     * GET 请求，用户刚填的内容和刚才的报错全都丢光了。
     */
    @PostMapping("/articles")
    public String createArticle(
            @Validated(ValidGroup.Create.class) @ModelAttribute("article") Article article,
            BindingResult binding,
            @RequestParam(value = "tagIds", required = false) List<Integer> tagIds,
            HttpSession session,
            Model model,
            RedirectAttributes flash) {

        if (binding.hasErrors()) {
            return backToArticleForm(model, binding.getErrorCount());
        }

        // 先存文章。这一步失败说明输入有问题（比如分类不存在），
        // 把原因显示在表单上让用户改，别跳走
        Article saved;
        try {
            saved = articleService.add(article);
        } catch (BusinessException e) {
            model.addAttribute("errorMsg", e.getMessage());
            return backToArticleForm(model, 0);
        }

        // 文章已经进库了，贴标签要是出错不该把整件事算作失败 ——
        // 所以单独接住，只提示一句
        try {
            applyTags(saved.getArticleId(), tagIds, session);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", "文章已保存，但标签没贴全：" + e.getMessage());
            return "redirect:/admin/articles";
        }

        flash.addFlashAttribute("okMsg", "已发表《" + saved.getTitle() + "》");
        return "redirect:/admin/articles";
    }

    /**
     * 提交对文章的修改。
     * <p>
     * 这里的 {@code id} 来自地址栏，表单里另外还有一个隐藏的 {@code articleId} 字段。
     * 两个值通常一样，但**以地址栏为准** —— 页面上的隐藏字段是能被改的，
     * 而地址栏里的路径是用户自己点的，拿它当权威来源更安全。
     * <p>
     * 隐藏字段存在的意义只是"让校验通过"：
     * 修改分组下 articleId 是必填的（见 {@code ValidGroup.Update}），
     * 表单里没有这个字段的话，校验会直接判失败。
     */
    @PostMapping("/articles/{id}")
    public String updateArticle(
            @PathVariable("id") Integer id,
            @Validated(ValidGroup.Update.class) @ModelAttribute("article") Article article,
            BindingResult binding,
            @RequestParam(value = "tagIds", required = false) List<Integer> tagIds,
            HttpSession session,
            Model model,
            RedirectAttributes flash) {

        article.setArticleId(id);

        if (binding.hasErrors()) {
            return backToArticleForm(model, binding.getErrorCount());
        }

        // 改文章要判权限（只有作者本人或管理员能改），所以得先知道"你是谁"。
        // 没选身份的话连操作人都没有，没法往下传 —— 和删除那边一个道理
        User me = currentUser(session);
        if (me == null) {
            model.addAttribute("errorMsg", "请先在右上角选择一个身份，再修改文章");
            return backToArticleForm(model, 0);
        }

        try {
            articleService.update(article, me.getUserId());
        } catch (BusinessException e) {
            model.addAttribute("errorMsg", e.getMessage());
            return backToArticleForm(model, 0);
        }

        try {
            applyTags(id, tagIds, session);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", "文章已保存，但标签没改全：" + e.getMessage());
            return "redirect:/admin/articles";
        }

        flash.addFlashAttribute("okMsg", "已保存对《" + article.getTitle() + "》的修改");
        return "redirect:/admin/articles";
    }

    /**
     * 删除文章。
     * <p>
     * 权限校验交给 Service 层做（只有作者本人或管理员能删），
     * 这里只负责"当前没人登录"这种页面级的情况 —— 那种情况下连操作人是谁都不知道，
     * 没法往下传。
     */
    @PostMapping("/articles/{id}/delete")
    public String deleteArticle(@PathVariable("id") Integer id,
                                HttpSession session,
                                RedirectAttributes flash) {

        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再执行删除");
            return "redirect:/admin/articles";
        }

        try {
            // 会连带删掉这篇文章的评论和标签关联，都在 Service 的一个事务里完成
            articleService.delete(id, me.getUserId());
            flash.addFlashAttribute("okMsg", "已删除文章 #" + id);
        } catch (BusinessException e) {
            // 比如"你不是作者也不是管理员" —— 这是正常的业务拒绝，
            // 把原因如实显示出来就行，不用当错误处理
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/articles";
    }

    /**
     * 表单页的收尾工作：把下拉框需要的数据补上。
     * <p>
     * 校验失败要重新渲染表单页时，这些数据也得重新放一遍 ——
     * 每次请求都是一个全新的 Model，上一个请求放进去的东西不会留到下一个请求。
     * 这一点和 Session 正好相反，初学最容易搞混。
     *
     * @param errorCount 校验失败的条数，用来拼一句提示；传 0 表示这次不是校验失败
     */
    private String backToArticleForm(Model model, int errorCount) {
        model.addAttribute("allCategories", categoryService.listAll());
        model.addAttribute("allTags", tagService.listAll());

        // tagIds 可能为 null（用户一个标签都没勾），而模板里要对它调 contains，
        // 所以统一换成空集合，免得模板报空指针
        if (model.getAttribute("tagIds") == null) {
            model.addAttribute("tagIds", List.of());
        }
        if (errorCount > 0) {
            model.addAttribute("errorMsg", "表单里有 " + errorCount + " 处需要修改，请看下面的红色提示");
        }
        return "admin/article-form";
    }

    /**
     * 按页面上的勾选情况，给文章补上新勾的标签、去掉取消勾选的标签。
     * <p>
     * 这里做的是"差集"而不是"先全删再全加"：后者也能得到一样的结果，
     * 但会把没动过的那些标签也删一遍再加回来，中间表的记录白白翻新一轮。
     * <p>
     * 顺便说一句，{@code article_tag} 中间表上没有"顺序"或"添加时间"这类字段，
     * 所以删了再加回来在数据上看不出区别 —— 但换成有这类字段的表就不一样了。
     *
     * @param tagIds 页面上勾选的标签ID；一个都没勾时是 null
     */
    private void applyTags(Integer articleId, List<Integer> tagIds, HttpSession session) {
        User me = currentUser(session);
        if (me == null) {
            // 没选身份就动不了标签 —— Service 层要 operatorId 判权限。
            // 页面上对应的区域这时候也是禁用的，正常操作走不到这里
            return;
        }

        Set<Integer> wanted = tagIds == null ? Set.of() : new HashSet<>(tagIds);
        Set<Integer> current = new HashSet<>();
        for (Tag tag : articleService.listTags(articleId)) {
            current.add(tag.getTagId());
        }

        // 新勾上的：现在没有，加上
        for (Integer tagId : wanted) {
            if (!current.contains(tagId)) {
                articleService.addTag(articleId, tagId, me.getUserId());
            }
        }
        // 取消勾选的：现在有，去掉
        for (Integer tagId : current) {
            if (!wanted.contains(tagId)) {
                articleService.removeTag(articleId, tagId, me.getUserId());
            }
        }
    }

    // ============================================================
    // 分类
    // ============================================================

    /**
     * 分类管理页：上面是新增表单，下面每行能直接改名字、删掉。
     */
    @GetMapping("/categories")
    public String listCategories(Model model) {
        model.addAttribute("categories", categoryService.listAll());
        // 顶部新增表单要绑一个对象。放个空的进去，表单里 th:field 才有东西可绑
        model.addAttribute("category", new Category());
        return "admin/categories";
    }

    /**
     * 新增分类。
     * <p>
     * 校验失败时不重定向，直接把列表页渲回去 —— 这样用户填的内容和错误提示都还在。
     * 顶部表单要绑的 {@code category} 对象，@ModelAttribute 参数已经自动放进模型了。
     */
    @PostMapping("/categories")
    public String createCategory(
            @Validated(ValidGroup.Create.class) @ModelAttribute("category") Category category,
            BindingResult binding,
            HttpSession session,
            Model model,
            RedirectAttributes flash) {

        if (binding.hasErrors()) {
            model.addAttribute("categories", categoryService.listAll());
            return "admin/categories";
        }

        // 分类的增删改只有管理员能做，所以要先知道"你是谁"。
        // 没选身份的话连操作人都没有，没法往下传 ——
        // 和文章那几个处理器一个道理，这里只负责页面级的情况，
        // "选了身份但不是管理员"由 Service 层拒绝
        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再新增分类");
            return "redirect:/admin/categories";
        }

        try {
            categoryService.add(category, me.getUserId());
            flash.addFlashAttribute("okMsg", "已新增分类「" + category.getName() + "」");
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    /**
     * 修改分类名称和描述（仅管理员）。
     */
    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable("id") Integer id,
                                 @ModelAttribute Category category,
                                 HttpSession session,
                                 RedirectAttributes flash) {
        category.setCategoryId(id);

        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再修改分类");
            return "redirect:/admin/categories";
        }

        try {
            categoryService.update(category, me.getUserId());
            flash.addFlashAttribute("okMsg", "已保存分类 #" + id);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    /**
     * 删除分类（仅管理员）。分类底下还有文章时会被 Service 层拦下来。
     */
    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable("id") Integer id,
                                 HttpSession session,
                                 RedirectAttributes flash) {
        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再执行删除");
            return "redirect:/admin/categories";
        }

        try {
            categoryService.delete(id, me.getUserId());
            flash.addFlashAttribute("okMsg", "已删除分类 #" + id);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/categories";
    }

    // ============================================================
    // 标签
    // ============================================================

    /** 标签管理页 */
    @GetMapping("/tags")
    public String listTags(Model model) {
        model.addAttribute("tags", tagService.listAll());
        model.addAttribute("tag", new Tag());
        return "admin/tags";
    }

    /** 新增标签（仅管理员） */
    @PostMapping("/tags")
    public String createTag(
            @Validated(ValidGroup.Create.class) @ModelAttribute("tag") Tag tag,
            BindingResult binding,
            HttpSession session,
            Model model,
            RedirectAttributes flash) {

        if (binding.hasErrors()) {
            model.addAttribute("tags", tagService.listAll());
            return "admin/tags";
        }

        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再新增标签");
            return "redirect:/admin/tags";
        }

        try {
            tagService.add(tag, me.getUserId());
            flash.addFlashAttribute("okMsg", "已新增标签「" + tag.getName() + "」");
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/tags";
    }

    /** 改标签名（仅管理员） */
    @PostMapping("/tags/{id}")
    public String updateTag(@PathVariable("id") Integer id,
                            @ModelAttribute Tag tag,
                            HttpSession session,
                            RedirectAttributes flash) {
        tag.setTagId(id);

        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再修改标签");
            return "redirect:/admin/tags";
        }

        try {
            tagService.update(tag, me.getUserId());
            flash.addFlashAttribute("okMsg", "已保存标签 #" + id);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/tags";
    }

    /** 删标签（仅管理员，会一并清掉它在中间表里的关联） */
    @PostMapping("/tags/{id}/delete")
    public String deleteTag(@PathVariable("id") Integer id,
                            HttpSession session,
                            RedirectAttributes flash) {
        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再执行删除");
            return "redirect:/admin/tags";
        }

        try {
            tagService.delete(id, me.getUserId());
            flash.addFlashAttribute("okMsg", "已删除标签 #" + id);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/tags";
    }

    // ============================================================
    // 用户
    // ============================================================

    /** 用户管理页 */
    @GetMapping("/users")
    public String listUsers(Model model) {
        model.addAttribute("users", userService.listAll());
        model.addAttribute("user", new User());
        return "admin/users";
    }

    /**
     * 注册新用户。
     * <p>
     * 复用 {@code UserService.register}，所以"用户名不能重复"这条规则
     * 和管理后台之外的地方完全一致，不用在这里重写一遍。
     */
    @PostMapping("/users")
    public String createUser(
            @Validated(ValidGroup.Create.class) @ModelAttribute("user") User user,
            BindingResult binding,
            Model model,
            RedirectAttributes flash) {

        if (binding.hasErrors()) {
            model.addAttribute("users", userService.listAll());
            return "admin/users";
        }
        try {
            userService.register(user);
            flash.addFlashAttribute("okMsg", "已新增用户「" + user.getUsername() + "」");
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * 修改用户资料（本人或管理员）。
     * <p>
     * 页面上那个"新密码"输入框留空就表示不改密码 —— 这是 {@code ValidGroup.Update}
     * 分组下密码不做必填校验换来的便利。留空时传过来的是空字符串，
     * 由 Service 层判断成"不修改"。
     * <p>
     * 注意地址栏里的 {@code id}（要改谁）和会话里的身份（谁在操作）是两回事：
     * 改自己的资料时两者相同，管理员改别人的时候才不一样。
     */
    @PostMapping("/users/{id}")
    public String updateUser(@PathVariable("id") Integer id,
                             @ModelAttribute User user,
                             HttpSession session,
                             RedirectAttributes flash) {
        user.setUserId(id);

        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再修改用户资料");
            return "redirect:/admin/users";
        }

        try {
            userService.update(user, me.getUserId());
            flash.addFlashAttribute("okMsg", "已保存用户 #" + id);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /** 删用户（本人或管理员）。该用户还写过文章时会被 Service 层拦下来 */
    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable("id") Integer id,
                             HttpSession session,
                             RedirectAttributes flash) {
        User me = currentUser(session);
        if (me == null) {
            flash.addFlashAttribute("errorMsg", "请先在右上角选择一个身份，再执行删除");
            return "redirect:/admin/users";
        }

        try {
            userService.delete(id, me.getUserId());
            flash.addFlashAttribute("okMsg", "已删除用户 #" + id);
        } catch (BusinessException e) {
            flash.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
