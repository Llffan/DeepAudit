"""Sample 病案首页 PDF generator.

Public API:

    from sample_pdf import generate_pdf, generate_mock_record

    # One-shot: produce a PDF + paired ground-truth JSON.
    gt = generate_pdf("data/samples/case_001.pdf", seed=1, traps=["R001"])

The CLI entry is in `cli.py`:

    python -m sample_pdf.cli --count 10 --out-dir data/samples --error-rate 0.5

The HQMS field standard is loaded from `docs/病案首页与质控业务/病案标准.xlsx`
on first call and cached. Provide an explicit path via the
`SAMPLE_PDF_SPEC` env var or pass `spec_path=` if running from a non-repo
working directory.
"""

from .core import generate_pdf, generate_mock_record, AVAILABLE_TRAPS

__all__ = ["generate_pdf", "generate_mock_record", "AVAILABLE_TRAPS"]
