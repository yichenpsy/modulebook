from pathlib import Path
import json
from pydantic import ValidationError

from module_schema_step3 import ExtractedModule

BASE_DIR = Path(__file__).parent

INPUT_PATH = BASE_DIR / "output" / "llm_extracted_B01.json"
OUTPUT_PATH = BASE_DIR / "output" / "validated_B01.json"


def load_json(path: Path) -> dict:
    """
    Load the LLM output as JSON.

    This checks the first basic requirement:
    Is the LLM output valid JSON?
    """
    if not path.exists():
        raise FileNotFoundError(f"Input file not found: {path}")

    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise ValueError(
            f"Invalid JSON in {path}\n"
            f"Error: {error}"
        )


def validate_module(data: dict) -> ExtractedModule:
    """
    Validate the JSON data against the predefined Pydantic schema.
    """
    try:
        return ExtractedModule.model_validate(data)
    except ValidationError as error:
        print("Validation failed.")
        print(error)
        raise


def save_validated_module(module: ExtractedModule, path: Path) -> None:
    """
    Save the validated module as clean JSON.
    """
    path.write_text(
        json.dumps(
            module.model_dump(mode="json"),
            ensure_ascii=False,
            indent=2
        ),
        encoding="utf-8"
    )


def main():
    raw_data = load_json(INPUT_PATH)
    validated_module = validate_module(raw_data)
    save_validated_module(validated_module, OUTPUT_PATH)

    print("Validation passed.")
    print(f"Module: {validated_module.module_number}")
    print(f"Title: {validated_module.title_de}")
    print(f"Credits: {validated_module.credits}")
    print(f"Status: {validated_module.status}")
    print(f"Source page: {validated_module.source_page}")
    print(f"Saved validated JSON to: {OUTPUT_PATH}")


if __name__ == "__main__":
    main()