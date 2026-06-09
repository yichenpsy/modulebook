# PDF Import MVP Run Summary

## Import scope

This MVP import run processed 2 BHT module handbook PDFs:

1. medieninformatik_2024
2. architektur_2021

## Input PDFs

- tools/pdf_import/data/input/2024_11_Modulhandbuch_zur_Studien-_und_Pruefungsordnung_2017.pdf
- tools/pdf_import/data/input/2021_Modulhandbuch_zur_Studien-_und_Pruefungsordnung_2012.pdf

## Split result

| Study program | Expected modules | Detected modules | Status |
|---|---:|---:|---|
| medieninformatik_2024 | 54 | 54 | ok |
| architektur_2021 | 39 | 39 | ok |

## Extraction result

| Metric | Count |
|---|---:|
| PDFs processed | 2 |
| Modules extracted | 93 |
| Valid modules | 93 |
| Need review | 0 |
| Modules with additional fields | 43 |
| Additional field values | 43 |

## Additional fields

The current normalized output still captures `Verwendbarkeit` as an additional field.

| Additional field | Modules |
|---|---:|
| Verwendbarkeit | 43 |

To promote it to an official normalized field, add it to `normalized_field_map`, rerun normalization, and then rebuild the staging database.

## Staging database

The SQLite staging database was rebuilt at:

```text
tools/pdf_import/data/output/module_import_staging.sqlite
```

The staging table is:

```text
module_import_staging
```

The table now uses language-specific title columns from the normalized JSONL output:

```text
title_de
title_en
```

It no longer has the old derived `title` column.

## Staging result

| Metric | Count |
|---|---:|
| Rows inserted | 93 |
| Non-null `title_de` values | 93 |
| Non-null `title_en` values | 92 |

## Staging field config

The staging columns are generated from `normalized_field_map` plus staging-specific extras and overrides:

```yaml
staging_extra_fields:
  - source_file
  - source_pages
  - study_program
  - study_program_code

staging_field_overrides:
  title:
    - title_de
    - title_en
```
