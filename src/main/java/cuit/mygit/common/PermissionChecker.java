package cuit.mygit.common;

import cuit.mygit.entity.User;
import cuit.mygit.exception.BusinessException;
import cuit.mygit.mapper.UserMapper;
import org.springframework.stereotype.Component;

/**
 * 写操作的权限校验器。
 * <p>
 * 系统里所有"改数据"的动作都要求请求方说明自己是谁（{@code operatorId}），
 * 由这个类统一判断"这个人有没有资格做这件事"。规则一共两条：
 * <ul>
 *   <li>{@link #checkOwnerOrAdmin} —— <b>本人或管理员</b>。用在有"主人"的数据上：
 *       文章、评论、用户资料。自己写的自己当然能改，管理员能改任何人的。</li>
 *   <li>{@link #checkAdmin} —— <b>仅管理员</b>。用在没有主人的全站共享数据上：
 *       分类、标签。{@code category} 表和 {@code tag} 表里都没有 owner 列，
 *       不存在"我的分类"这种说法，所以只能由管理员动。</li>
 * </ul>
 * <p>
 * <b>为什么单独抽成一个类</b>：这两条规则最早是各写各的 ——
 * {@code ArticleServiceImpl} 和 {@code CommentServiceImpl} 里各有一份逐字相同的
 * 私有 {@code checkPermission}，只能靠注释互相提醒"改一处要记得改另一处"。
 * 现在分类、标签、用户三个模块也要用同一套规则，再复制就是五份了。
 * 收到这里之后，"谁能在什么数据上做什么"这件事全项目只有一个答案。
 * <p>
 * <b>它不是 Spring Security</b>：本项目的 REST 接口没有登录态，
 * 身份是前端从 localStorage 里读出来、作为一个普通查询参数传给后端的。
 * 这意味着"我是管理员"这句话在技术上是可以伪造的 —— 见 README「设计要点」里的说明。
 * 这个类解决的是<b>越权</b>（拿着自己的身份去动别人的数据），
 * 不是<b>冒充</b>（直接声称自己是管理员）。后者要靠真正的认证体系，
 * 超出这个课程项目的范围。
 */
@Component
public class PermissionChecker {

    /**
     * 管理员角色值。
     * <p>
     * 和 {@code user} 表的 role 字段约定一致：1 是管理员，0 是普通用户。
     * 字面量集中在这里，免得散落在各个 Service 里。
     */
    private static final int ROLE_ADMIN = 1;

    /** 查操作人的角色要用到用户表 */
    private final UserMapper userMapper;

    /**
     * 这里用构造器注入而不是字段 {@code @Autowired}，
     * 和同目录下的 {@link MarkdownRenderer} 一致。
     * 好处是依赖写成了 final：这个类离了 UserMapper 就干不了活，
     * 与其允许"先 new 出来、之后再补上"，不如让它在语法上就依赖不上缺。
     * 顺带也让单元测试可以写 {@code new PermissionChecker(userMapper)}。
     */
    public PermissionChecker(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /**
     * 校验"本人或管理员"。
     * <p>
     * 用在有明确主人的数据上（文章、评论、用户资料）。
     *
     * @param operatorId 操作人ID（当前登录用户）
     * @param ownerId    这条数据的主人ID（文章作者、评论作者、被改的用户本人）
     * @param action     要做的动作，用来拼错误提示，如"删除这篇文章"
     * @throws BusinessException 操作人ID为空、操作人不存在、或既不是本人也不是管理员时抛出
     */
    public void checkOwnerOrAdmin(Integer operatorId, Integer ownerId, String action) {
        User operator = requireOperator(operatorId);

        // 情况一：自己的数据，自己当然能改。
        // 放在管理员判断前面，不光是"先想到自己"——
        // 本人这一支不用看 role，短路掉一次比较
        if (operatorId.equals(ownerId)) {
            return;
        }

        // 情况二：管理员能改任何人的
        if (hasAdminRole(operator)) {
            return;
        }

        // 情况三：既不是本人也不是管理员，拒绝
        throw new BusinessException("没有权限" + action + "（只有作者本人和管理员可以操作）");
    }

    /**
     * 校验"必须是管理员"。
     * <p>
     * 用在没有主人的全站共享数据上（分类、标签）。
     *
     * @param operatorId 操作人ID（当前登录用户）
     * @param action     要做的动作，用来拼错误提示，如"新增分类"
     * @throws BusinessException 操作人ID为空、操作人不存在、或操作人不是管理员时抛出
     */
    public void checkAdmin(Integer operatorId, String action) {
        User operator = requireOperator(operatorId);

        if (hasAdminRole(operator)) {
            return;
        }

        throw new BusinessException("没有权限" + action + "（只有管理员可以操作）");
    }

    /**
     * 查一下这个操作人是不是管理员。
     * <p>
     * 前面两个 {@code checkXxx} 是"能不能做这件事"的完整答案，
     * 这个方法回答的是一个更小的问题 —— 留给<b>"本人能做一半、管理员能全做"</b>的场景用。
     * 目前只有一处：用户资料本人可以改，但其中的 {@code role} 字段是身份而不是资料，
     * 只有管理员能动，所以 {@code UserServiceImpl.update} 需要先知道操作人的身份，
     * 再决定是把 role 原样放回去还是照改。
     * <p>
     * <b>注意它不抛"没有权限"</b>：不是管理员是一个正常答案（false），不是错误。
     * 但这仍然会校验操作人本身是否有效 —— ID 为空或查不到人时照抛，
     * 免得调用方拿一个不存在的身份继续往下走。
     *
     * @param operatorId 操作人ID（当前登录用户）
     * @return 是管理员返回 true，普通用户返回 false
     * @throws BusinessException 操作人ID为空、或操作人不存在时抛出
     */
    public boolean isAdmin(Integer operatorId) {
        return hasAdminRole(requireOperator(operatorId));
    }

    /**
     * 两条规则共用的前段：确认"操作人"这个人是真实存在的。
     * <p>
     * {@code operatorId} 为空说明前端没带身份（没登录，或者忘了传参数），
     * 查不到人说明传了个库里没有的ID。这两种情况都轮不到谈权限，
     * 先拒绝掉，免得后面拿着一个 null 去比。
     *
     * @param operatorId 操作人ID
     * @return 库里查出来的操作人对象
     * @throws BusinessException ID 为空、或库里没有这个人时抛出
     */
    private User requireOperator(Integer operatorId) {
        if (operatorId == null) {
            throw new BusinessException("缺少操作人ID");
        }
        User operator = userMapper.selectById(operatorId);
        if (operator == null) {
            throw new BusinessException("操作人不存在：" + operatorId);
        }
        return operator;
    }

    /**
     * 这个人是不是管理员。
     * <p>
     * 用 {@code Integer.valueOf(ROLE_ADMIN).equals(...)} 而不是 {@code ==}：
     * role 在数据库里允许为 NULL，走的是包装类型。
     * 写成 {@code operator.getRole() == 1} 会在 role 为 null 时拆箱抛空指针，
     * 而且包装类型用 {@code ==} 比的是对象引用，超过 127 就不相等了。
     *
     * @param operator 操作人对象
     * @return role 为 1 时返回 true；0、null 或其他值都返回 false
     */
    private boolean hasAdminRole(User operator) {
        return Integer.valueOf(ROLE_ADMIN).equals(operator.getRole());
    }
}
