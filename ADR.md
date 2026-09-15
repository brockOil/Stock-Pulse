# Architecture Decision Record - StockPulse

Format for each entry: **Context → Options → Decision → Tradeoffs**.

---

## 1. Where does commerce logic live?

**Context.** Pricing and reorder logic (deterministic rules today, an LLM call tomorrow,
a competitor-aware strategy in sprint 2) needs a home that doesn't turn into a god-class
mixing business rules, persistence, and event wiring.

**Options.**
- Put the `if stock < threshold ...` logic directly in `ProductService`, alongside CRUD.
- Put it in the JPA entities as domain methods (`Product.recommendPrice()`).
- Extract it behind a `PricingStrategy` / `ReorderStrategy` interface, implemented by
  dedicated strategy classes, orchestrated by a separate `SuggestionService`.

**Decision.** The third option. `PricingStrategy`/`ReorderStrategy`
(`strategy/PricingStrategy.java`, `strategy/ReorderStrategy.java`) are pure functions of
`(Product, TriggerReason, categoryPeerVelocity) -> Recommendation`, with no persistence
or HTTP awareness. `SuggestionService` is the only place that turns a `Recommendation`
into a persisted `PricingSuggestion`/`ReorderSuggestion` and applies the idempotency rule.
`ProductService` owns product CRUD and trigger *detection* (not recommendation).
`ProductLifecycleService` owns the `Product.status` state machine exclusively, because
three different call sites (stock/order updates, suggestion creation, suggestion
decisions) all need to flip status, and duplicating that `if` chain three times is how
you get a status bug.

**Tradeoffs.** More files and more indirection than "just put it in the service" for a
system this size. Worth it here because T-2 explicitly asks for a pluggable engine and
T-6 asks the ADR to defend the boundary - a single service class would have made the
sprint-2 `CompetitorAwareStrategy` insertion point (see #6) much less obvious.

---

## 2. Unified AI call vs. separate pricing/reorder calls?

**Context.** T-2 offers a choice: "Two contracts - or one unified `CommerceAdvisor`
returning both pricing and reorder recommendations." A unified call is one LLM round
trip (cheaper, faster) instead of two.

**Options.**
- One `CommerceAdvisor.recommend(...)` returning `{pricing, reorder}` in a single prompt
  and a single parsed JSON object.
- Two separate contracts (`PricingStrategy`, `ReorderStrategy`), each with its own AI
  implementation and its own LLM call.

**Decision.** Two separate contracts. `AiPricingStrategy` and `AiReorderStrategy` each
build their own prompt (`PromptBuilder.buildPricingPrompt` /
`buildReorderPrompt`) and make an independent `LLMGateway.callLLM(...)` call.

**Tradeoffs.** This costs an extra LLM round trip when both suggestion types fire
together (the common case - `AgenticLoopListener.handle()` always generates both). We
accepted that cost for three reasons that mattered more here:
1. **Independent fallback.** If the AI response for pricing is malformed but reorder is
   fine, a unified call would force an all-or-nothing decision about whether to trust
   the reorder half. Separate calls mean each strategy validates and falls back
   independently (`AiPricingStrategy`/`AiReorderStrategy` each wrap their own
   `RuleBasedXStrategy` fallback).
2. **Clean contracts for the strategy registry.** `CommerceStrategyRegistry` switches
   pricing and reorder strategy mode *independently* (`PATCH /api/config/strategy` takes
   a `suggestionType`). A unified advisor would need its own internal mode-per-field
   logic to support "AI pricing, rule-based reorder" - a real demo scenario - which two
   contracts give for free.
3. **Prompt quality.** Pricing and reorder are genuinely different questions (see #4's
   "two prompts, not one" note) even for the *same* trigger; cramming both into one
   prompt/response schema pushes toward a lowest-common-denominator prompt.

If sprint 2 shows LLM cost/latency actually matters at scale, the fix is additive: a
`UnifiedAiCommerceAdvisor` implementing both interfaces and internally caching one LLM
call per (product, trigger) pair - it wouldn't require touching `SuggestionService` or
the registry.

---

## 3. How does runtime strategy switching work?

**Context.** T-2 requires switching the active strategy "via config at runtime - no code
change, no restart" and requires that HTTP on-demand endpoints and the async agentic
loop "use the same contracts without modification."

**Options.**
- A Spring `@ConditionalOnProperty` bean per strategy, requiring an app restart to change.
- A factory method with a `switch` on a config string, re-read on every call (works, but
  spreads the "which strategy is active" knowledge across every caller).
- A single registry bean holding the current mode behind an `AtomicReference`, exposed
  through one method per suggestion type (`activePricingStrategy()`,
  `activeReorderStrategy()`).

**Decision.** `CommerceStrategyRegistry` (the third option). It's constructed with all
four concrete strategy beans (`ruleBasedPricingStrategy`, `aiPricingStrategy`,
`ruleBasedReorderStrategy`, `aiReorderStrategy`, wired by `@Qualifier`) plus two
`AtomicReference<StrategyMode>` fields seeded from
`commerce.pricing-strategy`/`commerce.reorder-strategy` at startup.
`StrategyConfigController` is the only place that mutates them
(`PATCH /api/config/strategy`); `SuggestionService` (both the on-demand and the async
path, since both go through the same method) is the only place that reads them. Neither
caller knows or cares which concrete class is active.

**Tradeoffs.** State lives in a singleton in memory - a restart resets to the
`application.properties` default, and a multi-instance deployment would need this moved
to shared config (e.g. a feature-flag service) to stay consistent across nodes. Fine for
a single-instance hackathon deployment; called out here so it isn't a silent surprise
later.

---

## 4. LLM failure handling

**Context.** T-3 requires validating bounds, handling timeouts/quota/unparseable JSON,
and explicitly states the async path must never silently drop a suggestion. It also
warns that inventory-low and demand-spike are "genuinely different merchandising
decisions" and the model needs distinct trigger context to reason well.

**Options for failure handling.**
- Let exceptions propagate and return an error to the caller (fine for the HTTP path,
  wrong for the async path - nothing is listening for an error there).
- Catch failures and skip suggestion creation entirely.
- Catch failures and fall back to the rule-based strategy, always returning a usable
  `Recommendation`.

**Decision.** The third option, implemented *inside* the AI strategy, not by its caller.
`AiPricingStrategy.recommend()` wraps the entire LLM call → `JsonExtractor.extractJsonObject`
→ Jackson parse → bounds validation (`price-max-multiplier`/`price-min-multiplier` off
the current price, positive price, recognized `direction` enum, non-blank reasoning)
pipeline in one `try/catch`; any `Exception` (timeout, connection refused, malformed
JSON, out-of-bounds price) logs a warning and delegates to the injected
`ruleBasedPricingStrategy`. `AiReorderStrategy` mirrors this (positive-integer quantity
bounded by `commerce.ai.max-reorder-quantity`, lead time defaulted if missing/invalid).
Because the fallback lives inside the strategy, `SuggestionService` and
`AgenticLoopListener` never need to know AI was involved at all - they always get back a
`Recommendation` with `.source()` set to `RULE_BASED` or `AI`, which is persisted as
`generatedBy` on the suggestion for UI transparency (`Badge` in the React console).

**Two prompts, not one.** `PromptBuilder` builds a distinct `switch` branch per
`TriggerReason` for both pricing and reorder prompts. For pricing, `INVENTORY_LOW`
explicitly frames the raise-price-vs-clearance tradeoff and asks the model to reason
about it; `DEMAND_SPIKE` frames a margin-capture opportunity instead. Sharing one
template with an interpolated field would have produced generically-worded reasoning
that doesn't actually engage with which situation is which - exactly the failure mode
the brief calls out.

**Tradeoffs.** Every AI failure is currently silent to the *merchandiser* - they see a
rule-based suggestion with no indication the AI path was attempted and failed except the
`generatedBy: RULE_BASED` badge and the backend log line. That's a deliberate priority
call (a working, conservative suggestion beats a visible error box), but a
"strategy health" indicator would be a reasonable sprint-2 addition.

---

## 5. Agentic loop trigger and decoupling

**Context.** T-4 requires the stock/order endpoints to return immediately while
suggestion generation happens off the request thread, requires idempotency (no duplicate
`PENDING` suggestions for the same product + trigger + type), and requires both trigger
types to be able to fire for the same product.

**Options for decoupling.**
- A `@Scheduled` poller scanning for low-stock products every N seconds.
- Direct synchronous method calls from `ProductService` into `SuggestionService`.
- `ApplicationEventPublisher` + `@EventListener` + `@Async`.

**Decision.** `ApplicationEventPublisher` + `@Async` - but specifically
`@TransactionalEventListener(phase = AFTER_COMMIT)`, not plain `@EventListener`.
`ProductService.evaluateTriggers()` publishes `InventoryLowEvent`/`DemandSpikeEvent`
synchronously from inside the same `@Transactional` method that just changed
`stockLevel`/`demandVelocity`. A plain `@Async @EventListener` would race that
transaction: the async thread could start reading the product on a separate DB
connection *before* the publishing transaction commits, and (depending on isolation
level) see stale data. `AFTER_COMMIT` guarantees the listener only runs once the
triggering change is durably committed, so `AgenticLoopListener` always observes the
stock/velocity values that caused the trigger. `fallbackExecution = true` is a
deliberate safety net: if an event is ever published outside a transaction, the loop
still fires instead of silently vanishing - given the brief's "silent drop is worse than
a rule-based suggestion" principle, that principle should apply to the trigger mechanism
itself, not just AI failures.

A dedicated bounded `ThreadPoolTaskExecutor` (`agenticTaskExecutor`,
`config/AsyncConfig.java`) backs the `@Async` work with `CallerRunsPolicy`, so a burst of
simulated orders applies backpressure instead of spawning unbounded threads.

**Two triggers, one handler.** `AgenticLoopListener.handle(productId, trigger)` is shared
by both `onInventoryLow` and `onDemandSpike` - each just calls it with a different
`TriggerReason`. Both triggers can and do fire for the same product in the same request
(the demo path deliberately does this: `PRD-008` starts below its reorder threshold *and*
crosses the demand-spike ratio in the same order). Idempotency is keyed on
`(productId, triggerReason, suggestionType, status=PENDING)`
(`existsByProductIdAndTriggerReasonAndStatus` in both suggestion repositories), so
`INVENTORY_LOW` and `DEMAND_SPIKE` each get their own suggestion pair rather than being
deduplicated against each other - a merchandiser sees *why* each suggestion exists,
which the UI surfaces as a badge.

**Tradeoffs.** `AFTER_COMMIT` means a request that touches stock but rolls back (e.g. a
future validation failure between the stock write and commit) correctly never fires the
loop - but it also means there's a small window between "HTTP response sent" and
"suggestion visible" that's slightly larger than a naive synchronous call, which is
exactly the async tradeoff T-4 asks for. The category-peer-average query
(`ProductRepository.averagePeerDemandVelocity`) runs once per trigger evaluation and
again inside the strategy - acceptable at hackathon scale, a candidate for caching if
category sizes grow.

---

## 6. Extensibility and deliberate exclusions

**Context.** The brief asks the ADR to point at an actual sprint-2 seam in the code, and
to name what was deferred as a priority decision rather than a time excuse.

**Where sprint 2 plugs in (concrete, not aspirational):**
- `Product.costPrice` / `Product.supplierId` (nullable columns, already in the schema and
  the `CreateProductRequest`/`ProductResponse` DTOs) are exactly the sprint-2
  `marginFloor`/supplier-catalog fields the roadmap describes - adding a margin-floor
  check is a one-line addition to `RuleBasedPricingStrategy.recommend()` and a new
  bounds check in `AiPricingStrategy.validateBounds()`, not a schema migration.
- A `CompetitorAwareStrategy implements PricingStrategy` needs nothing but a
  `@Component("competitorAwareStrategy")` and one more branch in
  `CommerceStrategyRegistry`'s constructor + `StrategyMode` enum value - it plugs into
  the exact same contract `RuleBasedPricingStrategy` and `AiPricingStrategy` already
  satisfy, and both the HTTP and async callers pick it up automatically.
- A price-change cooldown (sprint 2: "no second suggestion within N hours") is a single
  additional `existsByProductIdAndCreatedAtAfter(...)`-style check alongside the existing
  idempotency check in `SuggestionService.generatePricingSuggestion`.

**Deliberately excluded from this sprint (priority decisions, not oversights):**
- **Product catalog editing/deletion.** Only create + read are implemented; the brief
  scopes this sprint to the signal → recommendation → approval loop, and PATCH/DELETE on
  products would add state-machine edge cases (e.g. deleting a product with pending
  suggestions) without adding evaluation signal.
- **Persistent/relational database.** H2 in-memory was chosen over Postgres specifically
  so `mvn spring-boot:run` has zero external setup and the README's "< 5 minutes"
  promise is actually true; the JPA layer has no H2-specific code, so swapping the
  `spring.datasource.*` properties for Postgres is the entire migration.
- **Auto-apply of high-confidence suggestions.** Explicitly a sprint-3 item in the brief;
  implementing it now would violate the human-checkpoint guarantee this sprint is
  graded on ("prices don't change without accept").
- **True per-token provider streaming for Gemini** (the SSE bonus falls back to one
  chunk for Gemini, real token streaming only for the OpenAI-compatible providers) - a
  deliberate scope cut on an already-optional +5 bonus rather than on core scope.

---

## 7. Frontend framework: React 18 vs. Angular 17

**Context.** The tech spec offers either React 18 (Vite) or Angular 17 and asks for the
choice to be documented.

**Options.** React 18 + Vite. Angular 17 (standalone components).

**Decision.** React + Vite. The console is a small, mostly-flat component tree
(a product table, a suggestion list, a couple of forms) with one real piece of
non-trivial client state (poll-driven product/suggestion lists plus an in-flight SSE
stream) - a good fit for React's local `useState`/`useEffect` without needing Angular's
DI/module system or RxJS for anything this app actually does. Vite's dev server starts
and hot-reloads fast enough to stay out of the way during the sprint's tight time budget.

**Tradeoffs.** Angular's stricter structure (services, DI, RxJS streams) scales better
to a larger app - if sprint 2/3 add the catalog board, price-history charts, and
multi-view navigation the roadmap describes, an Angular rewrite might pay for itself in
maintainability. For this sprint's scope, that structure would have been overhead
without a matching benefit.
