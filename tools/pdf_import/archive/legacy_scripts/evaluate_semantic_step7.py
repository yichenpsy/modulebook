from pathlib import Path
import json
import csv
from difflib import SequenceMatcher

BASE_DIR = Path(__file__).parent

PREDICTED_PATH = BASE_DIR / "output" / "validated_B01.json"
GOLD_PATH = BASE_DIR / "gold_standard" / "gold_standard_B01.json"

CSV_OUTPUT_PATH = BASE_DIR / "output" / "semantic_evaluation_B01.csv"
SUMMARY_OUTPUT_PATH = BASE_DIR / "output" / "semantic_evaluation_summary_B01.json"


CRITICAL_FIELDS = [
    "module_number",
    "title_de",
    "title_en",
    "credits",
    "status",
    "source_page",
]

NUMERIC_FIELDS = [
    "credits",
]

EXACT_FIELDS = [
    "module_number",
    "title_de",
    "title_en",
    "learning_area",
    "semester_level",
    "teaching_format",
    "status",
    "frequency",
    "grade_calculation",
    "recognized_modules",
    "additional_notes",
    "source_page",
]

LONG_TEXT_FIELDS = [
    "workload",
    "learning_objectives",
    "prerequisites",
    "exam_type",
    "contents",
]

LIST_FIELDS = [
    "literature",
]


def load_json(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    return json.loads(path.read_text(encoding="utf-8"))


def normalize(value) -> str:
    """
    Convert value into comparable text.
    Handles strings, numbers, None, and lists.
    """
    if value is None:
        return ""

    if isinstance(value, list):
        value = " ".join(str(item) for item in value)

    value = str(value)
    value = " ".join(value.split())
    return value.strip()


def exact_match(predicted, gold) -> tuple[float, bool, str]:
    predicted_norm = normalize(predicted)
    gold_norm = normalize(gold)

    passed = predicted_norm == gold_norm
    return 1.0 if passed else 0.0, passed, "normalized exact match"


def numeric_match(predicted, gold) -> tuple[float, bool, str]:
    try:
        predicted_number = float(predicted)
        gold_number = float(gold)
    except (TypeError, ValueError):
        return 0.0, False, "could not compare as number"

    passed = predicted_number == gold_number
    return 1.0 if passed else 0.0, passed, "numeric comparison"


def text_similarity(predicted, gold, threshold: float = 0.90) -> tuple[float, bool, str]:
    predicted_norm = normalize(predicted)
    gold_norm = normalize(gold)

    if not predicted_norm and not gold_norm:
        return 1.0, True, "both empty"

    score = SequenceMatcher(None, predicted_norm, gold_norm).ratio()
    passed = score >= threshold

    return score, passed, f"text similarity >= {threshold}"


def length_ratio(predicted, gold) -> float | None:
    predicted_len = len(normalize(predicted))
    gold_len = len(normalize(gold))

    if gold_len == 0:
        return None

    return round(predicted_len / gold_len, 3)


def list_recall(predicted, gold) -> tuple[float, bool, str]:
    """
    Simple literature evaluation:
    check whether each gold item appears in the predicted text.
    """
    predicted_text = normalize(predicted).lower()

    if not isinstance(gold, list):
        gold_items = [gold]
    else:
        gold_items = gold

    if len(gold_items) == 0:
        return 1.0, True, "no gold items"

    found = 0

    for item in gold_items:
        item_text = normalize(item).lower()
        if item_text and item_text in predicted_text:
            found += 1

    score = found / len(gold_items)
    passed = score == 1.0

    return score, passed, "gold item recall"


def evaluate_field(field_name: str, predicted: dict, gold: dict) -> dict:
    predicted_value = predicted.get(field_name)
    gold_value = gold.get(field_name)

    if field_name in NUMERIC_FIELDS:
        score, passed, comment = numeric_match(predicted_value, gold_value)
        metric = "numeric_match"

    elif field_name in EXACT_FIELDS:
        score, passed, comment = exact_match(predicted_value, gold_value)
        metric = "exact_match"

    elif field_name in LONG_TEXT_FIELDS:
        score, passed, comment = text_similarity(predicted_value, gold_value)
        metric = "text_similarity"

    elif field_name in LIST_FIELDS:
        score, passed, comment = list_recall(predicted_value, gold_value)
        metric = "list_recall"

    else:
        score, passed, comment = text_similarity(predicted_value, gold_value)
        metric = "default_similarity"

    return {
        "field_name": field_name,
        "metric": metric,
        "score": round(score, 3),
        "passed": passed,
        "critical": field_name in CRITICAL_FIELDS,
        "length_ratio": length_ratio(predicted_value, gold_value),
        "comment": comment,
        "predicted_value": normalize(predicted_value),
        "gold_value": normalize(gold_value),
    }


def write_csv(rows: list[dict], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)

    fieldnames = [
        "field_name",
        "metric",
        "score",
        "passed",
        "critical",
        "length_ratio",
        "comment",
        "predicted_value",
        "gold_value",
    ]

    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def summarize(rows: list[dict]) -> dict:
    total = len(rows)
    passed = sum(row["passed"] for row in rows)

    critical_rows = [row for row in rows if row["critical"]]
    critical_passed = sum(row["passed"] for row in critical_rows)

    failed_fields = [
        row["field_name"]
        for row in rows
        if not row["passed"]
    ]

    return {
        "total_fields": total,
        "passed_fields": passed,
        "field_accuracy": round(passed / total, 3),
        "critical_fields": len(critical_rows),
        "passed_critical_fields": critical_passed,
        "critical_field_accuracy": round(critical_passed / len(critical_rows), 3),
        "failed_fields": failed_fields,
    }


def main():
    predicted = load_json(PREDICTED_PATH)
    gold = load_json(GOLD_PATH)

    fields_to_evaluate = (
        CRITICAL_FIELDS
        + [
            "workload",
            "learning_area",
            "learning_objectives",
            "prerequisites",
            "semester_level",
            "teaching_format",
            "frequency",
            "exam_type",
            "grade_calculation",
            "recognized_modules",
            "contents",
            "literature",
            "additional_notes",
        ]
    )

    # Remove duplicates but keep order
    fields_to_evaluate = list(dict.fromkeys(fields_to_evaluate))

    rows = [
        evaluate_field(field, predicted, gold)
        for field in fields_to_evaluate
    ]

    write_csv(rows, CSV_OUTPUT_PATH)

    summary = summarize(rows)
    SUMMARY_OUTPUT_PATH.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print("Semantic evaluation finished.")
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"CSV saved to: {CSV_OUTPUT_PATH}")
    print(f"Summary saved to: {SUMMARY_OUTPUT_PATH}")


if __name__ == "__main__":
    main()