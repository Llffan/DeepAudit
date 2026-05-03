"""Render a record dict to a 病案首页-style PDF.

Layout: A4 portrait, dense form-grid using ReportLab Platypus tables.
Fonts: bundled CID `STSong-Light` (no .ttf required, Chinese works
out of the box).
"""
from __future__ import annotations

from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from reportlab.platypus import (
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


# Register CID Chinese font once at module load. Idempotent.
_FONT = "STSong-Light"
try:
    pdfmetrics.registerFont(UnicodeCIDFont(_FONT))
except Exception:  # already registered, or rare ttf path issue
    pass


def _styles():
    base = getSampleStyleSheet()["BodyText"]
    title = ParagraphStyle(
        "title", parent=base, fontName=_FONT, fontSize=16, leading=20,
        alignment=1, spaceAfter=2,
    )
    sub = ParagraphStyle(
        "sub", parent=base, fontName=_FONT, fontSize=10, leading=14,
        alignment=1, textColor=colors.grey, spaceAfter=8,
    )
    section = ParagraphStyle(
        "section", parent=base, fontName=_FONT, fontSize=10, leading=14,
        textColor=colors.HexColor("#1f3a68"), spaceBefore=4, spaceAfter=2,
    )
    cell = ParagraphStyle(
        "cell", parent=base, fontName=_FONT, fontSize=8, leading=11,
    )
    return title, sub, section, cell


def _val(rec: dict, code: str) -> str:
    v = rec.get(code)
    if v is None or v == "":
        return ""
    return str(v)


def _kv_row(rec, *items):
    """Build a single table row of [(label, code, span), ...]."""
    cells = []
    for label, code, _span in items:
        cells.append(label)
        cells.append(_val(rec, code))
    return cells


def _build_kv_table(rows: list, *, col_widths: list, label_indices: list[int]):
    """Bordered KV table; label cells get a light grey background."""
    style_cmds = [
        ("FONT", (0, 0), (-1, -1), _FONT, 8),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#888")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]
    for c in label_indices:
        style_cmds.append(("BACKGROUND", (c, 0), (c, -1), colors.HexColor("#eef2f7")))
        style_cmds.append(("FONTSIZE", (c, 0), (c, -1), 8))
    return Table(rows, colWidths=col_widths, style=TableStyle(style_cmds))


def render_pdf(record: dict, hospital_name: str, out_path: str | Path) -> Path:
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)

    doc = SimpleDocTemplate(
        str(out),
        pagesize=A4,
        leftMargin=12 * mm, rightMargin=12 * mm,
        topMargin=10 * mm, bottomMargin=10 * mm,
        title="病案首页",
    )
    title_st, sub_st, section_st, _cell_st = _styles()
    story: list = []

    story.append(Paragraph(record.get("JGMC") or hospital_name, title_st))
    story.append(Paragraph("住 院 病 案 首 页", title_st))
    story.append(Paragraph("Inpatient Medical Record Front Page", sub_st))

    # ---------------- 患者基本信息 ----------------
    story.append(Paragraph("一、 患者基本信息", section_st))
    # 6 cols: label, val, label, val, label, val (3 KV pairs / row)
    p_cols = [22 * mm, 36 * mm, 22 * mm, 36 * mm, 22 * mm, 48 * mm]
    p_rows = [
        _kv_row(record, ("病案号", "BAH", 1), ("姓名", "XM", 1), ("性别", "XB", 1)),
        _kv_row(record, ("年龄", "NL", 1), ("出生日期", "CSRQ", 1), ("民族", "MZ", 1)),
        _kv_row(record, ("国籍", "GJ", 1), ("婚姻", "HY", 1), ("职业", "ZY", 1)),
        _kv_row(record, ("身份证号", "SFZH", 1), ("住院次数", "ZYCS", 1), ("健康卡号", "JKKH", 1)),
        # Wide rows: address spans 5 cols of 22+36+22+36+22+48 = 186mm
        ["现住址", _val(record, "XZZ"), "电话", _val(record, "DH"), "邮编", _val(record, "YB1")],
        ["联系人", _val(record, "LXRXM"), "关系", _val(record, "GX"), "电话", _val(record, "DH1")],
    ]
    story.append(_build_kv_table(p_rows, col_widths=p_cols, label_indices=[0, 2, 4]))
    story.append(Spacer(1, 4 * mm))

    # ---------------- 入出院信息 ----------------
    story.append(Paragraph("二、 入院 / 出院信息", section_st))
    a_cols = [22 * mm, 50 * mm, 22 * mm, 50 * mm, 18 * mm, 24 * mm]
    a_rows = [
        ["入院途径", _val(record, "RYTJ"), "入院时间", _val(record, "RYSJ"), "入院科别", _val(record, "RYKB")],
        ["入院病房", _val(record, "RYBF"), "出院时间", _val(record, "CYSJ"), "出院科别", _val(record, "CYKB")],
        ["出院病房", _val(record, "CYBF"), "实际住院 (天)", _val(record, "SJZY"), "离院方式", _val(record, "LYFS")],
    ]
    story.append(_build_kv_table(a_rows, col_widths=a_cols, label_indices=[0, 2, 4]))
    story.append(Spacer(1, 4 * mm))

    # ---------------- 诊断 ----------------
    story.append(Paragraph("三、 诊断", section_st))
    dx_cols = [40 * mm, 70 * mm, 30 * mm, 46 * mm]
    dx_rows = [
        ["门(急)诊诊断", _val(record, "MZZD_XYZD"), "诊断编码", _val(record, "JBBM")],
        ["出院主要诊断", _val(record, "ZYZD"), "诊断编码", _val(record, "ZYZD_JBBM")],
        ["其他诊断 1", _val(record, "QTZD1"), "编码", _val(record, "ZYZD_JBBM1")],
        ["其他诊断 2", _val(record, "QTZD2"), "编码", _val(record, "ZYZD_JBBM2")],
        ["病理诊断", _val(record, "BLZD"), "病理号", _val(record, "BLH")],
    ]
    story.append(_build_kv_table(dx_rows, col_widths=dx_cols, label_indices=[0, 2]))
    story.append(Spacer(1, 4 * mm))

    # ---------------- 主要手术操作 ----------------
    story.append(Paragraph("四、 主要手术操作", section_st))
    op_cols = [26 * mm, 30 * mm, 30 * mm, 30 * mm, 22 * mm, 48 * mm]
    op_rows = [
        ["编码", _val(record, "SSJCZBM1"), "名称", _val(record, "SSJCZMC1"), "日期", _val(record, "SSJCZRQ1")],
        ["术者", _val(record, "SZ1"), "Ⅰ助", _val(record, "YZ1"), "麻醉方式", _val(record, "MZFS1")],
    ]
    story.append(_build_kv_table(op_rows, col_widths=op_cols, label_indices=[0, 2, 4]))
    story.append(Spacer(1, 4 * mm))

    # ---------------- 医务人员 ----------------
    story.append(Paragraph("五、 医务人员", section_st))
    s_cols = [22 * mm, 30 * mm, 22 * mm, 30 * mm, 22 * mm, 30 * mm, 22 * mm, 8 * mm]
    s_rows = [
        ["科主任", _val(record, "KZR"), "主任医师", _val(record, "ZRYS"),
         "主治医师", _val(record, "ZZYS"), "住院医师", _val(record, "ZYYS")],
    ]
    s_cols2 = [22 * mm, 30 * mm, 22 * mm, 30 * mm]
    s_rows2 = [
        ["责任护士", _val(record, "ZRHS"), "编码员", _val(record, "BMY")],
    ]
    story.append(_build_kv_table(s_rows, col_widths=s_cols, label_indices=[0, 2, 4, 6]))
    story.append(_build_kv_table(s_rows2, col_widths=s_cols2, label_indices=[0, 2]))
    story.append(Spacer(1, 4 * mm))

    # ---------------- 费用大类 ----------------
    story.append(Paragraph("六、 住院费用 (元)", section_st))
    cost_cols = [44 * mm, 30 * mm, 44 * mm, 30 * mm, 18 * mm, 20 * mm]

    def fmt(v):
        if v is None or v == "":
            return ""
        try:
            return f"{float(v):,.2f}"
        except (TypeError, ValueError):
            return str(v)

    cost_rows = [
        ["总费用",                  fmt(record.get("ZFY")),
         "其中：自付金额",          fmt(record.get("ZFJE")),
         "住院天数",                _val(record, "SJZY")],
        ["(1)一般医疗服务费",      fmt(record.get("YLFWF")),
         "(2)一般治疗操作费",       fmt(record.get("ZLCZF")),
         "(3)护理费",               fmt(record.get("HLF"))],
        ["(5)病理诊断费",          fmt(record.get("BLZDF")),
         "(6)实验室诊断费",         fmt(record.get("ZDF")),
         "(7)影像学诊断费",         fmt(record.get("YXXZDF"))],
        ["(9)非手术治疗",          fmt(record.get("FSSZLXMF")),
         "(10)手术治疗费",          fmt(record.get("SSZLF")),
         "  其中：手术费",          fmt(record.get("SSF"))],
        ["  其中：麻醉费",          fmt(record.get("MZF")),
         "(15)西药费",              fmt(record.get("XYF")),
         "(16)中成药费",            fmt(record.get("ZCYF"))],
        ["(26)其他类",              fmt(record.get("QTF")),
         "", "",
         "", ""],
    ]
    story.append(_build_kv_table(cost_rows, col_widths=cost_cols, label_indices=[0, 2, 4]))
    story.append(Spacer(1, 6 * mm))

    # Footer
    foot = ParagraphStyle(
        "foot", fontName=_FONT, fontSize=7, leading=10,
        textColor=colors.grey, alignment=1,
    )
    story.append(Paragraph(
        "本页由 DeepAudit sample-pdf 生成 · "
        "字段定义参 docs/病案首页与质控业务/病案标准.xlsx · 仅用于测试",
        foot,
    ))

    doc.build(story)
    return out
