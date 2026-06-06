from enum import Enum
from pydantic import BaseModel, Field, ConfigDict


class ModuleStatus(str, Enum):
    """
    Allowed module status values.

    For the MVP, we only include the values we expect in the module handbook.
    Later we can extend this if needed.
    """
    PFLICHTMODUL = "Pflichtmodul"
    WAHLPFLICHTMODUL = "Wahlpflichtmodul"


class ExtractedModule(BaseModel):
    """
    Structured data extracted from one module block.

    This is the contract between:
    - LLM extraction
    - Python validation
    - staging DB
    """

    model_config = ConfigDict(extra="forbid")

    module_number: str = Field(
        description="Official module number, for example B01 or WP01"
    )

    title_de: str = Field(
        description="German module title"
    )

    title_en: str | None = Field(
        default=None,
        description="English module title if available"
    )

    credits: float = Field(
        description="ECTS credits / Leistungspunkte as a number, for example 5 or 2.5"
    )

    workload: str = Field(
        description="Workload text exactly or nearly exactly as stated in the PDF"
    )

    learning_area: str = Field(
        description="Lerngebiet"
    )

    learning_objectives: str = Field(
        description="Lernziele / Kompetenzen"
    )

    prerequisites: str | None = Field(
        default=None,
        description="Voraussetzungen. Use null if no prerequisite is stated."
    )

    semester_level: str = Field(
        description="Niveaustufe, for example 1. Studienplansemester"
    )

    teaching_format: str = Field(
        description="Lehrform"
    )

    status: ModuleStatus = Field(
        description="Module status, for example Pflichtmodul or Wahlpflichtmodul"
    )

    frequency: str = Field(
        description="Häufigkeit des Angebotes"
    )

    exam_type: str = Field(
        description="Prüfungsform"
    )

    grade_calculation: str = Field(
        description="Ermittlung der Modulnote"
    )

    recognized_modules: str | None = Field(
        default=None,
        description="Anerkannte Module"
    )

    contents: list[str] = Field(
        description="Main module contents. Use a list of content items."
    )

    literature: list[str] = Field(
        description="Literature entries. Use an empty list if no literature is given."
    )

    additional_notes: str | None = Field(
        default=None,
        description="Weitere Hinweise"
    )

    source_file: str = Field(
        description="PDF file name from which the module was extracted"
    )

    source_page: int = Field(
        description="PDF page number where the module block starts"
    )