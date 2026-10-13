package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.common.ValidGroup;
import cuit.mygit.entity.Tag;
import cuit.mygit.service.TagService;
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
 * 文章标签控制器（控制层）。
 * <p>
 * 这一层管的是"标签本身"的增删改查（比如新增一个叫 Java 的标签）。
 * "给某篇文章贴某个标签"属于文章和标签之间的关系，
 * 放在 {@link ArticleController} 里（{@code /article/{id}/tag/{tagId}}）。
 * <p>
 * 这几个接口的权限和对分类的处理一致：查是公开的，增删改只放行管理员。
 * 注意别和 {@code /article/{id}/tag/{tagId}} 搞混 ——
 * 那个改的是"文章内容"，由文章作者本人或管理员操作；
 * 这里改的是"标签本身"，标签不属于任何人，只能管理员动。
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
     * POST /tag/add?operatorId=1
     * 请求体：{@code {"name":"Java"}}
     *
     * @param operatorId 操作人ID（当前登录用户），必须是管理员
     */
    @PostMapping("/add")
    public Result<Tag> add(
            @Validated(ValidGroup.Create.class) @RequestBody Tag tag,
            @RequestParam("operatorId") Integer operatorId) {
        return Result.success(tagService.add(tag, operatorId));
    }

    /**
     * 修改标签名称。
     * <p>
     * PUT /tag/update
     * 请求体必须带 tagId。
     * <p>
     * 分组用 Update：新增时 ID 是数据库生成的，提交时还没有；
     * 修改时必须带上 ID，否则不知道改哪一条。规则见 {@link ValidGroup}。
     */
    @PutMapping("/update")
    public Result<Void> update(
            @Validated(ValidGroup.Update.class) @RequestBody Tag tag,
            @RequestParam("operatorId") Integer operatorId) {
        tagService.update(tag, operatorId);
        return Result.success(null);
    }

    /**
     * 删除标签。
     * <p>
     * DELETE /tag/3?operatorId=1
     * <p>
     * 标签被贴在某些文章上也没关系 —— 会连带把那些关联一起清掉，
     * 文章本身不受影响。
     *
     * @param operatorId 操作人ID（当前登录用户），必须是管理员
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id,
                               @RequestParam("operatorId") Integer operatorId) {
        tagService.delete(id, operatorId);
        return Result.success(null);
    }
}
