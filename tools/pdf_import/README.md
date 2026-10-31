# PDF import tools

The active rule-based extraction pipeline is `module_import_v2/`; its HTTP wrapper is `services/pdf_import_service/main.py` at the repository root.

- [Pipeline and review guide](../../docs/architecture/pdf-import.md): stages, configuration, CLI example, outputs, and limitations.
- [Local setup](../../docs/development/local-setup.md#2-enable-pdf-import): Python installation and HTTP/subprocess modes.
- [Deployment](../../docs/deployment.md): container and Kubernetes configuration.

`configs/` contains extraction rules and source metadata. `data/` contains source PDFs and generated/review artifacts; these are not a substitute for reproducible accuracy tests. `archive/` contains earlier extraction experiments and is outside the active application path.
