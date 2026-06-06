from pathlib import Path
import json
import re

BASE_DIR = Path(__file__).parent

INPUT_PATH = BASE_DIR / "output" / "modulehandbook_pages_B01_B03.json"
OUTPUT_PATH = BASE_DIR / "output" / "module_blocks_B01_B03.json"


def extract_module_number(text: str) -> str:
    """
    Extract module number from a module page.

    Expected pattern in extracted text:

    Modulnummer
    B01
    """
    pattern = r"Modulnummer\s+([A-Z]{1,3}\d{2})"
    match = re.search(pattern, text)

    if not match:
        raise ValueError("Could not find module number in text")

    return match.group(1)


def remove_page_header(text: str) -> str:
    """
    Remove simple page header if present.

    Example header:
    Modulhandbuch für den Bachelor-Studiengang Medieninformatik
             6

    This is not perfect general cleaning.
    It is only enough for the MVP.
    """
    lines = text.splitlines()

    cleaned_lines = []
    for line in lines:
        stripped = line.strip()

        if stripped.startswith("Modulhandbuch für den Bachelor-Studiengang"):
            continue

        if stripped.isdigit():
            continue

        cleaned_lines.append(line)

    return "\n".join(cleaned_lines).strip()


def split_pages_into_module_blocks(pages: list[dict]) -> list[dict]:
    module_blocks = []

    for page in pages:
        raw_text = page["text"]
        cleaned_text = remove_page_header(raw_text)
        module_number = extract_module_number(cleaned_text)

        module_blocks.append({
            "module_number": module_number,
            "source_file": page["source_file"],
            "source_page": page["page_number"],
            "raw_text": cleaned_text,
        })

    return module_blocks


def main():
    if not INPUT_PATH.exists():
        raise FileNotFoundError(f"Input file not found: {INPUT_PATH}")

    pages = json.loads(INPUT_PATH.read_text(encoding="utf-8"))

    module_blocks = split_pages_into_module_blocks(pages)

    OUTPUT_PATH.write_text(
        json.dumps(module_blocks, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print(f"Created {len(module_blocks)} module blocks")
    print(f"Saved to: {OUTPUT_PATH}")

    for block in module_blocks:
        print("-" * 60)
        print(f"Module: {block['module_number']}")
        print(f"Source page: {block['source_page']}")
        print(block["raw_text"][:300])


if __name__ == "__main__":
    main()