"""Mock 病案首页 record generator.

Produces a dict keyed by HQMS field code, e.g. {'BAH': '20240517001',
'XM': '张三', ...}. Values respect the spec's data type but are not
guaranteed to satisfy every conditional rule -- the rules layer (R001/
R002 et al.) is exactly what the QC system is meant to catch.
"""
from __future__ import annotations

import random
from datetime import date, datetime, timedelta

from faker import Faker

# A small but realistic ICD-10 / ICD-9-CM-3 sample. For the demo PDF we
# rotate through these; the live system uses the full icd_dict table
# loaded by V2 (see plan §4.6).
ICD10_SAMPLES = [
    ("J18.901", "肺炎"),
    ("K35.901", "急性阑尾炎"),
    ("I20.000", "不稳定性心绞痛"),
    ("E11.900", "2型糖尿病"),
    ("J42.x00", "慢性支气管炎"),
    ("I10.x00", "高血压"),
    ("K80.201", "胆囊结石"),
    ("N20.000", "肾结石"),
    ("J44.901", "慢性阻塞性肺疾病"),
    ("Z37.000", "单胎顺产"),  # used for trap injection (gender_obstetric)
    ("S06.901", "颅脑损伤"),
    ("C78.000", "肺继发恶性肿瘤"),
]

ICD9_SAMPLES = [
    ("47.0901", "腹腔镜下阑尾切除术"),
    ("51.2300", "腹腔镜下胆囊切除术"),
    ("36.0700", "经皮冠状动脉支架置入术"),
    ("55.5101", "肾结石碎石术"),
    ("78.5904", "颅骨骨折切开复位术"),
    ("00.4000", "操作 双侧"),
]

DEPARTMENTS = [
    "呼吸内科", "心血管内科", "消化内科", "普外科", "骨科",
    "神经外科", "妇产科", "儿科", "急诊科", "肿瘤科", "泌尿外科",
]

ANESTHESIA = [
    ("1", "全身麻醉"),
    ("2", "椎管内麻醉"),
    ("3", "神经阻滞麻醉"),
    ("4", "局部麻醉"),
    ("5", "复合麻醉"),
]

# RC026 入院途径 codes
ADMISSION_ROUTE = [
    ("1", "急诊"),
    ("2", "门诊"),
    ("3", "其他医疗机构转入"),
    ("9", "其他"),
]

# RC019 离院方式 codes
DISCHARGE_STATUS = [
    ("1", "医嘱离院"),
    ("2", "医嘱转院"),
    ("3", "医嘱转社区/乡镇"),
    ("4", "非医嘱离院"),
    ("5", "死亡"),
    ("9", "其他"),
]

# RC001 性别
GENDER = [("1", "男"), ("2", "女")]

ETHNICITY = ["汉族", "回族", "满族", "壮族", "维吾尔族", "蒙古族"]

MARITAL = [("1", "未婚"), ("2", "已婚"), ("3", "丧偶"), ("4", "离婚"), ("9", "其他")]


def _mask_id(idcard: str) -> str:
    """Display-friendly mask: keep first 6 + last 4."""
    if len(idcard) < 10:
        return idcard
    return idcard[:6] + "********" + idcard[-4:]


def generate_mock_record(seed: int | None = None) -> dict:
    """Return a dict keyed by HQMS code with realistic mock values."""
    fake = Faker("zh_CN")
    if seed is not None:
        random.seed(seed)
        Faker.seed(seed)

    gender_code, gender_label = random.choice(GENDER)
    age = random.randint(1, 88)
    birth_year = date.today().year - age
    birth = date(birth_year, random.randint(1, 12), random.randint(1, 28))

    admit = fake.date_time_between(start_date="-3y", end_date="-1d")
    los = random.randint(1, 21)
    discharge = admit + timedelta(days=los)

    icd10_code, icd10_name = random.choice(ICD10_SAMPLES[:9])  # default avoids Z37 obstetric
    has_op = random.random() < 0.6
    op_code, op_name, op_date, op_operator, op_anes = (None,) * 5
    if has_op:
        op_code, op_name = random.choice(ICD9_SAMPLES)
        op_date = admit + timedelta(days=random.randint(1, max(1, los - 1)))
        op_operator = fake.name()
        op_anes_code, op_anes_label = random.choice(ANESTHESIA)
        op_anes = f"{op_anes_code} {op_anes_label}"

    adm_route_code, adm_route_label = random.choice(ADMISSION_ROUTE)
    dis_status_code, dis_status_label = random.choice(DISCHARGE_STATUS[:3])

    # Costs: lay out so the sum matches total within rounding.
    total = round(random.uniform(3000, 50000), 2)
    breakdown = {
        "YLFWF":  round(total * random.uniform(0.04, 0.08), 2),  # 一般医疗服务费
        "ZLCZF":  round(total * random.uniform(0.02, 0.05), 2),  # 一般治疗操作费
        "HLF":    round(total * random.uniform(0.04, 0.07), 2),  # 护理费
        "BLZDF":  round(total * random.uniform(0.02, 0.06), 2),  # 病理诊断费
        "ZDF":    round(total * random.uniform(0.03, 0.07), 2),  # 实验室诊断费
        "YXXZDF": round(total * random.uniform(0.04, 0.09), 2),  # 影像学诊断费
        "FSSZLXMF": round(total * random.uniform(0.05, 0.10), 2),  # 非手术治疗
        "SSZLF":  round(total * random.uniform(0.0, 0.20), 2) if has_op else 0.0,  # 手术治疗费
        "MZF":    round(total * random.uniform(0.0, 0.05), 2) if has_op else 0.0,
        "SSF":    round(total * random.uniform(0.0, 0.10), 2) if has_op else 0.0,
        "XYF":    round(total * random.uniform(0.10, 0.25), 2),  # 西药费
        "ZCYF":   round(total * random.uniform(0.0, 0.05), 2),   # 中成药费
        "QTF":    round(total * random.uniform(0.01, 0.04), 2),  # 其他费
    }
    # Force totals to actually sum (otherwise R-cost-sum trap fires)
    raw_sum = sum(breakdown.values())
    if raw_sum > 0:
        scale = total / raw_sum
        breakdown = {k: round(v * scale, 2) for k, v in breakdown.items()}

    record = {
        # 机构与基本
        "JGMC":  fake.company() + "医院",
        "BAH":   admit.strftime("%Y%m%d") + str(random.randint(1, 999)).zfill(3),
        "ZYCS":  random.randint(1, 5),
        "XM":    fake.name(),
        "XB":    f"{gender_code} {gender_label}",
        "CSRQ":  birth.strftime("%Y-%m-%d"),
        "NL":    age,
        "GJ":    "中国",
        "MZ":    random.choice(ETHNICITY),
        "HY":    "{0} {1}".format(*random.choice(MARITAL)),
        "ZY":    fake.job()[:8],
        "SFZH":  _mask_id(fake.ssn()),
        "CSD":   fake.province() + fake.city_name(),
        "GG":    fake.province() + fake.city_name(),
        # V6 — 新生儿专属（仅 age==0 时填充，让 age_birth_weight trap 等规则可命中）
        "BZYZS_NL": random.randint(0, 364) if age == 0 else None,
        "XSETZ":    random.randint(2500, 4500) if age == 0 else None,
        "XSERYTZ":  random.randint(2400, 4400) if age == 0 else None,
        "XZZ":   fake.address().split("\n")[0],
        "DH":    fake.phone_number(),
        "YB1":   fake.postcode(),
        "HKDZ":  fake.address().split("\n")[0],
        "YB2":   fake.postcode(),
        "GZDWJDZ": (fake.company() + " " + fake.address().split("\n")[0])[:80],
        "DWDH":  fake.phone_number(),
        "YB3":   fake.postcode(),
        "LXRXM": fake.name(),
        "GX":    "1 配偶",
        "DZ":    fake.address().split("\n")[0],
        "DH1":   fake.phone_number(),
        # 入出院
        "RYTJ":  f"{adm_route_code} {adm_route_label}",
        "RYSJ":  admit.strftime("%Y-%m-%d %H:%M"),
        "RYKB":  random.choice(DEPARTMENTS),
        "RYBF":  f"{random.randint(1, 18)}病区",
        "ZKKB":  None,  # 专科科别：仅转科病例填充；MVP 默认 null 由 R-completeness 类规则覆盖
        "CYSJ":  discharge.strftime("%Y-%m-%d %H:%M"),
        "CYKB":  random.choice(DEPARTMENTS),
        "CYBF":  f"{random.randint(1, 18)}病区",
        "SJZY":  los,
        # 诊断（西医）
        "MZZD_XYZD": icd10_name,
        "JBBM":     icd10_code,
        "ZYZD":     icd10_name,
        "ZYZD_JBBM": icd10_code,
        # V7: 主诊入院病况（HQMS RC014 — 1 有 / 2 临床未确定 / 3 情况不明 / 4 无）
        "XY_RYBQ":  random.choice(["1 有", "1 有", "1 有", "2 临床未确定"]),
        # 出院其他诊断（前 3 条占位）
        "QTZD1":     random.choice(ICD10_SAMPLES)[1] if random.random() < 0.7 else None,
        "ZYZD_JBBM1": None,  # filled below if QTZD1 set
        "QTZD2":     random.choice(ICD10_SAMPLES)[1] if random.random() < 0.4 else None,
        "ZYZD_JBBM2": None,
        # 病理
        "BLZD":     None,
        "BLH":      None,
        # 手术
        "SSJCZBM1": op_code,
        "SSJCZMC1": op_name,
        "SSJCZRQ1": op_date.strftime("%Y-%m-%d %H:%M") if op_date else None,
        "SZ1":      op_operator,
        "YZ1":      fake.name() if has_op else None,
        "MZFS1":    op_anes,
        "MZYS1":    fake.name() if has_op else None,
        # 离院
        "LYFS":     f"{dis_status_code} {dis_status_label}",
        "ZZYJH":    "2 否",
        # 医务人员
        "KZR":      fake.name(),
        "ZRYS":     fake.name(),
        "ZZYS":     fake.name(),
        "ZYYS":     fake.name(),
        "ZRHS":     fake.name(),
        "BMY":      fake.name(),
        # 费用
        "ZFY":      total,
        "ZFJE":     round(total * random.uniform(0.1, 0.5), 2),
        **breakdown,
    }

    # Fill paired diagnosis codes for QTZD1/2 if names exist
    for slot in (1, 2):
        if record.get(f"QTZD{slot}"):
            for code, label in ICD10_SAMPLES:
                if label == record[f"QTZD{slot}"]:
                    record[f"ZYZD_JBBM{slot}"] = code
                    break

    return record
