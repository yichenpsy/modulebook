import argparse
import base64
import json
import os
from pathlib import Path
from typing import Any

from tools.pdf_import.one_shot_llm_staging.generate_staging_db import (
    DEFAULT_API_URL,
    DEFAULT_MANIFEST_PATH,
    DEFAULT_MODEL,
    ENV_PATH,
    PROJECT_ROOT,
    load_manifest,
    normalize_modules,
    parse_json_response,
    write_report_csv,
    write_staging_db,
)


PDF_IMPORT_ROOT = PROJECT_ROOT / "tools" / "pdf_import"
DEFAULT_DB_PATH = PDF_IMPORT_ROOT / "data" / "output" / "module_import_staging_direct_pdf.sqlite"
DEFAULT_REPORT_PATH = PDF_IMPORT_ROOT / "data" / "output" / "direct_pdf_staging_report.csv"
DEFAULT_MAX_PDF_BYTES = 25 * 1024 * 1024


def build_instruction_prompt(
    *,
    source_file: str,
    study_program: str | None,
    study_program_code: str | None,
    expected_module_count: int | None,
) -> str:
    expected_hint = (
        f"The expected module count is {expected_module_count}."
        if expected_module_count is not None
        else "The expected module count is unknown."
    )

    return f"""
You extract module handbook data from the attached German university PDF.

Return JSON only. Do not wrap the response in markdown.

Task:
Read the attached PDF directly and extract every module. Return exactly this
JSON shape:

{{
  "modules": [
    {{
      "source_file": "{source_file}",
      "source_pages": [1],
      "study_program": {json.dumps(study_program, ensure_ascii=False)},
      "study_program_code": {json.dumps(study_program_code, ensure_ascii=False)},
      "module_number": "B01",
      "title_de": "German module title",
      "title_en": null,
      "credits": "5",
      "workload": null,
      "learning_area": null,
      "learning_objectives": null,
      "prerequisites": null,
      "level": null,
      "teaching_form": null,
      "status": "Pflichtmodul",
      "frequency": null,
      "exam_form": null,
      "module_grade": null,
      "recognized_modules": null,
      "contents": ["content item"],
      "literature": ["literature item"],
      "additional_notes": null,
      "room_requirements": null
    }}
  ]
}}

Rules:
- Include all modules that appear in the PDF.
- {expected_hint}
- Use only these status values: "Pflichtmodul" or "Wahlpflichtmodul".
- Keep source_pages as the PDF page numbers where the module appears.
- Keep contents and literature as arrays of strings.
- Use null for missing optional fields.
- Preserve German text as written.
- Do not invent missing values.
- Do not include fields other than the fields shown above.
""".strip()


def read_pdf_data_url(pdf_path: Path, max_pdf_bytes: int) -> str:
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    pdf_bytes = pdf_path.read_bytes()
    if len(pdf_bytes) > max_pdf_bytes:
        raise ValueError(
            f"{pdf_path} is {len(pdf_bytes)} bytes, which exceeds "
            f"--max-pdf-bytes={max_pdf_bytes}."
        )

    encoded = base64.b64encode(pdf_bytes).decode("ascii")
    return f"data:application/pdf;base64,{encoded}"


def build_messages(
    *,
    prompt: str,
    pdf_data_url: str,
    pdf_filename: str,
    pdf_payload_mode: str,
) -> list[dict[str, Any]]:
    if pdf_payload_mode == "openai_file_data":
        return [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {
                        "type": "file",
                        "file": {
                            "filename": pdf_filename,
                            "file_data": pdf_data_url,
                        },
                    },
                ],
            }
        ]

    if pdf_payload_mode == "data_url_text":
        return [
            {
                "role": "user",
                "content": (
                    prompt
                    + "\n\nThe PDF is provided as a data URL below:\n"
                    + pdf_data_url
                ),
            }
        ]

    raise ValueError(f"Unsupported --pdf-payload-mode: {pdf_payload_mode}")


def call_llm_with_pdf(
    *,
    messages: list[dict[str, Any]],
    api_url: str,
    model: str,
    timeout_seconds: int,
) -> str:
    import requests
    from dotenv import load_dotenv

    load_dotenv(ENV_PATH)
    api_key = os.getenv("BHT_LLM_API_KEY")

    if not api_key:
        raise RuntimeError(f"Missing BHT_LLM_API_KEY. Set it in {ENV_PATH}.")

    try:
        response = requests.post(
            api_url,
            headers={
                "x-api-key": api_key,
                "Content-Type": "application/json",
            },
            json={
                "model": model,
                "messages": messages,
                "temperature": 0.0,
            },
            timeout=timeout_seconds,
        )
    except requests.exceptions.ConnectionError as exc:
        raise RuntimeError(
            "Could not connect to the LLM API. If the error mentions DNS or "
            "NameResolutionError, the API hostname is not resolvable from this "
            "network. Connect to the BHT network/VPN, check DNS, or pass the "
            "correct endpoint with --api-url. "
            f"Endpoint: {api_url}"
        ) from exc
    except requests.exceptions.Timeout as exc:
        raise RuntimeError(
            f"LLM API request timed out after {timeout_seconds}s. Increase "
            "--timeout-seconds or retry with a smaller PDF/import."
        ) from exc
    except requests.exceptions.RequestException as exc:
        raise RuntimeError(f"LLM API request failed: {exc}") from exc

    if response.status_code == 501 and "Unknown part type: file" in response.text:
        raise RuntimeError(
            "The configured LLM API endpoint does not support PDF/file message "
            "parts. It returned: Unknown part type: file. This direct-PDF "
            "method cannot work with this endpoint unless BHT RIS provides a "
            "file-capable API or a different payload format. Use "
            "tools.pdf_import.one_shot_llm_staging.generate_staging_db for this "
            "endpoint, or pass a file-capable endpoint with --api-url."
        )

    if response.status_code != 200:
        raise RuntimeError(f"LLM API error {response.status_code}: {response.text}")

    data = response.json()
    return data["choices"][0]["message"]["content"]


def resolve_imports(args: argparse.Namespace) -> list[dict[str, Any]]:
    if args.pdf:
        return [
            {
                "import_id": args.study_program_code or Path(args.pdf).stem,
                "source_file": args.pdf,
                "study_program": args.study_program,
                "study_program_code": args.study_program_code,
                "expected_module_count": args.expected_module_count,
            }
        ]

    imports = load_manifest(args.manifest)

    if args.all:
        return imports

    if not args.import_id:
        raise ValueError("Pass --import-id, --all, or --pdf.")

    matched = [item for item in imports if item["import_id"] == args.import_id]
    if not matched:
        available = ", ".join(item["import_id"] for item in imports)
        raise ValueError(f"Unknown import id {args.import_id!r}. Available: {available}")

    return matched


def run_import(
    item: dict[str, Any],
    args: argparse.Namespace,
    *,
    multiple_imports: bool,
) -> list[dict[str, Any]]:
    source_file = item["source_file"]
    pdf_path = PROJECT_ROOT / source_file
    if Path(source_file).is_absolute():
        pdf_path = Path(source_file)

    prompt = build_instruction_prompt(
        source_file=str(source_file),
        study_program=item.get("study_program"),
        study_program_code=item.get("study_program_code"),
        expected_module_count=item.get("expected_module_count"),
    )
    pdf_data_url = read_pdf_data_url(pdf_path, args.max_pdf_bytes)
    messages = build_messages(
        prompt=prompt,
        pdf_data_url=pdf_data_url,
        pdf_filename=pdf_path.name,
        pdf_payload_mode=args.pdf_payload_mode,
    )
    raw = call_llm_with_pdf(
        messages=messages,
        api_url=args.api_url,
        model=args.model,
        timeout_seconds=args.timeout_seconds,
    )

    if args.raw_output:
        raw_output_path = args.raw_output
        if multiple_imports:
            raw_output_path = raw_output_path / f"{item['import_id']}.json"
        raw_output_path.parent.mkdir(parents=True, exist_ok=True)
        raw_output_path.write_text(raw, encoding="utf-8")

    data = parse_json_response(raw)
    return normalize_modules(
        data["modules"],
        source_file=str(source_file),
        study_program=item.get("study_program"),
        study_program_code=item.get("study_program_code"),
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate module_import_staging SQLite DB by sending PDFs directly to the LLM API."
    )
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--import-id", help="Import id from import_manifest.yaml.")
    source.add_argument("--all", action="store_true", help="Run every manifest import.")
    source.add_argument("--pdf", type=Path, help="Custom PDF path.")

    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST_PATH)
    parser.add_argument("--db", type=Path, default=DEFAULT_DB_PATH)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT_PATH)
    parser.add_argument("--append", action="store_true")
    parser.add_argument("--raw-output", type=Path)
    parser.add_argument("--study-program")
    parser.add_argument("--study-program-code")
    parser.add_argument("--expected-module-count", type=int)
    parser.add_argument("--api-url", default=DEFAULT_API_URL)
    parser.add_argument("--model", default=DEFAULT_MODEL)
    parser.add_argument(
        "--timeout-seconds",
        type=int,
        default=300,
        help="LLM API request timeout in seconds.",
    )
    parser.add_argument("--max-pdf-bytes", type=int, default=DEFAULT_MAX_PDF_BYTES)
    parser.add_argument(
        "--pdf-payload-mode",
        choices=["openai_file_data", "data_url_text"],
        default="openai_file_data",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    imports = resolve_imports(args)
    all_modules = []
    multiple_imports = len(imports) > 1

    for item in imports:
        print(f"Running direct-PDF LLM import: {item['import_id']}")
        modules = run_import(item, args, multiple_imports=multiple_imports)
        print(f"Extracted modules: {len(modules)}")
        all_modules.extend(modules)

    write_staging_db(args.db, all_modules, append=args.append)
    if args.report:
        write_report_csv(args.report, all_modules)

    mode = "appended to" if args.append else "written to"
    print(f"Total staged modules: {len(all_modules)}")
    print(f"DB {mode}: {args.db}")
    if args.report:
        print(f"Report written to: {args.report}")


if __name__ == "__main__":
    main()
