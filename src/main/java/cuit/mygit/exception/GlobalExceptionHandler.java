package cuit.mygit.exception;

import cuit.mygit.common.Result;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 —— 把所有 Controller 抛出的异常统一转成 JSON。
 * <p>
 * 为什么需要它？假设没有这个类，Service 层抛出 BusinessException("用户名已存在")，
 * Spring 会返回一个 500 错误页面（一大段 HTML），浏览器那边根本没法处理。
 * <p>
 * {@code @RestControllerAdvice} 的意思是"给所有 Controller 加一层拦截"：
 * 任何 Controller 方法抛异常，都会先走到这里，由对应的方法决定返回什么。
 * 有了它，各个 Controller 里就不用写 try-catch 了，代码干净很多。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     * <p>
     * 这类异常是"业务上本来就不允许"，比如"用户名已存在"，
     * 不是程序 bug，所以只把消息原样返回给用户，不用打印堆栈。
     *
     * @param e 业务异常
     * @return {@code {"code":500,"msg":"用户名已存在","data":null}}
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    /**
     * 处理请求体格式错误。
     * <p>
     * 比如 JSON 少了个引号、括号没配对，或者用 Postman/curl 测试时
     * 中文被按 GBK 编码发过来（服务端按 UTF-8 解析就会失败）。
     * <p>
     * 这种错误如果不单独处理，会被下面的兜底方法接走，
     * 提示成"服务器内部错误"—— 明明是调用方发错了，却让人以为是服务端挂了。
     *
     * @param e 请求体解析异常
     * @return 指明原因的失败 JSON
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleJsonError(HttpMessageNotReadableException e) {
        return Result.error("请求体格式错误：请检查 JSON 是否合法，中文请用 UTF-8 编码发送");
    }

    /**
     * 兜底处理其他所有异常。
     * <p>
     * 走到这里的一般是真出了 bug，比如空指针、SQL 写错。
     * 这种要**把堆栈打印到控制台**，方便我们看是哪一行出的问题；
     * 但返回给浏览器的消息里不带堆栈（堆栈会暴露代码结构，也没人看得懂）。
     *
     * @param e 其他异常
     * @return 统一的失败 JSON
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOtherException(Exception e) {
        // 打印到控制台，方便排查问题
        e.printStackTrace();
        return Result.error("服务器内部错误，请联系管理员");
    }
}
