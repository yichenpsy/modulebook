from pathlib import Path
import yaml


PROJECT_ROOT = Path(__file__).resolve().parents[3]
PDF_IMPORT_ROOT = PROJECT_ROOT / "tools" / "pdf_import"


def load_yaml(path: Path) -> dict:
    with path.open("r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def load_base_config() -> dict:
    config_path = PDF_IMPORT_ROOT / "configs" / "base.yaml"
    return load_yaml(config_path)


def load_import_manifest() -> dict:
    manifest_path = PDF_IMPORT_ROOT / "configs" / "import_manifest.yaml"
    return load_yaml(manifest_path)


def get_imports() -> list[dict]:
    manifest = load_import_manifest()
    return manifest["imports"]
