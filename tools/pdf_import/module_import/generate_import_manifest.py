import argparse
import re
import time
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


def load_existing_manifest() -> dict:
    if not MANIFEST_PATH.exists():
        return {"imports": {}}

    with MANIFEST_PATH.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {"imports": {}}


def build_manifest(existing_manifest: dict | None = None):
    pdf_files = sorted(INPUT_DIR.glob("*.pdf"))

    if not pdf_files:
        raise FileNotFoundError(f"No PDF files found in {INPUT_DIR}")

    existing_imports = {}
    if existing_manifest is not None:
        existing_imports = existing_manifest.get("imports", {})

    imports = {}

    for pdf_path in pdf_files:
        import_id = slugify_filename(pdf_path.name)

        default_item = {
            "source_file": str(pdf_path),
            "study_program_code": import_id,
            "study_program": guess_study_program_name(pdf_path.name),
            "expected_module_count": None,
        }

        # Preserve manually edited fields, especially expected_module_count.
        existing_item = existing_imports.get(import_id, {})
        imports[import_id] = {
            **default_item,
            **existing_item,
            "source_file": str(pdf_path),
        }

    return {
        "imports": imports
    }


def write_manifest(manifest: dict) -> None:
    MANIFEST_PATH.parent.mkdir(parents=True, exist_ok=True)

    with MANIFEST_PATH.open("w", encoding="utf-8") as f:
        yaml.safe_dump(
            manifest,
            f,
            allow_unicode=True,
            sort_keys=False,
        )


def sync_manifest() -> dict:
    existing_manifest = load_existing_manifest()
    manifest = build_manifest(existing_manifest=existing_manifest)
    write_manifest(manifest)
    return manifest


def get_input_snapshot() -> tuple[tuple[str, int, int], ...]:
    return tuple(
        sorted(
            (str(path), path.stat().st_mtime_ns, path.stat().st_size)
            for path in INPUT_DIR.glob("*.pdf")
        )
    )


def print_manifest_summary(manifest: dict) -> None:
    print(f"Manifest written: {MANIFEST_PATH}")
    print(f"PDFs found: {len(manifest['imports'])}")

    for import_id, item in manifest["imports"].items():
        print(f"- {import_id}: {item['source_file']}")


def watch_manifest(interval_seconds: float) -> None:
    last_snapshot = None

    while True:
        current_snapshot = get_input_snapshot()

        if current_snapshot != last_snapshot:
            manifest = sync_manifest()
            print_manifest_summary(manifest)
            last_snapshot = current_snapshot

        time.sleep(interval_seconds)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--watch",
        action="store_true",
        help="Keep watching data/input and update import_manifest.yaml when PDFs change.",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=2.0,
        help="Polling interval in seconds for --watch mode.",
    )
    return parser.parse_args()


def main():
    args = parse_args()

    if args.watch:
        watch_manifest(interval_seconds=args.interval)
        return

    manifest = sync_manifest()
    print_manifest_summary(manifest)


if __name__ == "__main__":
    main()
