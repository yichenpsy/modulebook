import argparse
import json
from pathlib import Path

import pymupdf4llm


def extract_pages_to_markdown(pdf_path: Path) -> list[dict]:
    page_chunks = pymupdf4llm.to_markdown(str(pdf_path), page_chunks=True)

    pages = []
    for chunk in page_chunks:
        page_object = {
            "page_number": chunk["metadata"]["page_number"],
            "raw_markdown": chunk["text"],
        }
        pages.append(page_object)

    return pages


def write_pages_json(output_json: Path, pages: list[dict]) -> None:
    output_json.parent.mkdir(parents=True, exist_ok=True)

    with output_json.open("w", encoding="utf-8") as file:
        json.dump(pages, file, ensure_ascii=False, indent=2)


def convert_pdf_to_pages_markdown(pdf_path: Path, output_json: Path) -> list[dict]:
    pages = extract_pages_to_markdown(pdf_path)
    write_pages_json(output_json, pages)
    return pages


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("pdf", type=Path, help="Input PDF path.")
    parser.add_argument("output", type=Path, help="Output pages_md.json path.")
    return parser.parse_args()


def main():
    args = parse_args()
    pages = convert_pdf_to_pages_markdown(args.pdf, args.output)
    print(f"Written: {args.output}")
    print(f"Pages: {len(pages)}")


if __name__ == "__main__":
    main()
