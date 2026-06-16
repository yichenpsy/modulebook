# One-shot LLM staging DB import

This folder contains an alternative importer that asks the LLM API to extract
all modules from a PDF and writes the result directly into the staging SQLite DB.

It skips the existing multi-step flow:

1. PDF page extraction
2. regex module splitting
3. field parsing
4. Python normalization
5. JSONL staging

Instead, the command does this in one run:

1. Read PDF text page by page.
2. Send the full text to the LLM API with a strict JSON schema prompt.
3. Validate and normalize the returned JSON in Python.
4. Replace or append rows in `module_import_staging`.

## Setup

Use the same environment file as the existing LLM client:

```bash
tools/pdf_import/.env
```

Required variable:

```bash
BHT_LLM_API_KEY=...
```

Install dependencies from the parent tool folder if needed:

```bash
pip install -r tools/pdf_import/requirements.txt
```

## Run one import from the manifest

```bash
python -m tools.pdf_import.one_shot_llm_staging.generate_staging_db \
  --import-id medieninformatik_2024
```

Default output:

```bash
tools/pdf_import/data/output/module_import_staging_one_shot.sqlite
tools/pdf_import/data/output/one_shot_staging_report.csv
tools/pdf_import/one_shot_llm_staging/output/<import_id>/staging_input.json
```

## Run all imports from the manifest

```bash
python -m tools.pdf_import.one_shot_llm_staging.generate_staging_db --all
```

## Run a custom PDF

```bash
python -m tools.pdf_import.one_shot_llm_staging.generate_staging_db \
  --pdf tools/pdf_import/data/input/medieninformatik_2024.pdf \
  --study-program medieninformatik_2024 \
  --study-program-code medieninformatik_2024
```

## Useful options

```bash
--db PATH              SQLite output path.
--report PATH          CSV report output path.
--stage-input-output-dir PATH
                       Root folder for per-PDF JSON files written before staging.
--append               Append rows instead of replacing the staging table.
--raw-output PATH      Save raw LLM JSON response for review.
--model MODEL          Override the default model.
--api-url URL          Override the default API endpoint.
--max-input-chars N    Refuse very large prompts unless changed explicitly.
```

The LLM must return JSON only. If the response is malformed or required fields
are missing, the command fails before writing the DB.

The report CSV is written from the current run's validated modules. It includes
one row per module with core fields, page numbers, `contents`/`literature`
counts, missing optional fields, and short text previews for quick review.

## Per-PDF pre-staging output

Every imported PDF also gets its own review folder below:

```bash
tools/pdf_import/one_shot_llm_staging/output/<import_id>/
```

Files:

```bash
llm_response_raw.txt       Raw text returned by the LLM.
llm_response_parsed.json   Parsed JSON before Python normalization.
staging_input.json         Validated modules that will be written to SQLite.
staging_input.jsonl        Same modules as JSON Lines for line-by-line review.
```

If the LLM response is not parseable JSON, the command still writes:

```bash
llm_response_raw.txt
llm_response_parse_error.txt
```

Open `llm_response_raw.txt` first. Common causes are an empty response, an
explanatory sentence before the JSON, truncated JSON, or an API-side error
message returned as plain text.

You can change the root folder:

```bash
python -m tools.pdf_import.one_shot_llm_staging.generate_staging_db \
  --import-id medieninformatik_2024 \
  --stage-input-output-dir tools/pdf_import/one_shot_llm_staging/output_debug
```

## Troubleshooting

### `NameResolutionError` for `llmapi-key.ris.bht-berlin.de`

This means the machine cannot resolve the API hostname before any request reaches
the LLM service. It is usually a network/DNS issue, not a prompt or API key issue.

Check:

```bash
nslookup llmapi-key.ris.bht-berlin.de
```

If the hostname is only available inside the BHT network, connect to the BHT VPN
or run the importer from a network where this hostname resolves.

If the API endpoint changed, pass it explicitly:

```bash
python -m tools.pdf_import.one_shot_llm_staging.generate_staging_db \
  --import-id medieninformatik_2024 \
  --api-url https://NEW_HOST/v1/chat/completions
```
