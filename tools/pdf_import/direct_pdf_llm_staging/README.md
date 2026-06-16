# Direct PDF LLM staging DB import

This folder contains an experimental importer that sends the PDF file itself to
the LLM API instead of extracting PDF text locally first.

The flow is:

1. Read the PDF as bytes.
2. Encode it as a base64 `data:application/pdf` file payload.
3. Ask the LLM to extract all modules into the staging JSON shape.
4. Reuse the same Python validation, SQLite staging, and CSV report logic as the
   one-shot text importer.

This only works if the configured chat completions endpoint supports PDF/file
content parts. If the endpoint only accepts plain text chat messages, use
`tools/pdf_import/one_shot_llm_staging` instead.

Known BHT RIS behavior: `https://llmapi-key.ris.bht-berlin.de/v1/chat/completions`
currently returns `501 Unknown part type: file` for the default file payload.
That means this endpoint does not support direct PDF attachments.

## Run one import from the manifest

```bash
python3 -m tools.pdf_import.direct_pdf_llm_staging.generate_staging_db \
  --import-id medieninformatik_2024
```

Default output:

```bash
tools/pdf_import/data/output/module_import_staging_direct_pdf.sqlite
tools/pdf_import/data/output/direct_pdf_staging_report.csv
```

## Run all imports from the manifest

```bash
python3 -m tools.pdf_import.direct_pdf_llm_staging.generate_staging_db --all
```

## Run a custom PDF

```bash
python3 -m tools.pdf_import.direct_pdf_llm_staging.generate_staging_db \
  --pdf tools/pdf_import/data/input/medieninformatik_2024.pdf \
  --study-program medieninformatik_2024 \
  --study-program-code medieninformatik_2024
```

## Payload modes

The default mode is:

```bash
--pdf-payload-mode openai_file_data
```

It sends the PDF in a chat message content array:

```json
{
  "type": "file",
  "file": {
    "filename": "modulehandbook.pdf",
    "file_data": "data:application/pdf;base64,..."
  }
}
```

If the BHT RIS API expects another file attachment shape, adjust
`build_messages()` in `generate_staging_db.py`.

## Troubleshooting

### `501 Unknown part type: file`

The endpoint accepted the HTTP request but rejected the chat message because it
does not implement file/PDF content parts. This direct-PDF method cannot work
with that endpoint.

Use the text-based one-shot importer instead:

```bash
python3 -m tools.pdf_import.one_shot_llm_staging.generate_staging_db \
  --import-id medieninformatik_2024 \
  --timeout-seconds 600
```

Or use `--api-url` with an endpoint that explicitly supports PDF/file inputs.
