# PDF Import Pipeline

A Python-based PDF import pipeline was developed to extract module information from module handbook PDFs and convert it into structured JSON data.

The pipeline is implemented as a rule-based extraction process. It uses the regular table layout of the source PDFs, especially two-column Markdown tables in the form `| field label | field value |`, to detect modules and map their content to normalized fields.

## Important Libraries and Packages

The implementation uses the following important libraries and packages:

- `pymupdf4llm`: converts each PDF page into Markdown while preserving table-like structures.
- `PyYAML`: loads the extraction configuration from `base.yaml`, including field labels, module-start regex patterns, and required fields.
- `re`: matches module-start patterns and normalizes field labels.
- `json` and `jsonl`: store intermediate and final extraction results.
- `csv`: writes the detected first and last module pages for debugging and review.
- `pathlib`: handles file and directory paths in a platform-independent way.
- `FastAPI`, `uvicorn`, and `python-multipart`: expose the pipeline as an upload-based PDF import service.

Some packages are included for supporting or extended workflows:

- `pymupdf`: underlying PDF processing dependency.
- `pydantic`: data validation support.
- `pandas`: tabular analysis and reporting support.
- `jsonschema`: schema-based validation support.
- `requests` and `python-dotenv`: integration and environment configuration support.

## Pipeline Steps

1. **PDF to page-level Markdown**

   The input PDF is converted into page-level Markdown by `pymupdf4llm`. Each page is stored with its page number and raw Markdown text in `pages_md.json`.

2. **Module page range detection**

   The pipeline scans all pages to find the first and last pages that contain module descriptions.

   A page is considered a module page when it contains Markdown table rows and when the first column of these rows matches enough predefined module field labels. These labels come from `normalized_field_map` in `tools/pdf_import/configs/base.yaml`.

   In the current implementation, the first module page is detected with a stricter threshold, `min_field_count=10`, while the last module page uses `min_field_count=2`. The lower threshold for the last page is useful because final module pages may contain fewer complete fields.

3. **Module splitting**

   After the module page range is known, the pipeline splits the page content into individual module blocks.

   A new module starts when a table row contains a module-number label, such as `Modulnummer`, `Module ID`, or `Modul ID`, and the adjacent value matches a configured regex pattern.

   The regex patterns are defined in `module_start_patterns` in `base.yaml`. This makes the detection configurable instead of hard-coding one PDF format.

4. **Field boundary detection and field matching**

   Inside each module block, the pipeline reads Markdown table rows and treats the first cell as the field label.

   If the first cell matches one of the predefined labels in `normalized_field_map`, the row is mapped to a normalized field name, such as `module_number`, `credits`, `workload`, or `exam_form`.

   The remaining cells in the same row are treated as the field value. Unmatched labels are stored in `additional_fields` and reported for later review.

5. **Normalization and output generation**

   The extracted module data is normalized and written to structured output files:

   - `module_blocks_md.json`: raw module blocks after splitting.
   - `normalized_modules.json`: final structured module data.
   - `normalized_modules.jsonl`: line-based JSON output for easier review and batch processing.
   - `report.json`: import status, detected module count, missing required fields, unmatched labels, and output paths.

## Main Problems Solved

The implementation mainly solves two problems.

First, it detects module descriptions by using the repeated table structure of the original PDF. Instead of treating the PDF as plain text only, it checks whether pages contain two-column table rows with known module field labels.

Second, it detects field boundaries inside each module by using the first cell of each table row. This works well because the original module descriptions usually follow a label-value table structure.

## Known Limitations

The table structure in the PDFs is not always stable or consistent.

One common issue is that tables can span multiple pages. If a table starts on one page and continues on the next page without repeating a field label, some content may be missed or attached to the wrong field.

Another issue is label variation. Some module handbooks introduce new fields or use field labels that are not yet included in `base.yaml`. In these cases, the pipeline stores the unknown labels as additional fields, but they may still require manual review or an update to the field-label configuration.

