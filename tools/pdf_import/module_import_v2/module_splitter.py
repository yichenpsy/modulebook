from pathlib import Path
import json
import yaml


pdf_name = "Data-Engineering_2025"

WORK_DIR = Path("tools/pdf_import/data/work") / pdf_name
PAGES_JSON = WORK_DIR / "pages_md.json"
BASE_YAML = Path("tools/pdf_import/configs/base.yaml")

MODULE_BLOCKS_JSON = WORK_DIR / "module_blocks_md.json"


# Set these from the previous boundary detection result.
# If you want to use all pages, set them to None.
FIRST_MODULE_PAGE_NUMBER = 6
LAST_MODULE_PAGE_NUMBER = 103


def load_pages(path: Path):
    """Load extracted page Markdown data."""
    with path.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_field_label_map(path: Path):
    """Load normalized field labels from base.yaml."""
    with path.open("r", encoding="utf-8") as file:
        config = yaml.safe_load(file)

    return config["normalized_field_map"]


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
        cleaned_cell = clean_cell_text(cell)

        for label in module_number_labels:
            if label in cleaned_cell:
                module_number = find_first_non_empty_cell_after(cells, index)

                if module_number:
                    return module_number

    return None


def split_modules_from_pages(
    pages: list[dict],
    field_label_map: dict,
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
            
            module_number = detect_module_start_from_line(
                line,
                module_number_labels,
            )

            if module_number:
                if current_module is not None:
                    modules.append(current_module)

                current_module = {
                    "module_number": module_number,
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


def main():
    pages = load_pages(PAGES_JSON)
    field_label_map = load_field_label_map(BASE_YAML)

    module_pages = filter_module_pages(
        pages,
        FIRST_MODULE_PAGE_NUMBER,
        LAST_MODULE_PAGE_NUMBER,
    )

    modules = split_modules_from_pages(
        module_pages,
        field_label_map,
    )

    write_json(
        MODULE_BLOCKS_JSON,
        modules,
    )

    print(f"Written to: {MODULE_BLOCKS_JSON}")
    print(f"Found modules: {len(modules)}")


if __name__ == "__main__":
    main()