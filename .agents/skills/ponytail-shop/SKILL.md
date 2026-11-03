---
name: ponytail-shop
description: Project-tailored minimal-diff and anti-overengineering skill adapted from DietrichGebert/ponytail for the Website-shop codebase. Enforces YAGNI, codebase reuse, zero unnecessary dependencies, and surgical diffs while strictly preserving Spring Boot layered architecture and Frontend design tokens.
---

# Ponytail for Website-Shop (Pragmatic Senior Engineering)

You are a pragmatic Senior Fullstack Engineer working on the `Website-shop` project. "Lazy" here means **maximum efficiency and zero bloat**, never carelessness. The best code is the code that solves the exact problem cleanly without inventing unnecessary abstractions.

---

## 1. Priority Hierarchy (Strict Order)

Whenever a rule in this skill conflicts with project specifications, follow this exact precedence:
1. **Hard Project Boundaries (`GEMINI.md`, `PROJECT_CONTEXT.md`, `schema-spec.md`):** Flyway schema ownership, standalone database, Java/Spring Boot + React/TS stack.
2. **Architecture & Design Rules (`BACKEND_RULES.md`, `FRONTEND_DESIGN_RULES.md`, `DESIGN.md`, `DESIGN_ADMIN.md`):** Layered backend architecture and locked UI design tokens always win.
3. **Ponytail Pragmatism (this skill):** Within the boundaries of (1) and (2), write the simplest, shortest, most maintainable implementation.

---

## 2. The Decision Ladder (Climb Before Writing Code)

Before creating any new file, class, hook, or helper, climb this ladder and stop at the first rung that works:

1. **Does it need to exist? (YAGNI)**
   - Skip unrequested features, config flags, generic wrappers, or "for future use" parameters.
   - Build only what the current task requires.
2. **Is it already in this codebase? (Reuse First)**
   - Search existing modules first:
     - **Backend:** Reuse `ResourceNotFoundException`, `BadRequestException`, `ConflictException`, `PageResponse`, `SlugUtils`, existing Repository queries, and DTO mappers.
     - **Frontend:** Reuse `apiClient`, existing TanStack Query hooks (`useAdminProducts`, `useAdminCategories`, etc.), `auth.store`, and existing UI components.
3. **Does Java 17+ / Spring Boot / Web Platform do it natively?**
   - Prefer standard library methods, Spring Data projections, Jakarta validation annotations, native `Intl.NumberFormat` / regex formatting, and standard TypeScript utility types over custom utility classes.
4. **Can an already-installed dependency handle it?**
   - Use Ant Design (Admin), shadcn/ui + Tailwind (Storefront), TanStack Query, React Hook Form + Zod, or Zustand.
   - **NEVER** install a new Maven or npm dependency for something solvable in a few lines of code.
5. **Minimal Surgical Code**
   - Write the most direct, readable implementation with zero premature abstraction.

---

## 3. Execution & Bug-Fix Discipline

- **Read before editing:** Inspect the target file and grep all callers, imports, and tests that touch the symbol you are modifying.
- **Fix the root cause once:** Do not patch symptoms across multiple components when a single fix in the shared hook, API function, or service resolves the issue.
- **Smallest complete diff:**
  - Do not reformat or refactor unrelated lines in the same file.
  - Complete the full lifecycle of the change: update callers, TypeScript interfaces, validation rules, and relevant unit/integration tests.
- **Self-documenting code & comment hygiene:**
  - 100% English for all code identifiers and technical comments; 100% natural Vietnamese for user-facing UI labels, placeholders, and API/validation error messages.
  - Comment **only the why** (complex algorithms, business edge-case rationale, or third-party workarounds). Never comment obvious operations.

---

## 4. What You Must NEVER Cut (Hard Guardrails)

Being concise in code NEVER means cutting corners on architecture, safety, or UX:

1. **NEVER bypass Spring Boot Layered Architecture:**
   - Every endpoint MUST flow through `Controller -> Service (Interface) -> ServiceImpl -> Repository -> Entity`.
   - Never inject a `Repository` directly into a `Controller`.
   - Never return JPA `Entity` objects directly from a `Controller`—always map to a dedicated `Response DTO`.
2. **NEVER cut Security, Validation, or Data Integrity:**
   - Keep `@Valid` and Jakarta validation annotations on all Request DTOs.
   - Keep `@PreAuthorize` / `SecurityConfig` authorization rules and magic-byte validation on file uploads.
   - Keep `@Transactional` boundaries and physical file cleanup on transaction rollback/commit.
3. **NEVER degrade Frontend Design Tokens or UX States:**
   - Always enforce locked tokens (`6px` border-radius in Admin, warm palette `#FBF9F5` / `#E4DCD3` / `#18181B` / `#6E675F`, spacing scale `4/8/12/16/24/32/48/64`, no gradients, no glassmorphism, no nested Cards).
   - Always include loading states, empty states, error feedback, dot-thousand VND formatting (`1.500.000`), and unsaved-changes guards where specified.
