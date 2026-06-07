from pathlib import Path
import json
import fitz  # PyMuPDF

BASE_DIR = Path(__file__).parent

PDF_PATH = BASE_DIR / "input" / "BA_Medieninformatik_2024_11_Modulhandbuch_zur_Studien-_und_Pruefungsordnung_2017.pdf"
OUTPUT_PATH = BASE_DIR / "output" / "modulehandbook_pages_B01_B03.json"

START_PAGE = 6
END_PAGE = 8


def clean_text(text: str) -> str:
    lines = [line.rstrip() for line in text.splitlines()]
    text = "\n".join(lines)

    while "\n\n\n" in text:
        text = text.replace("\n\n\n", "\n\n")

    return text.strip()


def extract_pages(pdf_path: Path, start_page: int, end_page: int) -> list[dict]:
    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    doc = fitz.open(pdf_path)
    results = []

    for page_number in range(start_page, end_page + 1):
        page = doc[page_number - 1]
        text = clean_text(page.get_text("text"))

        results.append({
            "source_file": pdf_path.name,
            "page_number": page_number,
            "text": text,
        })

    return results


if __name__ == "__main__":
    pages = extract_pages(PDF_PATH, START_PAGE, END_PAGE)

    OUTPUT_PATH.parent.mkdir(parents=True, exist_ok=True)

    OUTPUT_PATH.write_text(
        json.dumps(pages, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print(f"Extracted {len(pages)} pages")
    print(f"Saved to: {OUTPUT_PATH}")

    for page in pages:
        print("-" * 60)
        print(f"Page {page['page_number']}")
        print(page["text"][:500])