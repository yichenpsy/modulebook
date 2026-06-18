import argparse
import json
from pathlib import Path
import fitz

import pymupdf4llm

pdf_name = "medieninformatik_2024"

INPUT_PDF= Path(
    f"/Users/yichenzhong/__repo/modulebook/tools/pdf_import/data/input/{pdf_name}.pdf"
)


OUTPUT_JSON = Path(
    f"tools/pdf_import/data/work/{pdf_name}/pages_md.json"
)


def main():
    print(INPUT_PDF)
    print(INPUT_PDF.exists())
    
    page_chunks = pymupdf4llm.to_markdown(str(INPUT_PDF), page_chunks=True)
    
    pages = []
    for chunk in page_chunks:
        page_object = {
            "page_number": chunk["metadata"]["page_number"],
            "raw_markdown": chunk["text"],
        }
        pages.append(page_object)
    
    OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)

    with OUTPUT_JSON.open("w", encoding="utf-8") as file:
        json.dump(pages, file, ensure_ascii=False, indent=2)   
    
    # print(type(page_chunks))
    # print(len(page_chunks))
    # print(page_chunks[0].keys())
    # print(page_chunks[0]["metadata"])
    # print(page_chunks[0]["text"][:1000])
    
    
    # document = fitz.open(INPUT_PDF)
    # page_count = len(document)
    # print(page_count)
    
    # pages = []
    # for page_index in range(page_count):
    #     page = document[page_index]
    #     page_number = page_index + 1
    #     raw_text = page.get_text("text")
            
    #     page_object = {
    #         "page_number": page_number,
    #         "raw_text": raw_text,
    #     }
    #     pages.append(page_object)
    
    # OUTPUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    # with OUTPUT_JSON.open("w", encoding="utf-8") as file:
    #     json.dump(pages, file, ensure_ascii=False, indent=2)
    
    # document.close()
    
if __name__ == "__main__":
    main()