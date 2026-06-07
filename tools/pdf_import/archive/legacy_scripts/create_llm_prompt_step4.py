from pathlib import Path
import json

BASE_DIR = Path(__file__).parent

INPUT_PATH = BASE_DIR / "output" / "module_blocks_B01_B03.json"
OUTPUT_PATH = BASE_DIR / "output" / "llm_prompt_B01.txt"

TARGET_MODULE = "B01"


def load_module_block(module_number: str) -> dict:
    if not INPUT_PATH.exists():
        raise FileNotFoundError(f"Input file not found: {INPUT_PATH}")

    blocks = json.loads(INPUT_PATH.read_text(encoding="utf-8"))

    for block in blocks:
        if block["module_number"] == module_number:
            return block

    raise ValueError(f"Module {module_number} not found")


def create_prompt(block: dict) -> str:
    return f"""
You are an information extraction system.

Your task:
Extract structured JSON from the given German module handbook text.

Rules:
1. Return JSON only.
2. Do not use Markdown.
3. Do not add explanations.
4. Do not invent missing information.
5. Use null if a value is missing.
6. Convert credits like "5 LP" to a number, for example 5.
7. Split bilingual titles:
   - "Mathematik I / Mathematics 1"
   - title_de = "Mathematik I"
   - title_en = "Mathematics 1"
8. Keep long German text as close as possible to the source.
9. contents must be a list of strings.
10. literature must be a list of strings.
11. status must be exactly one of:
    - "Pflichtmodul"
    - "Wahlpflichtmodul"
12. Include source_file and source_page from the metadata.

Required JSON structure:
{{
  "module_number": "string",
  "title_de": "string",
  "title_en": "string or null",
  "credits": number,
  "workload": "string",
  "learning_area": "string",
  "learning_objectives": "string",
  "prerequisites": "string or null",
  "semester_level": "string",
  "teaching_format": "string",
  "status": "Pflichtmodul or Wahlpflichtmodul",
  "frequency": "string",
  "exam_type": "string",
  "grade_calculation": "string",
  "recognized_modules": "string or null",
  "contents": ["string"],
  "literature": ["string"],
  "additional_notes": "string or null",
  "source_file": "string",
  "source_page": integer
}}

Metadata:
source_file: {block["source_file"]}
source_page: {block["source_page"]}

Module text:
\"\"\"
{block["raw_text"]}
\"\"\"
""".strip()


def main():
    block = load_module_block(TARGET_MODULE)
    prompt = create_prompt(block)

    OUTPUT_PATH.write_text(prompt, encoding="utf-8")

    print(f"Prompt created for module {TARGET_MODULE}")
    print(f"Saved to: {OUTPUT_PATH}")
    print("-" * 60)
    print(prompt[:1000])


if __name__ == "__main__":
    main()