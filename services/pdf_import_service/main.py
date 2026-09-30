import json
import os
import tempfile
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

from tools.pdf_import.module_import_v2.pipeline_single import run_single_pdf


def configure_tracing() -> None:
    service_name = os.getenv("OTEL_SERVICE_NAME", "pdf-import-service")
    endpoint = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT")

    provider = TracerProvider(
        resource=Resource.create({"service.name": service_name})
    )

    if endpoint:
        provider.add_span_processor(
            BatchSpanProcessor(OTLPSpanExporter(endpoint=endpoint))
        )

    trace.set_tracer_provider(provider)


configure_tracing()
app = FastAPI(title="Modulebook PDF Import Service")
FastAPIInstrumentor.instrument_app(app, excluded_urls="/health")


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/import")
async def import_pdf(
    file: UploadFile = File(...),
    sourceFile: str | None = Form(default=None),
) -> dict:
    if not file.filename or not file.filename.lower().endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Only PDF files are supported")

    with tempfile.TemporaryDirectory(prefix="modulebook-pdf-import-") as temp_dir:
        work_dir = Path(temp_dir)
        input_pdf = work_dir / "input.pdf"
        output_json = work_dir / "normalized_modules.json"
        report_json = work_dir / "report.json"

        content = await file.read()
        if not content:
            raise HTTPException(status_code=400, detail="PDF file is empty")

        input_pdf.write_bytes(content)

        try:
            report = run_single_pdf(
                input_pdf=input_pdf,
                work_dir=work_dir,
                output_json=output_json,
                report_json=report_json,
                source_file=sourceFile or file.filename,
            )
        except Exception as error:
            raise HTTPException(status_code=500, detail=str(error)) from error

        if report.get("status") == "failed":
            raise HTTPException(status_code=500, detail=report)

        normalized_modules = json.loads(output_json.read_text(encoding="utf-8"))

        return {
            "report": report,
            "normalizedModules": normalized_modules,
        }
