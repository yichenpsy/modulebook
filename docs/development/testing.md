# Testing and evaluation

This guide separates existing automated checks, manual acceptance procedures, and historical thesis evidence. The procedures below are not a record that the current revision has passed them.

## Automated Java tests

From the repository root, using JDK 21:

```bash
./mvnw test
```

The Maven wrapper uses `pom.xml`; Surefire reports are written under `target/surefire-reports/`. CI runs `mvn clean test` and provisions PostgreSQL 16. Its database service does not by itself mean that repository SQL or migrations are exercised by the tests.

Tests are under [src/test/java/de/bht/modulebook](../../src/test/java/de/bht/modulebook).

| Test class | Existing checks |
| --- | --- |
| `ModuleServiceTest` | Initial/grouped changes, module-number conflicts, deletion confirmation, deletion logging, edit logging, and empty field selections |
| `ModuleControllerTest`, `ModulebookControllerTest` | Controller method delegation and returned data; these calls do not establish real HTTP binding or validation behavior |
| `ModuleChangeLogServiceTest` | Avoiding false changes from database IDs, blank optional values, raw workload text, and component ordering |
| `PdfImportedModuleMapperTest` | Field mapping, missing module number, and workload component parsing |
| `PdfImportServiceTest` | Saving, per-row errors, missing-module marking on re-import, duplicate numbers, and empty input |
| `PythonPdfImportRunnerTest` | Multipart request/response handling against an HTTP stub, including HTTP/1.1 |
| `ModulebookApplicationTests` | Application context loading with Flyway disabled |

Mocks/stubs cover key paths. Dedicated real-database migration tests, restore/snapshot service tests, automated browser tests, and systematic PDF field-accuracy tests are not present in this suite. In particular, controller method tests do not prove that invalid JSON receives the expected HTTP status.

## Manual acceptance checks

Use [Local setup](local-setup.md) with a fresh disposable database and a representative PDF. Record the commit, environment, source filename/hash, steps, expected/actual result, and report or screenshot for each run. IDs below are local checklist labels; FR/NFR references come from [Requirements](../product/requirements.md).

| Check | Procedure | Expected result |
| --- | --- | --- |
| A1 · FR1.1–1.3 / NFR3 | Start against the empty database, create a modulebook and module, restart the app | All migrations complete; data remains readable after restart |
| A2 · FR1.3–1.4 | Submit missing number/title/credits, negative credits, and a duplicate number through the normal module API; edit with a commit message | DTO/service rules reject invalid requests; duplicate number is scoped to the modulebook; valid edit is persisted |
| A3 · FR1.4 | Enter 5 credits and 2 SWS in the editor | Current code suggests 150 total, 34 presence, and 116 self-study hours; differing entered hours trigger guidance. This checks code behavior, not the domain correctness of its SWS formula |
| A4 · FR1.5 / FR4.1–4.2 | Set one module to `DEACTIVE`; open current publication and browser print preview | Module stays in stored data but is excluded from current publication output |
| A5 · FR3.1–3.3 | Upload a PDF, inspect its report, edit extracted values, then save | Preview alone creates no module records; save persists reviewed rows and creates the initial version; inspect per-row errors |
| A6 · FR3.4 | Re-import with an existing module, a new module, and one previously stored module omitted | Match/update existing data, create the new module, and add `MISSING_FROM_IMPORT` to the omitted module without automatically changing its status |
| A7 · FR2.1–2.5 | Save a named version, edit fields with a reason, and compare version points | History shows the operation and old/new values; browser comparison reflects changed fields |
| A8 · FR2.3 / FR4.3 | Save a version, then edit or delete a module included in it; open the saved version and print preview | Saved publication retains its snapshot content, including the deleted module |
| A9 · FR2.6 | Restore before an eligible edit; separately include a module created later | Later active changes are reverted/discarded; deletion conflict requires explicit force. Deletion/import/restore-before change sets are not eligible targets |
| A10 · FR2.6 | Restore a saved version containing a deleted module and omitting a currently active one | Missing module is recreated; absent active module becomes `DEACTIVE`; a restore change set is recorded |
| A11 · FR2.6 | On a separate fixture, reset to the first PDF import; also check a fixture with a subsequently deleted initial module | Surviving fields revert, later-created modules and later application history/versions are removed, and the initial version is retained; a physically deleted initial module is not recreated |
| A12 · FR1.2–1.3 | Attempt deletion without `DELETE`, then with it on disposable records | Exact confirmation is required; confirmed deletion is permanent in current data |
| A13 · NFR4 | In an instrumented deployment, perform a module read/edit and PDF normalization | Verify Java/Python spans in the external backend, errors, and trace correlation; a healthy pod alone is insufficient |

Test restore variants on separate fixtures so earlier destructive actions do not invalidate later expectations. Current source also limits snapshot restore to module state: it does not rewrite modulebook identity or saved-version dates.

Include failure cases in handover checks: an import where every row fails can still create the initial version; nonempty `changedFieldPaths` does not protect submitted workload/teaching-form data from being saved; restore-before cannot recover a physically deleted module later in its range. These are current implementation limits, not successful outcomes to infer from the happy-path checks.

## Historical PDF evaluation

Source: the thesis identified in [Requirements](../product/requirements.md#source-and-implementation-differences), §4.6, Table 4.3 (page 38). These are thesis-reported results, not results rerun for the current revision.

| Handbook | Actual module count reported | Detected count reported |
| --- | --- | --- |
| Architektur (B.Sc.) | 39 | 39 |
| Augenoptik (B.Sc.) | 49 | 48 |
| Data Engineering (B.Sc.) | 38 | 38 |
| Maschinenbau (B.Sc.) | 85 | 85 |
| Medieninformatik (B.Sc.) | 54 | 54 |

The reported miss was B23 in Augenoptik: its module number was merged into title content, so the expected new-module anchor was not detected. Matching counts alone do not prove correct boundaries or field contents. The thesis did not establish systematic field-level accuracy or a reliable field-level ground truth.

Current manifest expectations differ from these counts, and exact PDF revisions and run commits are not established by the table. Do not copy the thesis counts into configuration without checking the source documents. The manifest is configurable report metadata, not a verified ground-truth dataset.

### Re-evaluating extraction

1. Fix the input PDF revision and record its hash, code commit, dependency versions, and extraction configuration.
2. Manually annotate module numbers, boundaries, and selected field values in a reference set.
3. Run the [single-PDF CLI](../architecture/pdf-import.md#configuration); retain the report and normalized output before manual correction.
4. Compare module identities and boundaries, then field contents; record missing, extra, split, merged, and incorrectly mapped values separately.
5. Report automatic extraction results separately from the corrected/saved result. Review `missing_required_fields` and unmatched labels even when status is `ok`.

This is a proposed repeatable procedure, not an existing automated evaluation framework.

## Quality evidence and remaining limits

The thesis's functional evaluation is requirements-based. Prototype feedback informed usability, but no formal study of the final implementation was conducted. Layering and migrations support maintainability without measuring it. BHT deployment and OpenObserve trace inspection are historical deployment evidence; they do not verify another environment or the current live state. Future usability, performance, and extraction-accuracy claims need new recorded results.
