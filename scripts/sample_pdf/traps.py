"""Quality-control trap injection.

Each trap deliberately mutates a clean record so it triggers a specific
QC rule. Used by the CLI to build a mixed corpus of clean + violated
PDFs for end-to-end testing of the rules engine and LLM extractor.

Trap registry: name -> (rule_code | None, mutator).
- rule_code: the QC rule expected to fire (so ground-truth knows which
  rule should be flagged).
- mutator: a function (record_dict) -> mutation_description; mutates
  in place and returns a short string explaining what was changed.
"""
from __future__ import annotations

from datetime import datetime, timedelta
from typing import Callable

Mutator = Callable[[dict], str]


def trap_R001(rec: dict) -> str:
    """有主手术编码，但术者或手术日期空 (R001 完整性)."""
    if not rec.get("SSJCZBM1"):
        # If no op was generated, force one in so the trap is meaningful.
        rec["SSJCZBM1"] = "47.0901"
        rec["SSJCZMC1"] = "腹腔镜下阑尾切除术"
    rec["SZ1"] = None
    rec["SSJCZRQ1"] = None
    return "cleared SZ1 (术者) and SSJCZRQ1 (手术日期) while keeping SSJCZBM1 set"


def trap_R002(rec: dict) -> str:
    """住院天数与入出院日期不一致 (R002 逻辑)."""
    rec["SJZY"] = (rec.get("SJZY") or 5) + 7
    return f"inflated SJZY (实际住院天数) by 7 so it no longer matches dischargeDate - admissionDate"


def trap_gender_obstetric(rec: dict) -> str:
    """男性 + 产科诊断 (Z37 单胎顺产) -- 跨字段一致性."""
    rec["XB"] = "1 男"
    rec["ZYZD"] = "单胎顺产"
    rec["ZYZD_JBBM"] = "Z37.000"
    rec["MZZD_XYZD"] = "单胎顺产"
    rec["JBBM"] = "Z37.000"
    return "set XB=男 and ZYZD=单胎顺产/Z37.000 (impossible combination)"


def trap_cost_sum_mismatch(rec: dict) -> str:
    """总费用 ≠ 各分项之和 -- 费用合计校验."""
    # Drop a chunk off the total without redistributing
    total = rec.get("ZFY", 0)
    rec["ZFY"] = round(total * 0.6, 2)
    return f"reduced ZFY (总费用) to 60% of itemized sum so totals no longer reconcile"


def trap_age_birth_weight(rec: dict) -> str:
    """年龄 < 1 但缺出生体重 (HQMS 条件必填规则)."""
    rec["NL"] = 0
    rec["BZYZS_NL"] = 5
    rec["XSETZ"] = None
    return "set NL=0 (年龄<1) without filling XSETZ (出生体重) -- expected to be flagged"


def trap_main_dx_pathology_missing(rec: dict) -> str:
    """主诊为 C/D 类肿瘤但缺病理诊断 (HQMS 条件必填)."""
    rec["ZYZD"] = "肺继发恶性肿瘤"
    rec["ZYZD_JBBM"] = "C78.000"
    rec["BLZD"] = None
    rec["BLH"] = None
    return "set main diagnosis to C78 (oncology) without filling BLZD (病理诊断)"


REGISTRY: dict[str, tuple[str | None, Mutator]] = {
    "R001":              ("R001", trap_R001),
    "R002":              ("R002", trap_R002),
    "gender_obstetric":  (None,   trap_gender_obstetric),    # future rule
    "cost_sum":          (None,   trap_cost_sum_mismatch),   # future rule
    "age_birth_weight":  (None,   trap_age_birth_weight),    # future rule
    "main_dx_pathology": (None,   trap_main_dx_pathology_missing),
}

AVAILABLE_TRAPS = list(REGISTRY.keys())


def apply_traps(record: dict, traps: list[str]) -> list[dict]:
    """Apply each named trap in order. Returns ground-truth descriptors."""
    out = []
    for name in traps:
        if name not in REGISTRY:
            raise ValueError(
                f"Unknown trap {name!r}. Available: {sorted(REGISTRY)}"
            )
        rule_code, mutator = REGISTRY[name]
        change = mutator(record)
        out.append({"trap": name, "ruleCode": rule_code, "change": change})
    return out
