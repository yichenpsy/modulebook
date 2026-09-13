import json
import re
from pathlib import Path


MODULE_HEADER_ALIASES = [
    "Modul",
    "Modulnummer",
    "Modul-Nr.",
]

MODULE_NAME_HEADER_ALIASES = [
    "Modulname",
]

COORDINATOR_HEADER_ALIASES = [
    "Koordinator/in",
    "Koordinator*in",
]


def load_pages(path: Path) -> list[dict]:
    """Load page markdown records from pages_md.json."""
    return json.loads(path.read_text(encoding="utf-8"))


def get_page_markdown(page: dict) -> str:
    """Get Markdown text from one page record."""
    return page.get("raw_markdown") or page.get("raw_text") or ""


def is_markdown_table_row(line: str) -> bool:
    """Check whether a line looks like a Markdown table row."""
    stripped = line.strip()
    return stripped.startswith("|") and stripped.endswith("|")


def split_markdown_table_row(line: str) -> list[str]:
    """
    Split a Markdown table row into cells.

    Empty cells are preserved because column positions matter here.
    """
    stripped = line.strip()
    return [cell.strip() for cell in stripped.strip("|").split("|")]


def is_separator_row(cells: list[str]) -> bool:
    """Check whether a Markdown table row is a separator row like |---|---|."""
    return all(
        cell.replace("-", "").replace(":", "").strip() == ""
        for cell in cells
    )


def clean_cell_text(cell: str) -> str:
    """
    Remove simple Markdown and HTML fragments from one table cell.

    The PDF converter often turns line breaks inside cells into <br>.
    """
    text = str(cell)
    text = text.replace("<br>", " ")
    text = text.replace("<br/>", " ")
    text = text.replace("<br />", " ")
    text = text.replace("&nbsp;", " ")
    text = re.sub(r"\*\*", "", text)
    text = re.sub(r"\s+", " ", text)
    return text.strip()


def normalize_header_text(text: str) -> str:
    """
    Normalize a header for alias matching.

    Examples:
    - "Modul- nummer" becomes "modulnummer"
    - "Koordinator/in" becomes "koordinatorin"
    - "Koordinator*in" becomes "koordinatorin"
    """
    text = clean_cell_text(text).lower()
    text = re.sub(r"[^a-zäöüß0-9]+", "", text)
    return text.strip()


def normalize_module_number(text: str) -> str:
    """Normalize the module id used as the dictionary key."""
    text = clean_cell_text(text)
    text = re.sub(r"\s+", "", text)
    return text.strip("* |")


def clean_coordinator_text(text: str) -> str | None:
    """Clean the coordinator cell and return None for empty placeholder values."""
    text = str(text)
    text = text.replace("<br>", "\n")
    text = text.replace("<br/>", "\n")
    text = text.replace("<br />", "\n")
    text = text.replace("&nbsp;", " ")
    text = re.sub(r"\*\*", "", text)
    text = re.sub(r"[ \t]+", " ", text)
    text = re.sub(r"\n\s*", "\n", text)
    text = text.strip(" \n|")

    if not text or text in {"-", "–"}:
        return None

    return text


def find_header_index(cells: list[str], aliases: list[str]) -> int | None:
    """Return the first cell index that matches one of the accepted headers."""
    normalized_aliases = {
        normalize_header_text(alias)
        for alias in aliases
    }

    for index, cell in enumerate(cells):
        if normalize_header_text(cell) in normalized_aliases:
            return index

    return None


def detect_coordinator_table_columns(cells: list[str]) -> dict[str, int] | None:
    """
    Detect the required coordinator table header.

    A valid header must contain:
    - Modul / Modulnummer / Modul-Nr.
    - Modulname
    - Koordinator/in / Koordinator*in
    """
    module_index = find_header_index(cells, MODULE_HEADER_ALIASES)
    module_name_index = find_header_index(cells, MODULE_NAME_HEADER_ALIASES)
    coordinator_index = find_header_index(cells, COORDINATOR_HEADER_ALIASES)

    if (
        module_index is None
        or module_name_index is None
        or coordinator_index is None
    ):
        return None

    return {
        "module": module_index,
        "module_name": module_name_index,
        "coordinator": coordinator_index,
    }


def looks_like_new_non_coordinator_header(cells: list[str]) -> bool:
    """
    Detect a new table header that is not the coordinator table.

    This prevents later tables, for example a study plan table, from being
    parsed with the previous coordinator table's column positions.
    """
    has_module_header = find_header_index(cells, MODULE_HEADER_ALIASES) is not None
    has_module_name_header = (
        find_header_index(cells, MODULE_NAME_HEADER_ALIASES) is not None
    )
    has_coordinator_header = (
        find_header_index(cells, COORDINATOR_HEADER_ALIASES) is not None
    )

    return (has_module_header or has_module_name_header) and not has_coordinator_header


def row_has_required_columns(cells: list[str], columns: dict[str, int]) -> bool:
    """Check whether a row is wide enough for the detected table columns."""
    return len(cells) > max(columns.values())


def looks_like_module_number(value: str) -> bool:
    """Check whether a cell looks like a module number, for example B01 or WP1-02."""
    value = normalize_module_number(value)

    if not value:
        return False

    return re.fullmatch(
        r"[A-Z]{1,4}\d{1,2}(?:[.-]\d{1,2})?[a-zA-Z]?|WP\d?-\d{1,2}|SP\d-\d{2}",
        value,
    ) is not None


def add_coordinator_from_row(
    coordinators_by_module: dict[str, str],
    cells: list[str],
    columns: dict[str, int],
) -> None:
    """Add one table row to the coordinator map if it contains usable data."""
    if not row_has_required_columns(cells, columns):
        return

    module_number = normalize_module_number(cells[columns["module"]])
    coordinator = clean_coordinator_text(cells[columns["coordinator"]])

    if not looks_like_module_number(module_number):
        return

    if coordinator is None:
        return

    coordinators_by_module.setdefault(module_number, coordinator)

# corn function
def extract_coordinators_from_pages(pages: list[dict]) -> dict[str, str]:
    """
    Extract module coordinators from Markdown tables in page records.

    The return value maps module number to coordinator, for example:
    {"B01": "Prof. Dr. Example"}.
    """
    coordinators_by_module = {}
    active_columns = None

    for page in pages:
        markdown = get_page_markdown(page)

        for line in markdown.splitlines():
            if not is_markdown_table_row(line):
                if line.lstrip().startswith("#"):
                    active_columns = None
                continue

            cells = split_markdown_table_row(line)

            if is_separator_row(cells):
                continue

            detected_columns = detect_coordinator_table_columns(cells)
            if detected_columns is not None:
                active_columns = detected_columns
                continue

            if looks_like_new_non_coordinator_header(cells):
                active_columns = None
                continue

            if active_columns is None:
                continue

            add_coordinator_from_row(
                coordinators_by_module,
                cells,
                active_columns,
            )

    return coordinators_by_module


def extract_coordinators_from_pages_file(path: Path) -> dict[str, str]:
    """Convenience wrapper for reading pages_md.json from disk."""
    return extract_coordinators_from_pages(load_pages(path))


def add_coordinators_to_modules(
    modules: list[dict],
    coordinators_by_module: dict[str, str],
) -> int:
    """
    Add coordinator values to normalized module records.

    Returns the number of modules that received a coordinator.
    """
    added_count = 0

    for module in modules:
        module_number = normalize_module_number(module.get("module_number") or "")
        coordinator = coordinators_by_module.get(module_number)

        module["coordinator"] = coordinator

        if coordinator is not None:
            added_count += 1

    return added_count


def main() -> None:
    import argparse

    parser = argparse.ArgumentParser()
    parser.add_argument("pages_json", type=Path)
    args = parser.parse_args()

    coordinators = extract_coordinators_from_pages_file(args.pages_json)
    print(json.dumps(coordinators, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
