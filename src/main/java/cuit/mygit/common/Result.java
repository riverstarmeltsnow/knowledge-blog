package cuit.mygit.common;

/**
 * 统一响应结果包装类。
 * <p>
 * 接口返回给前端的数据统一包成这个格式，前端好处理：
 * <pre>
 * { "code": 200, "msg": "操作成功", "data": [ ... ] }
 * </pre>
 *
 * @param <T> 真正要返回的业务数据的类型
 */
public class Result<T> {

    /** 状态码：200 表示成功，500 表示失败 */
    private Integer code;

    /** 提示信息 */
    private String msg;

    /** 业务数据 */
    private T data;

    /**
     * 成功时调用，返回带数据的成功结果
     */
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        return result;
    }

    /**
     * 失败时调用，只带错误提示
     */
    public static <T> Result<T> error(String msg) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMsg(msg);
        return result;
    }

    // ==================== getter / setter ====================

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
