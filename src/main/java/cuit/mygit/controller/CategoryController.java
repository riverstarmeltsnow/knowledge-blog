package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.common.ValidGroup;
import cuit.mygit.entity.Category;
import cuit.mygit.service.CategoryService;
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
 * 文章分类控制器（控制层）。
 * <p>
 * 五个接口刚好对应"增删改查"四件事：add 增、delete 删、update 改、
 * list 和 getById 查。
 * <p>
 * <b>查的两个不用身份，写的三个都要</b>：分类是给所有访客看的公开数据，
 * 浏览页要能随便读；而增删改会动到全站共用的字典表，只放行管理员，
 * 所以那三个接口都要求带 {@code operatorId}。
 */
@RestController
@RequestMapping("/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 查询所有分类。
     * <p>
     * GET /category/list
     */
    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.success(categoryService.listAll());
    }

    /**
     * 按ID查分类。
     * <p>
     * GET /category/1
     */
    @GetMapping("/{id}")
    public Result<Category> getById(@PathVariable("id") Integer id) {
        return Result.success(categoryService.getById(id));
    }

    /**
     * 新增分类。
     * <p>
     * POST /category/add?operatorId=1
     * 请求体：{@code {"name":"学习笔记","description":"课程知识整理"}}
     * <p>
     * 名称重复时会返回 {@code {"code":500,"msg":"分类名称已存在：学习笔记"}}；
     * 操作人不是管理员时返回 {@code 没有权限新增分类（只有管理员可以操作）}。
     *
     * @param operatorId 操作人ID（当前登录用户），必须是管理员
     */
    @PostMapping("/add")
    public Result<Category> add(
            @Validated(ValidGroup.Create.class) @RequestBody Category category,
            @RequestParam("operatorId") Integer operatorId) {
        return Result.success(categoryService.add(category, operatorId));
    }

    /**
     * 修改分类。
     * <p>
     * PUT /category/update
     * 请求体必须带 categoryId。
     * <p>
     * 分组用 Update：新增时 ID 是数据库生成的，提交时还没有；
     * 修改时必须带上 ID，否则不知道改哪一条。规则见 {@link ValidGroup}。
     */
    @PutMapping("/update")
    public Result<Void> update(
            @Validated(ValidGroup.Update.class) @RequestBody Category category,
            @RequestParam("operatorId") Integer operatorId) {
        categoryService.update(category, operatorId);
        return Result.success(null);
    }

    /**
     * 删除分类。
     * <p>
     * DELETE /category/2?operatorId=1
     * <p>
     * 如果该分类下还有文章，会返回失败并提示先处理那些文章。
     *
     * @param operatorId 操作人ID（当前登录用户），必须是管理员
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id,
                               @RequestParam("operatorId") Integer operatorId) {
        categoryService.delete(id, operatorId);
        return Result.success(null);
    }
}
