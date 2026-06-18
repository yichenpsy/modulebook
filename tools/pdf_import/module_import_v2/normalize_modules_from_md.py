import json
import re
from pathlib import Path
from collections import Counter

import yaml

pdf_name = "medieninformatik_2024"
WORK_DIR = Path("tools/pdf_import/data/work") / pdf_name

BASE_PATH = Path("tools/pdf_import/configs/base.yaml")



INPUT_PATH = WORK_DIR / "module_blocks_md.json"
OUTPUT_PATH = WORK_DIR / "normalized_modules.json"


def load_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )


def load_field_map(base_path):
    base = yaml.safe_load(base_path.read_text(encoding="utf-8"))
    return base["normalized_field_map"]


def clean_cell(text):
    text = str(text)
    text = text.replace("<br>", "\n").replace("<br/>", "\n")
    text = re.sub(r"\*\*", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def normalize_label(text):
    text = clean_cell(text).lower()
    text = text.replace("/", " ")
    text = re.sub(r"[^a-zäöüß0-9]+", " ", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def parse_markdown_rows(markdown):
    """Parse pipe-delimited Markdown table rows into (label, value) pairs."""

    rows = []

    for line in markdown.splitlines():
        line = line.strip()

        if not line.startswith("|"):
            continue

        # Clean cells early to handle <br>, **, whitespace
        raw_cells = [c.strip() for c in line.strip("|").split("|")]
        cells = [clean_cell(c) for c in raw_cells]

        if len(cells) < 2:
            continue

        # skip markdown separator row: |---|---|
        if all(re.fullmatch(r":?-{3,}:?", c.strip()) for c in cells):
            continue

        label = cells[0]
        value = " | ".join(cells[1:]).strip()

        if not label or not value:
            continue

        rows.append((label, value))

    return rows


def get_module_markdown(module_block):
    """
    Keep this flexible because module_blocks_md.json may use different key names.
    """
    parts = []

    for key in ["markdown", "module_markdown", "table_markdown", "raw_text", "raw_markdown"]:
        value = module_block.get(key)
        if isinstance(value, str):
            parts.append(value)

    tables = module_block.get("tables")
    if isinstance(tables, list):
        for table in tables:
            if isinstance(table, str):
                parts.append(table)
            elif isinstance(table, dict):
                for key in ["markdown", "table_markdown", "raw_text"]:
                    if isinstance(table.get(key), str):
                        parts.append(table[key])

    return "\n\n".join(parts)

"""
English Version hier change!

"""

def find_matching_fields(label_cell, field_map):
    label_norm = normalize_label(label_cell)
    matched_fields = []

    for field_name, labels in field_map.items():
        for label in labels:
            base_label_norm = normalize_label(label)

            if not base_label_norm:
                continue

            # exact match or label appears inside a longer label cell
            if label_norm == base_label_norm or base_label_norm in label_norm:
                matched_fields.append(field_name)
                break

    return matched_fields


def add_value(module, field_name, value):
    if module.get(field_name) in [None, ""]:
        module[field_name] = value
    else:
        module[field_name] += "\n" + value


def normalize_credits(value):
    if value is None:
        return None

    match = re.search(r"\d+(?:[,.]\d+)?", str(value))
    if not match:
        return value

    number = match.group(0).replace(",", ".")
    return float(number)


def normalize_one_module(module_block, field_map):
    normalized = {field_name: None for field_name in field_map.keys()}
    normalized["additional_fields"] = {}

    # keep source metadata if available
    normalized["source_file"] = module_block.get("source_file")
    normalized["source_pages"] = module_block.get(
        "source_pages",
        module_block.get("pages")
    )

    markdown = get_module_markdown(module_block)
    rows = parse_markdown_rows(markdown)

    matched_count = 0
    unmatched_labels = []

    for label, value in rows:
        matched_fields = find_matching_fields(label, field_map)

        if not matched_fields:
            normalized["additional_fields"][label] = value
            unmatched_labels.append(label)
            continue

        for field_name in matched_fields:
            add_value(normalized, field_name, value)
            matched_count += 1

    # simple database-friendly cleanup
    if "credits" in normalized:
        normalized["credits"] = normalize_credits(normalized["credits"])

    return normalized, matched_count, unmatched_labels


def main():
    field_map = load_field_map(BASE_PATH)
    module_blocks = load_json(INPUT_PATH)

    normalized_modules = []
    total_matched = 0
    all_unmatched_labels = []

    for module_block in module_blocks:
        normalized, matched_count, unmatched_labels = normalize_one_module(
            module_block,
            field_map
        )

        normalized_modules.append(normalized)
        total_matched += matched_count
        all_unmatched_labels.extend(unmatched_labels)

    write_json(OUTPUT_PATH, normalized_modules)

    print(f"Written: {OUTPUT_PATH}")
    print(f"Modules: {len(normalized_modules)}")
    print(f"Matched rows: {total_matched}")
    print(f"Unmatched rows: {len(all_unmatched_labels)}")

    if all_unmatched_labels:
        print("\nUnmatched labels:")
        for label, count in Counter(all_unmatched_labels).most_common():
            print(f"- {label}: {count}")


if __name__ == "__main__":
    main()