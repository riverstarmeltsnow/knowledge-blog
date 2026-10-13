package cuit.mygit.common;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Markdown 渲染器 —— 把文章的 Markdown 源码转成 HTML。
 * <p>
 * 文章正文以 <b>Markdown 原文</b>存进数据库（content 字段），
 * 显示的时候才渲染成 HTML。存原文而不是存 HTML，好处是：
 * <ul>
 *   <li>以后想换渲染器、改样式，老文章不用动 —— 原文还在</li>
 *   <li>想在别处复用（导出、生成摘要、全文搜索）都是拿原文处理</li>
 *   <li>编辑器里点"编辑"，拿回来的是用户当初敲的那些字符，而不是一堆 {@code <p>}</li>
 * </ul>
 * <p>
 * <b>为什么在服务端渲染，而不是引一个 marked.js 到前端？</b>
 * 两个原因，第二个更重要：
 * <ol>
 *   <li>省事：不用在页面里再引一个 CDN 上的 js 文件，也就没有
 *       "CDN 挂了页面就白了"这种问题</li>
 *   <li>安全：渲染器的配置（转义、URL 过滤）写在 Java 里，
 *       前端改不了。放前端的话，配置就在用户能改的代码里了</li>
 * </ol>
 * <p>
 * {@code @Component} 让 Spring 启动时创建这一个实例。下面的
 * {@link Parser} 和 {@link HtmlRenderer} 一旦 build 完就不再变化，
 * commonmark-java 说明它们是线程安全的，所以可以放成成员变量共用，
 * 不用每次渲染都新建一个。
 */
@Component
public class MarkdownRenderer {

    /** 解析器：Markdown 文本 → 语法树 */
    private final Parser parser;

    /** 渲染器：语法树 → HTML 字符串 */
    private final HtmlRenderer renderer;

    public MarkdownRenderer() {
        // 把表格扩展同时挂到解析器和渲染器上。
        // 两头都要挂：解析器不认识表格语法的话，源码根本解析不出表格节点，
        // 渲染器再能干也没有东西可渲染
        List<Extension> extensions = List.of(TablesExtension.create());

        this.parser = Parser.builder()
                .extensions(extensions)
                .build();

        this.renderer = HtmlRenderer.builder()
                .extensions(extensions)

                // ---------- 下面三行是安全配置，别删 ----------

                // 把源码里直接写的原生 HTML 转义成文本。
                // 不加这条，用户在正文里写 <script>alert(1)</script>，
                // 渲染出来就是一段真的、会被浏览器执行的脚本 ——
                // 因为这段 HTML 是我们自己拼进页面再发出去的，
                // 前端那边再怎么转义都拦不住。
                // 代价是正文里不能直接写 HTML 标签（会被显示成文字），
                // 对博客来说这个交换划算：Markdown 已经够用了
                .escapeHtml(true)

                // 过滤 url 协议，挡掉 [点我](javascript:alert(1)) 这类链接。
                // 转义管的是"是不是标签"，这条管的是"链接指向哪"
                .sanitizeUrls(true)

                // 单个换行也渲染成 <br/>。
                //
                // CommonMark 标准行为是把段落内的单换行折成空格（源码里的换行
                // 只是排版，不代表显示效果）。但那意味着：在老版本里
                // 直接敲回车分行的文章，加了 Markdown 之后会挤成一整段 ——
                // 内容一个字没改，样子却变了。
                // 这里改成 <br/>，让"回车就是换行"这个直觉成立，
                // 老文章的样子也就保持住了
                .softbreak("<br />\n")

                .build();
    }

    /**
     * 把 Markdown 渲染成 HTML。
     *
     * @param markdown Markdown 原文；null 或空白返回空串
     * @return 渲染好的 HTML 片段
     */
    public String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        Node document = parser.parse(markdown);
        return renderer.render(document);
    }
}
