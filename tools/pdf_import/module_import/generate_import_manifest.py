import re
from pathlib import Path

import yaml


ROOT = Path("tools/pdf_import")

INPUT_DIR = ROOT / "data/input"
MANIFEST_PATH = ROOT / "configs/import_manifest.yaml"


def slugify_filename(filename):
    """
    Convert PDF filename into a stable import_id.

    Example:
    2024_11_Modulhandbuch_zur_Studien-_und_Pruefungsordnung_2017.pdf
    -> 2024_11_modulhandbuch_zur_studien_und_pruefungsordnung_2017
    """
    name = Path(filename).stem.lower()

    name = name.replace("ä", "ae")
    name = name.replace("ö", "oe")
    name = name.replace("ü", "ue")
    name = name.replace("ß", "ss")

    name = re.sub(r"[^a-z0-9]+", "_", name)
    name = re.sub(r"_+", "_", name)
    name = name.strip("_")

    return name


def guess_study_program_name(filename):
    """
    MVP version:
    Use filename stem as readable name.
    Later you can improve this manually or by parsing PDF title pages.
    """
    return Path(filename).stem


def build_manifest():
    pdf_files = sorted(INPUT_DIR.glob("*.pdf"))

    if not pdf_files:
        raise FileNotFoundError(f"No PDF files found in {INPUT_DIR}")

    imports = {}

    for pdf_path in pdf_files:
        import_id = slugify_filename(pdf_path.name)

        imports[import_id] = {
            "source_file": str(pdf_path),
            "study_program_code": import_id,
            "study_program": guess_study_program_name(pdf_path.name),
            "expected_module_count": None,
        }

    return {
        "imports": imports
    }


def main():
    manifest = build_manifest()

    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)

    with MANIFEST_PATH.open("w", encoding="utf-8") as f:
        yaml.safe_dump(
            manifest,
            f,
            allow_unicode=True,
            sort_keys=False,
        )

    print(f"Manifest written: {MANIFEST_PATH}")
    print(f"PDFs found: {len(manifest['imports'])}")

    for import_id, item in manifest["imports"].items():
        print(f"- {import_id}: {item['source_file']}")


if __name__ == "__main__":
    main()