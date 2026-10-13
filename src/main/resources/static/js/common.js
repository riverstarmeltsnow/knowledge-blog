/**
 * 公共工具 —— 所有页面都引这一份。
 *
 * 这里只放"每个页面都要用"的东西：请求封装、提示条、时间格式化、身份管理。
 * 某个页面自己才用得上的逻辑，写在那个页面自己的 <script> 里，不要往这塞。
 */

// ============================================================
// 一、请求封装
// ============================================================

/**
 * 调后端接口。
 *
 * 后端所有接口返回的都是同一种格式：{code, msg, data}，
 * code 不是 200 就表示这次操作失败了。这里把它统一转成
 * "成功返回 data、失败抛出异常"，调用方只要 try/catch 一次，
 * 不用每个接口都写一遍 if (res.code !== 200) 的判断。
 *
 * @param {string} method HTTP 方法，如 'GET' / 'POST'
 * @param {string} path   接口路径，如 '/article/list'
 * @param {object} [body] 请求体；GET 请求不用传
 * @returns {Promise<*>} 成功时是后端的 data 字段
 * @throws {Error} 后端返回失败、或连不上服务器时抛出，message 可以直接显示给用户
 */
async function api(method, path, body) {
    const options = { method: method, headers: {} };

    // 只有带请求体的请求才需要设 Content-Type。
    // 给 GET 也设上倒不会报错，但会平白触发一次浏览器的预检请求
    if (body !== undefined && body !== null) {
        options.headers['Content-Type'] = 'application/json;charset=utf-8';
        options.body = JSON.stringify(body);
    }

    let result;
    try {
        const response = await fetch(path, options);
        result = await response.json();
    } catch (e) {
        // 只有网络层出问题才会走到这（后端没启动、网线拔了）。
        // 后端返回 400 或 500 都不算失败 —— fetch 照样正常返回，
        // 只是里面的 code 字段是 500 而已，那是下面那段在处理
        throw new Error('连不上服务器，请确认后端已经启动');
    }

    if (result.code !== 200) {
        throw new Error(result.msg || '请求失败');
    }
    return result.data;
}

/** GET 的简写 */
function get(path) {
    return api('GET', path);
}

/** POST 的简写 */
function post(path, body) {
    return api('POST', path, body);
}

/**
 * 发一个表单格式的 POST（application/x-www-form-urlencoded）。
 *
 * 和 post() 的区别只在请求体怎么编码：
 *   post()     —— JSON 体，服务端用 @RequestBody 接
 *   postForm() —— 表单体，服务端用 @RequestParam 接
 *
 * 登录接口的签名是 @RequestParam，本来靠查询串也能传，
 * 但那样密码会出现在地址栏、浏览器历史和后端访问日志里。
 * 同样的参数放进请求体，服务端照样收得到，路上却干净得多。
 *
 * @param {string} path
 * @param {object} params 形如 {username:'hexin', password:'123456'}
 * @returns {Promise<*>} 成功时是后端的 data 字段
 * @throws {Error} 和 api() 一样，失败时抛出可以直接显示的 message
 */
async function postForm(path, params) {
    const body = new URLSearchParams();
    for (const key in params) {
        body.append(key, params[key]);
    }

    let result;
    try {
        const response = await fetch(path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=utf-8' },
            body: body
        });
        result = await response.json();
    } catch (e) {
        throw new Error('连不上服务器，请确认后端已经启动');
    }

    if (result.code !== 200) {
        throw new Error(result.msg || '请求失败');
    }
    return result.data;
}

/** PUT 的简写 */
function put(path, body) {
    return api('PUT', path, body);
}

/** DELETE 的简写。注意 request 是路径 + 查询串，比如 '/article/3?operatorId=1' */
function del(path) {
    return api('DELETE', path);
}

// ============================================================
// 二、登录状态
//
// 登录页把 POST /user/login 返回的用户写进 localStorage，其它页面都从
// 这里读，刷新和跳页都不会丢。注意它只代表**浏览器这一侧**的状态 ——
// 后端那些 JSON 接口是无状态的，服务端并没有存会话，所以它挡不住
// "手动改 localStorage 冒充别人"。真正的把关始终在后端。
// ============================================================

/** localStorage 里存当前用户的键名 */
const ME_KEY = 'mygit.me';

/**
 * 取当前"登录"的用户。
 *
 * @returns {object|null} 形如 {userId:1, username:'hexin', role:0}；没登录时是 null
 */
function getMe() {
    const raw = localStorage.getItem(ME_KEY);
    return raw ? JSON.parse(raw) : null;
}

/**
 * 设置当前用户。传 null 表示退出。
 */
function setMe(user) {
    if (user) {
        localStorage.setItem(ME_KEY, JSON.stringify(user));
    } else {
        localStorage.removeItem(ME_KEY);
    }
}

/**
 * 退出登录：清掉登录状态，然后回首页。
 *
 * 只清浏览器这头的东西 —— 后端压根没存会话，没什么可清的。
 * 这一点要在演示时讲清楚：这不是"服务端登出"，是无状态接口的必然结果。
 */
function logout() {
    setMe(null);
    location.href = 'index.html';
}

/**
 * 判断当前身份能不能改动某条内容。
 *
 * 规则只有一条：本人可以，管理员可以，其他人不行。
 *
 * ⚠️ 这只是**前端的预判**，用来决定按钮要不要置灰，
 * 目的是少让用户白点一次。真正的把关在后端 ——
 * localStorage 谁都能改，绕过前端直接调接口照样会被后端拦住。
 *
 * @param {number} ownerId 内容作者的 userId
 * @returns {boolean}
 */
function canTouch(ownerId) {
    const me = getMe();
    if (!me) {
        return false;
    }
    return me.userId === ownerId || me.role === 1;
}

/**
 * 判断当前身份是不是管理员。
 *
 * 用在**没有主人**的数据上 —— 分类和标签。这两张表里没有"作者"这一列，
 * 是全站共用的字典数据，所以不存在"本人可以改自己的"这种说法，
 * 增删改一律只有管理员能做。
 *
 * 和 canTouch 的区别就一句话：canTouch 多问了一个"是不是本人"，
 * 这里只问角色。用它之前先想清楚改的东西**有没有主人**。
 *
 * ⚠️ 同样只是**前端的预判**，只为少让人白点一次。
 * 最终裁决在 PermissionChecker（后端），绕开前端直接调接口照样会被拒。
 *
 * @returns {boolean}
 */
function isAdmin() {
    const me = getMe();
    return !!me && me.role === 1;
}

// ============================================================
// 三、界面小工具
// ============================================================

/**
 * 在右下角弹一条提示，两秒多后自动消失。
 *
 * @param {string} message 提示文字
 * @param {string} [type]  'ok' 绿色成功 / 'err' 红色错误；不传是中性灰
 */
function toast(message, type) {
    const box = document.createElement('div');
    box.className = 'toast' + (type ? ' toast-' + type : '');
    box.textContent = message;
    document.body.appendChild(box);

    // 先让它显示出来，下一帧再加动画类，这样过渡效果才会触发
    // （元素刚创建就加过渡，浏览器会把两步合成一步，动画就不见了）
    requestAnimationFrame(() => box.classList.add('toast-show'));

    setTimeout(() => {
        box.classList.remove('toast-show');
        // 等淡出动画放完再删掉元素，否则元素会"啪"地一下消失
        setTimeout(() => box.remove(), 300);
    }, 2600);
}

/**
 * 把 HTML 特殊字符转义掉。
 *
 * **任何来自数据库的文本，插进页面前都要过一遍这个函数。**
 * 文章标题里如果有人写了 <script>alert(1)</script>，
 * 直接塞进 innerHTML 就会被浏览器当代码执行 —— 这就是 XSS。
 * 转义之后它只会原样显示成一串文字。
 *
 * @param {*} text 任意内容，null / undefined 会变成空字符串
 * @returns {string} 可以安全放进 HTML 的字符串
 */
function escapeHtml(text) {
    if (text === null || text === undefined) {
        return '';
    }
    return String(text)
        .replace(/&/g, '&amp;')   // 这个必须放第一个，否则会把下面生成的 & 再转一次
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

/** 把 1 位数补成 2 位：9 → 09 */
function pad2(n) {
    return n < 10 ? '0' + n : String(n);
}

/**
 * 把后端返回的时间显示成人话。
 *
 * 最近一个月内说"3 天前"，更久远的直接显示日期 ——
 * 博客上的时间主要是给人一个"新不新"的感觉，
 * 精确到秒反而没人看。
 *
 * @param {string} iso 形如 '2026-09-20T11:02:12'
 * @returns {string}
 */
function formatTime(iso) {
    if (!iso) {
        return '';
    }
    const time = new Date(iso);
    const seconds = (Date.now() - time.getTime()) / 1000;

    // 电脑时间和服务器时间对不齐时可能算出负数，按"刚刚"处理
    if (seconds < 60) {
        return '刚刚';
    }
    if (seconds < 3600) {
        return Math.floor(seconds / 60) + ' 分钟前';
    }
    if (seconds < 86400) {
        return Math.floor(seconds / 3600) + ' 小时前';
    }
    if (seconds < 86400 * 30) {
        return Math.floor(seconds / 86400) + ' 天前';
    }
    return time.getFullYear() + '-' + pad2(time.getMonth() + 1) + '-' + pad2(time.getDate());
}

/** document.querySelector 的简写 */
function $(selector) {
    return document.querySelector(selector);
}

/** document.querySelectorAll 的简写，返回真数组（NodeList 没有 map/filter） */
function $$(selector) {
    return Array.from(document.querySelectorAll(selector));
}

/**
 * 把查询串拼到 URL 上，顺便处理中文编码。
 *
 * 不编码的话，浏览器虽然也能发出去，但服务端收到的字节可能对不上，
 * 报"Character decoding failed"。encodeURIComponent 会统一按 UTF-8 编码。
 *
 * @param {string} path   '/article/search'
 * @param {object} params 形如 {keyword: '实验'}
 * @returns {string} '/article/search?keyword=%E5%AE%9E%E9%AA%8C'
 */
function withQuery(path, params) {
    const parts = [];
    for (const key in params) {
        const value = params[key];
        // 值为空的参数直接不传，省得后端收到一个空字符串还要判断
        if (value !== undefined && value !== null && value !== '') {
            parts.push(encodeURIComponent(key) + '=' + encodeURIComponent(value));
        }
    }
    return parts.length ? path + '?' + parts.join('&') : path;
}

// ============================================================
// 四、页面上反复出现的小块（卡片、空状态、名字映射）
// ============================================================

/**
 * 把 userId 换成用户名。
 *
 * 为什么要传一个 users 数组进来，而不是在这里自己发请求？
 * 因为调用方往往要连着画十几张卡片，每张都发一次请求就是十几遍。
 * 页面开头请求一次 /user/list 拿全量，之后从数组里找，是这个项目里
 * 处理"字典表"的统一做法。
 *
 * @param {object[]} users 全部用户
 * @param {number} userId
 * @returns {string} 找不到时返回"未知用户"，总比显示个光秃秃的数字强
 */
function userNameOf(users, userId) {
    const user = users.find(u => u.userId === userId);
    return user ? user.username : '未知用户';
}

/**
 * 把 categoryId 换成分类名。
 *
 * @param {object[]} categories 全部分类
 * @param {number} categoryId
 * @returns {string} 没有分类、或分类被删了，都返回空串（页面上就不显示这一块）
 */
function categoryNameOf(categories, categoryId) {
    if (!categoryId) {
        return '';
    }
    const category = categories.find(c => c.categoryId === categoryId);
    return category ? category.name : '';
}

/**
 * 一篇博客文章的卡片。
 *
 * 首页和"分类与标签"页都用它，所以放这儿 —— 两边各写一份的话，
 * 将来改样式得记住改两个地方，很容易漏。
 *
 * @param {object} post 文章对象（不需要正文，列表页不显示正文）
 * @param {object[]} users 全部用户
 * @param {object[]} categories 全部分类
 * @param {number[]} [followedIds] 当前用户关注了谁（一串用户ID）。
 *        传了就在相应作者的卡片上多一个「已关注」小标；不传就没有。
 *        做成可选参数是为了让"分类与标签"页不用改 —— 它不关心关注关系，
 *        继续按三个参数调就行，卡片上自然不出现这个标。
 * @returns {string} HTML 片段
 */
function articleCardHtml(post, users, categories, followedIds) {
    const author = userNameOf(users, post.userId);
    const category = categoryNameOf(categories, post.categoryId);

    // 「已关注」只在首页出现，靠传进来的关注列表判断。
    // 注意 followedIds 可能是 undefined，所以先判它存不存在再调 includes。
    const followed = followedIds && followedIds.includes(post.userId);

    return ''
        + '<a class="post-card" href="post.html?id=' + post.articleId + '">'
        +   '<h2 class="post-title">' + escapeHtml(post.title) + '</h2>'
        +   '<div class="post-meta">'
        +     '<span class="meta-item">' + escapeHtml(author) + '</span>'
        +     (followed ? '<span class="follow-chip">已关注</span>' : '')
        +     (category ? '<span class="cat-chip">' + escapeHtml(category) + '</span>' : '')
        +     '<span class="meta-item">' + formatTime(post.createTime) + '</span>'
        +     '<span class="meta-item">' + (post.viewCount || 0) + ' 次浏览</span>'
        +   '</div>'
        + '</a>';
}

/**
 * 一堆文章卡片的列表。
 *
 * @param {object[]} posts
 * @param {object[]} users
 * @param {object[]} categories
 * @param {number[]} [followedIds] 当前用户关注了谁，见 articleCardHtml
 */
function articleListHtml(posts, users, categories, followedIds) {
    return '<div class="post-list">'
        + posts.map(post => articleCardHtml(post, users, categories, followedIds)).join('')
        + '</div>';
}

/**
 * 空状态："这里啥也没有"的那一块。
 *
 * @param {string} text 主提示
 * @param {string} [hint] 下面那行小字，可以给个下一步的建议
 * @param {string} [icon] 顶上那个符号，默认是个空信箱
 */
function emptyHtml(text, hint, icon) {
    return '<div class="empty">'
        + '<span class="empty-icon">' + (icon || '📭') + '</span>'
        + '<div class="empty-text">' + escapeHtml(text) + '</div>'
        + (hint
            ? '<div class="empty-hint">' + escapeHtml(hint) + '</div>'
            : '')
        + '</div>';
}

// ============================================================
// 五、导航栏上的两个通用控件（搜索框、登录状态）
// ============================================================

/**
 * 给导航栏的搜索框绑定行为：按回车或点按钮，都跳到首页并带上关键字。
 *
 * 不管当前在哪个页面，搜索结果统一在首页显示 ——
 * 每个页面各做一套搜索结果是自找麻烦，结果还得互相对齐。
 */
function initSearchBox() {
    const form = document.querySelector('#searchForm');
    if (!form) {
        return;
    }

    form.addEventListener('submit', event => {
        event.preventDefault();

        const input = document.querySelector('#searchInput');
        const keyword = input.value.trim();
        if (!keyword) {
            toast('请输入要搜索的关键字');
            return;
        }

        // 关键字放进 URL 而不是直接在这里请求：刷新页面结果还在，
        // 也能把搜索结果的地址直接发给别人
        location.href = 'index.html?keyword=' + encodeURIComponent(keyword);
    });
}

/**
 * 往导航栏里填登录状态。
 *
 * 没登录：显示「登录 / 注册」两个入口。
 * 登录了：显示用户名（管理员多挂一个徽章）和「退出」。
 *
 * 每个页面的 init 里都调一次。它读的是 localStorage，所以登录之后
 * 跳回首页，导航栏自己就变成已登录的样子，不用页面之间互相传状态。
 *
 * @param {string} selector 显示登录状态的元素选择器
 */
function renderLoginState(selector) {
    const el = document.querySelector(selector);
    if (!el) {
        return;
    }

    const me = getMe();
    if (!me) {
        el.innerHTML = '<a class="nav-auth-link" href="login.html">登录</a>'
            + '<span class="nav-auth-sep">/</span>'
            + '<a class="nav-auth-link" href="register.html">注册</a>';
        return;
    }

    const roleTag = me.role === 1 ? '<span class="badge badge-admin">管理员</span>' : '';
    el.innerHTML = '<span class="who-name">' + escapeHtml(me.username) + '</span>'
        + roleTag
        + '<button class="nav-auth-out" type="button" onclick="logout()">退出</button>';
}
