# Implementation Documentation Sync Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the PLAN, PROFILE, REPORT, FOODDATA, and NUTRI implementation documents into alignment with the current repository source while clearly separating implemented behavior from agreed future Food/Ingredient, allergen, and chronic-condition schema.

**Architecture:** Treat current Java entities, DTOs, controllers, services, and Flyway migrations as the source of truth for `AS-IS`. Preserve the agreed normalized schema for recipes, allergens, and chronic conditions as `TO-BE` only; do not alter product code or migrations. Correct claims and examples in the five module documents, preserving useful requirements while marking incomplete behavior explicitly.

**Tech Stack:** Markdown documentation; Java/Spring Boot source inspection; PostgreSQL/Flyway migrations.

**Spec:** User-approved approach 1 in conversation: document current implementation as-is and future agreed schema separately; audit PLAN, PROFILE, REPORT, FOODDATA, and NUTRI docs against source.

## Global Constraints

- Documentation-only changes; do not modify Java, SQL migrations, API specs, seed data, or tests.
- `AS-IS` claims must be supported by current source or migrations.
- `TO-BE` designs must not be worded as implemented or deployed.
- The agreed future model is `foods` + `ingredients` + `food_ingredients`; normalized allergen catalog and food/user relation tables; normalized chronic-condition catalog and profile-condition relation table.
- Existing nutritional snapshots used by PLAN/NUTRI remain stored on `foods`/logs unless and until code changes.
- Avoid expanding scope into other module documents.

## Review Focus

- Current PROFILE request/response and persistence differ from older docs; validate exact field names, routes, and constraints against current source.
- PLAN contains medically unsafe overclaims; describe only implemented filters/constraints and do not claim clinical safety for unsupported condition data.
- REPORT endpoint query parameter is `date` (not `endDate`); examples and forecasting details must match current DTO/service behavior.
- NUTRI database limit for `water_logs.amount_ml` is 5000 ml; current docs must not claim 3000.
- Food, allergen, ingredient, and chronic-condition normalization has not yet been migrated; preserve existing JSONB as-is and label normalized design as to-be.

---

### Task 1: Sync FOODDATA model and agreed future food composition schema

**Files:**
- Modify: `docs/IMPLEMENT_MODULE_FOODDATA.md`

**Interfaces:**
- Consumes current schema from `V20261118100000__food_schema.sql`, `V20261123100000__plan_schema.sql`, `Food.java`, `CustomFoodRequest.java`, Food DTOs, and `FoodServiceImpl`.
- Produces an explicit `AS-IS` data dictionary and `TO-BE` schema section for ingredients/recipe composition and normalized allergens.

- [ ] **Step 1: Correct current schema description and field list**

Reflect that current `foods` includes 30 columns after PLAN migration, including `feature_vector`; current allergen storage is `foods.allergens JSONB`; ingredients and condition-specific sodium/purine/potassium/phosphorus fields do not exist. Show current supported custom-food request fields from `CustomFoodRequest` and distinguish derived serving nutrition from user input accurately.

- [ ] **Step 2: Add agreed future model as explicitly unimplemented**

Document `ingredients`, `food_ingredients(food_id, ingredient_id, quantity_grams)`, `allergens`, `food_allergens`, and `user_allergies` as proposed future schema. State that recipe-derived nutrition/allergens should be calculated server-side and summarized back to `foods` for current consumers. Do not claim these tables/migrations/APIs exist.

- [ ] **Step 3: Remove contradictory rationale**

Replace statements that ingredients are intentionally merged into flat foods and that JSONB allergen storage is the final normalized design. Clearly state the YOLO/OFF flat-food records remain valid as current records while user-composed recipes are a future capability.

- [ ] **Step 4: Review document for unsupported as-is claims**

Check endpoints, seed, table fields, source enums and statuses against current source/migrations. Mark any planned feature as planned rather than completed.

### Task 2: Sync PROFILE data model and future normalized health relations

**Files:**
- Modify: `docs/IMPLEMENT_MODULE_PROFILE.md`

**Interfaces:**
- Consumes `HealthProfileRequest`, `HealthProfileResponse`, `UserHealthProfile`, `HealthProfileServiceImpl`, profile migration, and current controller.
- Produces current JSONB-based profile description plus clearly separated to-be normalized allergy and chronic-condition relations.

- [ ] **Step 1: Reconcile current entity and migration fields**

List actual current fields, including `preference_vector` and `chronic_conditions` added by PLAN migration; distinguish these from the initial profile migration. Confirm actual endpoint route/method and validation from current controller/DTO/service.

- [ ] **Step 2: Document agreed future allergy/condition schema**

Keep current `allergies JSONB` and `chronic_conditions JSONB` in `AS-IS`. Add `allergens` + `user_allergies` (with `food_allergens` cross-reference) and `chronic_conditions` + `user_profile_conditions` in `TO-BE`. Mention migration/backfill needed; do not imply implementation.

- [ ] **Step 3: Reconcile acceptance/behavior claims**

Update target weight requirement and direction validation, partial custom macro behavior, age and calorie safety values against actual request/service/calculator. Remove inaccurate statement that only maintain goal permits null target if source enforces targets for change goals.

### Task 3: Sync PLAN algorithm, API and limitations

**Files:**
- Modify: `docs/IMPLEMENT_MODULE_PLAN.md`

**Interfaces:**
- Consumes current PLAN migration (including later budget-column migration), entities, DTOs, controller, `MealPlanServiceImpl`, and `RuleBasedMenuGenerator`.
- Produces accurate current contracts and medical-data limitations; references normalized future schema only as dependency/design.

- [ ] **Step 1: Correct schema and lifecycle documentation**

Add `max_budget_vnd` from the later migration; align statuses, request fields, response fields and actual entity/migration names. State allergies/conditions are currently JSONB-backed profile data; future normalized relations remain unimplemented.

- [ ] **Step 2: Correct algorithm and clinical-safety claims**

Reflect actual calories/macros scoring and condition filters in code. Remove unsupported guarantees (e.g. universal hard daily sugar ceiling, clinical-grade safety, unsupported disease nutrients); state sodium/purine/etc. source data is absent where applicable. Describe allergy/condition checks as limited by available food metadata.

- [ ] **Step 3: Correct endpoint and apply semantics**

Match controller routes, HTTP statuses, request parameters and service behavior. Document that apply accepts an optional target date and note its current duplicate-entry/changed-profile validation limitations if they remain in source; do not prescribe unimplemented behavior as current.

- [ ] **Step 4: Correct learning behavior and tests/task status**

Align preference-vector updates with current log/apply behavior. Mark implementation/test status accurately based on current source, not old sprint plan language.

### Task 4: Sync NUTRI persistence, endpoints and constraints

**Files:**
- Modify: `docs/IMPLEMENT_MODULE_NUTRI.md`

**Interfaces:**
- Consumes current NUTRI migration, entities, controllers, DTOs and services.
- Produces accurate schema/API descriptions and current integration semantics.

- [ ] **Step 1: Reconcile migration fields and constraints**

Match `user_food_logs`, `water_logs`, and `daily_summaries` exactly to migration, including 5000 ml water-log ceiling, identifiers, snapshots, idempotency and actual nullability/types.

- [ ] **Step 2: Reconcile APIs and actual request contracts**

Verify all routes/methods and request/response fields against controllers/DTOs; remove unsupported endpoints (such as favorites if absent) and correct date/copy semantics.

- [ ] **Step 3: Reconcile behavior and module integrations**

Describe actual recalculation triggers, events, AI/MinIO wiring, allergy-warning behavior, and status. Distinguish implemented functionality from requirements not yet present.

### Task 5: Sync REPORT API and forecast documentation

**Files:**
- Modify: `docs/IMPLEMENT_MODULE_REPORT.md`

**Interfaces:**
- Consumes current REPORT controller, service, response DTOs, forecast engine and migrations.
- Produces response examples and algorithm descriptions consistent with actual source.

- [ ] **Step 1: Correct report endpoints and parameters**

Use actual `date` parameter for calories/macros/weight, actual summary/dashboard parameters and exact routes. Remove unsupported pagination or `endDate` examples.

- [ ] **Step 2: Reconcile DTO response examples and forecast behavior**

Match field names and nullability to DTOs. Confirm min data count, EMA coefficient, projection behavior, and net calorie formula with engine; fix sign/formula prose if inconsistent.

- [ ] **Step 3: Mark acceptance targets vs verified implementation**

Keep intended performance/SRS goals labeled as targets unless tests/benchmarks verify them. Correct claims about tables/integrations to current migration/service behavior.

### Task 6: Cross-document verification

**Files:**
- Verify: `docs/IMPLEMENT_MODULE_FOODDATA.md`
- Verify: `docs/IMPLEMENT_MODULE_PROFILE.md`
- Verify: `docs/IMPLEMENT_MODULE_PLAN.md`
- Verify: `docs/IMPLEMENT_MODULE_NUTRI.md`
- Verify: `docs/IMPLEMENT_MODULE_REPORT.md`

- [ ] **Step 1: Search for contradictions and stale claims**

Search all five docs for JSONB-vs-normalized statements, `endDate`, `3000`, old route names, universal medical-safety guarantees, and “implemented/completed” claims. Resolve each against source or label it as target/to-be.

- [ ] **Step 2: Inspect documentation-only diff**

Run `git diff --check` and inspect `git diff -- docs/IMPLEMENT_MODULE_FOODDATA.md docs/IMPLEMENT_MODULE_PROFILE.md docs/IMPLEMENT_MODULE_PLAN.md docs/IMPLEMENT_MODULE_NUTRI.md docs/IMPLEMENT_MODULE_REPORT.md`. Confirm no code or migration files changed.
