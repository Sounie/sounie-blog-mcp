# Gemma tokenizer redistribution terms

Research checked 2026-10-08. Scope: official Google and official Hugging Face sources only; the Hugging Face repository belongs to the `google` publisher. This is source-based research, not legal advice.

## Scope and classification

### Takeaway

Google currently classifies **EmbeddingGemma 2** as an Apache 2.0 model: Google’s EmbeddingGemma 2 documentation says it is “licensed under the Apache 2.0 License,” and Google’s launch post calls it released under a “commercially permissive Apache 2.0 license.” The separate Gemma Terms of Use page says its terms apply to models in its Appendix; the Appendix lists “EmbeddingGemma” but does not name “EmbeddingGemma 2.” This distinction strongly supports treating the version-2 release as Apache-licensed rather than applying the Gemma Terms’ extra redistribution conditions, but the older family-name entry and how terms apply to an individual tokenizer artifact leave some interpretive uncertainty.

### Cited Findings

- Google’s model documentation describes EmbeddingGemma 2 as an open-source 740M parameter multimodal model and states: “EmbeddingGemma 2 is provided with open weights and licensed under the Apache 2.0 License, allowing you to fine-tune and deploy it in your own projects and applications.” [Google, EmbeddingGemma documentation](https://ai.google.dev/gemma/docs/embeddinggemma)
- Google’s official launch announcement states: “Built on the Gemma 4 architecture and released under a commercially permissive Apache 2.0 license.” [Google, EmbeddingGemma 2 launch post](https://blog.google/innovation-and-ai/technology/developers-tools/embeddinggemma-2/)
- At the pinned Hugging Face revision, the `google/embeddinggemma-2` model card metadata says `license: apache-2.0`; its page says “License: Apache 2.0” and links to Google’s Gemma 4 license page. [Official Google model card at revision `914f7f89142e33e77833254d9c9b90c3cef7303b`](https://huggingface.co/google/embeddinggemma-2/blob/914f7f89142e33e77833254d9c9b90c3cef7303b/README.md)
- Google’s linked Gemma 4 license URL, [`https://ai.google.dev/gemma/docs/gemma_4_license`](https://ai.google.dev/gemma/docs/gemma_4_license), redirects to Google’s [Apache License 2.0 page](https://ai.google.dev/gemma/apache_2), which reproduces the Apache 2.0 license text. The Google docs page itself identifies EmbeddingGemma 2 as licensed under Apache 2.0.
- The current [Gemma Terms of Use](https://ai.google.dev/gemma/terms) states at its outset: “The terms below apply to Gemma models listed in the Appendix at bottom of this page. For Gemma 4 terms, see the Gemma 4 license.” Section 1.1(c) defines “Gemma” as “the set of machine learning language models, trained model weights and parameters identified in the Appendix.” The Appendix lists “EmbeddingGemma” but not the distinct name “EmbeddingGemma 2.”
- The Gemma Terms page says it was last modified April 1, 2026. Its listed Appendix model names include Gemma 1/1.1/2/3/3n, FunctionGemma, EmbeddingGemma, PaliGemma variants, ShieldGemma variants, CodeGemma variants, DataGemma variants, RecurrentGemma, Gemma Scope, Gemma-APS, T5Gemma, and VaultGemma. [Gemma Terms Appendix](https://ai.google.dev/gemma/terms)
- The same pinned model card instructs: “Deployments must adhere to the Gemma Prohibited Use Policy.” [Pinned model card](https://huggingface.co/google/embeddinggemma-2/blob/914f7f89142e33e77833254d9c9b90c3cef7303b/README.md); [Google Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy)

### Inferences

- The most direct version-specific Google statements identify **EmbeddingGemma 2**, not merely the broad Gemma family, and explicitly say Apache 2.0. That is strong evidence of the intended license classification for its published artifacts.
- The older Terms Appendix entry “EmbeddingGemma” could refer to the earlier model/version or could be read more broadly as a family label. The Appendix does not define version naming or expressly exclude EmbeddingGemma 2. Avoid claiming that the Terms unambiguously exclude every artifact bearing a Gemma-family name.
- The model card’s prohibited-use statement is relevant to deployment, but it does not by itself establish that every restriction and redistribution condition in the separate Gemma Terms is incorporated into the Apache license. The applicable legal effect is not resolved by these sources alone.

### Gaps

- Google’s sources do not state in one explicit sentence that “EmbeddingGemma 2 is not governed by the Gemma Terms of Use.” That conclusion is inferred from the separately stated Apache license, the Gemma 4 license cross-reference, and the Terms Appendix’s version-specific names.
- Neither Google’s general license statement nor the model card expressly analyzes the legal status of `tokenizer.json` as a standalone file, separate from the EmbeddingGemma 2 model distribution.

## Redistribution duties and notices

### Takeaway

For the Apache 2.0 classification Google assigns to EmbeddingGemma 2, the Google-hosted Apache text requires (among other conditions) giving recipients a copy of the license, marking modified files with prominent change notices, retaining applicable notices in distributed source derivatives, and carrying forward attribution notices if the upstream Work includes a `NOTICE` file. In contrast, the Gemma Terms would require a prescribed `Notice` file and several additional downstream restrictions if they governed the distributed material. The strongest reading of Google’s version-specific license statements is that the Apache conditions—not the separate Gemma Terms’ special `Notice` text—are the applicable redistribution regime for EmbeddingGemma 2, subject to the scope uncertainty above.

### Cited Findings

- The Gemma Terms define “Distribution” broadly as transmission, publication, or sharing of Gemma or Model Derivatives with a third party, including making it available as a hosted service. Section 3.1 allows reproducing or distributing copies only if all its conditions are met. [Google Gemma Terms, §§1.1(b), 3.1](https://ai.google.dev/gemma/terms)
- Section 3.1 of the Gemma Terms requires distributors to: (1) include the §3.2 use restrictions as enforceable terms and notify subsequent users of those restrictions; (2) provide each third-party recipient a copy of the Gemma Terms; (3) put prominent notices on modified files; and (4) accompany non-hosted distributions with a `Notice` text file containing exactly: “Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms”. [Google Gemma Terms, §3.1](https://ai.google.dev/gemma/terms)
- The Gemma Terms’ §3.2 incorporates the [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy) and also prohibits use in violation of applicable law. [Google Gemma Terms, §3.2](https://ai.google.dev/gemma/terms)
- Google’s Apache 2.0 page includes §4 Redistribution. The relevant exact clauses are:
  - §4(a): “You must give any other recipients of the Work or Derivative Works a copy of this License”;
  - §4(b): “You must cause any modified files to carry prominent notices stating that You changed the files”;
  - §4(c): “You must retain, in the Source form of any Derivative Works that You distribute, all copyright, patent, trademark, and attribution notices from the Source form of the Work” (subject to its stated exception);
  - §4(d): “If the Work includes a ‘NOTICE’ text file as part of its distribution,” readable attribution notices from it must be included in one of the listed locations.
  [Google-hosted Apache License 2.0](https://ai.google.dev/gemma/apache_2)
- Section 4(d) makes Apache’s NOTICE carry-through conditional: it applies if the Work being redistributed includes a `NOTICE` file. Apache §4 does not independently command a new attribution statement or NOTICE file where the source Work has none. The same section says any NOTICE contents are “for informational purposes only and do not modify the License.” [Google-hosted Apache License 2.0, §4(d)](https://ai.google.dev/gemma/apache_2)
- The exact pinned Hugging Face repository API listing contains `tokenizer.json` and `tokenizer.model`, but no file named `LICENSE` or `NOTICE`. It identifies `tokenizer.json` as 32,170,510 bytes with SHA-256 `4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4`. [Official Hugging Face repository file listing at pinned revision](https://huggingface.co/api/models/google/embeddinggemma-2/tree/914f7f89142e33e77833254d9c9b90c3cef7303b)
- The pinned repository metadata gives the model-level Apache identifier (`license: apache-2.0`), and its README links to Google’s Apache 2.0 page. [Pinned model card](https://huggingface.co/google/embeddinggemma-2/blob/914f7f89142e33e77833254d9c9b90c3cef7303b/README.md)

### Inferences

- On the pinned repository snapshot, there is no upstream `NOTICE` file visible in the repository listing, so Apache §4(d)’s conditional carry-through trigger is not apparent from that snapshot. This is not proof that no separate upstream distribution or incorporated component has a NOTICE file.
- Under Apache 2.0, redistributing the tokenizer file as part of the licensed Work should be accompanied by a copy of the Apache 2.0 license. Preserve any applicable attribution/copyright notices present in source material, and prominently mark a modified tokenizer file as changed. The repo listing does not show an upstream standalone license file to copy, but Google’s page reproduces the license text.
- The specific Gemma Terms `Notice` sentence, downstream notice of use restrictions, and copy-of-Gemma-Terms duties are not Apache 2.0 requirements. They appear in a separate set of terms whose model scope needs to be established before applying them to EmbeddingGemma 2.
- “Attribution” should be distinguished from “license notice”: Apache requires preservation of source attribution notices if present and a license copy; it does not appear to impose a general obligation to credit Google in marketing or a product UI. This is a reading of the quoted conditions, not a legal opinion on whether other attribution duties attach to this data.

### Gaps

- The pinned Hugging Face tree listing shows no `NOTICE` or `LICENSE`, but it cannot establish whether upstream Google distributions outside this repository include additional notices.
- No reviewed official source analyzes whether shipping only a tokenizer file rather than the complete model counts as distribution of the licensed Apache “Work” for every licensing purpose.

## Pinned tokenizer artifact and practical interpretation

### Takeaway

The requested revision is an official Hugging Face snapshot by the `google` account, and `tokenizer.json` is present at that immutable commit. The repository-level metadata and Google documentation designate EmbeddingGemma 2 as Apache 2.0. On the available official record, the practical low-risk course for redistributing the tokenizer is to include the Apache 2.0 license text alongside it, preserve any notices that are actually present, and identify local changes; do not automatically append the special Gemma Terms Notice as though the general Gemma Terms necessarily govern this v2 artifact.

### Cited Findings

- Hugging Face’s official model API confirms repository ID `google/embeddinggemma-2`, author `google`, pinned SHA `914f7f89142e33e77833254d9c9b90c3cef7303b`, and `cardData.license: apache-2.0`. The API also lists `tokenizer.json` among files at that exact revision. [Model metadata at pinned revision](https://huggingface.co/api/models/google/embeddinggemma-2/revision/914f7f89142e33e77833254d9c9b90c3cef7303b)
- The exact tokenizer file is available at [Hugging Face `tokenizer.json` for revision `914f7f89142e33e77833254d9c9b90c3cef7303b`](https://huggingface.co/google/embeddinggemma-2/blob/914f7f89142e33e77833254d9c9b90c3cef7303b/tokenizer.json). Hugging Face reports the file’s SHA-256 as `4d777ef5bdc1aa36227abdfb77c3e49e7b9c892d16e1b6bda41c393504828be4`.
- The model card’s repository-level metadata and license link are part of the pinned README, but no file-specific license statement appears in the tokenizer file listing itself. [Pinned model card](https://huggingface.co/google/embeddinggemma-2/blob/914f7f89142e33e77833254d9c9b90c3cef7303b/README.md); [pinned repository file listing](https://huggingface.co/api/models/google/embeddinggemma-2/tree/914f7f89142e33e77833254d9c9b90c3cef7303b)
- Google’s Apache page §4(a) requires providing recipients a copy of the license; §4(b) covers modified files; §4(c) preserves applicable source notices; §4(d) applies if the Work includes a NOTICE file. [Google-hosted Apache License 2.0](https://ai.google.dev/gemma/apache_2)

### Inferences

- Because the repository license metadata is at model level rather than on `tokenizer.json` specifically, the conclusion that the individual tokenizer file is covered by the Apache grant is a reasonable reading of Google’s stated model license, but not an express file-by-file grant in the sources reviewed.
- Include a license copy when redistributing; retain any upstream copyright/attribution notices discovered in the actual file or associated materials; mark local modifications conspicuously. An optional source/version/hash record improves traceability but is not identified here as an independent legal requirement.
- The special Gemma Terms “Notice” file says Gemma is subject to the Gemma Terms. Adding it for an Apache-licensed EmbeddingGemma 2 redistribution could inaccurately communicate that the older additional terms apply. Only add it if authoritative terms for the exact distributed artifact require it or legal counsel advises doing so.

### Gaps

- The legal question whether the tokenizer JSON alone, as distinct from a model checkpoint or other repository files, is an independently licensed “Work” is not answered expressly by Google or the pinned model card.
- The sources establish Google’s product-level license classification and file presence, but do not resolve how a court or counsel would reconcile the older Gemma Terms Appendix entry “EmbeddingGemma” with EmbeddingGemma 2’s explicit Apache 2.0 classification.
