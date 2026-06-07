import re


def split_module_blocks(
    pages: list[dict],
    module_start_patterns: list[str],
    source_file: str | None = None,
) -> list[dict]:
    """
    Split cleaned page text into module blocks.

    A module block starts with labels like:
    Modulnummer B01
    Module ID B01
    Modul ID B01
    """

    full_text_parts = []
    position_to_page = []
    current_position = 0

    for page in pages:
        page_number = page["page_number"]
        text = page.get("clean_text") or page.get("raw_text") or ""

        start = current_position
        full_text_parts.append(text)
        current_position += len(text)

        end = current_position
        position_to_page.append({
            "page_number": page_number,
            "start": start,
            "end": end,
        })

        full_text_parts.append("\n")
        current_position += 1

    full_text = "".join(full_text_parts)

    matches = find_module_start_matches(full_text, module_start_patterns)

    module_blocks = []

    for index, match_info in enumerate(matches):
        module_number = match_info["module_number"]
        block_start = match_info["start"]

        if index + 1 < len(matches):
            block_end = matches[index + 1]["start"]
        else:
            block_end = len(full_text)

        raw_text = full_text[block_start:block_end].strip()
        source_pages = find_source_pages(block_start, block_end, position_to_page)

        module_blocks.append({
            "module_number": module_number,
            "source_file": source_file,
            "source_pages": source_pages,
            "raw_text": raw_text,
            "char_count": len(raw_text),
        })

    return module_blocks


def find_module_start_matches(
    full_text: str,
    module_start_patterns: list[str],
) -> list[dict]:
    """
    Find all module start positions from multiple patterns.
    """

    matches = []

    for pattern_text in module_start_patterns:
        pattern = re.compile(pattern_text, re.MULTILINE)

        for match in pattern.finditer(full_text):
            matches.append({
                "start": match.start(),
                "end": match.end(),
                "module_number": match.group(1),
                "matched_text": match.group(0),
            })

    # Sort by position in document
    matches = sorted(matches, key=lambda item: item["start"])

    # Remove duplicate matches at the same position
    unique_matches = []
    seen_positions = set()

    for match in matches:
        if match["start"] not in seen_positions:
            unique_matches.append(match)
            seen_positions.add(match["start"])

    return unique_matches


def find_source_pages(
    block_start: int,
    block_end: int,
    position_to_page: list[dict],
) -> list[int]:
    """
    Find which pages overlap with a module block.
    """

    pages = []

    for page_info in position_to_page:
        page_start = page_info["start"]
        page_end = page_info["end"]

        overlaps = page_start < block_end and page_end > block_start

        if overlaps:
            pages.append(page_info["page_number"])

    return pages


def create_split_summary(
    module_blocks: list[dict],
    expected_module_count: int | None = None,
    min_block_chars: int = 500,
) -> dict:
    """
    Create a simple quality summary for the split result.
    """

    module_numbers = [block["module_number"] for block in module_blocks]

    duplicate_module_numbers = sorted({
        number for number in module_numbers
        if module_numbers.count(number) > 1
    })

    short_blocks = [
        block["module_number"]
        for block in module_blocks
        if block["char_count"] < min_block_chars
    ]

    blocks_missing_title = [
        block["module_number"]
        for block in module_blocks
        if "Titel" not in block["raw_text"] and "Title" not in block["raw_text"]
    ]

    blocks_missing_status = [
        block["module_number"]
        for block in module_blocks
        if "Status" not in block["raw_text"]
    ]

    status = "ok"

    if duplicate_module_numbers:
        status = "needs_review"

    if short_blocks:
        status = "needs_review"

    if blocks_missing_title:
        status = "needs_review"

    if blocks_missing_status:
        status = "needs_review"

    if expected_module_count is not None:
        if len(module_blocks) != expected_module_count:
            status = "needs_review"

    return {
        "detected_module_count": len(module_blocks),
        "expected_module_count": expected_module_count,
        "duplicate_module_numbers": duplicate_module_numbers,
        "short_blocks": short_blocks,
        "blocks_missing_title": blocks_missing_title,
        "blocks_missing_status": blocks_missing_status,
        "status": status,
    }