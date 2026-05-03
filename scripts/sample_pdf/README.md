# sample_pdf — 病案首页测试 PDF 生成器

按 HQMS 标准（`docs/病案首页与质控业务/病案标准.xlsx`，168 个字段）生成
病案首页 PDF，配套 Ground-Truth JSON。用于：

1. **离线测试 LLM 抽取链路**：拿生成的 PDF 喂 qwen-vl-max，再把抽取
   结果跟 GT JSON 比，得到字段识别准确率。
2. **离线测试规则引擎**：故意注入质控陷阱（trap）→ PDF + GT 标注
   "应触发哪条规则" → 检验 R001/R002/未来规则的检出率。

## 安装

```bash
cd scripts/sample_pdf
pip install -r requirements.txt
```

不需要任何 .ttf 字体 —— ReportLab 内置的 `STSong-Light` (CID 字体)
直接渲染中文，零依赖。

## CLI

```bash
# 1 张干净 PDF
python -m sample_pdf.cli --count 1 --out-dir data/samples

# 10 张，60% 注入随机陷阱
python -m sample_pdf.cli --count 10 --out-dir data/samples --error-rate 0.6

# 5 张，限定从两类陷阱里抽
python -m sample_pdf.cli --count 5 --out-dir data/samples \
    --error-rate 1.0 --traps R001 R002

# 可重现：固定 seed
python -m sample_pdf.cli --count 5 --out-dir data/samples --seed 42 --error-rate 0.5
```

输出到 `data/samples/`：

```
case_001_R001.pdf           ← 注入了 R001 陷阱的样本
case_001_R001.json          ← 配套 Ground-Truth
case_002_clean.pdf          ← 无陷阱
case_002_clean.json
...
```

PDF 和 JSON 都默认被 `.gitignore` 忽略（病案数据不进仓库）。

## 编程 API

```python
from sample_pdf import generate_pdf, AVAILABLE_TRAPS

# 单张干净 PDF
gt = generate_pdf("data/samples/case_001.pdf", seed=1)

# 注入指定陷阱
gt = generate_pdf(
    "data/samples/case_002.pdf",
    seed=2,
    traps=["R001"],
)

# 多陷阱叠加（顺序应用）
gt = generate_pdf(
    "data/samples/case_003.pdf",
    seed=3,
    traps=["R002", "cost_sum"],
)

print(gt["traps"])        # [{'trap': 'R001', 'ruleCode': 'R001', 'change': '...'}]
print(gt["fields"]["XM"]) # 患者姓名
```

## 可用的陷阱（trap registry）

| Trap 名 | 对应规则 | 行为 |
|---|---|---|
| `R001` | R001 完整性 | 保留主手术编码，清空术者 + 手术日期 |
| `R002` | R002 逻辑 | 把住院天数加 7，与日期差不一致 |
| `gender_obstetric` | 未来规则 | 强制男性 + 单胎顺产 (Z37.000) |
| `cost_sum` | 未来规则 | 总费用减到分项之和的 60% |
| `age_birth_weight` | 未来规则 | 年龄=0 但出生体重空 |
| `main_dx_pathology` | 未来规则 | C78 肿瘤主诊但无病理诊断 |

新增陷阱：编辑 `traps.py` 的 `REGISTRY` 字典即可。

## 模块结构

```
scripts/sample_pdf/
  __init__.py        — exports generate_pdf / AVAILABLE_TRAPS
  core.py            — generate_pdf orchestrator
  spec.py            — load HQMS xlsx → {code: FieldSpec}
  mock.py            — generate_mock_record (faker + 字典随机)
  traps.py           — 陷阱注入注册表
  render.py          — ReportLab 渲染（A4 单页 6 节）
  cli.py             — CLI 入口 (python -m sample_pdf.cli)
  requirements.txt
  README.md
```

## 已知限制

- **单页固定布局**：一张 PDF = 一个病人，A4 portrait，约 30 个可见字段。
  HQMS 标准里多发字段（`QTZD1-40`、`SSJCZBM2-41`）目前只渲染前 1-2 条。
- **ICD/手术编码采用样本池**：`mock.py` 内置 ~12 条常见 ICD-10 + 6 条 ICD-9-CM-3。
  Phase 1 ICD 字典灌库后可改为从 DB 抽样。
- **无视觉干扰**：纯净渲染，没加噪点/旋转/水印。如果要测 OCR 鲁棒性，
  后续可在 `render.py` 后挂一个 `imgaug` 处理步骤。
