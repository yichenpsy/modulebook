Please create and maintain a documentation file in this repository called:

`docs/THESIS_CODE_UNDERSTANDING.md`

Purpose:
This document is for my Bachelor thesis project. I use AI-assisted coding heavily, so I need a concise but accurate document that helps me understand the important parts of my own system well enough to explain and defend the technical decisions in my thesis and oral defense.

The document should NOT try to explain every line of code. It should focus on the architecture, important implementation decisions, data flow, business logic, persistence, versioning, testing, deployment, and maintainability.

Please analyze the CURRENT state of the repository and write the document based only on code and configuration that actually exists in the project.

## Important rules

1. Do not invent features that are not implemented.
2. Clearly distinguish:

   * implemented
   * partially implemented
   * planned / TODO
3. Prefer simple explanations over framework-internal details.
4. Focus on what I personally must understand and be able to explain.
5. Do not document trivial boilerplate unless it is important for understanding the system.
6. When the project changes later, UPDATE this existing document instead of creating a new document.
7. Preserve still-correct explanations and only add/change sections affected by the latest code changes.
8. If you find unnecessary complexity, generated abstractions, duplicated logic, or code that is difficult to explain, explicitly flag it.
9. Use concrete class names, method names, table names, endpoints, and file paths from the repository.
10. Keep the document useful for oral examination preparation.

## Structure

Use approximately this structure:

# Thesis Code Understanding Guide

## 1. System Overview

Explain in simple terms:

* What the system currently does
* Main technologies actually used
* Main architectural layers
* Important packages/directories
* A simple architecture diagram in text form

Example style:

Frontend / HTTP Client
↓
Controller
↓
Service
↓
Repository
↓
PostgreSQL

Only include layers that really exist.

---

## 2. Core Domain Model

For every important domain entity currently implemented, explain:

* What it represents in the real module handbook domain
* Important fields
* Important relationships
* Why it exists as a separate entity/table
* Which relationships I should especially understand

Include concrete examples where useful.

Pay particular attention to concepts such as:

* StudyProgram
* SPO / examination regulation
* Modulebook
* Module
* Module workload
* Teaching forms
* Examination forms
* Users / roles
* Versioning entities

Only include those that actually exist.

---

## 3. Database Design

Explain:

* Current tables
* Primary keys
* Foreign keys
* Important constraints
* One-to-one / one-to-many / many-to-many relationships
* Important SQL migrations
* Why the schema is structured this way

For each important table, answer:

"What problem would occur if this table did not exist or were merged into another table?"

Flag anything that appears over-normalized, under-normalized, or inconsistent.

---

## 4. Request and Data Flow

For every important implemented feature, describe the full flow.

For example:

### Create Module

HTTP request
→ ModuleController
→ request DTO
→ ModuleService
→ ModuleRepository
→ SQL
→ PostgreSQL
→ response DTO
→ HTTP response

Use the real classes and methods.

Do this for currently implemented important flows such as:

* create
* read
* update
* delete
* version creation
* version comparison
* authentication
* PDF generation

Only include implemented flows.

---

## 5. Controller Layer

Explain:

* Which controllers exist
* Their endpoints
* Their responsibilities
* What logic belongs here
* What logic should NOT be here

For each controller, list the methods I should be able to explain.

Do not explain Spring internals unless necessary.

---

## 6. DTOs

Explain:

* What request DTOs are
* What response DTOs are
* Why the project uses them
* Difference between DTO and domain/entity object
* Which DTOs currently exist

For each important DTO:

* where it enters/leaves the system
* why it is useful
* validation rules if present

---

## 7. Service Layer

Explain:

* Which services exist
* Which business rules they contain
* Why business logic belongs here rather than Controller or Repository

For each important service method, summarize:

Input
→ important decisions
→ repository calls
→ output

Flag service methods that are only pass-through methods without meaningful logic.

---

## 8. Repository / JDBC Layer

This section is especially important.

Explain:

* Why repositories exist
* How JdbcTemplate is used
* How SQL queries map to Java objects
* How RowMapper works in THIS project
* What Optional means in methods such as `findById`
* How insert/update/delete operations work
* How generated IDs are handled, if applicable

For each repository:

* important methods
* SQL operations
* tables affected

Explain the actual code, not generic JDBC theory.

---

## 9. Versioning

Treat this as a major thesis topic.

Explain the CURRENT implementation in detail:

* What creates a new version
* Whether versions are snapshots, change records, or both
* Tables/classes involved
* Relationship between Module and versions
* How previous versions are retrieved
* How two versions are compared
* How changed fields are detected
* Who made the change, if tracked
* timestamps
* change reasons/status if implemented
* transaction behavior
* what happens if an update fails

Also explain:

### Design rationale

Why this implementation was chosen.

### Alternatives

Briefly explain plausible alternatives such as:

* field-level change log
* event sourcing
* storing JSON snapshots

Explain why the CURRENT approach is simpler or more appropriate, if supported by the implementation.

### Questions I should be able to answer

Generate 5–10 likely oral-defense questions specifically about versioning.

---

## 10. Transactions and Data Consistency

If transactions are used, explain:

* where
* why
* what operations must succeed together
* what rollback means in this project

Use a concrete project example.

If transactions are not used where they probably should be, flag this.

---

## 11. Validation and Error Handling

Explain current:

* input validation
* Bean Validation annotations
* domain/business validation
* not-found handling
* database errors
* global exception handling
* HTTP status codes

Use concrete code examples by class/method name, but do not paste large code blocks.

Flag missing validation or inconsistent error handling.

---

## 12. Authentication and Authorization

If implemented, explain:

* authentication flow
* authorization flow
* user entity
* roles
* Spring Security configuration
* protected endpoints

Clearly explain the difference between:

authentication = who are you?

authorization = what are you allowed to do?

If not implemented, mark this section as planned/not implemented.

---

## 13. PDF / Module Handbook Generation

If implemented, explain:

Structured database data
→ template/model
→ HTML/document generation
→ PDF

Explain:

* important classes
* templates
* libraries
* where generated files go
* how module data becomes document content

Also explain how this supports maintainability compared with manually maintaining Word/PDF files.

---

## 14. Testing

Analyze the actual tests.

Group them into:

* unit tests
* repository/integration tests
* controller/API tests
* Testcontainers tests
* end-to-end tests

For each important test, explain:

* what it proves
* what could break if the test fails

Also identify important core behavior that currently has no test.

---

## 15. Docker, CI/CD and Kubernetes

Explain the repository's current deployment flow using actual files.

Prefer a diagram like:

git push
↓
GitLab CI
↓
Maven build/test
↓
Docker image
↓
Container Registry
↓
Kubernetes Deployment
↓
Pod
↓
Service
↓
Ingress
↓
Browser

Explain important files such as:

* Dockerfile
* `.gitlab-ci.yml`
* deployment.yaml
* service.yaml
* ingress.yaml

Focus on what I need to conceptually understand, not every YAML property.

---

## 16. Observability

If implemented, explain:

* Spring Boot Actuator
* metrics
* logs
* traces
* OpenTelemetry / Micrometer / Jaeger or other actual tools
* how a request could theoretically be followed through the system

If not implemented, say so.

---

## 17. Maintainability

This section is critical because maintainability is part of my thesis.

Evaluate the CURRENT codebase in terms of:

* separation of concerns
* modularity
* readability
* duplication
* coupling
* cohesion
* database design
* testability
* changeability

For every positive claim, point to concrete code structure.

For every weakness, give a concrete example.

Do not say "the system is maintainable" without evidence.

---

## 18. Important Design Decisions

Maintain a table:

| Decision | Current choice | Why | Alternative |
| -------- | -------------- | --- | ----------- |

Include important decisions such as:

* JDBC vs JPA
* normalized relational schema
* Controller/Service/Repository
* versioning strategy
* DTO usage
* PostgreSQL
* deployment strategy

Only include decisions supported by this repository or clearly documented comments/configuration.

---

## 19. Things I MUST Understand

Create three priority levels.

### Priority A — Must explain confidently

Core thesis concepts and architecture.

### Priority B — Should understand

Important implementation mechanisms.

### Priority C — Recognition is enough

Framework/configuration details that I do not need to memorize.

For each item, provide a short self-test question.

Example:

* [ ] I can explain why `ModuleService` exists.

  * Self-test: What would be wrong with putting SQL directly inside `ModuleController`?

---

## 20. Things I Do NOT Need to Memorize

Explicitly list low-value details such as:

* exact annotation syntax
* exact Maven commands
* internal Spring implementation
* every Kubernetes YAML property
* framework source code

Adjust this list according to the actual project.

---

## 21. Current Technical Debt / Risks

List the most important current risks, especially:

* AI-generated complexity
* duplicated code
* dead code
* inconsistent naming
* missing tests
* missing transactions
* oversized classes
* unclear responsibilities
* tightly coupled code
* features that I would have difficulty explaining in a defense

Use severity:

HIGH
MEDIUM
LOW

---

## 22. Latest Changes

Maintain a short changelog inside this document.

For every update, add:

### YYYY-MM-DD

* Important files added/changed
* New behavior
* What I now need to understand
* Existing concepts affected
* New possible thesis-defense questions

Do not list every cosmetic/code-format change.

---

## 23. Daily Review

At the end, maintain a section called:

# Today's 15-Minute Review

Automatically generate this based on the CURRENT project state.

Include:

### 5 concepts to review today

### 3 pieces of code to open and inspect

Give exact file paths.

### 3 questions I should answer without AI

### 1 small code modification I could make myself

It should be safe, small, and useful for testing my understanding.

### 1 architecture/data-flow exercise

For example:
"Draw what happens after PUT /modules/{id}."

---

## Update behavior

Whenever I later ask:

"Update my thesis understanding document"

you should:

1. inspect the current git diff / recent repository changes;
2. inspect any newly added important files;
3. compare them against `docs/THESIS_CODE_UNDERSTANDING.md`;
4. update only affected sections;
5. update "Latest Changes";
6. regenerate "Today's 15-Minute Review";
7. tell me briefly:

   * what changed technically,
   * what new things I need to understand,
   * whether the latest code introduced unnecessary complexity.

Do not rewrite the entire document unnecessarily.

## Writing style

Write for a Media Informatics bachelor student who understands basic programming, Java, databases and web concepts, but is still learning Spring Boot backend architecture.

Use:

* simple language
* concrete project examples
* diagrams
* tables
* short code references
* clear cause → effect explanations

Avoid:

* unexplained jargon
* long generic Spring Boot tutorials
* unnecessary implementation trivia
* pretending that I need to memorize syntax

The goal is not to make me able to rewrite the entire application from memory.

The goal is to make sure I can confidently explain:

1. what the system does,
2. how data moves through it,
3. why the architecture is designed this way,
4. how important features such as module CRUD and versioning work,
5. how the system is persisted, tested and deployed,
6. what design trade-offs I made,
7. and how these decisions relate to maintainability.
