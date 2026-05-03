实现“模拟数据生成器”是开发病案首页 OCR 识别和质控系统最有效的方法。通过代码生成“标准 PDF”，你可以精确控制每一个像素点的文本内容，从而为你的大模型（LLM）提供完美的“标准答案（Ground Truth）”来进行对比。

以下是详细的实现步骤和 Python 代码框架：

### 核心实现流程

1. **解析 HQMS 字段定义**：将 Excel 里的字段（如 `A01`, `C01`）映射为 Python 字典。
2. **生成模拟数据（Mocking）**：使用 `Faker` 库生成随机姓名、地址，结合 ICD 编码库生成诊断。
3. **注入质控规则错误（QC Traps）**：故意制造逻辑冲突（如：性别男 + 宫外孕诊断）。
4. **PDF 渲染绘制**：使用 `ReportLab` 绘制复杂的病案首页表格并将数据填入。

------

### 第一步：准备环境

你需要安装以下库：

Bash

```
pip install reportlab faker pandas
```

### 第二步：编写数据生成逻辑（Python）

这是一个简化的生成器示例，演示如何构建包含“质控陷阱”的数据。

Python

```
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from faker import Faker
import random

# 初始化
fake = Faker('zh_CN')

# 模拟 HQMS 字段数据
def generate_mock_data(has_error=False):
    data = {
        "hospital_name": "XX省第一人民医院",
        "patient_name": fake.name(),
        "gender": random.choice(["男", "女"]),
        "age": random.randint(1, 90),
        "id_card": fake.ssn(),
        "admission_date": "2023-05-01 10:00:00",
        "discharge_date": "2023-05-10 14:00:00",
        "main_diagnosis": "慢性支气管炎",
        "icd_code": "J42.x00",
        "total_cost": f"{round(random.uniform(5000, 20000), 2)}"
    }
    
    # 注入质控错误陷阱：性别男，但诊断是“顺产”
    if has_error:
        data["gender"] = "男"
        data["main_diagnosis"] = "单胎顺产"
        data["icd_code"] = "Z37.000"
        
    return data

def create_pdf(filename, data):
    # 创建画布 (A4纸)
    c = canvas.Canvas(filename)
    
    # 注册中文字体（必须，否则中文乱码。请确保系统中有黑体或宋体文件）
    # pdfmetrics.registerFont(TTFont('SimSun', 'simsun.ttc'))
    # c.setFont('SimSun', 10)

    # 1. 绘制标题
    c.setFontSize(16)
    c.drawCentredString(300, 800, "住 院 病 案 首 页")
    
    # 2. 绘制基本信息行（模拟表格线条）
    c.setLineWidth(1)
    c.line(50, 780, 550, 780) # 顶线
    c.setFontSize(10)
    
    # 填充数据
    c.drawString(60, 760, f"姓名：{data['patient_name']}")
    c.drawString(160, 760, f"性别：{data['gender']}")
    c.drawString(260, 760, f"年龄：{data['age']}")
    c.drawString(360, 760, f"身份证号：{data['id_card']}")
    
    # 3. 绘制诊断表格区
    c.rect(50, 600, 500, 140) # 诊断框
    c.drawString(60, 720, "主要诊断：")
    c.drawString(120, 720, data['main_diagnosis'])
    c.drawString(350, 720, f"疾病编码：{data['icd_code']}")
    
    # 4. 底部费用
    c.drawString(60, 580, f"住院总费用：{data['total_cost']} 元")

    c.save()

# 生成 5 张标准件和 5 张带逻辑错误的件
for i in range(5):
    create_pdf(f"normal_case_{i}.pdf", generate_mock_data(has_error=False))
    create_pdf(f"error_case_{i}.pdf", generate_mock_data(has_error=True))
```

------

### 第三步：针对“质控校验”的深度实现

为了让你的 AI 能够像专家一样校验，你需要在生成数据时建立一个“校验真值库（Ground Truth JSON）”。

1. **元数据对应**： 在生成 PDF 的同时，保存一个同名的 `.json` 文件。
   - `case_001.pdf` -> `case_001.json`
   - JSON 记录：`{"field": "A01", "value": "男", "is_valid": false, "error_msg": "性别与诊断逻辑不符"}`
2. **设计质控规则库（Logic Matrix）**： 你可以根据 HQMS 标准中的“备注”列，在 Python 代码中预设规则：
   - **年龄规则**：如果 `年龄 < 1`，则 `新生儿出生体重` 必填。
   - **手术规则**：如果有 `手术操作代码`，则 `麻醉方式` 不能为空。
   - **费用规则**：`住院总费用` 必须等于各分项费用之和。
3. **视觉干扰模拟（进阶）**： 如果你的 OCR 系统要模拟真实扫描件，可以利用 Python 的 `imgaug` 或 `opencv` 库：
   - 将生成的 PDF 转为图片。
   - 添加**噪点、旋转 1-2 度、模糊、红色公章水印**。
   - 这能极大提升你大模型在处理真实医院“烂画质”病案时的鲁棒性。

### 第四步：如何对接大模型（LLM）进行质控

当你有了这批 PDF 后，你的开发流程如下：

1. **输入**：将 PDF 发送给视觉大模型（如 GPT-4o 或 Claude 3.5 Sonnet）。

2. **提示词（Prompt）**：

   > "你是一个专业的病案质控专家。请识别附件 PDF 中的所有 HQMS 字段，并根据以下规则进行逻辑校验：1. 男生不能有产科诊断... 请以 JSON 格式输出识别结果和违规项。"

3. **评估**：将大模型输出的结果，与你生成 PDF 时配套的 `Ground Truth JSON` 进行字段比对，计算 **识别准确率** 和 **质控检出率**。

### 资源建议

- **ICD 编码库**：去爬取或下载一份国家临床版 ICD-10 编码表（Excel），作为模拟数据时随机抽取的池子。
- **ReportLab 模板**：你可以先根据你图片中的布局，花半天时间微调 `c.drawString(x, y)` 的坐标，一旦模板调准，后续生成 10 万份不同数据的 PDF 只要几分钟。

你需要我为你写一个更完整的、带有表格线条的 `ReportLab` 绘制模板代码吗？