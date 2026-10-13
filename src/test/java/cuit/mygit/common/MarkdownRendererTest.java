package cuit.mygit.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarkdownRenderer 的单元测试。
 * <p>
 * 这个类不依赖 Spring 也不依赖数据库，直接 new 出来就能测 ——
 * 因为它没有注入任何东西，构造完就是可用的。
 * <p>
 * 断言用的是 {@code contains} 而不是整串相等：commonmark 输出的换行和
 * 缩进属于实现细节，把它们写死在断言里，将来升个版本就会红一片，
 * 而那种红说明不了任何问题。这里只关心"该有的标签有没有出现"。
 */
class MarkdownRendererTest {

    /** 每个用例都新建一个，反正它没有状态、开销也小 */
    private final MarkdownRenderer renderer = new MarkdownRenderer();

    // ==================== 基本语法 ====================

    @Test
    @DisplayName("标题：# 的数量决定 h1~h6")
    void headings() {
        assertTrue(renderer.toHtml("## 标题").contains("<h2>标题</h2>"));
        assertTrue(renderer.toHtml("# 一级").contains("<h1>一级</h1>"));
    }

    @Test
    @DisplayName("强调：**粗体** 和 *斜体*")
    void emphasis() {
        assertTrue(renderer.toHtml("**粗**").contains("<strong>粗</strong>"));
        assertTrue(renderer.toHtml("*斜*").contains("<em>斜</em>"));
    }

    @Test
    @DisplayName("列表：有序和无序都能渲染")
    void lists() {
        String ul = renderer.toHtml("- 甲\n- 乙");
        assertTrue(ul.contains("<ul>"));
        assertTrue(ul.contains("<li>甲</li>"));

        assertTrue(renderer.toHtml("1. 甲\n2. 乙").contains("<ol>"));
    }

    @Test
    @DisplayName("代码：行内 `code` 和代码块 ``` 都能渲染")
    void code() {
        assertTrue(renderer.toHtml("`x = 1`").contains("<code>x = 1</code>"));

        String block = renderer.toHtml("```\nx = 1\n```");
        assertTrue(block.contains("<pre><code>"));
    }

    @Test
    @DisplayName("引用：> 渲染成 blockquote")
    void blockquote() {
        assertTrue(renderer.toHtml("> 引用").contains("<blockquote>"));
    }

    @Test
    @DisplayName("表格：引了 GFM 扩展才认得 | 表头 | 这种写法")
    void tables() {
        String html = renderer.toHtml("| 姓名 | 年龄 |\n|---|---|\n| 张三 | 20 |");

        // 这条其实在验证"扩展确实挂上了"。扩展没生效的话，
        // 上面那段会被当成普通段落，渲染出来是一行带竖线的文字
        assertTrue(html.contains("<table>"), "表格没渲染出来，扩展可能没挂上：" + html);
        assertTrue(html.contains("<th>姓名</th>"));
        assertTrue(html.contains("<td>张三</td>"));
    }

    // ==================== 两处有意为之的"不像标准 Markdown" ====================

    @Test
    @DisplayName("单个换行渲染成 <br/>，而不是标准 Markdown 的折成空格")
    void softbreakBecomesBr() {
        String html = renderer.toHtml("第一行\n第二行");

        // 这是**故意**偏离 CommonMark 标准的一处。
        // 标准行为下，这两行会渲染成"第一行 第二行"（一个段落）。
        // 本系统在加 Markdown 之前，正文靠 white-space: pre-wrap 保留换行，
        // 换成标准行为的话，所有老文章都会突然挤成一整段。
        assertTrue(html.contains("<br />"), "单换行应该变成 <br/>：" + html);
    }

    // ==================== 安全 ====================

    @Test
    @DisplayName("安全：正文里直接写的 <script> 会被转义成文字，不会变成真标签")
    void rawHtmlIsEscaped() {
        String html = renderer.toHtml("<script>alert(1)</script>");

        // 这是整个类里最重要的一条断言。
        // 渲染结果是我们自己拼进页面发出去的，前端那边再转义也来不及了 ——
        // 所以必须在渲染这一步就把它变成文字
        assertFalse(html.contains("<script>"), "原生 <script> 标签漏出去了：" + html);
        assertTrue(html.contains("&lt;script&gt;"), "应该被转义成实体：" + html);
    }

    @Test
    @DisplayName("安全：img 的 onerror 这类内联事件也进不来")
    void rawHtmlImgIsEscaped() {
        String html = renderer.toHtml("<img src=x onerror=alert(1)>");

        // 断言要落在"标签没了"上，而不是"onerror 这几个字没了"。
        //
        // 转义之后的结果是 <p>&lt;img src=x onerror=alert(1)&gt;</p>：
        // onerror= 这几个字符**照样在**，只不过它是页面上的可见文字，
        // 不再是标签里的一个属性，浏览器不会去执行它。
        // （第一版这里断言 !contains("onerror=")，结果自己把自己测挂了 ——
        //   转义和删除是两回事，转义不会把危险的字删掉，只是让它失效。）
        assertFalse(html.contains("<img"), "原生 <img> 标签漏出去了：" + html);
        assertTrue(html.contains("&lt;img"), "应该被转义成文字：" + html);
    }

    @Test
    @DisplayName("安全：javascript: 链接被过滤掉")
    void javascriptUrlIsSanitized() {
        String html = renderer.toHtml("[点我](javascript:alert(1))");

        // 链接是 Markdown 语法写出来的，转义那条管不着它 ——
        // 标签是我们自己生成的，只是 href 里塞了个危险协议。
        // 靠的是 sanitizeUrls
        assertFalse(html.contains("javascript:"), "javascript: 链接漏出去了：" + html);
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("边界：null 和空白返回空串，不抛异常")
    void blankInput() {
        assertEquals("", renderer.toHtml(null));
        assertEquals("", renderer.toHtml(""));
        assertEquals("", renderer.toHtml("   \n  "));
    }

    @Test
    @DisplayName("边界：纯文本原样保留（老文章没写过 Markdown，也不能变样）")
    void plainTextStillWorks() {
        String html = renderer.toHtml("这就是一句普通的话。");

        assertTrue(html.contains("这就是一句普通的话。"));
    }
}
