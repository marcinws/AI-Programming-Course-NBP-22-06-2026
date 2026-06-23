# Handy.computer - transcription prompts

https://handy.computer/

Settings > Advanced > Experimental features > Post-processing Enabled

---

## Translate to English

```
Translate the transcript provided below from Polish to English.

Rules:
1. Translate faithfully with proper English grammar, punctuation, and natural interpretation, but keep the meaning strictly the same.
2. Preserve the exact meaning and (as much as reasonably possible) the word order. Do not paraphrase or add/remove information.
3. Keep names, brands, product names, and technical terms exactly as written (do not translate them).
4. If something is unclear in Polish, choose the most likely interpretation, but do not invent details.
5. Use safe plain punctuation for dictation: straight apostrophe ', straight quote ", hyphen -, and three dots ... . Never use curly quotes, smart apostrophes, typographic dashes, bullets, emoji, tabs, control characters, or line breaks.
6. Return one single line of text unless the transcript explicitly asks for multiple lines.
7. Return ONLY the English translation (no notes, no explanations, no extra text).

Names/terms that may appear and must be preserved exactly (or be corrected if misspelled):
Łukasz, Matuszewski, JSystems, Edukey, ChatGPT, OpenClaw, Encore, PayloadCMS, Claude Code, Claude Cowork, Codex, Tailwind, TailwindCSS, Playwright, OpenAI, AGENTS.md, React.js, Qwen, LoRA

Polish transcript to translate:
${output}
```

---

## Improve Transcriptions

```
Clean the transcript provided below:
1. Fix spelling, capitalization, and punctuation errors
2. Convert number words to digits (twenty-five: 25, ten percent: 10%, five dollars: $5)
3. Replace spoken punctuation with symbols (period: ., comma: ,, question mark: ?)
4. Use safe plain punctuation for dictation: straight apostrophe ', straight quote ", hyphen -, and three dots ... . Never use curly quotes, smart apostrophes, typographic dashes, bullets, emoji, tabs, control characters, or line breaks
5. Return one single line of text unless the transcript explicitly asks for multiple lines
6. Remove filler words (um, uh, eee, like as filler)
7. Do NOT make translations! Keep the original language of transcription (if it was Polish, keep it in Polish!)
8. Correct obvious grammar errors, make it more clear, correct, and nice to read
9. Correct probable transcription errors based on the context of the sentence to make it more clear. If something feels odd or is not clear assume transcription error and try to correct it based on context
10. When cleaning a transcript, prioritize understanding the intended meaning based on the context of the sentence. Correct probable transcription errors, misspellings, or unclear references by using contextual knowledge. For example, if the context suggests technical terms (like IDE, themes, or specific product names), infer the most likely correct term rather than sticking to a literal transcription if it makes no sense. Use your general knowledge regarding software development tools and popular terms

Names and words that may apear in the transcript and be misspelled, so you should correct them if you see something similar:
Łukasz, Matuszewski, JSystems, Edukey, ChatGPT, OpenClaw, Encore, Coolify, PayloadCMS, Claude Code, Claude Cowork, Codex, Tailwind, TailwindCSS, Playwright, OpenAI, AGENTS.md, React.js, Qwen, LoRA, theme (often misspeled as "team" - check the context of sentence)

Preserve exact meaning of the sentences and word order. Do not paraphrase or reorder content. But correct errors and probable mistakes in transcription.

Return only the cleaned transcript.

Transcript:
${output}
```
