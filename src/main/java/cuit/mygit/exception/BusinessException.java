package cuit.mygit.exception;

/**
 * 业务异常 —— 业务规则不允许某个操作时抛它。
 * <p>
 * 比如"用户名已存在""不能删除别人写的文章"，这些都不是程序 bug，
 * 而是业务上本来就该拒绝的情况。用这个异常把原因说清楚，
 * 由 {@code GlobalExceptionHandler} 统一接住，转成
 * {@code {"code":500,"msg":"用户名已存在"}} 返回给浏览器。
 * <p>
 * 为什么继承 RuntimeException 而不是 Exception？
 * 因为 RuntimeException 是"非受检异常"，Service 方法不用在签名上写
 * {@code throws BusinessException}，Controller 里也不用写 try-catch，
 * 代码干净很多。
 */
public class BusinessException extends RuntimeException {

    /**
     * @param message 给用户看的错误原因，会用中文写清楚，如"分类名称已存在"
     */
    public BusinessException(String message) {
        super(message);
    }
}
