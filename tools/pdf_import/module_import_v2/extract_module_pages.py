from pathlib import Path
import json
import yaml

pdf_name = "Data-Engineering_2025"

PAGES_JSON = Path(
    f"tools/pdf_import/data/work/{pdf_name}/pages_markdown.json"
)

BASE_YAML = Path(
    "tools/pdf_import/configs/base.yaml"
)


def load_pages(pages_json: Path):
    with pages_json.open("r", encoding="utf-8") as file:
        pages = json.load(file)

    return pages


def load_field_label_map(base_yaml: Path):
    with base_yaml.open("r", encoding="utf-8") as file:
        config = yaml.safe_load(file)

    normalized_field_map = config["normalized_field_map"]

    return normalized_field_map

# print(label_to_field["Modulnummer"]) -> module_number
def build_label_to_field_map(field_label_map: dict):
    label_to_field = {}

    for field_name, labels in field_label_map.items():
        for label in labels:
            label_to_field[label] = field_name

    return label_to_field

## Markdown table row

# is_markdown_table_row("| Modulnummer | B01 |") -> True
# is_markdown_table_row("| Modulnummer |  |") -> True
def is_markdown_table_row(line: str):
    stripped = line.strip()

    return stripped.startswith("|") and stripped.endswith("|")

# split_markdown_table_row("| Modulnummer | B01 |") -> ['Modulnummer', 'B01']
def split_markdown_table_row(line: str):
    stripped = line.strip()

    cells = stripped.strip("|").split("|")

    cleaned_cells = []
    for cell in cells:
        cleaned_cells.append(cell.strip())

    return cleaned_cells

# is_separator_row(["---", "---"]) -> True
def is_separator_row(cells: list[str]):
    for cell in cells:
        cleaned = cell.replace("-", "").replace(":", "").strip()

        if cleaned != "":
            return False

    return True

"""
helper

"Modulnummer Titel" -> ["module_number", "title"]
"Titel Title" -> ["title"]

"""
def extract_field_names_from_cell(cell: str, label_to_field: dict):
    matched_fields = set()

    for label, field_name in label_to_field.items():
        if label in cell:
            matched_fields.add(field_name)

    return matched_fields

"""
input: match_label_content_row("| Modulnummer | B01 |", label_to_field)
output: "module_number"

"""
def extract_fields_from_label_content_row(line: str, label_to_field: dict):
    matched_fields = set()
    unmatched_labels = []
    
    if not is_markdown_table_row(line):
        return set(), []

    cells = split_markdown_table_row(line)

    if is_separator_row(cells):
        return set(), []

    if len(cells) < 2:
        return set(), []

    label_cell = cells[0]
    content_cell = cells[1]

    matched_fields = extract_field_names_from_cell(label_cell, label_to_field)

    if not matched_fields:
        unmatched_labels.append(label_cell)

    return matched_fields, unmatched_labels

"""
input: 
| Modulnummer | B01 |
| Titel | Data Engineering |
| Leistungspunkte | 5 |
| Unbekanntes Feld | xyz |

output:
{'module_number', 'title', 'credits'}
['Unbekanntes Feld']
"""
def analyze_module_field_labels_in_page(markdown_text: str, label_to_field: dict):
    matched_fields = set()
    unmatched_label_cells = []

    lines = markdown_text.splitlines()

    for line in lines:
        field_names, unmatched = extract_fields_from_label_content_row(line, label_to_field)

        matched_fields.update(field_names)
        unmatched_label_cells.extend(unmatched)

    return matched_fields, unmatched_label_cells

# helper
def order_fields_by_base_map(matched_fields: set[str], field_label_map: dict):
    ordered_fields = []

    for field_name in field_label_map.keys():
        if field_name in matched_fields:
            ordered_fields.append(field_name)

    return ordered_fields

def find_first_module_page(pages: list[dict], label_to_field: dict, field_label_map: dict, min_field_count: int = 5):
    for page in pages:
        markdown_text = page.get("raw_markdown")

        matched_fields, unmatched_label_cells = analyze_module_field_labels_in_page(
            markdown_text,
            label_to_field
        )

        if len(matched_fields) >= min_field_count:
            return {
                "first_module_page_number": page["page_number"],
                "matched_field_count": len(matched_fields),
                "matched_fields":  order_fields_by_base_map(matched_fields, field_label_map),
                "unmatched_label_cells": unmatched_label_cells
            }

    return None

## find the last module page

def get_two_column_table_rows(markdown_text: str):
    rows = []

    lines = markdown_text.splitlines()

    for line in lines:
        if not is_markdown_table_row(line):
            continue

        cells = split_markdown_table_row(line)

        if is_separator_row(cells):
            continue

        if len(cells) != 2 :
            continue

        rows.append(cells)

    return rows

def extract_fields_from_first_column(markdown_text: str, label_to_field: dict):
    matched_fields = set()

    table_rows = get_two_column_table_rows(markdown_text)

    for cells in table_rows:
        first_cell = cells[0]

        field_names = extract_field_names_from_cell(
            first_cell,
            label_to_field
        )

        matched_fields.update(field_names)

    return matched_fields


def find_last_module_page(
    pages: list[dict],
    label_to_field: dict,
    field_label_map: dict,
    min_field_count: int = 2
):
    for page_index in range(len(pages) - 1, -1, -1):
        page = pages[page_index]

        markdown_text = page.get("raw_markdown") or page.get("raw_text") or ""

        current_table_rows = get_two_column_table_rows(markdown_text)

        if not current_table_rows:
            continue

        current_fields = extract_fields_from_first_column(
            markdown_text,
            label_to_field
        )

        previous_fields = set()
        previous_page_number = None

        if page_index > 0:
            previous_page = pages[page_index - 1]
            previous_page_number = previous_page["page_number"]

            previous_markdown_text = (
                previous_page.get("raw_markdown")
            )

            previous_fields = extract_fields_from_first_column(
                previous_markdown_text,
                label_to_field
            )

        if len(current_fields) >= min_field_count or len(previous_fields) >= min_field_count:
            return {
                "last_page_number": page["page_number"],
                "current_page_field_count": len(current_fields),
                "current_page_fields": order_fields_by_base_map(
                    current_fields,
                    field_label_map
                ),
                # "previous_page_number": previous_page_number,
                # "previous_page_field_count": len(previous_fields),
                # "previous_page_fields": order_fields_by_base_map(
                #     previous_fields,
                #     field_label_map
                # ),
            }

    return None


def main():
    pages = load_pages(PAGES_JSON)

    field_label_map = load_field_label_map(BASE_YAML)

    label_to_field = build_label_to_field_map(field_label_map)

    first_module_page = find_first_module_page(
        pages,
        label_to_field,
        field_label_map,
        min_field_count=10
    )
    last_module_page = find_last_module_page(
        pages,
        label_to_field,
        field_label_map,
        min_field_count=2
    )

    result = {
        "first_module_page": first_module_page,
        "last_module_page": last_module_page,
    }
    print(result)


if __name__ == "__main__":
    main()
