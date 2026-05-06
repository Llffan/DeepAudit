"""CLI entry: batch-generate 病案首页 PDFs.

    python -m sample_pdf.cli --count 10 --out-dir data/samples
    python -m sample_pdf.cli --count 5  --out-dir data/samples --error-rate 0.6
    python -m sample_pdf.cli --traps R001 R002 --out-dir data/samples
"""
from __future__ import annotations

import argparse
import random
import sys
from pathlib import Path

from .core import generate_pdf, render_from_fields
from .traps import AVAILABLE_TRAPS


def parse_args(argv: list[str] | None = None):
    p = argparse.ArgumentParser(
        prog="sample_pdf",
        description="生成 病案首页 测试 PDF + 配套 Ground-Truth JSON",
    )
    p.add_argument("--count", type=int, default=1,
                   help="number of PDFs to generate (default: 1)")
    p.add_argument("--out-dir", type=Path, default=Path("data/samples"),
                   help="output directory (default: data/samples)")
    p.add_argument("--seed", type=int, default=None,
                   help="base random seed; per-file seed is base+index")
    p.add_argument("--error-rate", type=float, default=0.0,
                   help="probability that any single PDF gets a random trap "
                        "(0.0 = clean only, 1.0 = always inject)")
    p.add_argument("--traps", nargs="+", choices=AVAILABLE_TRAPS, default=None,
                   help="explicit trap pool to draw from when injecting "
                        "(default: all)")
    p.add_argument("--hospital", type=str, default="DeepAudit 示例医院",
                   help="header hospital name")
    p.add_argument("--prefix", type=str, default="case",
                   help="filename prefix (default: case)")
    p.add_argument("--fields-json", type=str, default=None,
                   help="JSON object of HQMS field values; renders one PDF from DB "
                        "data, bypassing mock generator (ignores --count/--error-rate/--traps)")
    # --fields-json-file 是 --fields-json 的文件版，给 Java 调用方避开 Windows
    # cmd.exe 对 JSON 内嵌双引号的转义破坏（直接传 JSON 字符串在 Linux 没问题，
    # 在 Windows 几乎必坏）。两个参数互斥，--fields-json-file 优先。
    p.add_argument("--fields-json-file", type=Path, default=None,
                   help="Path to a UTF-8 JSON file; preferred over --fields-json on "
                        "Windows callers. Same semantics as --fields-json.")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    args.out_dir.mkdir(parents=True, exist_ok=True)

    # --fields-json[-file] path: render one PDF from pre-populated DB fields.
    # File 形式优先，绕开 Windows 命令行对 JSON 引号的转义破坏。
    if args.fields_json_file or args.fields_json:
        import json as _json
        if args.fields_json_file:
            fields = _json.loads(args.fields_json_file.read_text(encoding="utf-8"))
        else:
            fields = _json.loads(args.fields_json)
        pdf_path = args.out_dir / f"{args.prefix}_db_export.pdf"
        gt = render_from_fields(fields, pdf_path, hospital_name=args.hospital)
        traps_str = ", ".join(t["trap"] for t in gt["traps"]) or "db_export"
        print(f"  [  1/1] {pdf_path.name}  ({traps_str})")
        print(f"\nDone. Wrote 1 PDF + GT JSON to {args.out_dir}/")
        return 0

    pool = args.traps or AVAILABLE_TRAPS
    rng = random.Random(args.seed)

    for i in range(1, args.count + 1):
        traps_for_this: list[str] = []
        if args.error_rate > 0 and rng.random() < args.error_rate:
            traps_for_this = [rng.choice(pool)]

        suffix = traps_for_this[0] if traps_for_this else "clean"
        name = f"{args.prefix}_{i:03d}_{suffix}.pdf"
        pdf_path = args.out_dir / name

        seed = args.seed + i if args.seed is not None else None
        gt = generate_pdf(
            pdf_path,
            seed=seed,
            traps=traps_for_this,
            hospital_name=args.hospital,
        )
        traps_str = ", ".join(t["trap"] for t in gt["traps"]) or "clean"
        print(f"  [{i:3d}/{args.count}] {pdf_path.name}  ({traps_str})")

    print(f"\nDone. Wrote {args.count} PDF(s) + GT JSON(s) to {args.out_dir}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
