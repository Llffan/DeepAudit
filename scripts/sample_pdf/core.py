"""High-level callable API."""
from __future__ import annotations

import json
from pathlib import Path

from .mock import generate_mock_record
from .render import render_pdf
from .traps import AVAILABLE_TRAPS, apply_traps


def generate_pdf(
    output_pdf: str | Path,
    *,
    seed: int | None = None,
    traps: list[str] | None = None,
    hospital_name: str = "DeepAudit 示例医院",
    output_json: str | Path | None = None,
) -> dict:
    """Generate one sample 病案首页 PDF and a paired ground-truth JSON.

    Parameters
    ----------
    output_pdf:
        Path of the PDF to write (created if missing).
    seed:
        Forwarded to the mock generator for reproducibility.
    traps:
        List of trap names from `AVAILABLE_TRAPS`. Each one mutates the
        record before rendering and is recorded in the ground-truth JSON
        under `traps`. None or [] means a clean record.
    hospital_name:
        Header banner text. Falls back to record's JGMC if generated.
    output_json:
        Where to write the ground-truth JSON. Defaults to the PDF's
        path with `.json` suffix.

    Returns
    -------
    dict: the ground-truth dict that was also written to JSON.
    """
    pdf_path = Path(output_pdf)
    json_path = Path(output_json) if output_json else pdf_path.with_suffix(".json")

    record = generate_mock_record(seed=seed)
    trap_descriptors = apply_traps(record, traps or [])

    render_pdf(record, hospital_name=hospital_name, out_path=pdf_path)

    ground_truth = {
        "pdf": pdf_path.name,
        "seed": seed,
        "traps": trap_descriptors,
        "fields": record,
    }
    json_path.write_text(
        json.dumps(ground_truth, ensure_ascii=False, indent=2, default=str),
        encoding="utf-8",
    )
    return ground_truth
