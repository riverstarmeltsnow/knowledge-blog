package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.entity.Comment;
import cuit.mygit.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评论控制器（控制层）。
 * <p>
 * 删评论的接口多了一个 {@code operatorId} 参数，因为它需要知道
 * "现在是谁在操作"才能判断有没有权限。本实验还没做登录会话，
 * 所以由调用方在 URL 上直接带上当前用户ID，比如 {@code ?operatorId=1}。
 * 等以后接了登录功能，这个参数会从会话里自动取，不用再手写。
 */
@RestController
@RequestMapping("/comment")
public class CommentController {

    @Autowired
    private CommentService commentService;

    /**
     * 查某篇文章下的所有评论。
     * <p>
     * GET /comment/article/1
     *
     * @param articleId 文章ID
     * @return 评论列表，最新的在前
     */
    @GetMapping("/article/{articleId}")
    public Result<List<Comment>> listByArticle(@PathVariable("articleId") Integer articleId) {
        return Result.success(commentService.listByArticle(articleId));
    }

    /**
     * 查某个用户发过的所有评论。
     * <p>
     * GET /comment/user/1
     *
     * @param userId 用户ID
     * @return 评论列表
     */
    @GetMapping("/user/{userId}")
    public Result<List<Comment>> listByUser(@PathVariable("userId") Integer userId) {
        return Result.success(commentService.listByUser(userId));
    }

    /**
     * 发表评论。
     * <p>
     * POST /comment/add
     * 请求体：{@code {"content":"写得好","userId":2,"articleId":1}}
     *
     * @param comment 从请求体 JSON 转换来的评论对象
     * @return 发表成功的评论（带自增ID）
     */
    @PostMapping("/add")
    public Result<Comment> add(@RequestBody Comment comment) {
        return Result.success(commentService.add(comment));
    }

    /**
     * 删除评论。
     * <p>
     * DELETE /comment/5?operatorId=2
     *
     * @param id         要删的评论ID
     * @param operatorId 操作人ID（当前登录用户）
     * @return 不带数据的成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id,
                               @RequestParam("operatorId") Integer operatorId) {
        commentService.delete(id, operatorId);
        return Result.success(null);
    }
}
