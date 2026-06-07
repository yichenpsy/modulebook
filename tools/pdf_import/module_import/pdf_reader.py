from pathlib import Path
import fitz  # PyMuPDF


def extract_pages(pdf_path: str | Path) -> list[dict]:
    """
    Extract raw text page by page from a PDF.

    Returns:
    [
        {
            "page_number": 1,
            "raw_text": "..."
        }
    ]
    """
    pdf_path = Path(pdf_path)

    if not pdf_path.exists():
        raise FileNotFoundError(f"PDF not found: {pdf_path}")

    pages = []

    with fitz.open(pdf_path) as doc:
        for index, page in enumerate(doc, start=1):
            text = page.get_text("text") or ""

            pages.append({
                "page_number": index,
                "raw_text": text
            })

    return pages