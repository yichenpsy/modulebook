import re


def clean_page_text(text: str) -> str:
    """
    Light cleaning for extracted PDF text.

    This function should not change the meaning of the text.
    It only removes artifacts that make later splitting harder.
    """
    if text is None:
        return ""

    text = text.replace("\x00", "")
    text = text.replace("\ufeff", "")
    text = text.replace("\u00ad", "")  # soft hyphen
    text = text.replace("￾", "")       # PDF extraction artifact

    # Normalize line endings
    text = text.replace("\r\n", "\n")
    text = text.replace("\r", "\n")

    # Remove trailing spaces per line
    lines = [line.rstrip() for line in text.split("\n")]

    # Remove too many empty lines
    cleaned_lines = []
    empty_count = 0

    for line in lines:
        if line.strip() == "":
            empty_count += 1
            if empty_count <= 2:
                cleaned_lines.append("")
        else:
            empty_count = 0
            cleaned_lines.append(line)

    return "\n".join(cleaned_lines).strip()


def clean_pages(pages: list[dict]) -> list[dict]:
    """
    Clean all extracted pages.

    Input:
    [
        {
            "page_number": 1,
            "raw_text": "..."
        }
    ]

    Output:
    [
        {
            "page_number": 1,
            "raw_text": "...",
            "clean_text": "..."
        }
    ]
    """
    cleaned_pages = []

    for page in pages:
        cleaned_pages.append({
            **page,
            "clean_text": clean_page_text(page.get("raw_text", ""))
        })

    return cleaned_pages