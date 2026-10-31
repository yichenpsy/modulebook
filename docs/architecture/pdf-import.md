# PDF import

The active implementation is `tools/pdf_import/module_import_v2/`. It uses PyMuPDF4LLM and extraction rules, without an LLM call. `tools/pdf_import/archive/` contains earlier experiments.

## Application workflow

1. Create or select a modulebook, then upload a PDF through **Import PDF**.
2. `POST /api/pdf-import/normalize` accepts multipart fields `modulebookId`, `file`, and optional `modulebookReleaseDate` and `studyExaminationRegulationsDate`.
3. `PdfImportService` stores the upload in a unique job directory. `PythonPdfImportRunner` invokes extraction and returns `report` and `normalizedModules` for review. This step does not save modules to PostgreSQL.
4. The user reviews the fields; `POST /api/pdf-import/modules` saves the reviewed JSON. `PdfImportedModuleMapper` converts extraction fields into the Java model.
5. The save operation groups changes under a PDF-import change set and returns saved modules and per-row errors. Duplicate module numbers within the request reject the batch before saving; other row failures can leave a partially saved result.

Re-import matches an explicit module ID when supplied, otherwise the module number within the target modulebook. Existing module status is preserved. Existing modules absent from the imported list receive the comment `MISSING_FROM_IMPORT`; the backend does not automatically deactivate or delete them. The first import save creates an initial snapshot. See [Versioning](versioning.md) before resetting import history.

### Save response and validation boundaries

The JSON save request contains `modulebookId`, optional publication dates, and a nonempty `modules` array. Read `requestedCount`, `createdCount`, `errorCount`, `changeSetId`, and `errors` in the response. Despite their names, `createdCount` and `createdModules` include successful updates as well as new modules. A successful HTTP response is not evidence that every row was saved.

The import mapper requires a module number, title, and parseable credits, but does not run `ModuleRequest`'s full Bean Validation rules. Duplicate numbers are checked before the batch; subsequent row failures are caught individually. The first import-save path can create the initial snapshot even when every row fails, and failures after row processing can occur after earlier writes have committed. It is not an atomic operation or a full-success gate for initial-version creation.

Supply an explicit module ID in the normalized row's `id` field only when it belongs to the selected modulebook. The current ID-based save path does not separately check modulebook ownership before updating, so a supplied ID from another modulebook can reassign that record. When adding API clients, validate this relationship rather than assuming the import endpoint enforces it.

Error handling differs across the two HTTP services. FastAPI returns HTTP 400 for empty files or unsupported filename extensions. Java's normalization path throws `IllegalArgumentException` for those checks and wraps Python-service failures in runtime exceptions, without a dedicated controller exception mapping. Clients must not assume the Java endpoint preserves Python's error status or uses a uniform error schema.

## Execution modes

| Mode | Selection | Configuration and storage |
| --- | --- | --- |
| HTTP service | Nonempty `PDF_IMPORT_SERVICE_URL` | Full URL such as `http://127.0.0.1:8000/import`; FastAPI uses configuration bundled with its own working directory |
| Local subprocess | Empty service URL | Java launches `python -m tools.pdf_import.module_import_v2.pipeline_single` using configured executable, repository root, and YAML paths |

Both modes need a writable Java `pdf-import.job-root`. In HTTP mode, FastAPI deletes its temporary extraction directory after returning; Java retains the uploaded PDF and returned report/normalized JSON. Intermediate output paths inside the service report refer to that temporary directory, not downloadable persistent artifacts. No automatic cleanup of Java job directories is implemented.

`pdf-import.timeout-seconds` defaults to 300 and limits the local subprocess. The HTTP request currently has no equivalent explicit timeout. Spring's upload and request size limits are both `100MB`; the Kubernetes ingress manifest does not configure an upload limit, so its controller can impose a smaller limit.

## Extraction stages

1. Convert PDF pages to Markdown, retaining page numbers.
2. Detect the module page range from known table labels: at least 10 matched fields for the first page and 2 for the last page. These thresholds are arguments in `pipeline_single.py`.
3. Split module blocks using module-number labels and patterns.
4. Map table labels to normalized fields; collect unmatched labels for review.
5. Normalize values, extract coordinator information, and generate the report.

Local pipeline artifacts include `pages_md.json`, `module_boundary_pages.csv`, `module_blocks_md.json`, `normalized_modules.json`, `normalized_modules.jsonl`, and `report.json`.

## Field mapping

The YAML map normalizes PDF labels first; `PdfImportedModuleMapper` then maps the normalized JSON into Java fields. Examples from the current configuration:

| PDF label | Normalized JSON field | Java field / meaning |
| --- | --- | --- |
| `Modulnummer`, `Module ID`, `Modul ID` | `module_number` | `moduleNumber` |
| `Titel`, `Title` | `title` | `title` |
| `Leistungspunkte`, `Credits`, `Credit Points` | `credits` | `credits` |
| `Workload`, `Präsenzzeit`, `Präsenszeit` | `workload` | Original text plus parsed workload hours/components |
| `Verwendbarkeit` | `applicability` | `applicability` |
| `Lerngebiet`, `Learning Area` | `learning_area` | `learningArea` |
| `Status` | `status` | `moduleType`, such as compulsory/elective; this is not lifecycle `moduleStatus` |
| `Prüfungsform` | `exam_form` | `examinationForm` |

The complete source of label aliases is [base.yaml](../../tools/pdf_import/configs/base.yaml). Normalization alone does not guarantee every extracted field is persisted: review the Java mapper when adding a field. Coordinator data is extracted separately from page content rather than only from the module table.

## Configuration

- `configs/base.yaml`: relative to `tools/pdf_import/`; defines module-start patterns, label mappings, and required fields. Extend `normalized_field_map` when supporting new labels; adjust `module_start_patterns` for new module-number formats.
- `configs/import_manifest.yaml`: source paths and optional expected module counts. Expected counts are report metadata, not an acceptance gate. Lookup uses the exact `source_file` string; a browser-uploaded basename may not match a manifest entry containing a full relative path.
- `PdfImportProperties`: binds Java `pdf-import.*` settings. Java YAML overrides apply to subprocess mode; HTTP extraction uses the Python service's configuration.

For reproducible debugging, run a single PDF from the repository root after installing [Python dependencies](../development/local-setup.md#2-enable-pdf-import):

```bash
.venv/bin/python -m tools.pdf_import.module_import_v2.pipeline_single \
  --input /absolute/path/to/handbook.pdf \
  --work-dir tools/pdf_import/data/import_jobs/manual-check \
  --output tools/pdf_import/data/import_jobs/manual-check/normalized_modules.json \
  --report tools/pdf_import/data/import_jobs/manual-check/report.json
```

Replace the input path with your PDF. `manual-check` is a chosen output directory; use a new name to retain separate runs. The module path identifies the active CLI. `--base-yaml` and `--import-manifest` are optional configuration overrides; their defaults select the checked-in YAML files. This command creates files only, without saving database records.

## Review criteria

Inspect module count, first/last module pages, missing fields, unmatched labels, and the resulting module text against the source PDF. `status=ok` does not guarantee complete extraction: missing required fields and count differences do not automatically change it to a failure. `needs_review` indicates missing page boundaries; no module blocks yields `failed`.

## Limitations

The pipeline targets text PDFs with recognizable label/value tables. Scanned documents and different layouts are not established supported inputs. Tables continuing across pages, missing labels, unusual module numbers, and new field labels can split or map content incorrectly. Do not treat a successful HTTP response or CLI exit code as an accuracy measure; review before saving, then inspect the save response for errors.

The thesis reports one concrete failure: module B23 in Augenoptik was not recognized because its number was merged into title content. A missing module-start anchor can merge content into another block even when most fields look plausible. The five-handbook count comparison and its limits are recorded in [Testing and evaluation](../development/testing.md#historical-pdf-evaluation). Module detection and field correctness require separate checks.
