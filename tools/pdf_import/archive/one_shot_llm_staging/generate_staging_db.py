import argparse
import csv
import json
import os
import re
import sqlite3
import time
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[3]
PDF_IMPORT_ROOT = PROJECT_ROOT / "tools" / "pdf_import"
SCRIPT_ROOT = Path(__file__).resolve().parent
ENV_PATH = PDF_IMPORT_ROOT / ".env"
DEFAULT_API_URL = "https://llmapi-key.ris.bht-berlin.de/v1/chat/completions"
DEFAULT_MODEL = "bht/large"
DEFAULT_DB_PATH = PDF_IMPORT_ROOT / "data" / "output" / "module_import_staging_one_shot.sqlite"
DEFAULT_REPORT_PATH = PDF_IMPORT_ROOT / "data" / "output" / "one_shot_staging_report.csv"
DEFAULT_STAGE_INPUT_OUTPUT_DIR = SCRIPT_ROOT / "output"
DEFAULT_MANIFEST_PATH = PDF_IMPORT_ROOT / "configs" / "import_manifest.yaml"
DEFAULT_MAX_INPUT_CHARS = 220_000

STAGING_COLUMNS = [
    "source_file",
    "source_pages",
    "study_program",
    "study_program_code",
    "module_number",
    "title_de",
    "title_en",
    "credits",
    "workload",
    "learning_area",
    "learning_objectives",
    "prerequisites",
    "level",
    "teaching_form",
    "status",
    "frequency",
    "exam_form",
    "module_grade",
    "recognized_modules",
    "contents",
    "literature",
    "additional_notes",
    "room_requirements",
]

REQUIRED_FIELDS = [
    "source_file",
    "source_pages",
    "module_number",
    "title_de",
    "credits",
    "status",
    "contents",
    "literature",
]

LIST_FIELDS = {"source_pages", "contents", "literature"}
ALLOWED_STATUS = {"Pflichtmodul", "Wahlpflichtmodul"}
REPORT_OPTIONAL_FIELDS = [
    "title_en",
    "workload",
    "learning_area",
    "learning_objectives",
    "prerequisites",
    "level",
    "teaching_form",
    "frequency",
    "exam_form",
    "module_grade",
    "recognized_modules",
    "additional_notes",
    "room_requirements",
]
REPORT_COLUMNS = [
    "study_program_code",
    "study_program",
    "source_file",
    "module_number",
    "title_de",
    "title_en",
    "status",
    "credits",
    "source_pages",
    "contents_count",
    "literature_count",
    "missing_optional_fields",
    "learning_objectives_preview",
    "contents_preview",
    "literature_preview",
]


def load_manifest(path: Path) -> list[dict[str, Any]]:
    import yaml

    with path.open("r", encoding="utf-8") as f:
        manifest = yaml.safe_load(f)

    imports = manifest["imports"]
    if isinstance(imports, dict):
        return [{"import_id": import_id, **item} for import_id, item in imports.items()]

    return imports


def extract_pdf_text(pdf_path: Path) -> tuple[str, list[int]]:
    import fitz

    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    parts = []
    page_numbers = []

    with fitz.open(pdf_path) as doc:
        for page_number, page in enumerate(doc, start=1):
            text = page.get_text("text") or ""
            page_numbers.append(page_number)
            parts.append(f"\n\n=== PAGE {page_number} ===\n{text.strip()}")

    return "".join(parts).strip(), page_numbers


def build_prompt(
    *,
    pdf_text: str,
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
You extract module handbook data from German university PDFs.

Return JSON only. Do not wrap the response in markdown.

Task:
Extract every module from the provided page-marked PDF text and return exactly
this JSON shape:

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
- Include all modules that appear in the PDF text.
- {expected_hint}
- Use only these status values: "Pflichtmodul" or "Wahlpflichtmodul".
- Keep source_pages as the page numbers where the module appears.
- Keep contents and literature as arrays of strings.
- Use null for missing optional fields.
- Preserve German text as written.
- Do not invent missing values.
- Do not include fields other than the fields shown above.

PDF text:
{pdf_text}
""".strip()


def call_llm(
    *,
    prompt: str,
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
                "messages": [{"role": "user", "content": prompt}],
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

    if response.status_code != 200:
        raise RuntimeError(f"LLM API error {response.status_code}: {response.text}")

    data = response.json()
    return data["choices"][0]["message"]["content"]


def parse_json_response(raw: str) -> dict[str, Any]:
    text = raw.strip()
    if not text:
        raise ValueError("LLM response is empty.")

    fenced = re.fullmatch(r"```(?:json)?\s*(.*?)\s*```", text, re.DOTALL)
    if fenced:
        text = fenced.group(1).strip()

    try:
        data = json.loads(text)
    except json.JSONDecodeError as exc:
        json_start = text.find("{")
        json_end = text.rfind("}")
        if json_start >= 0 and json_end > json_start:
            candidate = text[json_start : json_end + 1]
            try:
                data = json.loads(candidate)
            except json.JSONDecodeError:
                preview = text[:500].replace("\n", "\\n")
                raise ValueError(
                    f"LLM response is not valid JSON: {exc}. "
                    f"Response preview: {preview!r}"
                ) from exc
        else:
            preview = text[:500].replace("\n", "\\n")
            raise ValueError(
                f"LLM response is not valid JSON: {exc}. "
                f"Response preview: {preview!r}"
            ) from exc

    if not isinstance(data, dict) or not isinstance(data.get("modules"), list):
        raise ValueError('LLM response must be an object with a "modules" array.')

    return data


def normalize_cell(value: Any) -> Any:
    if value == "":
        return None
    if isinstance(value, (list, dict)):
        return json.dumps(value, ensure_ascii=False)
    return value


def normalize_modules(
    modules: list[dict[str, Any]],
    *,
    source_file: str,
    study_program: str | None,
    study_program_code: str | None,
) -> list[dict[str, Any]]:
    normalized = []

    for index, module in enumerate(modules, start=1):
        if not isinstance(module, dict):
            raise ValueError(f"Module #{index} is not an object.")

        unknown_fields = set(module) - set(STAGING_COLUMNS)
        if unknown_fields:
            raise ValueError(
                f"Module #{index} contains unknown fields: {sorted(unknown_fields)}"
            )

        record = {column: module.get(column) for column in STAGING_COLUMNS}
        record["source_file"] = record["source_file"] or source_file
        record["study_program"] = record["study_program"] or study_program
        record["study_program_code"] = record["study_program_code"] or study_program_code

        for field in REQUIRED_FIELDS:
            value = record.get(field)
            if value is None or value == "" or value == []:
                raise ValueError(f"Module #{index} is missing required field: {field}")

        if record["status"] not in ALLOWED_STATUS:
            raise ValueError(
                f"Module #{index} has invalid status {record['status']!r}; "
                f"allowed: {sorted(ALLOWED_STATUS)}"
            )

        for field in LIST_FIELDS:
            if not isinstance(record[field], list):
                raise ValueError(f"Module #{index} field {field} must be an array.")

        if not all(isinstance(page, int) for page in record["source_pages"]):
            raise ValueError(f"Module #{index} source_pages must contain integers.")

        normalized.append(record)

    return normalized


def create_staging_table(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS module_import_staging (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_file TEXT,
            source_pages TEXT,
            study_program TEXT,
            study_program_code TEXT,
            module_number TEXT,
            title_de TEXT,
            title_en TEXT,
            credits TEXT,
            workload TEXT,
            learning_area TEXT,
            learning_objectives TEXT,
            prerequisites TEXT,
            level TEXT,
            teaching_form TEXT,
            status TEXT,
            frequency TEXT,
            exam_form TEXT,
            module_grade TEXT,
            recognized_modules TEXT,
            contents TEXT,
            literature TEXT,
            additional_notes TEXT,
            room_requirements TEXT
        )
        """
    )


def write_staging_db(db_path: Path, modules: list[dict[str, Any]], append: bool) -> None:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    placeholders = ", ".join("?" for _ in STAGING_COLUMNS)
    columns = ", ".join(STAGING_COLUMNS)
    rows = [
        tuple(normalize_cell(module.get(column)) for column in STAGING_COLUMNS)
        for module in modules
    ]

    with sqlite3.connect(db_path) as conn:
        create_staging_table(conn)
        with conn:
            if not append:
                conn.execute("DELETE FROM module_import_staging")
            conn.executemany(
                f"""
                INSERT INTO module_import_staging ({columns})
                VALUES ({placeholders})
                """,
                rows,
            )


def preview_text(value: Any, max_chars: int = 180) -> str:
    if value is None:
        return ""
    if isinstance(value, list):
        text = "; ".join(str(item) for item in value)
    else:
        text = str(value)

    text = " ".join(text.split())
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 3].rstrip() + "..."


def write_report_csv(report_path: Path, modules: list[dict[str, Any]]) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)

    rows = []
    for module in modules:
        missing_optional_fields = [
            field
            for field in REPORT_OPTIONAL_FIELDS
            if module.get(field) is None or module.get(field) == "" or module.get(field) == []
        ]
        rows.append(
            {
                "study_program_code": module.get("study_program_code"),
                "study_program": module.get("study_program"),
                "source_file": module.get("source_file"),
                "module_number": module.get("module_number"),
                "title_de": module.get("title_de"),
                "title_en": module.get("title_en"),
                "status": module.get("status"),
                "credits": module.get("credits"),
                "source_pages": "; ".join(str(page) for page in module.get("source_pages", [])),
                "contents_count": len(module.get("contents", [])),
                "literature_count": len(module.get("literature", [])),
                "missing_optional_fields": "; ".join(missing_optional_fields),
                "learning_objectives_preview": preview_text(module.get("learning_objectives")),
                "contents_preview": preview_text(module.get("contents")),
                "literature_preview": preview_text(module.get("literature")),
            }
        )

    with report_path.open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=REPORT_COLUMNS)
        writer.writeheader()
        writer.writerows(rows)


def safe_path_name(value: str) -> str:
    name = re.sub(r"[^A-Za-z0-9._-]+", "_", value.strip())
    return name.strip("._") or "import"


def format_duration(seconds: float) -> str:
    hours, remainder = divmod(seconds, 3600)
    minutes, seconds = divmod(remainder, 60)

    if hours >= 1:
        return f"{int(hours)}h {int(minutes)}m {seconds:.1f}s"
    if minutes >= 1:
        return f"{int(minutes)}m {seconds:.1f}s"
    return f"{seconds:.1f}s"


def get_stage_output_dir(output_root: Path, import_id: str) -> Path:
    return output_root / safe_path_name(import_id)


def write_raw_response_file(
    *,
    output_root: Path,
    import_id: str,
    raw_response: str,
) -> Path:
    output_dir = get_stage_output_dir(output_root, import_id)
    output_dir.mkdir(parents=True, exist_ok=True)
    raw_response_path = output_dir / "llm_response_raw.txt"
    raw_response_path.write_text(raw_response, encoding="utf-8")
    return raw_response_path


def write_parse_error_file(
    *,
    output_root: Path,
    import_id: str,
    error: Exception,
) -> Path:
    output_dir = get_stage_output_dir(output_root, import_id)
    output_dir.mkdir(parents=True, exist_ok=True)
    parse_error_path = output_dir / "llm_response_parse_error.txt"
    parse_error_path.write_text(str(error), encoding="utf-8")
    return parse_error_path


def write_stage_input_files(
    *,
    output_root: Path,
    import_id: str,
    source_file: str,
    raw_response: str,
    parsed_response: dict[str, Any],
    normalized_modules: list[dict[str, Any]],
    duration_seconds: float,
) -> Path:
    output_dir = get_stage_output_dir(output_root, import_id)
    output_dir.mkdir(parents=True, exist_ok=True)

    (output_dir / "llm_response_raw.txt").write_text(raw_response, encoding="utf-8")
    (output_dir / "llm_response_parsed.json").write_text(
        json.dumps(parsed_response, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    (output_dir / "staging_input.json").write_text(
        json.dumps(
            {
                "import_id": import_id,
                "source_file": source_file,
                "module_count": len(normalized_modules),
                "duration_seconds": round(duration_seconds, 3),
                "duration": format_duration(duration_seconds),
                "modules": normalized_modules,
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )

    with (output_dir / "staging_input.jsonl").open("w", encoding="utf-8") as f:
        for module in normalized_modules:
            f.write(json.dumps(module, ensure_ascii=False) + "\n")

    return output_dir


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
    started_at = time.perf_counter()
    source_file = item["source_file"]
    pdf_path = PROJECT_ROOT / source_file
    if Path(source_file).is_absolute():
        pdf_path = Path(source_file)

    pdf_text, _page_numbers = extract_pdf_text(pdf_path)
    if len(pdf_text) > args.max_input_chars:
        raise ValueError(
            f"{source_file} extracted text has {len(pdf_text)} chars, which exceeds "
            f"--max-input-chars={args.max_input_chars}. Increase the limit only if "
            "the selected model supports the prompt size."
        )

    prompt = build_prompt(
        pdf_text=pdf_text,
        source_file=source_file,
        study_program=item.get("study_program"),
        study_program_code=item.get("study_program_code"),
        expected_module_count=item.get("expected_module_count"),
    )
    raw = call_llm(
        prompt=prompt,
        api_url=args.api_url,
        model=args.model,
        timeout_seconds=args.timeout_seconds,
    )
    raw_response_path = write_raw_response_file(
        output_root=args.stage_input_output_dir,
        import_id=item["import_id"],
        raw_response=raw,
    )

    if args.raw_output:
        raw_output_path = args.raw_output
        if multiple_imports:
            raw_output_path = raw_output_path / f"{item['import_id']}.json"
        raw_output_path.parent.mkdir(parents=True, exist_ok=True)
        raw_output_path.write_text(raw, encoding="utf-8")

    try:
        data = parse_json_response(raw)
    except ValueError as exc:
        parse_error_path = write_parse_error_file(
            output_root=args.stage_input_output_dir,
            import_id=item["import_id"],
            error=exc,
        )
        raise ValueError(
            f"LLM response for import {item['import_id']!r} is not valid staging "
            f"JSON. Raw response saved to: {raw_response_path}. "
            f"Parse error saved to: {parse_error_path}"
        ) from exc

    modules = normalize_modules(
        data["modules"],
        source_file=source_file,
        study_program=item.get("study_program"),
        study_program_code=item.get("study_program_code"),
    )
    duration_seconds = time.perf_counter() - started_at

    stage_input_dir = write_stage_input_files(
        output_root=args.stage_input_output_dir,
        import_id=item["import_id"],
        source_file=source_file,
        raw_response=raw,
        parsed_response=data,
        normalized_modules=modules,
        duration_seconds=duration_seconds,
    )
    print(f"Stage input files: {stage_input_dir}")
    print(f"Import duration: {format_duration(duration_seconds)}")

    return modules


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate module_import_staging SQLite DB directly with the LLM API."
    )
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--import-id", help="Import id from import_manifest.yaml.")
    source.add_argument("--all", action="store_true", help="Run every manifest import.")
    source.add_argument("--pdf", type=Path, help="Custom PDF path.")

    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST_PATH)
    parser.add_argument("--db", type=Path, default=DEFAULT_DB_PATH)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT_PATH)
    parser.add_argument(
        "--stage-input-output-dir",
        type=Path,
        default=DEFAULT_STAGE_INPUT_OUTPUT_DIR,
        help="Root folder for per-PDF JSON files written before SQLite staging.",
    )
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
        default=240,
        help="LLM API request timeout in seconds.",
    )
    parser.add_argument("--max-input-chars", type=int, default=DEFAULT_MAX_INPUT_CHARS)
    return parser.parse_args()


def main() -> None:
    total_started_at = time.perf_counter()
    args = parse_args()
    imports = resolve_imports(args)
    all_modules = []
    multiple_imports = len(imports) > 1

    for item in imports:
        print(f"Running one-shot LLM import: {item['import_id']}")
        modules = run_import(item, args, multiple_imports=multiple_imports)
        print(f"Extracted modules: {len(modules)}")
        all_modules.extend(modules)

    write_staging_db(args.db, all_modules, append=args.append)
    if args.report:
        write_report_csv(args.report, all_modules)

    mode = "appended to" if args.append else "written to"
    print(f"Total staged modules: {len(all_modules)}")
    print(f"Total duration: {format_duration(time.perf_counter() - total_started_at)}")
    print(f"DB {mode}: {args.db}")
    if args.report:
        print(f"Report written to: {args.report}")


if __name__ == "__main__":
    main()
