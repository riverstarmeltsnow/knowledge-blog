# -*- coding: utf-8 -*-
"""
生成个人知识博客系统的功能模块图（三层树形结构）
- 第一层：系统名
- 第二层：7 个功能模块
- 第三层：各模块下的具体功能点
输出：功能模块图.png（与本脚本同目录）
"""
import os
import matplotlib
matplotlib.use('Agg')          # 不弹窗，直接存文件
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle
import matplotlib.font_manager as fm

# ---------- 中文字体（Windows 自带微软雅黑）----------
FONT = fm.FontProperties(fname='C:/Windows/Fonts/msyh.ttc')
plt.rcParams['axes.unicode_minus'] = False

# 输出目录 = 本脚本所在目录，这样在哪儿运行都不会存错地方
OUT_DIR = os.path.dirname(os.path.abspath(__file__))

# ---------- 配色（与 ER 图保持一致）----------
DEEP = '#1a3d6d'      # 深蓝：边框、根节点
LIGHT = '#dae8fc'     # 浅蓝：模块框
PALE = '#eef4fb'      # 更浅：功能框
LINE_C = '#8a9bb0'    # 连线灰


fig, ax = plt.subplots(figsize=(16, 6))


def box(cx, cy, w, h, text, fc, ec, tc, fs=11, bold=False, lw=1.6):
    """画一个矩形框，文字居中"""
    ax.add_patch(Rectangle(
        (cx - w / 2, cy - h / 2), w, h,
        facecolor=fc, edgecolor=ec, linewidth=lw, zorder=3))
    ax.text(cx, cy, text, ha='center', va='center', fontproperties=FONT,
            fontsize=fs, color=tc, zorder=4,
            fontweight='bold' if bold else 'normal')


def line(p1, p2, lw=1.3):
    """画连线（画在框下面一层，会被框遮住端点）"""
    ax.plot([p1[0], p2[0]], [p1[1], p2[1]],
            color=LINE_C, linewidth=lw, zorder=1, solid_capstyle='round')


# ============================================================
# 数据定义：模块 → 该模块下的功能点列表
# ============================================================
MODULES = [
    ('用户模块',     ['注册账号', '登录退出', '个人资料管理']),
    ('文章模块',     ['发表文章', '编辑文章', '删除文章', '文章详情']),
    ('分类标签模块', ['分类管理', '标签管理']),
    ('评论模块',     ['发表评论', '删除评论']),
    ('搜索模块',     ['关键字搜索', '结果展示']),
    ('信息流与关注模块', ['热门/最新/推荐', '关注博主', '取消关注', '已关注标记']),
    ('后台管理模块', ['用户管理', '内容管理']),
]

N = len(MODULES)
GAP = 2.4                                   # 模块之间水平间距
XS = [2.0 + i * GAP for i in range(N)]      # 各模块中心 x 坐标
CENTER_X = (XS[0] + XS[-1]) / 2             # 所有模块的横向中心，标题和根节点都对齐它

# ---------- 第一层：系统 ----------
ROOT_Y, ROOT_H = 7.9, 0.85
box(CENTER_X, ROOT_Y, 4.2, ROOT_H, '个人知识博客系统',
    fc=DEEP, ec='#0f2544', tc='white', fs=15, bold=True, lw=2)

# ---------- 第二层：功能模块 ----------
Y_MOD, MOD_H = 6.6, 0.75
BUS_Y = 7.2                                  # 横向母线的高度

for x, (name, _) in zip(XS, MODULES):
    box(x, Y_MOD, 2.2, MOD_H, name,
        fc=LIGHT, ec=DEEP, tc=DEEP, fs=11.5, bold=True)

# 根节点 → 母线 → 各模块框
line((CENTER_X, ROOT_Y - ROOT_H / 2), (CENTER_X, BUS_Y))   # 根往下的竖线
line((XS[0], BUS_Y), (XS[-1], BUS_Y))                # 横向母线
for x in XS:
    line((x, BUS_Y), (x, Y_MOD + MOD_H / 2))         # 母线往下的短竖线

# ---------- 第三层：功能点 ----------
Y0 = 5.6          # 第一个功能框的中心 y
DY = 0.75         # 行距
SUB_H = 0.6       # 功能框高度

for x, (_, subs) in zip(XS, MODULES):
    # 模块框底边 → 第一个功能框顶边（一条长竖线，串联整列）
    line((x, Y_MOD - MOD_H / 2), (x, Y0 + SUB_H / 2))
    for i, sub in enumerate(subs):
        cy = Y0 - i * DY
        box(x, cy, 2.1, SUB_H, sub,
            fc=PALE, ec='#7fa8d4', tc='#33475b', fs=10)
        # 功能框之间补一小段竖线，让这一列视觉上连成一条
        if i > 0:
            line((x, cy + SUB_H / 2), (x, cy + SUB_H / 2 + (DY - SUB_H)))

# ---------- 标题 ----------
ax.text(CENTER_X, 8.75, '个人知识博客系统  功能模块图', ha='center', va='center',
        fontproperties=FONT, fontsize=17, fontweight='bold', color=DEEP)

# ---------- 画布范围 ----------
# 模块数变了这里也跟着变，不然最右边那个模块会被裁掉
ax.set_xlim(XS[0] - 1.8, XS[-1] + 1.8)
ax.set_ylim(2.6, 9.3)
ax.axis('off')
plt.tight_layout()

out = os.path.join(OUT_DIR, '功能模块图.png')
plt.savefig(out, dpi=200, facecolor='white')
print('已生成:', out)
