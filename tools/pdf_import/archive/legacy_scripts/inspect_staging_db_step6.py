from pathlib import Path
import sqlite3
import json

BASE_DIR = Path(__file__).parent
DB_PATH = BASE_DIR / "output" / "staging_modules.db"


def main():
    if not DB_PATH.exists():
        raise FileNotFoundError(f"Staging DB not found: {DB_PATH}")

    with sqlite3.connect(DB_PATH) as conn:
        conn.row_factory = sqlite3.Row

        rows = conn.execute(
            """
            SELECT
                id,
                module_number,
                title_de,
                title_en,
                credits,
                status,
                source_file,
                source_page,
                validation_status,
                created_at,
                updated_at
            FROM staging_modules
            ORDER BY module_number
            """
        ).fetchall()

        print(f"Found {len(rows)} staged module(s).")

        for row in rows:
            print("-" * 60)
            print(f"ID: {row['id']}")
            print(f"Module: {row['module_number']}")
            print(f"Title DE: {row['title_de']}")
            print(f"Title EN: {row['title_en']}")
            print(f"Credits: {row['credits']}")
            print(f"Status: {row['status']}")
            print(f"Source: {row['source_file']} page {row['source_page']}")
            print(f"Validation status: {row['validation_status']}")
            print(f"Created at: {row['created_at']}")
            print(f"Updated at: {row['updated_at']}")

        # Optional: show full JSON payload for B01
        payload_row = conn.execute(
            """
            SELECT payload_json
            FROM staging_modules
            WHERE module_number = ?
            """,
            ("B01",)
        ).fetchone()

        if payload_row:
            print("-" * 60)
            print("Full payload for B01:")
            payload = json.loads(payload_row["payload_json"])
            print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()