package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.service.UserFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关注控制器（控制层）。
 * <p>
 * 为什么单独开一个控制器，而不是把接口挂到 {@code UserController} 上：
 * 这里操作的对象是"关注关系"，不是"用户"。虽然四个接口都和用户有关，
 * 但没有一个是"增删改查某个用户"——关注一个人并不会修改那个用户。
 * 按被操作的资源来分控制器，比按"涉及到的表"来分清楚。
 * <p>
 * 身份的判断方式和项目里其它接口完全一致：靠查询串里带的
 * {@code followerId}，前端从 localStorage 里存的登录信息取出来传过来。
 * 这个项目没有用 Session 或 Spring Security，所以"我是谁"是显式传的。
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    /** 注入业务层 */
    @Autowired
    private UserFollowService userFollowService;

    /*
     * ============================================================
     * 和 ArticleController 里那个提醒是同一个道理：下面两个查接口
     * 的路径是固定文字（/status、/following/xxx），必须写在
     * /{targetId} 前面。
     *
     * 这里其实暂时不会冲突 —— /{targetId} 只映射了 POST 和 DELETE，
     * 两个查接口是 GET，HTTP 方法不同就不会撞。
     * 但以后要是有人加一个 GET /follow/{targetId} 来"查某人的关注情况"，
     * 顺序写反的话 /follow/status 就会被当成 targetId="status" 去转换，
     * 直接报类型转换错误。所以按规矩摆在前面，别留这个雷。
     * ============================================================
     */

    /**
     * 查"我关注了这个人没有"。
     * <p>
     * GET /follow/status?followerId=2&targetId=1
     * <p>
     * 给文章详情页的按钮用：返回 true 就显示"已关注"，false 显示"+ 关注"。
     * <p>
     * {@code followerId} <b>可以不传</b>，不传就当成游客，一律返回 false。
     * 这里和文章详情接口的设计是一致的：游客能正常用这个页面，
     * 只是"关注"这个功能对他不可用，而不是整个页面报错。
     *
     * @param followerId 关注者ID（当前登录用户），不传表示游客
     * @param targetId   被关注者ID
     * @return true 表示已关注
     */
    @GetMapping("/status")
    public Result<Boolean> status(
            @RequestParam(value = "followerId", required = false) Integer followerId,
            @RequestParam("targetId") Integer targetId) {
        return Result.success(userFollowService.isFollowing(followerId, targetId));
    }

    /**
     * 查某个人关注了谁（只返回对方的用户ID）。
     * <p>
     * GET /follow/following/2
     * <p>
     * 首页要用它：一次性拿到整个关注列表，然后给"我关注的作者"写的文章
     * 打上「已关注」标记。如果不提供这个接口，前端就得对每张卡片
     * 单独问一次"这个作者关注了没"，一页十条就是十次请求。
     *
     * @param userId 关注者ID
     * @return 被关注者的用户ID列表；没关注任何人时是空列表
     */
    @GetMapping("/following/{userId}")
    public Result<List<Integer>> following(@PathVariable("userId") Integer userId) {
        return Result.success(userFollowService.listFollowingIds(userId));
    }

    /**
     * 关注某个人。
     * <p>
     * POST /follow/1?followerId=2
     * <p>
     * 路径里放的是<b>被关注的人</b>，查询串里是<b>自己</b>。
     * 这样写是因为"我要关注谁"是这个操作的核心信息，适合放进路径；
     * 而"我是谁"是每个写接口都要带的东西，项目里统一放在查询串
     * （{@code operatorId} 也是这么传的）。
     * <p>
     * 失败会返回 500 + 中文提示，前端直接把 message 弹出来就行。
     * 可能出现的提示有：缺少操作人ID、缺少要关注的用户ID、
     * 操作人不存在、要关注的用户不存在、不能关注自己、已经关注过这个人了。
     *
     * @param targetId   要关注的用户ID
     * @param followerId 关注者ID（当前登录用户）
     * @return 不带数据的成功结果
     */
    @PostMapping("/{targetId}")
    public Result<Void> follow(@PathVariable("targetId") Integer targetId,
                               @RequestParam("followerId") Integer followerId) {
        userFollowService.follow(followerId, targetId);
        return Result.success(null);
    }

    /**
     * 取消关注。
     * <p>
     * DELETE /follow/1?followerId=2
     * <p>
     * 只解除这一条关注关系。对方还在，文章也还在，
     * 只是不再出现在"推荐"里、卡片上的「已关注」标记也不再出现。
     *
     * @param targetId   要取消关注的用户ID
     * @param followerId 关注者ID（当前登录用户）
     * @return 不带数据的成功结果
     */
    @DeleteMapping("/{targetId}")
    public Result<Void> unfollow(@PathVariable("targetId") Integer targetId,
                                 @RequestParam("followerId") Integer followerId) {
        userFollowService.unfollow(followerId, targetId);
        return Result.success(null);
    }
}
