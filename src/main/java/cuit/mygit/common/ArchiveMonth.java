package cuit.mygit.common;

import java.util.List;

/**
 * 归档接口返回的一个月：<b>月份 + 这个月发布的文章</b>。
 * <p>
 * 分组动作在 Service 层完成，这里只是把分组结果装起来。
 * 前端拿到的是一个已经分好组的数组，直接 for 两层就能画出来，
 * 不用自己按月份字符串去分桶。
 * <p>
 * 返回的数组本身<b>按月份从新到旧</b>排列（2026-03 排在 2026-01 前面），
 * 每个月里面的文章按发布时间从新到旧排列。
 */
public class ArchiveMonth {

    /** 月份，格式 {@code 2026-03} */
    private String month;

    /** 这个月发布的文章，按发布时间从新到旧 */
    private List<ArchiveItem> articles;

    public ArchiveMonth() {
    }

    public ArchiveMonth(String month, List<ArchiveItem> articles) {
        this.month = month;
        this.articles = articles;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public List<ArchiveItem> getArticles() {
        return articles;
    }

    public void setArticles(List<ArchiveItem> articles) {
        this.articles = articles;
    }
}
