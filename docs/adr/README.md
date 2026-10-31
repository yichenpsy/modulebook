# Architecture decision records

These records explain the reasons, alternatives, and consequences behind key implemented choices. Detailed operation and configuration remain in the architecture and deployment guides.

| Record | Decision |
| --- | --- |
| [0001](0001-ci-cd-on-kubernetes.md) | GitLab CI and Kubernetes delivery |
| [0002](0002-rule-based-pdf-import.md) | Rule-based PDF extraction with human review |
| [0003](0003-change-sets-and-snapshots.md) | Change sets and named snapshots |
| [0004](0004-opentelemetry-for-observability.md) | OpenTelemetry application instrumentation |

Recorded retrospectively on 2026-10-31. Current code and configuration establish the implementation; where historical reasons are available in the thesis, the records identify that source. Other alternative comparisons explain trade-offs without claiming a historical benchmark or formal evaluation. The [requirements guide](../product/requirements.md#source-and-implementation-differences) identifies the thesis and known differences.

Numbers identify records, not priorities. Give a new decision the next unused number. If a decision changes later, add a replacement record and mark the earlier one superseded, retaining the explanation of the former design.
