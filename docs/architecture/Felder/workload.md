# Workload Data Model

## Goal

This document explains how workload is stored in the database and how its fields are connected.

In this project, workload means the time effort of one module:

- total workload from credits
- presence hours from teaching forms and SWS
- self-study hours from the remaining time

## Related Entities

```text
modules
  1
  |
  | module_id
  v
module_workloads
  1
  |
  | module_workload_id
  v
presence_hour_components
  *
  |
  | teaching_form_id
  v
teaching_forms
```

## Tables

### `modules`

The module owns the workload.

Important field:

- `credits`: CP value of the module, for example `5.0`

`credits` is stored on `modules` because credits describe the whole module, not only the workload details.

### `module_workloads`

Stores the calculated workload summary for one module.

Important fields:

- `module_id`: links this workload to one module
- `presence_hours`: calculated hours spent in scheduled teaching
- `self_study_hours`: calculated hours spent outside scheduled teaching
- `raw_text`: original workload text from the module handbook, if imported from PDF

Relationship:

- one module has zero or one workload
- `module_workloads.module_id` is unique, so one module cannot have multiple workload summaries
- if a module is deleted, its workload is deleted too

### `presence_hour_components`

Stores the parts used to calculate presence hours.

Important fields:

- `module_workload_id`: links this component to one workload
- `teaching_form_id`: links this component to a teaching form, for example `SU` or `UE`
- `sws_amount`: number of SWS for this teaching form
- `sort_order`: keeps the display order stable

This table exists because one workload can contain multiple teaching forms.

Example:

```text
4 SWS SU + 2 SWS UE
```

This becomes two `presence_hour_components` rows.

### `teaching_forms`

Stores reusable teaching form types.

Examples from seed data:

- `SU`: Seminaristischer Unterricht
- `UE`: Uebung
- `P`: Praktikum
- `S`: Seminar
- `V`: Vorlesung

`presence_hour_components.teaching_form_id` points here, so workload does not store text like `Uebung` directly.

## Field Rules

The system uses these constants in `ModuleService`:

```text
1 CP = 30 hours
1 SWS = 17 presence hours per semester
```

These values come from the current service logic:

- `HOURS_PER_CREDIT = 30`
- `WEEKS_PER_SEMESTER = 17`

## Calculation

### Total Workload

```text
total_workload_hours = modules.credits * 30
```

Example:

```text
5 CP * 30 = 150 hours
```

### Presence Hours

```text
presence_hours = sum(sws_amount) * 17
```

Example:

```text
4 SWS SU + 2 SWS UE = 6 SWS
6 * 17 = 102 presence hours
```

### Self-Study Hours

```text
self_study_hours = total_workload_hours - presence_hours
```

Example:

```text
150 total hours - 102 presence hours = 48 self-study hours
```

## Full Example

Original module handbook text:

```text
5 Credits
102 Stunden Praesenz (4 SWS SU + 2 SWS UE), 48 Stunden Selbststudium
```

Database meaning:

```text
modules
id: 10
module_number: B01
credits: 5.0
```

```text
module_workloads
id: 20
module_id: 10
presence_hours: 102
self_study_hours: 48
raw_text: 102 Stunden Praesenz (4 SWS SU + 2 SWS UE), 48 Stunden Selbststudium
```

```text
presence_hour_components
id: 30
module_workload_id: 20
teaching_form_id: SU
sws_amount: 4
sort_order: 0
```

```text
presence_hour_components
id: 31
module_workload_id: 20
teaching_form_id: UE
sws_amount: 2
sort_order: 1
```

Why the fields connect this way:

- `credits` gives the total allowed workload hours
- `sws_amount` gives scheduled teaching time
- `teaching_form_id` explains what kind of teaching the SWS belongs to
- `presence_hours` is calculated from all SWS components
- `self_study_hours` is what remains after scheduled teaching
- `raw_text` keeps the original imported text for checking and debugging

## Design Decision

Workload is not stored only as raw text.

It is split into:

- summary: `module_workloads`
- detailed SWS parts: `presence_hour_components`
- reusable teaching form labels: `teaching_forms`

This makes the workload searchable, editable, and calculable while still keeping the original text.

## Open Questions

