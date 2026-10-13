package cuit.mygit.controller;

import cuit.mygit.common.Result;
import cuit.mygit.common.ValidGroup;
import cuit.mygit.entity.User;
import cuit.mygit.service.UserService;
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
 * 用户控制器（控制层）。
 * <p>
 * 这一层只干三件事：接收请求、把参数交给 Service、把结果包成 Result 返回。
 * **不写业务判断** —— 用户名重不重复、密码对不对，都是 UserService 的事。
 * <p>
 * {@code @RequestBody} 和 {@code @RequestParam} 的区别：
 * 前者从请求体的 JSON 里取数据（适合对象），
 * 后者从 URL 的问号后面取（适合一两个简单参数）。
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 注册。
     * <p>
     * POST /user/register
     * 请求体：{@code {"username":"hexin","password":"123456","email":"a@b.com"}}
     *
     * @param user 从请求体 JSON 自动转换成的 User 对象
     * @return 注册成功的用户（**不含密码**）
     */
    @PostMapping("/register")
    public Result<User> register(
            @Validated(ValidGroup.Create.class) @RequestBody User user) {
        return Result.success(userService.register(user));
    }

    /**
     * 登录。
     * <p>
     * POST /user/login?username=hexin&password=123456
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录成功的用户（不含密码）
     */
    @PostMapping("/login")
    public Result<User> login(@RequestParam String username,
                              @RequestParam String password) {
        return Result.success(userService.login(username, password));
    }

    /**
     * 查询所有用户。
     * <p>
     * GET /user/list
     *
     * @return 用户列表（不含密码）
     */
    @GetMapping("/list")
    public Result<List<User>> list() {
        return Result.success(userService.listAll());
    }

    /**
     * 按ID查用户。
     * <p>
     * GET /user/1
     * <p>
     * {@code @PathVariable} 把 URL 里的 {id} 部分取出来当参数。
     *
     * @param id 用户ID
     * @return 用户对象（不含密码）
     */
    @GetMapping("/{id}")
    public Result<User> getById(@PathVariable("id") Integer id) {
        return Result.success(userService.getById(id));
    }

    /**
     * 修改用户资料。
     * <p>
     * PUT /user/update?operatorId=1
     * 请求体必须带 userId，例如：{@code {"userId":1,"email":"new@b.com"}}
     * <p>
     * 只传 email 也可以，其余字段会保留原值。
     * <p>
     * 这里用 Update 分组，所以用户名和密码为空也<b>不会</b>被拦下 ——
     * 它们的"必填"只写在新增分组里。要是用 Create 分组，
     * 上面这个只改邮箱的请求就会被判成"用户名不能为空"直接拒绝，
     * 用户想改个邮箱还得把用户名密码重新填一遍。
     * <p>
     * {@code operatorId} 和 {@code userId} 是两个不同的东西，别搞混：
     * 前者是"<b>谁在操作</b>"，后者是"<b>要改谁</b>"。大多数时候两者相同
     * （改自己的资料），只有管理员改别人资料时才会不一样。
     * 权限判断正是拿这两个值去比：相同就是本人，不同就得看 operatorId 是不是管理员。
     *
     * @param user       要修改的用户
     * @param operatorId 操作人ID（当前登录用户），必须是本人或管理员
     * @return 不带数据的成功结果
     */
    @PutMapping("/update")
    public Result<Void> update(
            @Validated(ValidGroup.Update.class) @RequestBody User user,
            @RequestParam("operatorId") Integer operatorId) {
        userService.update(user, operatorId);
        return Result.success(null);
    }

    /**
     * 删除用户。
     * <p>
     * DELETE /user/2?operatorId=1
     * <p>
     * 能删自己的账号（注销），删别人的要管理员。
     *
     * @param id         用户ID
     * @param operatorId 操作人ID（当前登录用户），必须是本人或管理员
     * @return 不带数据的成功结果
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Integer id,
                               @RequestParam("operatorId") Integer operatorId) {
        userService.delete(id, operatorId);
        return Result.success(null);
    }
}
