# Domain models

One file per bounded context: `docs/domain/<context>.md`, written by the `domain-modeler` agent and approved by a human before implementation.

Each file contains:
1. **Purpose**: what the context is responsible for, and what it is not.
2. **Ubiquitous language**: a glossary table (term, meaning, code name).
3. **Aggregates**: root, entities, value objects, and the invariants each protects.
4. **Domain events**: name (past tense), trigger, payload.
5. **Context map**: relationships to other contexts (customer/supplier, conformist, anti-corruption layer…).
6. **Acceptance criteria**: numbered Given/When/Then scenarios per feature (`AC-<feature>-<n>`), referenced from tests.
