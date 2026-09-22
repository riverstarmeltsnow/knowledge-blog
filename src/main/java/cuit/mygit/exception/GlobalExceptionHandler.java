package cuit.mygit.exception;

import cuit.mygit.common.Result;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 把所有 Controller 抛出的异常统一转成 JSON。
 * <p>
 * 为什么需要它？假设没有这个类，Service 层抛出 BusinessException("用户名已存在")，
 * Spring 会返回一个 500 错误页面（一大段 HTML），浏览器那边根本没法处理。
 * <p>
 * {@code @RestControllerAdvice} 的意思是"给所有 Controller 加一层拦截"：
 * 任何 Controller 方法抛异常，都会先走到这里，由对应的方法决定返回什么。
 * 有了它，各个 Controller 里就不用写 try-catch 了，代码干净很多。
 * <p>
 * <b>各处理方法的取舍原则</b>：能说清"是你请求发得不对"的，就把原因
 * 原样告诉调用方；说不出所以然的（空指针、SQL 写错），只回一句笼统的话，
 * 细节写进日志。后者是怕把表名、字段名这些内部结构泄露出去。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 日志对象。
     * <p>
     * 兜底的未知异常必须留下记录 —— 那些异常是程序真的出了 bug，
     * 如果只是往控制台 printStackTrace 一下，日志文件里什么都没有，
     * 事后排查只能靠用户复述"当时点了什么按钮"。
     */
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     * <p>
     * 这类异常是"业务上本来就不允许"，比如"用户名已存在"，
     * 不是程序 bug，所以只把消息原样返回给用户，不用打印堆栈 ——
     * 用户输错一次密码就记一条错误日志的话，真正有价值的报错会被淹没。
     *
     * @param e 业务异常
     * @return {@code {"code":500,"msg":"用户名已存在","data":null}}
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.error(e.getMessage());
    }

    /**
     * 处理参数校验失败 —— 实体类上那些 {@code @NotBlank} / {@code @Size} 没通过。
     * <p>
     * 这是加了 {@code @Validated} 以后最常见的一类错误。抛异常的是 Spring
     * 而不是我们的代码，所以它的默认消息是一长串英文（还带着类名和字段路径），
     * 直接甩给前端没法看。这里改成取注解里写的那些中文提示。
     * <p>
     * 一个请求可能同时好几个字段不合法（标题空着、正文也空着），
     * 所以要把所有提示拼起来一次返回。只报第一条的话，用户改完再提交，
     * 又撞出下一条，来回好几轮才能提交成功。
     *
     * @param e 校验失败异常，里面装着每一条没通过的规则
     * @return 把所有失败原因用中文分号连起来的失败结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationError(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                // 注解上没写 message 的话 getDefaultMessage 会是 null，
                // 拼进字符串会变成"null"，退化成"字段名 不合法"更好
                .map(error -> error.getDefaultMessage() == null
                        ? error.getField() + " 不合法"
                        : error.getDefaultMessage())
                // 同一个提示可能来自不同字段（比如标题和正文的提示恰好一样），去个重
                .distinct()
                .collect(Collectors.joining("；"));

        return Result.error(message.isEmpty() ? "请求参数不合法" : message);
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
     * 处理"少传了必需的请求参数"。
     * <p>
     * 典型场景：调删除接口时忘了带 {@code operatorId}，
     * 或者调搜索接口时没带 {@code keyword}。
     * <p>
     * 把缺的那个参数名直接告诉调用方，比他对着接口文档一行行核对快得多。
     *
     * @param e 缺参数异常
     * @return 指明少了哪个参数的失败 JSON
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return Result.error("缺少必需的参数：" + e.getParameterName());
    }

    /**
     * 处理"参数类型对不上"。
     * <p>
     * 典型场景是在 URL 里写了 {@code /article/abc} 这种非数字的ID，
     * 而接收参数的类型是 Integer，转换时就炸了。
     * <p>
     * 这个提示能让人立刻意识到"哦，ID 那里我填错了"，
     * 而不是对着一句"服务器内部错误"怀疑后端挂了。
     *
     * @param e 类型转换失败异常
     * @return 指明是哪个参数格式不对的失败 JSON
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return Result.error("参数格式不正确：" + e.getName());
    }

    /**
     * 处理 URL 参数解码失败。
     * <p>
     * 场景是这样的：在 Windows 的 cmd 或 Git Bash 里用 curl 发一个带中文的请求，
     * 命令行默认按 GBK 把"实验"两个字编码进 URL，而服务端按 UTF-8 去解，
     * 字节对不上就炸了。报错原文是
     * {@code Character decoding failed. Parameter [keyword] ... has been ignored}。
     * <p>
     * 浏览器不会出这个问题（它一定按 UTF-8 发），用 Postman 选对编码也不会。
     * 主要就是命令行手工测试时容易撞上，给个能看懂的提示省得排查半天。
     * <p>
     * <b>注意</b>：{@code InvalidParameterException} 是 Tomcat 自己的类。
     * 本项目用的是 Spring Boot 内嵌 Tomcat，所以能这么写；
     * 万一以后换成了 Jetty 或 Undertow，这个 import 会编译不过，删掉这个方法即可。
     *
     * @param e 参数解码异常
     * @return 提示检查编码的失败 JSON
     */
    @ExceptionHandler(InvalidParameterException.class)
    public Result<Void> handleInvalidParameter(InvalidParameterException e) {
        return Result.error("请求参数解码失败：URL 里的中文等特殊字符请使用 UTF-8 编码");
    }

    /**
     * 处理"找不到静态资源"。
     * <p>
     * 访问一个不存在的页面、图片或 JS 文件时，Spring 会抛这个异常。
     * 它和"接口报错"完全是两回事 —— 是请求的这个地址本来就没东西，
     * 不是服务端出了问题。
     * <p>
     * 不单独处理的话，它会被下面那个兜底方法接走，变成一句
     * "服务器内部错误"，同时往日志里灌一整屏堆栈。而浏览器请求
     * {@code /favicon.ico} 这种家常便饭也会触发，日志很快就被刷得没法看。
     *
     * <p>
     * HTTP 状态码也设成 404（靠 {@code @ResponseStatus}），而不是默认的 200 ——
     * 这样浏览器和 curl 这些工具一眼就能看出"这东西不存在"，
     * 不用先把 JSON 解析一遍才知道。
     *
     * @param e 资源不存在异常
     * @return HTTP 404，body 是统一格式的 JSON
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        // 只记一行，不记堆栈：地址写错是调用方的常见手滑，
        // 不是需要排查的 bug，堆栈对它一点帮助都没有
        log.warn("找不到静态资源：{}", e.getResourcePath());
        return Result.error(404, "找不到这个资源：" + e.getResourcePath());
    }

    /**
     * 兜底处理其他所有异常。
     * <p>
     * 走到这里的一般是真出了 bug，比如空指针、SQL 写错。
     * 这种要**把堆栈写进日志**，方便我们看是哪一行出的问题；
     * 但返回给浏览器的消息里不带堆栈 —— 堆栈会暴露代码结构，也没人看得懂。
     *
     * @param e 其他异常
     * @return 统一的失败 JSON
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOtherException(Exception e) {
        // 用日志框架而不是 e.printStackTrace()：
        // 前者带时间戳、线程名、日志级别，而且能配置输出到文件；
        // printStackTrace 只往控制台一扔，程序一重启就没了。
        log.error("接口处理时发生未预期的异常", e);
        return Result.error("服务器内部错误，请联系管理员");
    }
}
