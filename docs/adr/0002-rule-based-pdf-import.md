# ADR 0002: Use rule-based PDF extraction with human review

Status: Accepted — implemented. Recorded retrospectively on 2026-10-31.

## Context

Module descriptions must be converted from handbook PDFs into editable structured data. The supported source layouts contain recurring label/value tables, but labels, module numbers, and page boundaries vary. Extraction errors must be visible before database changes are made.

## Decision

Use PyMuPDF4LLM to convert PDF pages to Markdown, then apply configurable rules to detect module pages, split module blocks, and map labels to normalized fields. Keep rules and source metadata in YAML.

Return extracted modules and a report for human review. Save reviewed data through a separate API request. The active pipeline makes no LLM call; the library name PyMuPDF4LLM does not mean a model is used.

## Rationale and alternatives

The thesis (§4.6, pp. 39–40) reports early LLM experiments for module-boundary detection, field extraction from pre-split blocks, and whole-document extraction. In that environment, the BHT RIS API was slow or timed out, returned incomplete fields depending on the prompt, and rejected direct file input with `501 Unknown part type: file`. Those prototype observations informed the rule-based implementation. They describe the tested setup at that time, not current capabilities of all models or APIs.

- Table labels give the extractor explicit field boundaries. Plain-text extraction alone would discard useful table structure and require another way to identify those boundaries.
- Configurable mappings let developers add known label variants without replacing the extraction process. Layout changes can still require code changes.
- A rule-based pipeline allows inspection of intermediate artifacts without an external model dependency. LLM extraction could handle more varied wording, but would add model configuration, output validation, and potentially an external service dependency. Earlier experiments remain in `tools/pdf_import/archive/`; the thesis observations do not establish a controlled comparative accuracy benchmark.
- Human review reduces reliance on extraction heuristics. Saving immediately after extraction would give users less opportunity to correct errors.

## Consequences

Support is strongest for recognizable text/table layouts. Multi-page tables, unfamiliar labels, and unusual module numbers need review; scanned documents are not established supported inputs.

A successful report is not an accuracy guarantee. Missing required fields and module-count differences do not automatically fail extraction. Saving can partially succeed, so the caller must also inspect per-row save errors. Intermediate files are retained in local subprocess mode; the HTTP service removes its temporary extraction directory after returning.

## Implementation references

- [Single-PDF pipeline](../../tools/pdf_import/module_import_v2/pipeline_single.py) and [extraction configuration](../../tools/pdf_import/configs/base.yaml).
- [Python HTTP service](../../services/pdf_import_service/main.py) and [Java import service](../../src/main/java/de/bht/modulebook/pdfimport/PdfImportService.java).
- [PDF import guide](../architecture/pdf-import.md) for execution modes, review criteria, and debugging.
- [Thesis source and implementation differences](../product/requirements.md#source-and-implementation-differences) and [historical evaluation](../development/testing.md#historical-pdf-evaluation).
