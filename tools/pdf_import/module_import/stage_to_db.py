import argparse
import json
import sqlite3
from pathlib import Path

from tools.pdf_import.module_import.config import PDF_IMPORT_ROOT


DEFAULT_INPUT_PATH = PDF_IMPORT_ROOT / "data" / "output" / "modules_valid_python.jsonl"
DEFAULT_DB_PATH = PDF_IMPORT_ROOT / "data" / "output" / "module_import_staging.sqlite"

STAGING_COLUMNS = [
    "source_file",
    "source_pages",
    "study_program",
    "study_program_code",
    "module_number",
    "title_de",
    "title_en",
    "credits",
    "workload",
    "learning_area",
    "learning_objectives",
    "prerequisites",
    "level",
    "teaching_form",
    "status",
    "frequency",
    "exam_form",
    "module_grade",
    "recognized_modules",
    "contents",
    "literature",
    "additional_notes",
    "room_requirements",
]


def load_jsonl(path: Path) -> list[dict]:
    records = []

    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))

    return records


def to_db_value(value):
    if isinstance(value, (list, dict)):
        return json.dumps(value, ensure_ascii=False)

    return value


def create_staging_table(conn: sqlite3.Connection) -> None:
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS module_import_staging (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_file TEXT,
            source_pages TEXT,
            study_program TEXT,
            study_program_code TEXT,
            module_number TEXT,
            title_de TEXT,
            title_en TEXT,
            credits TEXT,
            workload TEXT,
            learning_area TEXT,
            learning_objectives TEXT,
            prerequisites TEXT,
            level TEXT,
            teaching_form TEXT,
            status TEXT,
            frequency TEXT,
            exam_form TEXT,
            module_grade TEXT,
            recognized_modules TEXT,
            contents TEXT,
            literature TEXT,
            additional_notes TEXT,
            room_requirements TEXT
        )
        """
    )
    conn.commit()


def replace_staging_modules(conn: sqlite3.Connection, modules: list[dict]) -> None:
    placeholders = ", ".join("?" for _ in STAGING_COLUMNS)
    column_names = ", ".join(STAGING_COLUMNS)

    rows = [
        tuple(to_db_value(module.get(column)) for column in STAGING_COLUMNS)
        for module in modules
    ]

    with conn:
        conn.execute("DELETE FROM module_import_staging")
        conn.executemany(
            f"""
            INSERT INTO module_import_staging ({column_names})
            VALUES ({placeholders})
            """,
            rows,
        )


def stage_modules_to_db(input_path: Path, db_path: Path) -> int:
    modules = load_jsonl(input_path)

    db_path.parent.mkdir(parents=True, exist_ok=True)

    with sqlite3.connect(db_path) as conn:
        create_staging_table(conn)
        replace_staging_modules(conn, modules)

    return len(modules)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        type=Path,
        default=DEFAULT_INPUT_PATH,
        help="JSONL file with normalized valid modules.",
    )
    parser.add_argument(
        "--db",
        type=Path,
        default=DEFAULT_DB_PATH,
        help="SQLite staging database path.",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    staged_count = stage_modules_to_db(input_path=args.input, db_path=args.db)

    print(f"Staged modules: {staged_count}")
    print(f"Input: {args.input}")
    print(f"DB: {args.db}")


if __name__ == "__main__":
    main()
