from module_schema_step3 import ExtractedModule


def main():
    example = {
        "module_number": "B01",
        "title_de": "Mathematik I",
        "title_en": "Mathematics 1",
        "credits": 5,
        "workload": "102 Stunden Präsenz (4 SWS SU + 2 SWS Ü), 48 Stunden Selbststudium",
        "learning_area": "Mathematisch-naturwissenschaftliche Grundlagen",
        "learning_objectives": (
            "Kenntnisse in den mathematischen Grundlagen der Informatik, "
            "Sicherheit im abstrakten und strukturellen Denken."
        ),
        "prerequisites": "Empfehlung: die Belegung des Mathematik-Brückenkurses vor Studienbeginn",
        "semester_level": "1. Studienplansemester",
        "teaching_format": "Seminaristischer Unterricht, Übung",
        "status": "Pflichtmodul",
        "frequency": "jedes Semester",
        "exam_type": (
            "Die Prüfungsform wird nach §19(2) RSPO durch die Lehrkraft festgelegt. "
            "Sofern die Lehrkraft die Prüfungsform und die Prüfungsmodalitäten nicht "
            "am Semesteranfang in der Frist nach §19 (2) RSPO festlegt, gilt folgende "
            "Prüfungsform: 100% Klausur (90 Minuten)."
        ),
        "grade_calculation": "Siehe Studienplan",
        "recognized_modules": "Module vergleichbaren Inhalts",
        "contents": [
            "I Diskrete Mathematik",
            "Aussagenlogik",
            "Prädikatenlogik",
            "Grundbegriffe der Mengenlehre",
            "Kombinatorik",
            "Relationen und Abbildungen"
        ],
        "literature": [
            "G. + S. Teschl: Mathematik für Informatiker Band 1, Springer+Vieweg Verlag",
            "P. Hartmann: Mathematik für Informatiker, Vieweg+Teubner Verlag",
            "L. Görke: Mengen, Relationen, Funktionen, Harri Deutsch Verlag"
        ],
        "additional_notes": "Dieses Modul wird auf Deutsch angeboten.",
        "source_file": "BA_Medieninformatik_2024_11_Modulhandbuch_zur_Studien-_und_Pruefungsordnung_2017.pdf",
        "source_page": 6
    }

    '''
    Check whether this dictionary is a valid module.
    If yes, turn it into a clean module object.
    If no, stop and show an error.
    '''
    module = ExtractedModule.model_validate(example)

    print("Schema check passed.")
    print(module.model_dump_json(indent=2))


if __name__ == "__main__":
    main()