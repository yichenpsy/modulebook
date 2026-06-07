from pathlib import Path
import json
import sqlite3
from datetime import datetime, UTC

from module_schema_step3 import ExtractedModule

BASE_DIR = Path(__file__).parent

INPUT_PATH = BASE_DIR / "output" / "validated_B01.json"
DB_PATH = BASE_DIR / "output" / "staging_modules.db"


def load_validated_module(path: Path) -> ExtractedModule:
    """
    Load already validated JSON and validate it again before DB insert.

    Reason:
    The staging DB should only receive schema-valid data.
    """
    if not path.exists():
        raise FileNotFoundError(f"Input file not found: {path}")

    data = json.loads(path.read_text(encoding="utf-8"))
    return ExtractedModule.model_validate(data)


def connect_db(db_path: Path) -> sqlite3.Connection:
    """
    Connect to a local SQLite database.
    If the file does not exist, SQLite creates it automatically.
    """
    db_path.parent.mkdir(parents=True, exist_ok=True)
    return sqlite3.connect(db_path)


def create_staging_table(conn: sqlite3.Connection) -> None:
    """
    Create a staging table if it does not exist yet.

    The table stores:
    - some important searchable fields as normal columns
    - the full validated JSON as payload_json
    """
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS staging_modules (
            id INTEGER PRIMARY KEY AUTOINCREMENT,

            module_number TEXT NOT NULL,
            title_de TEXT NOT NULL,
            title_en TEXT,
            credits REAL NOT NULL,
            status TEXT NOT NULL,

            source_file TEXT NOT NULL,
            source_page INTEGER NOT NULL,

            payload_json TEXT NOT NULL,

            validation_status TEXT NOT NULL,
            created_at TEXT NOT NULL,
            updated_at TEXT NOT NULL,

            UNIQUE(module_number, source_file, source_page)
        )
        """
    )
    conn.commit()


def insert_or_update_module(conn: sqlite3.Connection, module: ExtractedModule) -> None:
    """
    Insert the module into staging DB.

    If the same module already exists, update it.
    This makes the script safe to run multiple times.
    """
    now = datetime.now(UTC).isoformat()

    payload_json = json.dumps(
        module.model_dump(mode="json"),
        ensure_ascii=False,
        indent=2
    )

    conn.execute(
        """
        INSERT INTO staging_modules (
            module_number,
            title_de,
            title_en,
            credits,
            status,
            source_file,
            source_page,
            payload_json,
            validation_status,
            created_at,
            updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(module_number, source_file, source_page)
        DO UPDATE SET
            title_de = excluded.title_de,
            title_en = excluded.title_en,
            credits = excluded.credits,
            status = excluded.status,
            payload_json = excluded.payload_json,
            validation_status = excluded.validation_status,
            updated_at = excluded.updated_at
        """,
        (
            module.module_number,
            module.title_de,
            module.title_en,
            module.credits,
            module.status.value,
            module.source_file,
            module.source_page,
            payload_json,
            "format_validated",
            now,
            now,
        )
    )
    conn.commit()


def main():
    module = load_validated_module(INPUT_PATH)

    with connect_db(DB_PATH) as conn:
        create_staging_table(conn)
        insert_or_update_module(conn, module)

    print("Module staged successfully.")
    print(f"Module: {module.module_number}")
    print(f"Title: {module.title_de}")
    print(f"Credits: {module.credits}")
    print(f"Status: {module.status.value}")
    print(f"Source page: {module.source_page}")
    print(f"Staging DB: {DB_PATH}")


if __name__ == "__main__":
    main()