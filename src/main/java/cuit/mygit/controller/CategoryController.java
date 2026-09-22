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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文章分类控制器（控制层）。
 * <p>
 * 五个接口刚好对应"增删改查"四件事：add 增、delete 删、update 改、
 * list 和 getById 查。
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
     * POST /category/add
     * 请求体：{@code {"name":"学习笔记","description":"课程知识整理"}}
     * <p>
     * 名称重复时会返回 {@code {"code":500,"msg":"分类名称已存在：学习笔记"}}。
     */
    @PostMapping("/add")
    public Result<Category> add(
            @Validated(ValidGroup.Create.class) @RequestBody Category category) {
        return Result.success(categoryService.add(category));
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
            @Validated(ValidGroup.Update.class) @RequestBody Category category) {
        categoryService.update(category);
        return Result.success(null);
    }

    /**
     * 删除分类。
     * <p>
     * DELETE /category/2
     * <p>
     * 如果该分类下还有文章，会返回失败并提示先处理那些文章。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        categoryService.delete(id);
        return Result.success(null);
    }
}
