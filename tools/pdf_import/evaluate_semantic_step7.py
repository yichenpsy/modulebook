from pathlib import Path
import json
import csv
from difflib import SequenceMatcher

BASE_DIR = Path(__file__).parent

PREDICTED_PATH = BASE_DIR / "output" / "validated_B01.json"
GOLD_PATH = BASE_DIR / "gold_standard" / "gold_standard_B01.json"

EVAL_CSV_PATH = BASE_DIR / "output" / "semantic_evaluation_B01.csv"
SUMMARY_PATH = BASE_DIR / "output" / "semantic_evaluation_summary_B01.json"


EXACT_FIELDS = [
    "module_number",
    "title_de",
    "title_en",
    "credits",
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
]

LIST_FIELDS = [
    "contents",
    "literature",
]

CRITICAL_FIELDS = [
    "module_number",
    "title_de",
    "credits",
    "status",
    "source_page",
]


def load_json(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(f"File not found: {path}")

    return json.loads(path.read_text(encoding="utf-8"))


def normalize(value) -> str:
    """
    Normalize values for comparison.
    This makes comparison less sensitive to line breaks and extra spaces.
    """
    if value is None:
        return ""

    if isinstance(value, list):
        value = " | ".join(str(item) for item in value)

    value = str(value)
    value = " ".join(value.split())
    return value.strip()


def exact_match(predicted, gold) -> float:
    return 1.0 if normalize(predicted) == normalize(gold) else 0.0


def similarity(predicted, gold) -> float:
    predicted_norm = normalize(predicted)
    gold_norm = normalize(gold)

    if not predicted_norm and not gold_norm:
        return 1.0

    return SequenceMatcher(None, predicted_norm, gold_norm).ratio()


def evaluate_field(field_name: str, predicted: dict, gold: dict) -> dict:
    predicted_value = predicted.get(field_name)
    gold_value = gold.get(field_name)

    if field_name in EXACT_FIELDS:
        metric = "exact_match"
        score = exact_match(predicted_value, gold_value)
        passed = score == 1.0

    elif field_name in LONG_TEXT_FIELDS:
        metric = "text_similarity"
        score = similarity(predicted_value, gold_value)
        passed = score >= 0.90

    elif field_name in LIST_FIELDS:
        metric = "list_text_similarity"
        score = similarity(predicted_value, gold_value)
        passed = score >= 0.80

    else:
        metric = "unknown"
        score = 0.0
        passed = False

    return {
        "field_name": field_name,
        "metric": metric,
        "score": round(score, 3),
        "passed": passed,
        "is_critical": field_name in CRITICAL_FIELDS,
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
        "is_critical",
        "predicted_value",
        "gold_value",
    ]

    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def summarize(rows: list[dict]) -> dict:
    total_fields = len(rows)
    passed_fields = sum(1 for row in rows if row["passed"])

    critical_rows = [row for row in rows if row["is_critical"]]
    passed_critical = sum(1 for row in critical_rows if row["passed"])

    return {
        "total_fields": total_fields,
        "passed_fields": passed_fields,
        "field_accuracy": round(passed_fields / total_fields, 3),
        "critical_fields": len(critical_rows),
        "passed_critical_fields": passed_critical,
        "critical_field_accuracy": round(passed_critical / len(critical_rows), 3),
        "failed_fields": [
            row["field_name"] for row in rows if not row["passed"]
        ],
    }


def main():
    predicted = load_json(PREDICTED_PATH)
    gold = load_json(GOLD_PATH)

    all_fields = EXACT_FIELDS + LONG_TEXT_FIELDS + LIST_FIELDS

    rows = [
        evaluate_field(field, predicted, gold)
        for field in all_fields
    ]

    write_csv(rows, EVAL_CSV_PATH)

    summary = summarize(rows)
    SUMMARY_PATH.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print("Semantic evaluation finished.")
    print(f"CSV table saved to: {EVAL_CSV_PATH}")
    print(f"Summary saved to: {SUMMARY_PATH}")
    print("-" * 60)
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()