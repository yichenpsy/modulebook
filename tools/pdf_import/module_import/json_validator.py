import json
from pathlib import Path
from jsonschema import Draft202012Validator

from tools.pdf_import.module_import.config import PDF_IMPORT_ROOT


def load_module_schema() -> dict:
    schema_path = PDF_IMPORT_ROOT / "schemas" / "module_schema_v1.json"

    with schema_path.open("r", encoding="utf-8") as f:
        return json.load(f)


def clean_llm_json_text(text: str) -> str:
    """
    Remove common wrappers or reasoning blocks from LLM output.
    Then extract the JSON object.
    """
    text = text.strip()

    # Remove <think>...</think> blocks if the model outputs reasoning
    if "</think>" in text:
        text = text.split("</think>", 1)[1].strip()

    # Remove Markdown wrappers
    if text.startswith("```json"):
        text = text.removeprefix("```json").strip()

    if text.startswith("```"):
        text = text.removeprefix("```").strip()

    if text.endswith("```"):
        text = text.removesuffix("```").strip()

    # Extract JSON object if there is extra text before or after it
    first_brace = text.find("{")
    last_brace = text.rfind("}")

    if first_brace != -1 and last_brace != -1 and last_brace > first_brace:
        text = text[first_brace:last_brace + 1]

    return text.strip()


def parse_llm_json(raw_output: str) -> tuple[dict | None, str | None]:
    """
    Parse raw LLM output into JSON.

    Returns:
    (data, error)
    """
    cleaned = clean_llm_json_text(raw_output)

    try:
        data = json.loads(cleaned)
        return data, None
    except json.JSONDecodeError as error:
        return None, str(error)


def validate_extracted_module(module_data: dict, schema: dict | None = None) -> dict:
    """
    Validate extracted module JSON against schema and simple semantic checks.
    """

    if schema is None:
        schema = load_module_schema()

    errors = []
    warnings = []

    validator = Draft202012Validator(schema)

    for error in sorted(validator.iter_errors(module_data), key=lambda e: e.path):
        path = ".".join(str(part) for part in error.path)
        if path:
            errors.append(f"{path}: {error.message}")
        else:
            errors.append(error.message)

    # Extra semantic checks
    title_de = module_data.get("title_de")
    if isinstance(title_de, str) and not title_de.strip():
        errors.append("title_de is empty")

    contents = module_data.get("contents")
    if isinstance(contents, list):
        if len(contents) == 0:
            warnings.append("contents is empty")
        elif total_text_length(contents) < 50:
            warnings.append("contents is suspiciously short")

    literature = module_data.get("literature")
    if isinstance(literature, list):
        if len(literature) == 0:
            warnings.append("literature is empty")
        elif total_text_length(literature) < 20:
            warnings.append("literature is suspiciously short")

    additional_fields = module_data.get("additional_fields")
    if isinstance(additional_fields, dict) and len(additional_fields) > 0:
        warnings.append("additional_fields is not empty")

    validation_status = "valid" if not errors else "need_review"

    return {
        "validation_status": validation_status,
        "validation_errors": errors,
        "validation_warnings": warnings,
    }


def total_text_length(values: list) -> int:
    return sum(len(str(value).strip()) for value in values)

    

def validate_json_file(path: str | Path) -> dict:
    """
    Load one extracted module JSON file and validate it.
    """

    path = Path(path)

    with path.open("r", encoding="utf-8") as f:
        module_data = json.load(f)

    return validate_extracted_module(module_data)


def validate_llm_raw_file(path: str | Path) -> dict:
    """
    Load one raw LLM output text file, parse JSON, and validate it.
    """

    path = Path(path)

    with path.open("r", encoding="utf-8") as f:
        raw_output = f.read()

    module_data, parse_error = parse_llm_json(raw_output)

    if parse_error is not None:
        return {
            "validation_status": "need_review",
            "validation_errors": [f"JSON parse error: {parse_error}"],
            "validation_warnings": [],
        }

    return validate_extracted_module(module_data)

if __name__ == "__main__":
    raw_path = PDF_IMPORT_ROOT / "data" / "review" / "llm_raw_architektur_2021_B01.txt"

    result = validate_llm_raw_file(raw_path)

    print(json.dumps(result, ensure_ascii=False, indent=2))