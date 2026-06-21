from pathlib import Path
import json
import re
import yaml


BASE_YAML = Path("tools/pdf_import/configs/base.yaml")


def load_pages(path: Path):
    """Load extracted page Markdown data."""
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_base_config(path: Path):
    """Load the full base YAML config."""
    with path.open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def write_json(path: Path, data):
    """Write data to a JSON file."""
    path.parent.mkdir(parents=True, exist_ok=True)

    with path.open("w", encoding="utf-8") as file:
        json.dump(data, file, ensure_ascii=False, indent=2)


def get_page_markdown(page: dict):
    """Get Markdown text from one page."""
    return page.get("raw_markdown") or page.get("raw_text") or ""


def filter_module_pages(
    pages: list[dict],
    first_page_number: int | None,
    last_page_number: int | None,
):
    """Keep only pages between first and last module page."""
    if first_page_number is None or last_page_number is None:
        return pages

    return [
        page
        for page in pages
        if first_page_number <= page["page_number"] <= last_page_number
    ]


def is_markdown_table_row(line: str):
    """Check whether a line looks like a Markdown table row."""
    stripped = line.strip()
    return stripped.startswith("|") and stripped.endswith("|")


def split_markdown_table_row(line: str):
    """
    Split a Markdown table row into cells.

    Empty cells are preserved because some real table cells may be empty.
    """
    stripped = line.strip()
    return [cell.strip() for cell in stripped.strip("|").split("|")]


def is_separator_row(cells: list[str]):
    """Check whether a Markdown table row is a separator row."""
    return all(
        cell.replace("-", "").replace(":", "").strip() == ""
        for cell in cells
    )


def clean_cell_text(cell: str):
    """Remove simple Markdown formatting from a cell."""
    return (
        cell.replace("**", "")
        .replace("<br>", " ")
        .replace("<br/>", " ")
        .replace("<br />", " ")
        .strip()
    )


def normalize_label_text(text: str):
    """Normalize a label cell for conservative module-start matching."""
    text = clean_cell_text(text).lower()
    text = re.sub(r"[^a-zäöüß0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def cell_starts_with_module_number_label(cell: str, module_number_labels: list[str]):
    """
    Check whether a cell is a real module-number label.

    Some PDFs produce a duplicated first table row containing the whole module
    text. That row contains "Modulnummer" somewhere in the cell, but it is not
    a label cell and must not start a new module.
    """
    cell_norm = normalize_label_text(cell)

    for label in module_number_labels:
        label_norm = normalize_label_text(label)

        if cell_norm == label_norm or cell_norm.startswith(label_norm + " "):
            return True

    return False


def matches_module_start_pattern(
    label_cell: str,
    value_cell: str,
    module_start_patterns: list[str],
):
    """Validate a label/value pair with base.yaml module_start_patterns."""
    label = clean_cell_text(label_cell).strip("| ")
    value = clean_cell_text(value_cell).strip("| ")

    if not value or len(value) > 40:
        return None

    text = re.sub(r"\s+", " ", f"{label} {value}").strip()

    for pattern in module_start_patterns:
        match = re.search(pattern, text)

        if match:
            return match.group(1)

    return None


def is_duplicated_page_wide_row(cells: list[str]):
    """
    Detect a PyMuPDF4LLM artifact where the whole module table is duplicated
    into a first two-column row before the real table rows.
    """
    if len(cells) != 2:
        return False

    left = clean_cell_text(cells[0])
    right = clean_cell_text(cells[1])

    if left != right:
        return False

    if len(left) < 200:
        return False

    return "Modulnummer" in left or "Module ID" in left


def find_first_non_empty_cell_after(cells: list[str], start_index: int):
    """Find the first non-empty cell after a given cell index."""
    for cell in cells[start_index + 1:]:
        cleaned = clean_cell_text(cell)

        if cleaned:
            return cleaned

    return None


def detect_module_start_from_line(
    line: str,
    module_number_labels: list[str],
    module_start_patterns: list[str],
):
    """
    Detect whether a line starts a new module.

    A module starts when a table row contains a module number label,
    for example "Modulnummer" or "Module ID".

    Returns the module number if found.
    Otherwise returns None.
    """
    if not is_markdown_table_row(line):
        return None

    cells = split_markdown_table_row(line)

    if is_separator_row(cells):
        return None

    for index, cell in enumerate(cells):
        if not cell_starts_with_module_number_label(cell, module_number_labels):
            continue

        module_number = find_first_non_empty_cell_after(cells, index)
        if module_number is None:
            continue

        matched_module_number = matches_module_start_pattern(
            cell,
            module_number,
            module_start_patterns,
        )

        if matched_module_number:
            return matched_module_number

    return None


def split_modules_from_pages(
    pages: list[dict],
    field_label_map: dict,
    module_start_patterns: list[str],
    source_file: str | None = None,
):
    """
    Split module pages into module blocks.

    A new block starts when a module_number label is found.
    """
    module_number_labels = field_label_map["module_number"]

    modules = []
    current_module = None

    for page in pages:
        page_number = page["page_number"]
        markdown_text = get_page_markdown(page)

        for line in markdown_text.splitlines():
            # Ignore all non-table lines.
            if not is_markdown_table_row(line):
                continue
            
            cells = split_markdown_table_row(line)

            # Ignore Markdown separator rows.
            if is_separator_row(cells):
                continue

            if is_duplicated_page_wide_row(cells):
                continue
            
            module_number = detect_module_start_from_line(
                line,
                module_number_labels,
                module_start_patterns,
            )

            if module_number:
                if current_module is not None:
                    modules.append(current_module)

                current_module = {
                    "module_number": module_number,
                    "source_file": source_file,
                    "source_pages": [page_number],
                    "raw_markdown": line,
                }

            else:
                if current_module is not None:
                    current_module["raw_markdown"] += "\n" + line

                    if page_number not in current_module["source_pages"]:
                        current_module["source_pages"].append(page_number)

    if current_module is not None:
        modules.append(current_module)

    return modules


def split_modules_to_json(
    pages_json: Path,
    output_json: Path,
    base_yaml: Path = BASE_YAML,
    first_page_number: int | None = None,
    last_page_number: int | None = None,
    source_file: str | None = None,
) -> list[dict]:
    pages = load_pages(pages_json)
    base_config = load_base_config(base_yaml)
    field_label_map = base_config["normalized_field_map"]
    module_start_patterns = base_config["module_start_patterns"]

    module_pages = filter_module_pages(
        pages,
        first_page_number,
        last_page_number,
    )

    modules = split_modules_from_pages(
        module_pages,
        field_label_map,
        module_start_patterns,
        source_file=source_file,
    )

    write_json(
        output_json,
        modules,
    )

    return modules


def main():
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("pages_json", type=Path)
    parser.add_argument("output_json", type=Path)
    parser.add_argument("--first-page", type=int, default=None)
    parser.add_argument("--last-page", type=int, default=None)
    parser.add_argument("--source-file", default=None)
    args = parser.parse_args()

    modules = split_modules_to_json(
        pages_json=args.pages_json,
        output_json=args.output_json,
        first_page_number=args.first_page,
        last_page_number=args.last_page,
        source_file=args.source_file,
    )

    print(f"Written to: {args.output_json}")
    print(f"Found modules: {len(modules)}")


if __name__ == "__main__":
    main()
