from pathlib import Path
import json
import yaml
import csv

pdf_name = "Data-Engineering_2025"

PAGES_JSON = Path("tools/pdf_import/data/work") / pdf_name / "pages_markdown.json"
BASE_YAML = Path("tools/pdf_import/configs/base.yaml")
BOUNDARY_PAGES_CSV = (
    Path("tools/pdf_import/data/work")
    / pdf_name
    / "module_boundary_pages.csv"
)


def load_pages(pages_json: Path):
    """Load the list of extracted PDF pages from a JSON file."""
    with pages_json.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_field_label_map(base_yaml: Path):
    """Load the normalized field label mapping from the base YAML config."""
    with base_yaml.open("r", encoding="utf-8") as file:
        config = yaml.safe_load(file)

    return config["normalized_field_map"]


def build_label_to_field_map(field_label_map: dict):
    """
    Build a lookup map from original field labels to normalized field names.

    Example:
    label_to_field["Modulnummer"] -> "module_number"
    """
    label_to_field = {}

    for field_name, labels in field_label_map.items():
        for label in labels:
            label_to_field[label] = field_name

    return label_to_field


# -------------------------------------------------------------------
# Markdown table row helpers
# -------------------------------------------------------------------

def is_markdown_table_row(line: str):
    """
    Check whether a line looks like a Markdown table row.

    Example:
    "| Modulnummer | B01 |" -> True
    """
    stripped = line.strip()
    return stripped.startswith("|") and stripped.endswith("|")


def split_markdown_table_row(line: str):
    """
    Split a Markdown table row into cells.

    Example:
    "| Modulnummer | B01 |" -> ["Modulnummer", "B01"]
    """
    stripped = line.strip()
    return [cell.strip() for cell in stripped.strip("|").split("|")]


def is_separator_row(cells: list[str]):
    """
    Check whether a Markdown table row is only a separator row.

    Example:
    ["---", "---"] -> True
    """
    return all(cell.replace("-", "").replace(":", "").strip() == "" for cell in cells)


def get_page_markdown(page: dict):
    """
    Return the Markdown text of a page.

    Some extraction steps may store the page content under raw_markdown,
    while others may use raw_text.
    """
    return page.get("raw_markdown") or page.get("raw_text") or ""


# -------------------------------------------------------------------
# Field label extraction
# -------------------------------------------------------------------

def extract_field_names_from_cell(cell: str, label_to_field: dict):
    """
    Extract all normalized field names whose labels appear in the given cell.

    Example:
    "Modulnummer Titel" -> {"module_number", "title"}
    "Titel Title" -> {"title"}
    """
    matched_fields = set()

    for label, field_name in label_to_field.items():
        if label in cell:
            matched_fields.add(field_name)

    return matched_fields


def extract_fields_from_label_content_row(line: str, label_to_field: dict):
    """
    Extract normalized field names from a two-column label-content table row.

    Example:
    "| Modulnummer | B01 |" -> {"module_number"}, []

    If the first cell does not match any known field label,
    the first cell is returned as an unmatched label cell.
    """
    if not is_markdown_table_row(line):
        return set(), []

    cells = split_markdown_table_row(line)

    if is_separator_row(cells):
        return set(), []

    if len(cells) < 2:
        return set(), []

    label_cell = cells[0]
    matched_fields = extract_field_names_from_cell(label_cell, label_to_field)

    unmatched_label_cells = []

    if not matched_fields:
        unmatched_label_cells.append(label_cell)

    return matched_fields, unmatched_label_cells


def analyze_module_field_labels_in_page(markdown_text: str, label_to_field: dict):
    """
    Analyze all Markdown table rows on one page.

    Returns:
    - matched normalized fields
    - unmatched first-column cells
    """
    matched_fields = set()
    unmatched_label_cells = []

    for line in markdown_text.splitlines():
        field_names, unmatched = extract_fields_from_label_content_row(
            line,
            label_to_field,
        )

        matched_fields.update(field_names)
        unmatched_label_cells.extend(unmatched)

    return matched_fields, unmatched_label_cells


def order_fields_by_base_map(matched_fields: set[str], field_label_map: dict):
    """
    Order matched fields according to the order in base.yaml.
    """
    return [
        field_name
        for field_name in field_label_map.keys()
        if field_name in matched_fields
    ]


# -------------------------------------------------------------------
# Module page detection
# -------------------------------------------------------------------

def get_two_column_table_rows(markdown_text: str):
    """
    Extract all valid two-column Markdown table rows from a page.

    A valid row:
    - is a Markdown table row
    - is not a separator row
    - has exactly two columns
    """
    rows = []

    for line in markdown_text.splitlines():
        if not is_markdown_table_row(line):
            continue

        cells = split_markdown_table_row(line)

        if is_separator_row(cells):
            continue

        if len(cells) < 2:
            continue

        rows.append(cells)

    return rows


def is_module_page(
    page: dict,
    label_to_field: dict,
    min_field_count: int = 5,
):
    """
    Check whether a page looks like a module description page.

    Conditions:
    1. The page must contain at least one two-column Markdown table row.
    2. The first column must contain at least min_field_count known module fields.
    """
    markdown_text = get_page_markdown(page)

    two_column_rows = get_two_column_table_rows(markdown_text)

    if not two_column_rows:
        return False

    matched_fields, _ = analyze_module_field_labels_in_page(
        markdown_text,
        label_to_field,
    )

    return len(matched_fields) >= min_field_count


def build_module_page_result(
    page: dict,
    label_to_field: dict,
    field_label_map: dict,
):
    """
    Build a detailed analysis result for one page.

    This is separated from is_module_page because is_module_page should only
    return True or False.
    """
    markdown_text = get_page_markdown(page)

    matched_fields, unmatched_label_cells = analyze_module_field_labels_in_page(
        markdown_text,
        label_to_field,
    )

    return {
        "page_number": page["page_number"],
        "matched_field_count": len(matched_fields),
        "matched_fields": order_fields_by_base_map(matched_fields, field_label_map),
        "unmatched_label_cells": unmatched_label_cells,
    }


# -------------------------------------------------------------------
# First and last module page search
# -------------------------------------------------------------------

def find_first_module_page(
    pages: list[dict],
    label_to_field: dict,
    field_label_map: dict,
    min_field_count: int = 5,
):
    """
    Find the first module page by scanning pages from front to back.
    """
    for page in pages:
        if is_module_page(
            page,
            label_to_field,
            min_field_count=min_field_count,
        ):
            page_result = build_module_page_result(
                page,
                label_to_field,
                field_label_map,
            )

            return {
                "first_module_page_number": page_result["page_number"],
                "matched_field_count": page_result["matched_field_count"],
                "matched_fields": page_result["matched_fields"],
                "unmatched_label_cells": page_result["unmatched_label_cells"],
            }

    return None


def find_last_module_page(
    pages: list[dict],
    label_to_field: dict,
    field_label_map: dict,
    min_field_count: int = 2,
):
    """
    Find the last module page by scanning pages from back to front.

    The logic is intentionally loose:
    - the current page is a module page, OR
    - the previous page is a module page

    If the previous page is a module page, the current page is still returned.
    This helps detect trailing continuation pages of the last module.
    """
    for page_index in range(len(pages) - 1, -1, -1):
        current_page = pages[page_index]

        current_is_module_page = is_module_page(
            current_page,
            label_to_field,
            min_field_count=min_field_count,
        )

        previous_page = None
        previous_is_module_page = False

        if page_index > 0:
            previous_page = pages[page_index - 1]
            previous_is_module_page = is_module_page(
                previous_page,
                label_to_field,
                min_field_count=min_field_count,
            )

        if current_is_module_page or previous_is_module_page:
            current_result = build_module_page_result(
                current_page,
                label_to_field,
                field_label_map,
            )

            result = {
                "last_module_page_number": current_page["page_number"],
                "current_page_is_module_page": current_is_module_page,
                "previous_page_is_module_page": previous_is_module_page,
                "current_page_field_count": current_result["matched_field_count"],
                "current_page_fields": current_result["matched_fields"],
                "current_page_unmatched_label_cells": current_result["unmatched_label_cells"],
            }

            if previous_page is not None:
                previous_result = build_module_page_result(
                    previous_page,
                    label_to_field,
                    field_label_map,
                )

                result["previous_page_number"] = previous_page["page_number"]
                result["previous_page_field_count"] = previous_result["matched_field_count"]
                result["previous_page_fields"] = previous_result["matched_fields"]
                result["previous_page_unmatched_label_cells"] = previous_result[
                    "unmatched_label_cells"
                ]

            return result

    return None


def write_boundary_pages_csv(
    output_csv: Path,
    pdf_name: str,
    first_module_page: dict | None,
    last_module_page: dict | None,
):
    """
    Write first and last module page results to a CSV file.
    This function directly uses the existing detection results.
    """
    rows = []

    if first_module_page is not None:
        rows.append({
            "pdf_name": pdf_name,
            "page_role": "first_module_page",
            "page_number": first_module_page["first_module_page_number"],
            "matched_field_count": first_module_page["matched_field_count"],
            "matched_fields": json.dumps(
                first_module_page.get("matched_fields", []),
                ensure_ascii=False,
            ),
            "unmatched_label_cells": json.dumps(
                first_module_page.get("unmatched_label_cells", []),
                ensure_ascii=False,
            ),
        })

    if last_module_page is not None:
        rows.append({
            "pdf_name": pdf_name,
            "page_role": "last_module_page",
            "page_number": last_module_page["last_module_page_number"],
            "matched_field_count": last_module_page["current_page_field_count"],
            "matched_fields": json.dumps(
                last_module_page.get("current_page_fields", []),
                ensure_ascii=False,
            ),
            "unmatched_label_cells": json.dumps(
                last_module_page.get("current_page_unmatched_label_cells", []),
                ensure_ascii=False,
            ),
        })

    output_csv.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "pdf_name",
        "page_role",
        "page_number",
        "matched_field_count",
        "matched_fields",
        "unmatched_label_cells",
    ]

    with output_csv.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

# -------------------------------------------------------------------
# Main script
# -------------------------------------------------------------------

def main():
    pages = load_pages(PAGES_JSON)

    field_label_map = load_field_label_map(BASE_YAML)
    label_to_field = build_label_to_field_map(field_label_map)

    first_module_page = find_first_module_page(
        pages,
        label_to_field,
        field_label_map,
        min_field_count=10,
    )

    last_module_page = find_last_module_page(
        pages,
        label_to_field,
        field_label_map,
        min_field_count=2,
    )

    write_boundary_pages_csv(
        BOUNDARY_PAGES_CSV,
        pdf_name,
        first_module_page,
        last_module_page,
    )

    result = {
        "first_module_page": first_module_page,
        "last_module_page": last_module_page,
    }

    print(json.dumps(result, ensure_ascii=False, indent=2))
    

    # pages = load_pages(PAGES_JSON)

    # field_label_map = load_field_label_map(BASE_YAML)
    # label_to_field = build_label_to_field_map(field_label_map)

    # page_58 = next(
    #     page for page in pages if page["page_number"] == 58
    # )

    # markdown_text = get_page_markdown(page_58)

    # table_rows = get_two_column_table_rows(markdown_text)

    # matched_fields, unmatched_label_cells = analyze_module_field_labels_in_page(
    #     markdown_text,
    #     label_to_field,
    # )

    # print("=" * 80)
    # print("Page:", page_58["page_number"])
    # print("Markdown length:", len(markdown_text))
    # print("Table row count:", len(table_rows))
    # print("Matched field count:", len(matched_fields))
    # print("Matched fields:", order_fields_by_base_map(matched_fields, field_label_map))
    # print("Unmatched label cells sample:", unmatched_label_cells[:20])
    # print("=" * 80)

    # print("\nRaw markdown table rows:")
    # for line in markdown_text.splitlines():
    #     if is_markdown_table_row(line):
    #         cells = split_markdown_table_row(line)
    #         print("-" * 40)
    #         print(line)
    #         print("Cell count:", len(cells))
    #         print("Cells:", cells)

if __name__ == "__main__":
    main()