INSERT INTO teaching_forms (code, label_de, label_en)
VALUES
    ('SU', 'Seminaristischer Unterricht', 'Seminar-style teaching'),
    ('UE', 'Übung', 'Exercise'),
    ('P', 'Praktikum', 'Lab'),
    ('S', 'Seminar', 'Seminar'),
    ('V', 'Vorlesung', 'Lecture')
ON CONFLICT (code) DO NOTHING;
