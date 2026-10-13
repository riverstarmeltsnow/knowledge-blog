# -*- coding: utf-8 -*-
"""
生成个人知识博客系统的 E/R 图（Chen 记法）
- 矩形 = 实体
- 菱形 = 联系
- 连线上的数字/字母 = 基数（1、N、M）
输出：ER图.png
"""
import matplotlib
matplotlib.use('Agg')          # 不弹窗，直接存文件
import matplotlib.pyplot as plt
from matplotlib.patches import Rectangle, Polygon, FancyArrowPatch
import matplotlib.font_manager as fm

# ---------- 中文字体（Windows 自带微软雅黑）----------
FONT = fm.FontProperties(fname='C:/Windows/Fonts/msyh.ttc')
plt.rcParams['axes.unicode_minus'] = False

fig, ax = plt.subplots(figsize=(12, 11.5))


def entity(cx, cy, text, dashed=False):
    """画实体：圆角矩形"""
    w, h = 1.7, 0.78
    ax.add_patch(Rectangle(
        (cx - w / 2, cy - h / 2), w, h,
        facecolor='#dae8fc', edgecolor='#1a3d6d', linewidth=1.8,
        linestyle='--' if dashed else '-', zorder=3))
    ax.text(cx, cy, text, ha='center', va='center',
            fontproperties=FONT, fontsize=12, zorder=4, color='#1a3d6d')


def relation(cx, cy, text):
    """画联系：菱形"""
    w, h = 1.5, 0.8
    ax.add_patch(Polygon(
        [(cx, cy + h / 2), (cx + w / 2, cy), (cx, cy - h / 2), (cx - w / 2, cy)],
        facecolor='#ffe6cc', edgecolor='#b85c00', linewidth=1.8, zorder=3))
    ax.text(cx, cy, text, ha='center', va='center',
            fontproperties=FONT, fontsize=10.5, zorder=4, color='#b85c00')


def line(p1, p2):
    """画连线（会被图形遮住中间部分，视觉上就是连到边缘）"""
    ax.plot([p1[0], p2[0]], [p1[1], p2[1]],
            color='#444444', linewidth=1.3, zorder=1)


def card(x, y, text):
    """标注基数"""
    ax.text(x, y, text, ha='center', va='center',
            fontproperties=FONT, fontsize=13, fontweight='bold',
            color='#c00000', zorder=5,
            bbox=dict(boxstyle='circle,pad=0.15', facecolor='white',
                      edgecolor='none', alpha=0.9))


# ---------- 实体坐标 ----------
USER = (0.8, 1.2)
ARTICLE = (4.3, 3.2)
COMMENT = (4.3, 0.4)
CATEGORY = (4.3, 6.2)
TAG = (8.6, 3.6)
ART_TAG = (6.9, 5.7)   # 中间表，用虚线框区分
USER_FOLLOW = (0.8, -2.4)  # 中间表：用户关注关系（放在用户实体正下方）

# ---------- 联系坐标 ----------
R_FABIAO = (2.3, 2.2)      # User 发表 Article
R_YONGYOU = (4.3, 1.8)     # Article 拥有 Comment
R_GUILEI = (4.3, 4.7)      # Category 归类 Article
R_BIAOZHU = (6.4, 3.4)     # Article 标注 Tag
R_PINGLUN = (2.4, 0.7)     # User 发表评论 Comment
R_HUIFU = (6.2, 0.4)       # Comment 回复 Comment（自联系）
R_GUANZHU = (0.8, -0.9)    # User 关注 User（自联系）

# ---------- 先画所有连线（zorder 低，会被图形遮住）----------
line(USER, R_FABIAO);      line(R_FABIAO, ARTICLE)        # 用户-文章
line(USER, R_PINGLUN);     line(R_PINGLUN, COMMENT)       # 用户-评论
line(ARTICLE, R_YONGYOU);  line(R_YONGYOU, COMMENT)       # 文章-评论
line(CATEGORY, R_GUILEI);  line(R_GUILEI, ARTICLE)        # 分类-文章
line(ARTICLE, R_BIAOZHU);  line(R_BIAOZHU, TAG)           # 文章-标签
line(R_BIAOZHU, ART_TAG)                                  # 联系→中间表

# 评论-评论（回复）：自联系，画成实体右侧的一条向外鼓起的弧线。
# 直接画直线的话两端都落在同一个实体上，看不出是"自己连自己"。
ax.add_patch(FancyArrowPatch(
    (5.15, 0.72), (5.15, 0.08),                           # 起止点都在评论实体的右边缘
    connectionstyle='arc3,rad=-1.15',                     # 负数=向右侧鼓出
    arrowstyle='-', color='#444444', linewidth=1.3, zorder=1))
line((5.85, 0.4), R_HUIFU)                                # 弧线顶点 → 菱形

# 用户-用户（关注）：同样是自联系，但这里用"两条边接同一个实体"画法 ——
# 一条从用户框左下角出去，一条从右下角出去，都收进同一个菱形。
# 两端各自标 1 和 N，读作"一个用户关注了多个用户"。
line((0.25, 0.81), (0.05, -0.9))                          # 用户左下角 → 菱形左顶点
line((1.65, 1.05), (1.55, -0.9))                          # 用户右下角 → 菱形右顶点
line((0.8, -1.3), USER_FOLLOW)                            # 菱形下顶点 → 中间表

# ---------- 再画实体 ----------
entity(*USER, '用户\nUSER')
entity(*ARTICLE, '文章\nARTICLE')
entity(*COMMENT, '评论\nCOMMENT')
entity(*CATEGORY, '分类\nCATEGORY')
entity(*TAG, '标签\nTAG')

# 中间表用虚线框，区别于普通实体
entity(*ART_TAG, '文章标签表\narticle_tag', dashed=True)
entity(*USER_FOLLOW, '用户关注表\nuser_follow', dashed=True)

# ---------- 再画联系 ----------
relation(*R_FABIAO, '发表')
relation(*R_YONGYOU, '拥有')
relation(*R_GUILEI, '归类')
relation(*R_BIAOZHU, '标注')
relation(*R_PINGLUN, '评论')
relation(*R_HUIFU, '回复')
relation(*R_GUANZHU, '关注')

# ---------- 标注基数 ----------
card(1.35, 1.95, '1')     # 用户端：一个用户
card(3.25, 2.65, 'N')     # 文章端：多篇文章
card(1.55, 0.68, '1')     # 用户端：一个用户
card(3.3, 0.75, 'N')      # 评论端：多条评论
card(4.3, 2.35, '1')      # 文章端：一篇文章
card(4.3, 1.05, 'N')      # 评论端：多条评论
card(4.3, 5.25, '1')      # 分类端：一个分类
card(4.3, 3.95, 'N')      # 文章端：多篇文章
card(5.3, 3.2, 'M')       # 文章端：多篇文章
card(7.6, 3.5, 'N')       # 标签端：多个标签
card(5.45, 0.92, '1')     # 评论端：一条顶层评论
card(5.45, -0.12, 'N')    # 评论端：多条回复
card(0.15, 0.05, '1')     # 关注者：一个用户
card(1.60, 0.05, 'N')     # 被关注者：多个用户

# ---------- 图例 ----------
ax.text(0.15, 7.0, '个人知识博客系统  E-R 图', fontproperties=FONT,
        fontsize=16, fontweight='bold', color='#1a3d6d')
ax.text(0.15, 6.6, '矩形=实体    菱形=联系    虚线框=多对多拆解出的中间表',
        fontproperties=FONT, fontsize=10, color='#666666')

ax.set_xlim(-0.2, 9.9)
ax.set_ylim(-3.3, 7.9)
ax.axis('off')
plt.tight_layout()
plt.savefig('ER图.png', dpi=200, facecolor='white')
print('已生成 ER图.png')
