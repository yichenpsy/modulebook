import re
from collections import defaultdict


IGNORE_START_WORDS = {
    "Die", "Der", "Das", "Den", "Dem",
    "Ein", "Eine", "Einer", "Eines",
    "In", "Im", "Für", "Bei", "Auf", "Aus", "Von", "Zur", "Zum",
    "Dieses", "Diese", "Dieser",
    "Bachelor", "Modulhandbuch",
}


def get_known_labels(base_config: dict) -> list[str]:
    labels = []

    for aliases in base_config.get("field_aliases", {}).values():
        labels.extend(aliases)

    labels.extend([
        "Modulnummer",
        "Titel",
        "Title",
        "Status",
    ])

    return sorted(set(labels), key=len, reverse=True)


def line_starts_with_known_label(line: str, known_labels: list[str]) -> bool:
    stripped = line.strip()

    for label in known_labels:
        if stripped.startswith(label):
            return True

    return False


def guess_unknown_label(line: str) -> str | None:
    """
    Guess whether a line may start with an unknown field label.
    This only creates a warning candidate.
    """

    stripped = line.strip()

    if not stripped:
        return None

    if len(stripped) < 3 or len(stripped) > 120:
        return None

    if stripped.startswith(("•", "-", "–")):
        return None

    if re.match(r"^\d+[\.\)]\s+", stripped):
        return None

    if "http://" in stripped or "https://" in stripped or "@" in stripped:
        return None

    first_word = stripped.split()[0].strip(":;,.()")

    if first_word in IGNORE_START_WORDS:
        return None

    if not re.match(r"^[A-ZÄÖÜ][A-Za-zÄÖÜäöüß/-]+$", first_word):
        return None

    return first_word


def discover_unknown_fields(
    module_blocks: list[dict],
    base_config: dict,
    min_count: int = 2,
) -> list[dict]:
    """
    Scan module blocks and report labels that look like fields
    but are not known in base.yaml.
    """

    known_labels = get_known_labels(base_config)
    unknown_candidates = defaultdict(list)

    for block in module_blocks:
        module_number = block["module_number"]
        source_file = block.get("source_file")

        for line in block["raw_text"].splitlines():
            if line_starts_with_known_label(line, known_labels):
                continue

            candidate = guess_unknown_label(line)

            if candidate is None:
                continue

            unknown_candidates[candidate].append({
                "source_file": source_file,
                "module_number": module_number,
                "example_line": line.strip(),
            })

    rows = []

    for label, examples in sorted(unknown_candidates.items()):
        if len(examples) < min_count:
            continue

        first_example = examples[0]

        rows.append({
            "unknown_label": label,
            "count": len(examples),
            "source_file": first_example["source_file"],
            "example_module": first_example["module_number"],
            "example_line": first_example["example_line"],
        })

    return rows