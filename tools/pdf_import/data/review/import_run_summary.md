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
| Additional fields | 0 |

## Field update

The field `Verwendbarkeit` was upgraded from an additional field to an official optional normalized field:

```text
Verwendbarkeit → applicability