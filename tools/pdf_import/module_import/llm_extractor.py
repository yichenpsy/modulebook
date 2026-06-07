import json
from pathlib import Path

from tools.pdf_import.module_import.config import PDF_IMPORT_ROOT


def load_prompt_template() -> str:
    prompt_path = PDF_IMPORT_ROOT / "prompts" / "extract_module_v1.txt"

    with prompt_path.open("r", encoding="utf-8") as f:
        return f.read()


def load_jsonl(path: Path) -> list[dict]:
    records = []

    with path.open("r", encoding="utf-8") as f:
        for line in f:
            if line.strip():
                records.append(json.loads(line))

    return records


def render_prompt(template: str, module_block: dict) -> str:
    prompt = template.replace("{{module_text}}", module_block["raw_text"])
    prompt = prompt.replace("{{source_file}}", str(module_block["source_file"]))
    prompt = prompt.replace("{{source_pages}}", json.dumps(module_block["source_pages"]))

    return prompt


def create_prompt_for_module(import_id: str, module_number: str) -> Path:
    module_blocks_path = (
        PDF_IMPORT_ROOT
        / "data"
        / "work"
        / import_id
        / "module_blocks.jsonl"
    )

    module_blocks = load_jsonl(module_blocks_path)

    selected_block = None

    for block in module_blocks:
        if block["module_number"] == module_number:
            selected_block = block
            break

    if selected_block is None:
        raise ValueError(f"Module not found: {module_number}")

    template = load_prompt_template()
    prompt = render_prompt(template, selected_block)

    output_path = (
        PDF_IMPORT_ROOT
        / "data"
        / "review"
        / f"llm_prompt_{import_id}_{module_number}.txt"
    )

    output_path.parent.mkdir(parents=True, exist_ok=True)

    with output_path.open("w", encoding="utf-8") as f:
        f.write(prompt)

    return output_path


if __name__ == "__main__":
    output_path = create_prompt_for_module(
        import_id="architektur_2021",
        module_number="B01",
    )

    print(f"Prompt written to: {output_path}")