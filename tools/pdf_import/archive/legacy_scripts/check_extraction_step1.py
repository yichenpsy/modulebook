from pathlib import Path
import json
import re

BASE_DIR = Path(__file__).parent
OUTPUT_PATH = BASE_DIR / "output" / "modulehandbook_pages_B01_B03.json"

EXPECTED_MODULES = {
    6: {
        "module_number": "B01",
        "title_contains": "Mathematik I / Mathematics 1",
        "credits_contains": "5 LP",
    },
    7: {
        "module_number": "B02",
        "title_contains": "Grundlagen der Theoretischen Informatik",
        "credits_contains": "5 LP",
    },
    8: {
        "module_number": "B03",
        "title_contains": "Mediendesign Grundlagen",
        "credits_contains": "5 LP",
    },
}

# Field labels visible in your PDF screenshot for B01.
# These are the labels we expect on each normal module page.
REQUIRED_FIELD_LABELS = [
    "Modulnummer",
    "Titel",
    "Leistungspunkte",
    "Workload",
    "Lerngebiet",
    "Lernziele / Kompetenzen",
    "Voraussetzungen",
    "Niveaustufe",
    "Lehrform",
    "Status",
    "Häufigkeit des Angebotes",
    "Prüfungsform",
    "Ermittlung der Modulnote",
    "Anerkannte Module",
    "Inhalte",
    "Literatur",
    "Weitere Hinweise",
]


def normalize_text(text: str) -> str:
    """
    Normalize whitespace so that small line-break differences do not break the check.
    """
    text = text.replace("\u00a0", " ")
    text = re.sub(r"[ \t]+", " ", text)
    return text.strip()


def assert_contains(text: str, expected: str, context: str) -> None:
    if expected not in text:
        raise AssertionError(f"Missing '{expected}' in {context}")


def main():
    if not OUTPUT_PATH.exists():
        raise FileNotFoundError(f"Output file not found: {OUTPUT_PATH}")

    pages = json.loads(OUTPUT_PATH.read_text(encoding="utf-8"))

    print(f"Loaded {len(pages)} extracted pages")

    # Check 1: exactly 3 pages
    assert len(pages) == 3, f"Expected 3 pages, got {len(pages)}"

    # Check 2: correct page numbers
    pages_by_number = {page["page_number"]: page for page in pages}
    expected_page_numbers = list(EXPECTED_MODULES.keys())

    assert sorted(pages_by_number.keys()) == expected_page_numbers, (
        f"Expected pages {expected_page_numbers}, got {sorted(pages_by_number.keys())}"
    )

    # Check 3: each page has the expected module and all field labels
    for page_number, expected in EXPECTED_MODULES.items():
        page = pages_by_number[page_number]
        text = normalize_text(page["text"])
        context = f"page {page_number}"

        print(f"Checking {context}...")

        assert_contains(text, expected["module_number"], context)
        assert_contains(text, expected["title_contains"], context)
        assert_contains(text, expected["credits_contains"], context)

        for label in REQUIRED_FIELD_LABELS:
            assert_contains(text, label, context)

    print("Extraction check passed.")
    print("Checked pages:", expected_page_numbers)
    print("Checked modules:", [v["module_number"] for v in EXPECTED_MODULES.values()])
    print("Checked required field labels:")
    for label in REQUIRED_FIELD_LABELS:
        print(f"- {label}")


if __name__ == "__main__":
    main()