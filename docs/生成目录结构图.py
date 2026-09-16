# -*- coding: utf-8 -*-
"""
生成个人知识博客系统的工程目录结构图
- 用树形结构展示项目的目录和文件（树线用真线条绘制，保证对齐整齐）
- 分层的核心包（controller/service/mapper/entity/common）用橙色标出，
  右侧加上对应的职责说明
输出：工程目录结构图.png（与本脚本同目录）
"""
import os
import matplotlib
matplotlib.use('Agg')          # 不弹窗，直接存文件
import matplotlib.pyplot as plt
import matplotlib.font_manager as fm

# ---------- 中文字体（Windows 自带微软雅黑）----------
FONT = fm.FontProperties(fname='C:/Windows/Fonts/msyh.ttc')
plt.rcParams['axes.unicode_minus'] = False

OUT_DIR = os.path.dirname(os.path.abspath(__file__))

# ---------- 配色 ----------
C_DIR = '#1a3d6d'      # 普通目录：深蓝
C_PKG = '#b85c00'      # 分层包：橙色（重点标注）
C_FILE = '#707070'     # 文件：灰色
C_NOTE = '#9aa5b1'     # 说明文字：浅灰
C_LINE = '#b8c4d0'     # 树线：浅灰蓝


# ============================================================
# 目录树数据
#   kind: dir=普通目录  pkg=分层包（高亮）  file=文件
#   note: 右侧的职责说明
# ============================================================
TREE = {
    'name': 'Mygit/', 'kind': 'dir', 'children': [
        {'name': 'pom.xml', 'kind': 'file'},
        {'name': 'mvnw  /  mvnw.cmd', 'kind': 'file'},
        {'name': 'src/', 'kind': 'dir', 'children': [
            {'name': 'main/', 'kind': 'dir', 'children': [
                {'name': 'java/cuit/mygit/', 'kind': 'dir', 'children': [
                    {'name': 'MygitApplication.java', 'kind': 'file', 'note': '启动类，程序入口'},
                    {'name': 'common/', 'kind': 'pkg', 'note': '公共类：统一返回格式', 'children': [
                        {'name': 'Result.java', 'kind': 'file'},
                    ]},
                    {'name': 'controller/', 'kind': 'pkg', 'note': '控制层：接收 HTTP 请求', 'children': [
                        {'name': 'ArticleController.java', 'kind': 'file'},
                    ]},
                    {'name': 'service/', 'kind': 'pkg', 'note': '业务层：处理业务逻辑', 'children': [
                        {'name': 'ArticleService.java', 'kind': 'file'},
                        {'name': 'impl/', 'kind': 'dir', 'children': [
                            {'name': 'ArticleServiceImpl.java', 'kind': 'file'},
                        ]},
                    ]},
                    {'name': 'mapper/', 'kind': 'pkg', 'note': '数据访问层：与数据库交互', 'children': [
                        {'name': 'ArticleMapper.java', 'kind': 'file'},
                    ]},
                    {'name': 'entity/', 'kind': 'pkg', 'note': '实体类：对应 5 张业务表', 'children': [
                        {'name': 'User.java', 'kind': 'file'},
                        {'name': 'Article.java', 'kind': 'file'},
                        {'name': 'Category.java', 'kind': 'file'},
                        {'name': 'Tag.java', 'kind': 'file'},
                        {'name': 'Comment.java', 'kind': 'file'},
                    ]},
                ]},
                {'name': 'resources/', 'kind': 'dir', 'children': [
                    {'name': 'application.properties', 'kind': 'file', 'note': '主配置（数据源 + MyBatis）'},
                    {'name': 'application-local.properties', 'kind': 'file', 'note': '本地密码（不提交）'},
                    {'name': 'mapper/', 'kind': 'dir', 'children': [
                        {'name': 'ArticleMapper.xml', 'kind': 'file', 'note': 'SQL 语句'},
                    ]},
                    {'name': 'static/  templates/', 'kind': 'dir'},
                ]},
            ]},
            {'name': 'test/java/cuit/mygit/', 'kind': 'dir', 'children': [
                {'name': 'MygitApplicationTests.java', 'kind': 'file'},
            ]},
        ]},
        {'name': 'docs/', 'kind': 'dir', 'note': '设计文档与图', 'children': [
            {'name': 'ER图.png', 'kind': 'file'},
            {'name': '功能模块图.png', 'kind': 'file'},
            {'name': '用例图.png', 'kind': 'file'},
            {'name': '工程目录结构图.png', 'kind': 'file'},
            {'name': 'schema.sql', 'kind': 'file'},
            {'name': '系统设计说明书.md', 'kind': 'file'},
        ]},
    ]
}


def flatten(node, depth=0, flags=()):
    """
    把嵌套的树摊平成一行行。
    返回 [(depth, flags, 名字, kind, note), ...]

    flags 记录每一层"是不是该层的最后一个子节点"（True=最后一个），
    用来决定树线怎么画：每一层的竖线要不要继续往下延伸。
    """
    lines = []

    # 根节点（depth=0）也要显示，否则第一层子目录看起来是悬空的
    lines.append((depth, flags, node['name'], node.get('kind', 'file'), node.get('note')))

    children = node.get('children', [])
    for i, child in enumerate(children):
        is_last = (i == len(children) - 1)
        lines.extend(flatten(child, depth + 1, flags + (is_last,)))

    return lines


ROWS = flatten(TREE)
N_ROWS = len(ROWS)

# ---------- 布局参数（1 个数据单位 = 1 行高）----------
X0 = 0.40          # 树的左起点
INDENT = 0.40      # 每一级缩进的宽度
FORK_DX = 0.12     # 分叉竖线在所在段里的偏移
TEXT_DX = -0.06    # 横线终点相对文字起点的留白

fig, ax = plt.subplots(figsize=(13, 10))


def seg_x(k):
    """第 k 段竖线的 x 坐标"""
    return X0 + k * INDENT + FORK_DX


for i, (depth, flags, name, kind, note) in enumerate(ROWS):
    y = N_ROWS - i          # 第一行排在最上面

    # -------- 画树线 --------
    # 1) 祖先层的竖线：如果那一层还有后面的兄弟，竖线就继续向下延伸
    for k in range(depth - 1):
        if not flags[k]:
            x = seg_x(k)
            ax.plot([x, x], [y - 0.5, y + 0.5], color=C_LINE, linewidth=1.1, zorder=1)

    # 2) 自己这一层的分叉线
    if depth > 0:
        x = seg_x(depth - 1)
        x_text = X0 + depth * INDENT
        # 横线：从竖线连到文字前面
        ax.plot([x, x_text + TEXT_DX], [y, y], color=C_LINE, linewidth=1.1, zorder=1)
        # 竖线：向上连到上一行；如果不是最后一个兄弟，还要向下连
        y_bottom = y - 0.5 if not flags[-1] else y
        ax.plot([x, x], [y_bottom, y + 0.5], color=C_LINE, linewidth=1.1, zorder=1)

    # -------- 写文字 --------
    if kind == 'pkg':
        color, weight = C_PKG, 'bold'
    elif kind == 'dir':
        color, weight = C_DIR, 'bold'
    else:
        color, weight = C_FILE, 'normal'

    ax.text(X0 + depth * INDENT, y, name, ha='left', va='center',
            fontproperties=FONT, fontsize=11, color=color, fontweight=weight, zorder=3)

    # 分层包和少数关键文件在右侧标注职责
    if note:
        ax.text(6.4, y, '←  ' + note, ha='left', va='center',
                fontproperties=FONT, fontsize=10, color=C_NOTE, zorder=3)

# ---------- 标题与图例 ----------
ax.text(X0, N_ROWS + 1.85, '个人知识博客系统  工程目录结构',
        ha='left', va='center', fontproperties=FONT,
        fontsize=17, fontweight='bold', color=C_DIR)

ax.text(X0, N_ROWS + 1.05,
        '橙色 = 四层架构的核心包     深蓝 = 目录     灰色 = 文件',
        ha='left', va='center', fontproperties=FONT,
        fontsize=10.5, color=C_NOTE)

# ---------- 画布范围 ----------
ax.set_xlim(0, 13)
ax.set_ylim(0, N_ROWS + 2.5)
ax.axis('off')
plt.tight_layout()

out = os.path.join(OUT_DIR, '工程目录结构图.png')
plt.savefig(out, dpi=200, facecolor='white')
print('已生成:', out)
