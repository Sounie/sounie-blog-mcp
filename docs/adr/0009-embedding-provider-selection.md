# 9. Embedding-provider selection

Date: 2026-10-08

## Status
Proposed; provider selection and the identity of the local serving alias were confirmed by the owner
on 2026-10-08. The remaining model-approval checkpoint is to pin the served EmbeddingGemma 2
revision and matching tokenizer assets, then approve its context and passage-budget profile before
implementation.

## Context
Search has two consumer-owned ports: `PassageEmbedder` in `search.domain.embedding` supplies the
model identity and passage vectors used by indexing, while `QueryEmbedder` in `search.application`
supplies query vectors. ADR 0006 split these ports by consumer. The selected provider must be the same
for both operations because vectors from different model spaces cannot be compared.
`IndexRecipe` incorporates the passage-model identity, and reconciliation re-embeds entries when the
recipe changes. The owner confirmed that the local OpenAI-compatible alias
`text-embedding-embeddinggemma-2` serves Google's EmbeddingGemma 2; the alias does not identify a
revision, so model and tokenizer revisions still need to be pinned together.

Provider-specific preparation is not interchangeable. Models can expect different query
instructions, document templates, tokenizers and context limits. The existing ONNX BGE path has a
known BGE query instruction and tokenizer-derived budget. The EmbeddingGemma 2 profile uses its own
query and passage preparation, matching tokenizer assets from the pinned model revision, and
explicitly configured context and token-budget values. An OpenAI-compatible API does not identify
task prefixes, tokenizer assets or a reliable context limit; these must be configured from the
confirmed model and pinned revision, not inferred from the API.

## Decision
1. Select the provider in the `app` composition root; do not merge or relocate the consumer-owned
   ports and do not introduce a new domain aggregate.
2. Parse `BLOG_MCP_EMBEDDING_PROVIDER` once at startup through the proposed
   `EmbeddingProviderSelection` composition type:
   - absent: select ONNX BGE (`OnnxEmbedder`);
   - `onnx`: explicitly select ONNX BGE;
   - `gemma2`: select the configured local OpenAI-compatible embedding adapter;
   - blank or any other value: report invalid configuration and fail startup, with no implicit
     fallback.
   Trim surrounding whitespace before matching.
3. Create exactly one selected adapter instance and supply that same instance to both
   `PassageEmbedder` and `QueryEmbedder`. Keep `Adapters` as the application composition boundary.
4. Select provider-specific query-preparation and passage-preparation strategies with the provider.
   The ports carry validated raw query text and canonical passage content; the selected adapter
   applies its own strategy. Keep strategy behavior provider-neutral at the domain boundary: no
   provider-specific prefixes, tokenizer classes, model templates or context claims enter the
   domain.
5. Select a provider-neutral `PassageTokenBudget` and matching `TokenCounter` with the provider.
   Chunking uses that budget for body, title, overlap and fully prepared passage limits. The adapter
   or explicit configuration supplies the actual tokenizer and supported context facts. For the
   local provider, tokenizer assets must match the exact pinned model revision, and the served model
   must use that same revision. Set profile limits from that revision's model/tokenizer configuration
   and validation; do not reuse ONNX BGE numbers or assume context capacity from the alias. The
   remaining model-approval checkpoint is approval of this pinned revision and profile.
6. Include configured model/revision identity, passage-preparation version and passage-budget
   parameters in `IndexRecipe`. Query preparation is excluded because it does not change stored
   passages. A provider or passage-profile change therefore makes old entries stale and the next
   reconcile re-embeds them before search compares vectors.
7. Emit no domain event for provider selection. It is process-start composition, not a business
   state transition.

### Provider-specific facts and limits

- ONNX BGE retains its known query preparation and BGE-matched tokenizer/budget. The current 400
  body-token, 100 overlap-token, 64 title-token and 465 total passage-token limits are BGE profile
  values, not defaults that apply to another model.
- The owner confirmed that the local alias `text-embedding-embeddinggemma-2` serves Google
  EmbeddingGemma 2. Its provider profile retains the model-specific query prefix
  `task: search result | query: ` and passage template
  `title: {title} | text: {content}`, using `title: none` when there is no title. These strategies
  are provider-specific, not shared domain behavior. Pin the exact served model revision and use
  tokenizer assets from that same revision. Determine supported context and budget values from the
  pinned assets and validate the fully prepared passage against them; no numeric Gemma2 budget is
  assumed here
  ([model card](https://huggingface.co/google/embeddinggemma-2),
  [tokenizer config](https://huggingface.co/google/embeddinggemma-2/raw/main/tokenizer_config.json),
  [model config](https://huggingface.co/google/embeddinggemma-2/raw/main/config.json)).
- An OpenAI-compatible endpoint's request shape does not establish the task prefix, tokenizer or
  reliable context limit. These must be established from the pinned model and tokenizer assets, not
  guessed from the endpoint or alias
  ([OpenAI embeddings API](https://developers.openai.com/api/reference/resources/embeddings/methods/create),
  [LM Studio embeddings documentation](https://lmstudio.ai/docs/developer/openai-compat/embeddings)).

### Rejected alternatives
- Merge the two ports into a provider interface: this reverses ADR 0006's consumer-owned port
  decision and makes both consumers depend on an abstraction neither owns.
- Construct passage and query adapters independently: it permits the two ports to use different
  models and silently makes similarity results invalid.
- Share BGE query instructions, tokenizer assumptions or fixed token budgets with another provider:
  model-specific input semantics and context limits differ.
- Treat blank or unknown input as the default: this hides configuration mistakes and could cause the
  application to index and search with an unintended model.
- Infer the pinned model revision, tokenizer or context limit from an OpenAI-compatible alias:
  compatibility of the HTTP contract is not evidence of those model properties. The owner-confirmed
  model identity does not establish the revision or its tokenizer assets.

## Consequences
- Production composition is configuration-driven, with ONNX BGE as the default. The selector and
  provider factory belong to `app`; the two consumer-owned ports remain separate.
- A provider profile consists of one shared adapter instance plus its query strategy, passage
  strategy, tokenizer and explicit token budget. Provider internals stay in adapters/configuration;
  search domain code consumes only the strategy and budget behavior it needs.
- Changing model/revision identity or passage preparation/budget changes recipe identity. Existing
  entries are treated as stale and rebuilt through the existing reconcile path.
- No dependency or bounded context is added, and no domain event is emitted.
- The local alias is owner-confirmed as Google EmbeddingGemma 2. Before implementation, pin the
  exact served model revision, provision tokenizer assets from that same revision, and approve
  context and passage-budget values from those assets and validation. The profile explicitly
  configures body, title, overlap and fully prepared passage limits; it does not inherit ONNX BGE
  limits or assume an unverified numeric context.

## Acceptance criteria
- AC-SRCH-40–45 cover startup selection, failure on invalid values, shared provider instance and
  recipe/vector compatibility.
- AC-SRCH-46 verifies query and passage preparation independently for each configured provider.
- AC-SRCH-47 verifies tokenizer assets match the pinned model revision and that the explicit,
  validated provider budget stays within the supported context without inheriting BGE assumptions.

## Sources
- Google EmbeddingGemma 2 model card: <https://huggingface.co/google/embeddinggemma-2>
- Google EmbeddingGemma 2 tokenizer configuration:
  <https://huggingface.co/google/embeddinggemma-2/raw/main/tokenizer_config.json>
- Google EmbeddingGemma 2 model configuration:
  <https://huggingface.co/google/embeddinggemma-2/raw/main/config.json>
- OpenAI embeddings request contract:
  <https://developers.openai.com/api/reference/resources/embeddings/methods/create>
- LM Studio OpenAI-compatible embeddings documentation:
  <https://lmstudio.ai/docs/developer/openai-compat/embeddings>
