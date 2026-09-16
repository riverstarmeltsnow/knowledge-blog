# -*- coding: utf-8 -*-
"""
生成个人知识博客系统的用例图（标准 UML 画法）
- 小人     = 参与者（游客 / 注册用户 / 管理员）
- 椭圆     = 用例（一个椭圆就是一个功能）
- 大矩形   = 系统边界（框内是系统提供的功能，框外是使用系统的人）
输出：用例图.png（与本脚本同目录）
"""
import os
import matplotlib
matplotlib.use('Agg')          # 不弹窗，直接存文件
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, Ellipse, Circle
import matplotlib.font_manager as fm

# ---------- 中文字体（Windows 自带微软雅黑）----------
FONT = fm.FontProperties(fname='C:/Windows/Fonts/msyh.ttc')
plt.rcParams['axes.unicode_minus'] = False

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

# ---------- 配色（与 ER 图、功能模块图保持一致）----------
DEEP = '#1a3d6d'
UC_FILL = '#eaf3fc'      # 用例椭圆填充
UC_EDGE = '#2e6da4'      # 用例椭圆边框
LINE_C = '#7d8fa5'       # 连线灰


fig, ax = plt.subplots(figsize=(13.5, 9.5))


def actor(x, y, text):
    """
    画参与者小人。
    (x, y) 是身体中心点：头在上，腿在下，名字在脚底下。
    """
    c = DEEP
    ax.add_patch(Circle((x, y + 0.72), 0.24, facecolor='white',
                        edgecolor=c, linewidth=1.8, zorder=4))          # 头
    ax.plot([x, x], [y + 0.48, y - 0.05], color=c, linewidth=1.8, zorder=4)   # 身体
    ax.plot([x - 0.36, x + 0.36], [y + 0.30, y + 0.30],
            color=c, linewidth=1.8, zorder=4)                                # 手臂
    ax.plot([x - 0.30, x], [y - 0.55, y - 0.05], color=c, linewidth=1.8, zorder=4)  # 左腿
    ax.plot([x + 0.30, x], [y - 0.55, y - 0.05], color=c, linewidth=1.8, zorder=4)  # 右腿
    ax.text(x, y - 0.88, text, ha='center', va='center', fontproperties=FONT,
            fontsize=12.5, fontweight='bold', color=c, zorder=4)


def usecase(cx, cy, text, w=2.8, h=0.8):
    """画用例椭圆"""
    ax.add_patch(Ellipse((cx, cy), w, h, facecolor=UC_FILL,
                         edgecolor=UC_EDGE, linewidth=1.5, zorder=3))
    ax.text(cx, cy, text, ha='center', va='center', fontproperties=FONT,
            fontsize=10, color=DEEP, zorder=4)


def line(p1, p2):
    """参与者到用例的连线（zorder 最低，会被图形遮住中间部分）"""
    ax.plot([p1[0], p2[0]], [p1[1], p2[1]],
            color=LINE_C, linewidth=1.2, zorder=1)


# ============================================================
# 布局坐标
# ============================================================
BOX_X0, BOX_Y0 = 3.2, 0.3          # 系统边界框左下角
BOX_W, BOX_H = 9.6, 10.9           # 系统边界框宽高

COL_LEFT = 5.4                     # 左列（游客 + 注册用户）用例的中心 x
COL_RIGHT = 10.4                   # 右列（管理员）用例的中心 x

A_TOURIST = (1.4, 9.4)             # 游客
A_USER = (1.4, 3.6)                # 注册用户
A_ADMIN = (14.6, 7.7)              # 管理员

# ---------- 用例清单：(中心x, 中心y, 文字) ----------
TOURIST_UC = [
    (COL_LEFT, 9.9, '浏览文章'),
    (COL_LEFT, 9.0, '搜索文章'),
    (COL_LEFT, 8.1, '查看文章详情'),
    (COL_LEFT, 7.2, '注册账号'),
]

USER_UC = [
    (COL_LEFT, 6.2, '登录'),
    (COL_LEFT, 5.35, '发表文章'),
    (COL_LEFT, 4.5, '编辑自己的文章'),
    (COL_LEFT, 3.65, '删除自己的文章'),
    (COL_LEFT, 2.8, '发表评论'),
    (COL_LEFT, 1.95, '删除自己的评论'),
    (COL_LEFT, 1.1, '管理个人资料'),
]

ADMIN_UC = [
    (COL_RIGHT, 9.5, '管理用户'),
    (COL_RIGHT, 8.6, '管理分类'),
    (COL_RIGHT, 7.7, '管理标签'),
    (COL_RIGHT, 6.8, '删除任意文章'),
    (COL_RIGHT, 5.9, '删除任意评论'),
]

# ---------- 先画所有连线（zorder 最低）----------
for uc in TOURIST_UC:
    line(A_TOURIST, uc[:2])
for uc in USER_UC:
    line(A_USER, uc[:2])
for uc in ADMIN_UC:
    line(A_ADMIN, uc[:2])

# ---------- 系统边界框 ----------
ax.add_patch(Rectangle((BOX_X0, BOX_Y0), BOX_W, BOX_H,
                       facecolor='none', edgecolor=DEEP, linewidth=2, zorder=2))
ax.text(BOX_X0 + BOX_W / 2, BOX_Y0 + BOX_H - 0.5, '个人知识博客系统',
        ha='center', va='center', fontproperties=FONT,
        fontsize=13.5, fontweight='bold', color=DEEP, zorder=4)

# ---------- 画用例椭圆 ----------
for cx, cy, text in TOURIST_UC + USER_UC + ADMIN_UC:
    usecase(cx, cy, text)

# ---------- 画参与者 ----------
actor(*A_TOURIST, '游客')
actor(*A_USER, '注册用户')
actor(*A_ADMIN, '管理员')

# ---------- 标题与说明 ----------
ax.text(8.0, 11.75, '个人知识博客系统  用例图', ha='center', va='center',
        fontproperties=FONT, fontsize=17, fontweight='bold', color=DEEP)
ax.text(0.3, 0.15, '说明：注册用户只能操作"自己的"文章和评论；管理员可以操作任意文章和评论。',
        ha='left', va='center', fontproperties=FONT, fontsize=10, color='#777777')

# ---------- 画布范围 ----------
ax.set_xlim(0.2, 16.2)
ax.set_ylim(-0.2, 12.1)
ax.axis('off')
plt.tight_layout()

out = os.path.join(OUT_DIR, '用例图.png')
plt.savefig(out, dpi=200, facecolor='white')
print('已生成:', out)
