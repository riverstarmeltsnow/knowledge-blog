package cuit.mygit.common;

import java.util.List;

/**
 * 分页查询的统一返回结构。
 * <p>
 * 分页接口不能只返回"这一页的十条数据"—— 前端还得知道总共多少条、
 * 一共几页、有没有下一页，才能把页码条画出来。这些信息一起装在
 * 这里返回，前端一个对象就够用了，不用再去调别的接口问总数。
 * <p>
 * 返回的 JSON 长这样：
 * <pre>
 * {
 *   "list": [ ... 当前页的十条文章 ... ],
 *   "total": 87,
 *   "page": 2,
 *   "size": 10,
 *   "totalPages": 9,
 *   "hasNext": true,
 *   "hasPrev": true
 * }
 * </pre>
 * <p>
 * 后面三个字段（totalPages / hasNext / hasPrev）是<b>算出来的</b>，
 * 数据库里没有对应的列。放在后端算一次，省得每个调用方各写一遍
 * "总页数 = (总数 + 每页数 - 1) / 每页数" 这种容易写错的式子。
 *
 * @param <T> 这一页数据的类型，比如 {@code Article}
 */
public class PageResult<T> {

    /** 当前页的数据，没有数据时是空集合（不是 null，前端不用额外判空） */
    private List<T> list;

    /** 满足条件的总记录数（不是这一页的条数） */
    private long total;

    /** 当前是第几页，从 1 开始数 */
    private int page;

    /** 每页多少条 */
    private int size;

    /** 总页数 = 总记录数 ÷ 每页条数，向上取整 */
    private int totalPages;

    /** 后面还有没有页 —— 前端用它决定"下一页"按钮要不要置灰 */
    private boolean hasNext;

    /** 前面还有没有页 */
    private boolean hasPrev;

    /**
     * 构造分页结果，顺便把三个派生字段算好。
     * <p>
     * 只留这一个构造方法、而且把计算放在这里，是为了<b>保证这三个值
     * 永远和 list/total/page/size 对得上</b>。如果做成 setter，
     * 就可能在别处被改成矛盾的值（比如 page=1 却 hasPrev=true）。
     *
     * @param list  当前页的数据
     * @param total 总记录数
     * @param page  当前页码（从 1 开始）
     * @param size  每页条数
     */
    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;

        // 向上取整求总页数。写成 (total + size - 1) / size 而不是 total / size，
        // 是因为整数除法会向下取整 —— 87 条每页 10 条时 total / size 得 8，
        // 可实际上第 9 页还有 7 条数据，页码条会少画一页。
        this.totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);

        // 当前页比总页数小，说明后面还有
        this.hasNext = page < totalPages;
        // 第 1 页前面当然没有
        this.hasPrev = page > 1;
    }

    // ==================== getter / setter ====================

    public List<T> getList() {
        return list;
    }

    public void setList(List<T> list) {
        this.list = list;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    /**
     * 注意方法名是 {@code isHasNext} 而不是 {@code hasNext}。
     * <p>
     * Java 对 boolean 的 getter 约定是 is 开头，而字段本身已经叫 hasNext 了，
     * 凑在一起就变成 isHasNext 这个读着别扭的名字。这是没办法的事 ——
     * 如果写成 {@code hasNext()}，Jackson 转 JSON 时会认不出它是属性，
     * 前端就拿不到 hasNext 这个字段了。
     */
    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public boolean isHasPrev() {
        return hasPrev;
    }

    public void setHasPrev(boolean hasPrev) {
        this.hasPrev = hasPrev;
    }
}
