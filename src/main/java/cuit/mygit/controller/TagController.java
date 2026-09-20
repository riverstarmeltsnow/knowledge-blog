package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.entity.Tag;
import cuit.mygit.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 文章标签控制器（控制层）。
 * <p>
 * 这一层管的是"标签本身"的增删改查（比如新增一个叫 Java 的标签）。
 * "给某篇文章贴某个标签"属于文章和标签之间的关系，
 * 放在 {@link ArticleController} 里（{@code /article/{id}/tag/{tagId}}）。
 */
@RestController
@RequestMapping("/tag")
public class TagController {

    @Autowired
    private TagService tagService;

    /**
     * 查询所有标签。
     * <p>
     * GET /tag/list
     */
    @GetMapping("/list")
    public Result<List<Tag>> list() {
        return Result.success(tagService.listAll());
    }

    /**
     * 按ID查标签。
     * <p>
     * GET /tag/1
     */
    @GetMapping("/{id}")
    public Result<Tag> getById(@PathVariable("id") Integer id) {
        return Result.success(tagService.getById(id));
    }

    /**
     * 新增标签。
     * <p>
     * POST /tag/add
     * 请求体：{@code {"name":"Java"}}
     */
    @PostMapping("/add")
    public Result<Tag> add(@RequestBody Tag tag) {
        return Result.success(tagService.add(tag));
    }

    /**
     * 修改标签名称。
     * <p>
     * PUT /tag/update
     * 请求体必须带 tagId。
     */
    @PutMapping("/update")
    public Result<Void> update(@RequestBody Tag tag) {
        tagService.update(tag);
        return Result.success(null);
    }

    /**
     * 删除标签。
     * <p>
     * DELETE /tag/3
     * <p>
     * 标签被贴在某些文章上也没关系 —— 会连带把那些关联一起清掉，
     * 文章本身不受影响。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id) {
        tagService.delete(id);
        return Result.success(null);
    }
}
