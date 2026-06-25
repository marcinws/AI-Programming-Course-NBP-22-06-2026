# AI in Programming - Course Notes

Notes from course for JSystem — AI dla programistów: od pomysłu do MVP

## PYTANIA NA START:
- Jak do tej pory używasz AI w pracy? Jakich narzędzi i w jaki sposób? (poziom autonomii)
- Jakie są Twoje doświadczenia? Pomaga? Przyspiesza? Spowalnia?
  - Czy używasz AGENTS.md, Rules?
- Największe problemy jakie masz z AI
- Co sądzisz o AI i przyszłości programowania?

---

## Trends 2025–2026 and what to expect next

- Knowledge base on latest trends: [NotebookLM](https://notebooklm.google.com/notebook/5a69e473-2453-4ee7-bcb6-866af29ba553)
- **Vibe Codding vs Vibe Engineering**
  - Linus Torvalds, DHH, and Node.js creator use automated code generation, e.g.:
    - "[the era of humans writing code is over.](https://x.com/kimmonismus/status/2013524952553492933)" (Node.js creator on AI generating code)
  - Microsoft named their new Power Apps platform Vibe - we prompt to build apps: [Vibe - Power Apps](https://vibe.powerapps.com/) (only in US right now) | Docs: [Omówienie nowego środowiska usługi Power Apps - Power Apps | Microsoft Learn](https://learn.microsoft.com/pl-pl/power-apps/vibe/overview)
  - Vibe Coding definition: [X.com](https://x.com/karpathy/status/1886192184808149383?lang=en)
- **Context rot & Context engineering**
- **Async Codding Agents in Cloud & on Mobile**
- CLI tools, Automation (Auto Review, Auto Mode), YOLO in container/cloud or local device (e.g. mac mini, Nvidia DGX Spark, AMD Strix Halo)
- Huge jump in quality of **Opus 4.5+**, GPT 5.4 and Gemini 3
- **Open Source LLMs** are very close to top models:
  - GLM-5.2, Minimax M3, Kimi K2.6, DeepSeek v4 Pro - best OSS models are all from China
  - Best OSS for consumer hardware: Google [Gemma 4](https://ollama.com/library/gemma4), [Qwen 3.6](https://ollama.com/library/qwen3.6)
  - could be used also with Claude Code: [Ollama](https://docs.ollama.com/integrations/claude-code), [vLLM](https://docs.vllm.ai/en/latest/serving/integrations/claude_code/) (good concurrency for scaling), [LocalAI](https://localai.io/integrations/index.html#claude-code)
- **Screenshots** as a better way to provide context for vision models (less tokens thanks to compression, more information thanks to formatting, colors, images, etc.)
  - [DeepSeek OCR research paper on context compression](https://arxiv.org/abs/2510.18234)
  - [Andrej Karpathy on X.com](https://x.com/karpathy/status/1980397031542989305?lang=en) "whether pixels are better inputs to LLMs than text. Maybe it makes more sense that all inputs to LLMs should only ever be images"
- **AI disappointment**, broken promises, productivity drop, less fun and joy, frustration:
  - [AI Coding Sucks - YouTube](https://www.youtube.com/watch?v=0ZUkQF6boNg)
  - undeterministic nature of LLMs,
  - hallucinations,
  - crazy pace of changes in AI tools,
  - skill degradation, vanishing muscle memory
    - AI detox

### Stages of AI-Assisted Programming (0 → 5):

| Stage | Name | Description | Who manages |
|-------|------|-------------|-------------|
| **0** | Manual | No AI — pure hand-written code | Developer writes everything |
| **1** | Autocomplete | AI tab completion (Copilot-style) | Developer drives, AI suggests |
| **2** | Chat | AI chat assistant — ask, copy-paste | Developer + AI pair |
| **3** | Agent | Full file edits, tools, AGENTS.md, MCP | Developer oversees one agent |
| **4** | Multi-Agent | Parallel agents, async cloud, CI/CD | Developer orchestrates a fleet |
| **5** | **Dark Factory** | Fully autonomous; humans manage specs, not code | Human defines *what*, agents decide *how* and *when* |

> **Dark Factory** (term borrowed from manufacturing — a factory floor that runs lights-out because no humans need to be present): applied to software, agents ship features 24/7, humans set direction, review specs, and handle escalations only.

### 3 New IT Roles in the Agentic Era:

1. **Orchestrators** — Manage fleets of agents, define tasks, review and steer output. Write less code and more context: AGENTS.md, PRDs, rules. The "new tech lead".
2. **System/Infrastructure Builders** — Build the scaffolding: CI/CD pipelines, MCP servers, agent sandboxes, observability, cost controls. Deep technical role that requires architecture thinking.
3. **Domain Experts as Programmers** — Subject-matter experts (lawyers, analysts, operations staff, doctors) who use natural language in Claude Code, Cursor or similar tools to produce working software. **They don't know they've become programmers** — they describe what they want in English and get working software. The most disruptive shift: an entirely new supply of "programmers" who never wrote a line of code.

### Research data on AI Coding:

- Knowledge base with research data: [NotebookLM](https://notebooklm.google.com/notebook/ae14d724-0256-482a-85b8-95fca9ba1c11)
- **Stanford** research on ROI of AI code (from 07.2025):
  - YT talk 12.2025 [Can you prove AI ROI in Software Eng? (Stanford 120k Devs Study) – Yegor Denisov-Blanch, Stanford - YouTube](https://www.youtube.com/watch?v=JvosMkuNxF8)
  - Insights on [X.com](https://x.com/dexhorthy/status/1992266735970652447)
  - Slides: [Yegor Denisov-Blanch - The AI Conference](https://aiconference.com/speakers/yegor-denisov-blanch/)
  - Original talk from 07.2025 on YT: [Does AI Actually Boost Developer Productivity? (100k Devs Study) - Yegor Denisov-Blanch, Stanford - YouTube](https://www.youtube.com/watch?v=tbDDYKRFjhk)
- US, 12.2025: [Beyond Productivity: Evaluating the Hidden Costs of Generative AI in Software Development by Edward Anderson, Geoffrey Parker, Burcu Tan :: SSRN](https://papers.ssrn.com/sol3/papers.cfm?abstract_id=5842302)
- METR, US, 07.2025: [Measuring the Impact of Early-2025 AI on Experienced Open-Source Developer Productivity - METR](https://metr.org/blog/2025-07-10-early-2025-ai-experienced-os-dev-study/)
- India 09.2025: [\[2509.19708\] Intuition to Evidence: Measuring AI's True Impact on Developer Productivity](https://arxiv.org/abs/2509.19708)
- Meta: [Measuring the Impact of AI on Developer Productivity at Meta - YouTube](https://www.youtube.com/watch?v=1OzxYK2-qsI)
- **QUESTION:**
  - **Do you measure velocity / ROI / time estimations / Quality?** e.g.
  - Velocity in Jira with story points,
  - number of comments on CR,
  - number of issues/bugs/tickets,
  - it helps to measure AI impact on their work
- **The Productivity J-Curve** - productivity drops for 1-2 months when you learn

### Case Studies:

- **Microsoft** plans to rewrite all code from **C/C++ to Rust** using AI and algorithms
  - Linkedin post from [Galen Hunt - Principal Software Engineer (CoreAI)](https://www.linkedin.com/posts/galenh_principal-software-engineer-coreai-microsoft-activity-7407863239289729024-WTzf)
   My goal is to eliminate every line of C and C++ from Microsoft by 2030. Our strategy is to combine AI *and* Algorithms to rewrite Microsoft's largest codebases. Our North Star is "1 engineer, 1 month, 1 million lines of code".   To accomplish this previously unimaginable task, we've built a powerful code processing infrastructure. Our algorithmic infrastructure creates a scalable graph over source code at scale. Our AI processing infrastructure then enables us to apply AI agents, guided by algorithms, to make code modifications at scale. The core of this infrastructure is already operating at scale on problems such as code understanding.

- **OpenClaw** — fastest-growing GitHub project in history (launched late Nov 2025)
  - **220k+ Stars, 790 contributors, 4,300+ open PRs** — [github.com/openclaw/openclaw](https://github.com/openclaw/openclaw)
  - Created by **Peter Steinberger** ([@steipete](https://github.com/steipete)), who joined **OpenAI in Feb 2026** to help build autonomous agents at scale. OpenClaw transitions to an OpenAI-backed open-source foundation.
  - **50 parallel Codex agents for PR triage:** Peter spins up 50 Codex instances simultaneously, each outputs a JSON report (vision match, intent, risk signals). All 50 reports ingested in one session to query/deduplicate/auto-close/merge — no vector DB needed: [X post](https://x.com/steipete/status/2025591780595429385)
  - **1,000–1,500 commits/day:** since switching to Codex (Oct 2025), regularly hits this range across 2 computers with multiple parallel agents. Oct 26, 2025: 1,374 commits in one day (for the first time >1000). [X post](https://x.com/steipete/status/2024524946114814414) | [GitHub profile](https://github.com/steipete)
  - Peter's articles on his workflow:
    - My [NotebookLM knowledge base](https://notebooklm.google.com/notebook/3e4c8c3c-d617-4060-998c-c684d124d762) with his articles and videos about how he works
    - [Essential Reading Aug 2025](https://steipete.me/posts/2025/essential-reading-august-2025)
    - [Optimal AI Dev Workflow (Aug 2025)](https://steipete.me/posts/2025/optimal-ai-development-workflow)
    - [Live Coding Session (Sep 2025)](https://steipete.me/posts/2025/live-coding-session-building-arena)
    - [Just Talk To It (Oct 2025)](https://steipete.me/posts/just-talk-to-it) — shorter prompts work when models are smarter + voice input; good AGENTS.md handles all context
    - [Shipping at Inference Speed (Dec 2025)](https://steipete.me/posts/2025/shipping-at-inference-speed) — multi-project parallel workflow; no worktrees, no RAG, commit straight to main
  - 🦞 **YCombinator** (world's top startup accelerator) leadership dressed as "Claws" (red lobsters), talking OpenClaw and the AI Agent Economy: [YouTube](https://www.youtube.com/watch?v=Q8wVMdwhlh4) — signals this is a social phenomenon, not just a dev tool. First time thousands of *non-technical* people started using autonomous agents.
  - My 10-day experience report with exact token/cost/productivity numbers + notes on Opus 4.6 and GLM-4.7 (now GLM-5): [10 dni z OpenClaw — edukey.ai](https://edukey.ai/pl/blog/10-dni-z-openclaw-i-claude-opus-46-ai-rozwija-sie-szybciej-niz-twoj-zespol)

### AI real impact we already see in Dev/IT:
- TailwindCSS financial issues
  - people stopped visiting Docs = less sales of their products = how to maintain OSS Projects???
  - [The Tailwind drama - YouTube](https://www.youtube.com/watch?v=luhgjBrRulk)
  - PR comment: [feat: add llms.txt endpoint for LLM-optimized documentation by quantizor · Pull Request #2388 · tailwindlabs/tailwindcss.com · GitHub](https://github.com/tailwindlabs/tailwindcss.com/pull/2388#issuecomment-3717222957)
  - Adam Wathan on X: [morning walk talk](https://x.com/adamwathan/status/2008909129591443925)
- StackOverflow is dying [Prepare your goodbyes - YouTube - Primagen](https://www.youtube.com/watch?v=Gy0fp4Pab0g)
- All Publishers see huge decline in Traffic because of AI search tools - "The Great Decoupling" (people search more, but traffic goes down - 60% of Google searches without clicks!)
- Long Video Courses are dying - people stop to use Udemy and similar platforms
  - [Why I stopped making coding tutorials - YouTube](https://www.youtube.com/watch?v=WCGTQBCE3FA)

### People and accounts to follow in AI

#### 📺 YouTube

- [Theo T3](https://www.youtube.com/@t3dotgg) — from TS, Web App and startup perspective
- [Nate B Jones](https://www.youtube.com/@NateBJones) — Deep thoughts on AI architecture, insights, deep dives
- [Silicon Valley Girl](https://www.youtube.com/@SiliconValleyGirl) — from startup and woman perspective, interviews
- [Alex Ziskind](https://www.youtube.com/@AZisk) — from Hardware perspective, testing GPUs, Local AI Models, Benchmarks
- [AI Code King](https://www.youtube.com/@AICodeKing) — Open Source, Coding Tools, alternative perspective
- [Matthew Berman](https://www.youtube.com/@matthew_berman) — from business and investor perspective, general AI news
- [Matt Pocock](https://www.youtube.com/@mattpocockuk) — AI Coding tools tips, skills, techniques
- [Rob Walling](https://www.youtube.com/@RobWalling) — AI in Startups, SaaS, Solo Devs, Bootstrap
- [YCombinator](https://www.youtube.com/@ycombinator) — AI from Startups and Investors perspective
- [Claude](https://www.youtube.com/@claude) — official
- [OpenAI](https://www.youtube.com/@OpenAI) — official
- [Fireship](https://www.youtube.com/@Fireship) — general AI news
- [Wes Roth](https://www.youtube.com/@WesRoth) — general AI news
- [Maximilian Schwarzmüller](https://www.youtube.com/@maximilian-schwarzmueller) — from TS, Web Dev perspective
- [Traversy Media](https://www.youtube.com/@TraversyMedia) — Web Dev perspective, provocative and honest
- [Greg Isenberg](https://www.youtube.com/@GregIsenberg) — General AI news and tips

#### 🐦 X.com

- [Andrej Karpathy](https://x.com/karpathy) — ex-OpenAI, Data Science
- [Paul Rohan](https://x.com/rohanpaul_ai) — Data science, AI deep dive, news
- [Tibo](https://x.com/thsottiaux) — OpenAI, Codex team
- [Petter Steinberger](https://x.com/steipete) — OpenClaw founder
- [Pliny Deliberator](https://x.com/elder_plinius) — Jail Breaking in AI
- [Boris Cherny](https://x.com/bcherny) — Claude Code creator, Anthropic
- [Matt Pocock](https://x.com/mattpocockuk) — AI tips in programming
- [Pieter Levels](https://x.com/levelsio) — famous Solo Dev, Startup founder, AI & bootstrap
- [Wes Bos](https://x.com/wesbos) — Syntax, AI in Web Dev

#### 🇵🇱 Po polsku

- [Mateusz Chrobok](https://www.youtube.com/@MateuszChrobok) — często o AI i bezpieczeństwie, w ciekawy sposób
- [This is IT](https://www.youtube.com/@MK_ThisIsIT) — wywiady, często o AI
  - [Polacy w OpenAI z jednego liceum — Wychował geniuszy ChatGPT](https://www.youtube.com/watch?v=w20lk3OyLMI&t=2s)
  - [Andrzej Dragan vs Krzysztof Zaręba (OpenAI)](https://www.youtube.com/watch?v=Y4SjbbZ5qoA)
  - [Krzysztof Zaręba z OpenAI (wywiad)](https://www.youtube.com/watch?v=6QhGUQ5iTdk)
- [Ania Kubów](https://www.youtube.com/@aniakubow) — po angielsku, ale Polka z Google, Amazon i Microsoft — często wrzuca coś o AI
- [Przeprogramowani](https://www.youtube.com/@Przeprogramowani) — rzadko wrzucają (i nie zawsze się z nimi zgadzam), ale są newsy AI
- [Michał Sadowski (Brand24)](https://www.youtube.com/@Michal.Sadowski) — biznes, SaaS, AI, startupy, rzadko wrzuca | [X.com](https://x.com/sadek)
- [Tomasz Karwatka](https://x.com/tomik99) — często o AI i biznesie, eCommerce (X.com)
- [Maciej Dobrodziej](https://www.linkedin.com/in/maciejdobrodziej/) — AI w Wideo (bardzo dobre jakościowo) (LinkedIn)
  - [Smok Wawelski](https://www.linkedin.com/feed/update/urn:li:activity:7337045944347037696)
  - [Bitwa pod Grunwaldem](https://www.linkedin.com/feed/update/urn:li:activity:7335560375406329856)

---

## AI Fundamentals

- **Knowledge Cutoff** - AI doesn't know latest versions & features of Tools/Languages
  - Better use **Java 21 (2023)** than 25 (07.2025)
  - Add context, docs for latest features
  - Overfitting - LLM may still struggle, ignore context and follow old path
- **Hallucinations**
  - RAG / Context / Browser helps to solve this issue, but not 100%
  - [AA-Omniscience Index](https://artificialanalysis.ai/evaluations/omniscience) (Benchmark for Hallucination level)
  - [LogProbs parameter](https://developers.openai.com/cookbook/examples/using_logprobs/) (log probabilities of each output token)
- **Context Window**
  - Temporary "working memory" of LLM,
  - Attention (uwaga, skupienie),
  - Context Rot,
  - Lost in the Middle
- **Tokens**, Tokenizer, Embedding (osadzanie), Vector DB, etc.
- **Autoregression**
  - model predicts future values in a sequence by using a linear combination of its own past values
  - one mistake may lead to cascade / domino effect of mistakes (better to start again - branching in ChatGPT, Cursor can also revert with back icon next to our prompts in history)
- What LLM needs to support to use it as **AI Agent**:
  - **Structured Output** / Json schema:
    - [OpenAI Structured Outputs Guide (JavaScript)](https://developers.openai.com/api/docs/guides/structured-outputs/?lang=javascript)
    - [OpenAI Java SDK Examples (structured output at the end of the list)](https://github.com/openai/openai-java/tree/332e1a18b4a11469e528f0359c997ae2beecd04a/openai-java-example/src/main/java/com/openai/example)
    - [Spring AI – OpenAI Structured Outputs](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html#_structured_outputs)
  - **Function Calling** / Tool usage
- **RAG** - architecture / concept of augmenting context / input with real data
  - Retrieval-Augmented Generation (creator regrets this name ;)
  - Semantic search in vector space, Vector DB, Graph DB, Hybrid
  - Reranking
    - [Vercel AI SDK – Reranking](https://ai-sdk.dev/docs/ai-sdk-core/reranking)
  - Local Vector DBs:
    - [SQLite Vector Extension](https://www.sqlite.ai/sqlite-vector)
    - [Chroma – Open-source Embedding Database](https://github.com/chroma-core/chroma)
- **Cache** - cost optimization, caching calculated weights for same input tokens
- **Reasoning**, Reinforced Learning & R1:
  - [How was DeepSeek-R1 built; For dummies : r/LLMDevs](https://www.reddit.com/r/LLMDevs/comments/1ibhpqw/how_was_deepseekr1_built_for_dummies/)
  - It changed a lot in AI, enabled Vibe Coding, Autonomus Agents
  - Should we always use reasoning models??? :)
- Types of AI Assistance in programming:
  - **autocomplete** (starting from [Tabnine](https://www.tabnine.com/), GH Copilot, now Cursor Tab [based on Supermaven](https://supermaven.com/blog/cursor-announcement))
  - Chat / Research / Talk about your code
  - Inline Generation
  - Code Generation, planning, TODOs, Tools, MCPs, etc.
  - Agentic workflows, task automation (local), not only codding, e.g.
    - Goose
    - Claude Cowork: [Introducing Cowork | Claude](https://claude.com/blog/cowork-research-preview)
  - Agentic workflows in the cloud, CI/CD, Repo
- **Prompting**
  - Polish language in prompting: [AI mówi po polsku – nasz język zdeklasował angielski w najnowszym rankingu (cryps.pl)](https://cryps.pl/sztuczna-inteligencja-mowi-po-polsku-nasz-jezyk-zdeklasowal-angielski-w-najnowszym-rankingu/)
  - Context vs Prompt Engineering: [Effective Context Engineering for AI Agents – Anthropic](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)
  - [Prompting Techniques](https://www.promptingguide.ai/techniques) data base
  - My slides from other course: [Promting - Generative AI szkolenie – DevPowers (slajd 8)](https://devpowers.com/szkolenia/generative-ai-08-12-2025/#slide-8)
  - Testowanie promptów: [Promptfoo – Open-source Prompt Testing](https://www.promptfoo.dev/)
  - Smart Prompt generator: [Prompt Cowboy – AI Prompt Generator](https://www.promptcowboy.ai/)

## LLM benchmarks, best models

- LMArena (agent vs agent fights based on [Elo rating system](https://en.wikipedia.org/wiki/Elo_rating_system)):
  - [Agent Leaderboard](https://arena.ai/leaderboard/agent)
  - [WebDev Leaderboard](https://arena.ai/leaderboard/code/webdev)
  - [Search Leaderboard](https://arena.ai/leaderboard/search)
- Design Arena Elo (agent vs agent fights in UI, Game Dev, etc.): [Design Arena](https://www.designarena.ai/leaderboard)
- Software Engineering Benchmarks:
  - DeepSWE Bench (newer and better): [DeepSWE Bench](https://deepswe.datacurve.ai/)
  - SWE Bench: [SWE-bench Leaderboards](https://www.swebench.com/)
- Terminal Bench: [Terminal-Bench](https://www.tbench.ai/leaderboard/terminal-bench/2.0)
- Tool Calling: [Berkeley Function Calling Leaderboard (BFCL) V4](https://gorilla.cs.berkeley.edu/leaderboard.html)
- AIME (High School Math Exam): [AIME 2025 Benchmark Leaderboard | Artificial Analysis](https://artificialanalysis.ai/evaluations/aime-2025)
- Market Share - Open Router: [LLM Rankings | OpenRouter](https://openrouter.ai/rankings)
- Cost vs Intelligence Index [Artificial Analysis](https://artificialanalysis.ai/evaluations/artificial-analysis-intelligence-index?eval-cost=intelligence-vs-total-cost#eval-cost-tabs)

---

## Best AI Codding Tools
- Gartner: [Best AI Code Assistants Reviews 2026 | Gartner Peer Insights](https://www.gartner.com/reviews/market/ai-code-assistants)
- YT comparison: [Best AI Coding Tools for Developers in 2026 - YouTube](https://www.youtube.com/watch?v=pvMGRSZJ4Jw&t=330s)
- **IDE & plugins:**
  - Zed [Zed — Love your editor again](https://zed.dev/) (Open Source)
    - [Should You Use Zed In 2026? - YouTube](https://www.youtube.com/watch?v=lRrElGM23h4)
    - Windows users: [Zed in WSL](https://zed.dev/docs/remote-development#wsl-support)
    - Offline / air-gapped env: [Agents/LLMs](https://zed.dev/docs/ai/use-a-local-model), [Zeta](https://zed.dev/docs/ai/edit-prediction#local-and-self-hosted-models) (edit prediction), [LSP](https://zed.dev/docs/configuring-languages#possible-configuration-options) (language servers from PATH)
  - Cursor [Cursor](https://cursor.com/)
  - GitHub Copilot: [GitHub Copilot · Plans & pricing · GitHub](https://github.com/features/copilot/plans)
    - Not only GPT, Claude Opus in all plans: [Claude Opus 4.5 is now generally available in GitHub Copilot - GitHub Changelog](https://github.blog/changelog/2025-12-18-claude-opus-4-5-is-now-generally-available-in-github-copilot/)
  - [Google Antigravity IDE](https://antigravity.google/product/antigravity-ide)
  - [Junie | IntelliJ IDEA Documentation](https://www.jetbrains.com/help/idea/junie.html)
  - [Cline - AI Coding, Open Source and Uncompromised](https://cline.bot/)
  - [Augment Code - The Software Agent Company](https://www.augmentcode.com/)
  - [Kilo - Move at Kilo Speed](https://kilo.ai/)
  - [Tabnine AI Code Assistant | Smarter AI Coding Agents. Total Enterprise Control.](https://www.tabnine.com/)
  - ~~Continue~~ (acquired by Cursor): https://www.continue.dev/
  - ~~Windsurf / Codeium~~ (founders joined Google, team joined Cognition, it's Devin Desktop now): https://devin.ai/desktop/ (VSCode Clone)
  - ... list can go on...
- **Desktop apps:**
  - [Claude Desktop](https://code.claude.com/docs/en/desktop-quickstart)
    - [Linux - Claude Desktop Debian](https://github.com/aaddrick/claude-desktop-debian) (unofficial re-packaged Electron app - works well)
  - [Codex App](https://developers.openai.com/codex/app)
    - Linux [Codex Desktop Linux](https://github.com/ilysenko/codex-desktop-linux), [Codex App Linux](https://github.com/better-slop/codex-app-linux) (both unofficial, re-packaged from source = build is required)
  - [Google Antigravity 2.0](https://antigravity.google/product/antigravity-2)
  - [OpenCode](https://opencode.ai/download) (Open Source)
    - WebUI mode in the browser - [supports Offline / air-gapped env](https://github.com/anomalyco/opencode/issues/18492#issuecomment-4135581838) 
  - [Goose](https://block.github.io/goose/) (Open Source)
- **CLI tools:**
  - Knowledge Base with comparisons: [NotebookLM](https://notebooklm.google.com/notebook/44128edb-6841-4315-909c-4402b2d13bd1)
  - [Claude Code - AI coding agent for terminal & IDE | Claude](https://claude.com/product/claude-code)
  - [Codex CLI](https://developers.openai.com/codex/cli/)
  - ~~Gemini CLI~~ (open source, killed by Google... replaced by closed source Antigravity CLI): [Google Gemini CLI](https://geminicli.com/)
  - [Google Antigravity CLI](https://antigravity.google/product/antigravity-cli) (rewritten in Go, based on Crush and Bubblewrap)
  - [Crush](https://github.com/charmbracelet/crush) (Open Source, works on FreeBSD!)
  - [Goose](https://block.github.io/goose/) (Open Source)
  - [OpenCode](https://opencode.ai/) (Open Source)
  - [Aider](https://aider.chat/) (Open Source, pair programming)
  - [GitHub Copilot CLI](https://docs.github.com/en/enterprise-cloud@latest/copilot/concepts/agents/about-copilot-cli)
  - [Droid by Factory](https://factory.ai/)
  - Letta Code (MemGPT): [Quickstart | Letta Docs](https://docs.letta.com/letta-code/quickstart) (Open Source)
  - AIChat for Terminal commands help: [GitHub - aichat - Shell Assistant, Chat-REPL, RAG, AI Agent](https://github.com/sigoden/aichat) (Open Source)
  - ... many more....

### Codex - official plugin from OpenAI for VSCode "family":
- [https://developers.openai.com/codex/ide/](https://developers.openai.com/codex/ide/)
- VSCode / Cursor / Antigravity / Windsurf: [https://open-vsx.org/extension/openai/chatgpt#review-details](https://open-vsx.org/extension/openai/chatgpt#review-details)

### Claude Code extension for VSCode "family":
[https://open-vsx.org/extension/Anthropic/claude-code#review-details](https://open-vsx.org/extension/Anthropic/claude-code#review-details)


---

## Pricing - rate limits

[NotebookLM](https://notebooklm.google.com/notebook/8a4739c4-f9b3-41a2-aaf0-612daa0750ce) on pricing of main AI Coding tools
[NotebookLM](https://notebooklm.google.com/notebook/ddbe409f-832b-4d19-ac7d-aed2e059d2c2) on pricing & quality of OpenSource Models (GLM/ M2) vs big providers

- **Google Gemini AI Pro** plan with CLI / Code Assist / Antigravity:
  - on Pro: 120 requests/m, 1500 r/day - [Quotas and limits  |  Gemini Code Assist ](https://developers.google.com/gemini-code-assist/resources/quotas)
  - Antigravity on Pro "high generous quota refreshed every 5h": [Google Antigravity Quota](https://antigravity.google/docs/plans)
- **JetBrains** AI Assistant has only 10 credits for $10 = around 100 requests/month
  - [Licensing and subscriptions | AI Assistant Documentation](https://www.jetbrains.com/help/ai-assistant/licensing-and-subscriptions.html#individual-use)
- **Claude Code Pro** ($17-20/m) & Max ($100 5x, $200 20x Pro) plans:
  - [Pricing | Claude](https://claude.com/pricing)
- **GitHub Copilot** 
  - Free: 50 premium requests/m + 2000 completions (NO: CLI, PRs, issue assign,  code reviews)
  - Pro for $10/m - unlimited GPT-5.4 mini, 300 premium requests (also Opus 4.5+)
  - Pro+ for $39/m - 1500 premium requests
  - [GitHub Copilot · Plans & pricing · GitHub](https://github.com/features/copilot/plans)
  - [Plans for GitHub Copilot - GitHub Docs](https://docs.github.com/en/copilot/get-started/plans) (detailed docs)
- **Cursor Pro** for $20/m provides best autocomplete
  - [Pricing · Cursor](https://cursor.com/pricing)
- **ChatGPT Plus** for $20/m
  - provides access to **Codex** (both CLI and Cloud agents with GitHub integration)
- **GLM Codding Plan**
  - GLM-5 is probably the best open source Coding LLM (some argue that M2.7 is better/faster)
  - Lite plan may be slow but costs only $3/m and offers 3x usage of Claude Code Pro!
  - Pro plan is for $12 for the first year, $30/m later (5x Lite plan = 15x Claude Code Pro)
    Video on GLM-5 and Minimax M2.7: [So close to Opus at 1/10th the price (GLM-4.7 and Minimax M2.1 showdown) - YouTube](https://www.youtube.com/watch?v=kEPLuEjVr_4)
  - Invite link with additional 10%: https://z.ai/subscribe?ic=IA5VBRGQV4
- **Tetrate.ai** $15 for free (OpenRouter for Enterprise):
  - 1. Create account for $5 free: [Tetrate: Safe, Fast, and Profitable AI for the Enterprise](https://tetrate.io/)
  - 2. Use goose CLI to add Tetrate Agent Router for $10 more: [Quickstart | goose](https://block.github.io/goose/docs/quickstart/)

## My Recommendations - tools to install/use:

- **Handy STT** (like SuperWhisper but Open Source MIT)
  - [Handy](https://handy.computer/)
  - architecture details (Tauri): [Code Wiki](https://codewiki.google/github.com/cjpais/handy)
- Claude Code - for multi-step tasks, leading in innovation, and access to best models on discounted prices (especially on Max plan)
  - Desktop & Mobile apps with Code Agent in the cloud! [Claude Desktop App](https://code.claude.com/docs/en/desktop)
  - ⚠️ **Anthropic API / Claude Code availability** — in the last months, uptime has sometimes dropped below 99% for 90 day window. When you see below API errors, check [status.claude.com](https://status.claude.com/):
    - `● API Error: 529 Overloaded` — server-side overload, usually temporary — try again in a moment
    - `● API Error: 500 Internal server error` — server-side issue, usually temporary — try again in a moment
    - If either persists, check https://status.claude.com
- Codex - for very high limits, best models for hard work (not as good as Opus in architecture, but great for development and more focused), good cloud agents with GitHub integration.
- Zed.ai - to use any AI tool/Provider you wont, Open Source, Rust (fast)
- Cursor - for best autocomplete and cloud agents, RAG on any docs
- GitHub Copilot - for integration with JetBrains IDEs, access to many models, good Cloud Agent (GH native), best Code Reviews online, and good price.
- Google AI Pro - not best for programming, but best value for money overall: Gemini App, Gmail, Docs + Gemini CLI + Antigravity + Code Assist + Veo + ...
  - NotebookLM for learning / knowledge base: [NotebookLM](https://notebooklm.google.com/)
- [OpenRouter](https://openrouter.ai/) for control, observability and 1 API Key for all tools
- [Goose](https://block.github.io/goose/) Agent for programming, PC control & automation workflows
  - Advent of AI: [Advent of AI](https://adventofai.dev/) (by Goose)
  - based on: [Advent of Code 2025](https://adventofcode.com/)

---

## AI Agents Tool-belt:

### Rules

- custom prompts/instructions per folder / type of file (with regex):
  - Cursor, GitHub Copilot & Claude Code has path based targeting e.g. `"src/api/**/*.ts"`
  - in Cluade Code: [Path Specific Rules](https://code.claude.com/docs/en/memory#path-specific-rules)
  - in Cursor: [Rules | Cursor Docs](https://cursor.com/docs/context/rules)
  - in OpenCode: [Rules | OpenCode](https://opencode.ai/docs/rules/)
  - in Codex CLI: [Rules](https://developers.openai.com/codex/rules)
  - examples:
    - [payload/templates/website/.cursor/rules at main · payloadcms/payload · GitHub](https://github.com/payloadcms/payload/tree/main/templates/website/.cursor/rules)

### AGENTS.md

Custom project file (or many nested files) with instructions and description of application or it's parts:
  - Growing standard: [AGENTS.md](https://agents.md/)
  - CLAUDE.md [CLAUDE.MD files: Customizing Claude Code for your codebase | Claude](https://claude.com/blog/using-claude-md-files)
  - GEMINI.md [Provide context with GEMINI.md files | Gemini CLI](https://geminicli.com/docs/cli/gemini-md/)
  - Discussions / Sources:
    - X.com thread: [Matt Pocock](https://x.com/mattpocockuk/status/2012906065856270504)
    - Article & Prompt to fix AGENTS.md: [A Complete Guide To AGENTS.md](https://www.aihero.dev/a-complete-guide-to-agents-md)
  - Examples:
    - [mcp-for-beginners/AGENTS.md at main · microsoft/mcp-for-beginners · GitHub](https://github.com/microsoft/mcp-for-beginners/blob/main/AGENTS.md) (includes Java Spring Boot)
    - [payload/templates/website/AGENTS.md at main · payloadcms/payload · GitHub](https://github.com/payloadcms/payload/blob/main/templates/website/AGENTS.md)

### SKILLS.md

- What are Skills:
  - [GitHub - agentskills/agentskills: Specification and documentation for Agent Skills](https://github.com/agentskills/agentskills)
  - [Overview - Agent Skills](https://agentskills.io/home)
- **skills.sh** - Library of Skills & "skill manager" tool from Vercel: [The Agent Skills Directory](https://skills.sh/)
- Skills in popular tools (docs):
  - in Copilot: [About Agent Skills - GitHub Docs](https://docs.github.com/en/copilot/concepts/agents/about-agent-skills)
  - in Claude Code: [Agent Skills - Claude Code Docs](https://code.claude.com/docs/en/skills)
  - in Gemini CLI: [Agent Skills | Gemini CLI](https://geminicli.com/docs/cli/skills/)
  - in Cursor: [Agent Skills | Cursor Docs](https://cursor.com/docs/context/skills)
  - in OpenCode: [Agent Skills | OpenCode](https://opencode.ai/docs/skills/)
  - in Codex CLI: [Agent Skills](https://developers.openai.com/codex/skills)
- **Most Popular Skills & skill libraries**
  - Engineering Skills from [Matt Pocock](https://github.com/mattpocock/skills/tree/main) (e.g. Grill Me, PRD, TDD, Triage)
  - Superpowers by [Obra](https://github.com/obra/superpowers) (e.g. TDD, Writing Plans, Executing Plans, Code Review, Debugging)
  - Microsoft [Azure Skills](https://github.com/microsoft/azure-skills)
  - [Anthropic](https://github.com/anthropics/skills) (not only for Claude, e.g. PDF, **MS Office** skills for Excel, Word, PowerPoint)
  - Reduce context window and token usage with [Caveman](https://github.com/JuliusBrussee/caveman/tree/main/skills/caveman)
  - Marketing Skills by [Corey Haines](https://github.com/coreyhaines31/marketingskills)
  - Extract [Design System](https://github.com/arvindrk/extract-design-system)
  - [Awesome Copilot](https://github.com/github/awesome-copilot) (library from GitHub with various skills, e.g. [SQL Optimization](https://www.skills.sh/github/awesome-copilot/sql-optimization), but also with Sub-Agents)
  - Vercel's [Next.js](https://github.com/vercel-labs/next-skills)
  - SQL / DB [Database Skills](https://github.com/planetscale/database-skills) | [DB Skills](https://db-skills.com/) from Planetscale
  - Convex (AI first Reactivity platform for rapid Web development) [Convex](https://github.com/get-convex/agent-skills)
  - Supabase & [PostgreSQL](https://github.com/supabase/agent-skills)
- **Skills for Java**
  - [java-testing – pluginagentmarketplace/custom-plugin-java](https://skills.sh/pluginagentmarketplace/custom-plugin-java/java-testing)
  - [java-spring-boot – pluginagentmarketplace/custom-plugin-java](https://skills.sh/pluginagentmarketplace/custom-plugin-java/java-spring-boot)
  - [java-maven – pluginagentmarketplace/custom-plugin-java](https://skills.sh/pluginagentmarketplace/custom-plugin-java/java-maven)
  - [java-architect – jeffallan/claude-skills](https://skills.sh/jeffallan/claude-skills/java-architect)
  - [java-coding-standards – affaan-m/everything-claude-code](https://skills.sh/affaan-m/everything-claude-code/java-coding-standards)
  - [android-java – alinaqi/claude-bootstrap](https://skills.sh/alinaqi/claude-bootstrap/android-java)
  - [custom-plugin-java/skills – full skills collection on GitHub](https://github.com/pluginagentmarketplace/custom-plugin-java/tree/main/skills)
  - [agent-browser SKILL.md – vercel-labs/agent-browser (example skill structure)](https://github.com/vercel-labs/agent-browser/blob/2fe7394dbeb89efb00e56899dd71f32db5ec1dee/skills/agent-browser/SKILL.md)
  - [spring-ai - Spring AI framework skill](https://skills.sh/teachingai/full-stack-skills/spring-ai)
  - [spring-ai-mcp-server-patterns - Spring AI MCP server patterns](https://skills.sh/giuseppe-trisciuoglio/developer-kit/spring-ai-mcp-server-patterns)
  - [langchain4j-spring-boot-integration - LangChain for Java with Spring Boot integration](https://skills.sh/giuseppe-trisciuoglio/developer-kit/langchain4j-spring-boot-integration)

### Custom Slash Commands / Prompts

**Custom slash commands** (in Claude Code) or **Custom Prompts** (in Codex) are simple reusable prompts (Markdown files) you invoke manually via `/command-name` (optionally with an extra message/argument). They are the lightweight sibling of Skills — no metadata, no auto-triggering. You write the prompt once and reuse it on demand.

- **What they are:** Markdown files stored in `.claude/commands/` (Claude Code CLI, Claude Code Desktop app, or Agent SDK). Each file becomes a `/`-command. You can optionally pass `$ARGUMENTS` to inject a custom message at invocation time.
- **When to use Commands vs Skills:**
  - Use **Custom Commands** for *simpler, reusable prompts* you want to trigger *explicitly* (e.g. a commit-message generator, a code-review checklist, a fixed prompt template). Lower overhead, no triggering heuristics.
  - Use **Skills** when the agent should *auto-detect* context and pull instructions in (via the `description` field), when you need to split knowledge/instructions in multiple files, or when you want bundled assets or scripts.
- **Grouping (optional):** Placing commands in a subfolder creates a `group:name` namespace. E.g. `.claude/commands/git/commit.md` is invoked as `/git:commit`.
- Docs in Claude Agent SDK (same mechanism for Claude Code CLI & Desktop app): [Creating custom slash commands](https://code.claude.com/docs/en/agent-sdk/slash-commands#creating-custom-slash-commands)
- Docs in Codex (DEPRECATED, use skills instead): [Custom Prompts](https://developers.openai.com/codex/custom-prompts)
- Example commands in this repo: [`/.claude/commands/`](.claude/commands/) — grouped under [`git/`](.claude/commands/git):
  - [`git/commit.md`](.claude/commands/git/commit.md) → invoked as `/git:commit`
  - [`git/diff.md`](.claude/commands/git/diff.md) → invoked as `/git:diff`

### Tools

- built-in Agent tools (provided by IDE / CLI you use):
  - Terminal
  - Browse / Search
  - Docs (RAG) lub Contex7 MCP jeśli nie ma docs wbudowanych
  - GIT
  - TODO list
  - Browser integration (e.g. in Cursor) / Chrome-devtools-mcp
  - + Custom Tools
- **LSP** (Language Server Protocol)
  - allows agent to use diagnostic tools and understand syntax, linting errors

### MCP - Tools & Servers

- MCP (Model Context Protocol)
  - open standard for anybody to build tools for agents
  - best suited for accessing external services (as MCP Servers, or **Connectors**)
  - but there are also local MCP tools available (e.g. Playwright)
  - Anthropic's Skill to build own MCP Tools: [MCP Builder](https://www.skills.sh/anthropics/skills/mcp-builder)
- Problems:
  - Context bloating = Context rot,
    - partially fixed by [Tool Discovery](https://modelcontextprotocol.info/docs/concepts/tools/#tool-discovery-and-updates) = 1 tool to rule them all
  - Too many choices for agent (max 5x MCP servers / 50 tools in total used at once!)
  - Slower than just Bash scripts, terminal commands (Agent also can use them!)
  - Sometimes CLI tool is better (with optional skill how to use it), e.g.
    - [Sentry CLI](https://cli.sentry.dev/)
    - [GitHub CLI](https://docs.github.com/en/github-cli/github-cli/quickstart)
    - [Playwright CLI](https://github.com/microsoft/playwright-cli) + [Skill](https://www.skills.sh/microsoft/playwright-cli/playwright-cli)
- History of the Standard
  - Introduced by Anthropic in 2024: [Introducing the Model Context Protocol \\ Anthropic](https://www.anthropic.com/news/model-context-protocol)
  - Donated to Linux Foundation subsidiary: [Donating the MCP and the Agentic AI Foundation](https://www.anthropic.com/news/donating-the-model-context-protocol-and-establishing-of-the-agentic-ai-foundation)
  - Open Standard: [What is the Model Context Protocol (MCP)? - Model Context Protocol](https://modelcontextprotocol.io/docs/getting-started/intro)
  - Why MCP moved from SSE to Streamable HTTP: [SSE vs. Streamable HTTP - which will be the standard for remote servers? : r/mcp](https://www.reddit.com/r/mcp/comments/1kdyse2/sse_vs_streamable_http_which_will_be_the_standard/)
- **Installation** instructions for Agents:
  - in Claude Code: [Connect Claude Code to tools via MCP - Claude Code Docs](https://code.claude.com/docs/en/mcp)
  - in Zed: [Redirecting... | Zed Code Editor Documentation](https://zed.dev/docs/assistant/model-context-protocol)
  - in Cursor: [Model Context Protocol (MCP) | Cursor Docs](https://cursor.com/docs/context/mcp)
  - in OpenCode: [MCP servers | OpenCode](https://opencode.ai/docs/mcp-servers/)
- **Most Popular MCPs**:
  - Context7 - documentation: [Context7 - Up-to-date documentation for AI](https://context7.com/)
  - Chrome DevTools: [GitHub - Chrome DevTools for coding agents](https://github.com/ChromeDevTools/chrome-devtools-mcp)
  - Playwright: [Microsoft Playwright MCP](https://github.com/microsoft/playwright-mcp)
    - Claude: `claude mcp add playwright npx @playwright/mcp@latest`
    - Codex: `codex mcp add playwright npx "@playwright/mcp@latest"`
  - Browser Use: [MCP Server - Browser Use](https://docs.browser-use.com/customize/integrations/mcp-server)
  - IntelliJ IDEA: [MCP Server | IntelliJ IDEA Documentation](https://www.jetbrains.com/help/idea/mcp-server.html)
  - GitHub: [MCP GitHub · GitHub](https://github.com/mcp/io.github.github/github-mcp-server)
  - Jenkins: [MCP server for Jenkins build tasks](https://github.com/jasonkylelol/jenkins-mcp-server)
  - Linear, GitLab, Atlassian, Azure DevOps
  - Notion, PostHog, Postman, Sentry, ...
  - PostgreSQL, MongoDB, ...
  - Figma
  - Blender
- **Example Java MCP Servers:**
  - Create own MCP server in Java: [modelcontextprotocol/java-sdk – The official Java SDK for MCP servers and clients, maintained with Spring AI](https://github.com/modelcontextprotocol/java-sdk)
  - [tangcent/maven-indexer-mcp](https://github.com/tangcent/maven-indexer-mcp)
  - [OpenLinkSoftware/mcp-jdbc-server – Java based MCP Server for JDBC](https://github.com/OpenLinkSoftware/mcp-jdbc-server)
  - [quarkiverse/quarkus-mcp-servers – Model Context Protocol Servers in Quarkus](https://github.com/quarkiverse/quarkus-mcp-servers)
  - [quarkiverse/quarkus-mcp-server – Extension for implementing MCP server features in Quarkus](https://github.com/quarkiverse/quarkus-mcp-server)
  - [hpalma/springinitializr-mcp – MCP server for Spring Initializr](https://github.com/hpalma/springinitializr-mcp)
  - [vishalmysore/a2ajava – Pure Java implementation of Google A2A protocol with Spring Boot; agents also exposed as MCP tools](https://github.com/vishalmysore/a2ajava)
  - [ECF/MCPToolGroups – Tool Groups Support for the Model Context Protocol](https://github.com/ECF/MCPToolGroups)
  - [arvindand/maven-tools-mcp – MCP server for Maven Central dependency intelligence for Maven, Gradle, SBT, Mill](https://github.com/arvindand/maven-tools-mcp)
  - [idachev/mcp-javadc](https://github.com/idachev/mcp-javadc)
  - [studykit/mcp-jar-indexer](https://github.com/studykit/mcp-jar-indexer)

Example MCP Config (it's standard, similar for all MCPs):

```json
{
  "mcpServers": {
    "maven-indexer": {
      "command": "npx.cmd",
      "args": [
        "-y",
        "maven-indexer-mcp@latest"
      ]
    },
    
  }
}
```

### ACP (Agent Client Protocol)

- use CLI Agents in other tools / IDEs
  - Created by Zed IDE to use Claude Code in Zed (now also for Codex, Gemini, OpenCode)
  - in ZED [Zed — Agent Client Protocol](https://zed.dev/acp)
  - in JetBrains IDEs: [Agent Client Protocol | JetBrains](https://www.jetbrains.com/help/ai-assistant/acp.html#install-agent-from-registry)
  - in OpenCode: [ACP Support | OpenCode](https://opencode.ai/docs/acp/)
  - in Goose: [Using goose in ACP Clients | goose](https://block.github.io/goose/docs/guides/acp-clients)

### Sub-Agents

Main agent can delegate tasks to sub-agents (often specialized), to focus on orchestration, minimize context window bloat and work in parallel (often faster).

- In most popular tools:
  - Claude Code: [Create custom subagents - Claude Code Docs](https://code.claude.com/docs/en/sub-agents)
    - [Agent Teams (experimental feature) vs Sub-Agents](https://code.claude.com/docs/en/agent-teams)
    - [Blog: Subagents in Claude Code](https://claude.com/blog/subagents-in-claude-code)
  - Codex CLI [Multi-agents](https://developers.openai.com/codex/multi-agent/)
  - Google Antigravity [Subagents](https://antigravity.google/docs/subagents)
  - Cursor: [Subagents | Cursor Docs](https://cursor.com/docs/context/subagents)
  - Copilot: [About custom agents - GitHub Docs](https://docs.github.com/en/copilot/concepts/agents/coding-agent/about-custom-agents)
  - OpenCode: [Agents | OpenCode](https://opencode.ai/docs/agents/)
  - Goose: [Subagents | goose](https://block.github.io/goose/docs/guides/subagents/)
- Nested Sub-Agents (sub-agents spawned by sub-agents):
  - [Claude Code - Nested Sub-Agents](https://code.claude.com/docs/en/sub-agents#spawn-nested-subagents) (max depth is 5, not configurable)
  - [Codex Subagent settings](https://developers.openai.com/codex/subagents#global-settings) - `agent.max_threads` (default: 6 - maximum number of concurrent agents) & `agent.max_depth` (default: 1 - only main agent can spawn sub-agents, set to 2+ to allow nested sub-agents)
- **Git Worktrees** for Parallel Agents in same repository:
  - What is a Git Worktree? [Git Worktree Docs](https://git-scm.com/docs/git-worktree)
  - Claude: [Run parallel Claude code sessions with Git worktrees | Claude Docs](https://code.claude.com/docs/en/common-workflows#run-parallel-claude-code-sessions-with-git-worktrees)
  - Codex: [Worktrees | OpenAI Codex Docs](https://developers.openai.com/codex/app/worktrees/)
  - Cursor: [Parallel Agents | Cursor Docs](https://cursor.com/docs/configuration/worktrees)
- Library of Sub-Agent definitions from GitHub: [Awesome Copilot Agents](https://github.com/github/awesome-copilot/tree/main/agents)

### Claude Code Plugins vs MCP/Skills

- https://code.claude.com/docs/en/plugins
- https://github.com/luongnv89/claude-howto/blob/main/claude_concepts_guide.md#plugins

## Security

- Validate skills, MCPs and any scripts before installing and using them.
- Check the license for skills, hooks and agents you copy from internet (e.g. Anthropic skills are not permissive) - license can be found on GitHub repo or inside skill folder.
- YOLO MODE requires good sandboxing or phisical 2nd device without sensitive credentials.
- https://code.claude.com/docs/en/best-practices#safe-autonomous-mode
- https://code.claude.com/docs/en/sandboxing

### Evals, Tests & Observability

- [PromptFoo](https://www.promptfoo.dev/) (open source) -prompt x model testing framework
- [LangFuse](https://langfuse.com/) (open source) - advanced OSS framework and application for tracing, evals, prompt management, metrics and debugging of LLL-based app! 
- OpenAI Evals:
  - https://developers.openai.com/api/docs/guides/evals/
  - https://developers.openai.com/api/docs/guides/evaluation-getting-started
- Google Vertex: https://docs.cloud.google.com/vertex-ai/generative-ai/docs/models/evaluation-overview
- Microsoft Foundry Evals: https://learn.microsoft.com/en-us/azure/ai-foundry/how-to/evaluate-generative-ai-app?view=foundry-classic


## Best Practices / Methodologies for Agentic Codding:

- Plan & Solve
- Plan-Execute-Test-Commit loop
- Good AGENTS.md and rules for better understanding of the project
- Make AI Agent ask you questions instead of acting immediately (Custom agent or in AGENTS.md)
  - With better initial prompt and info Agent will not interrupt you so often and work longer
- Product Requirement Document (PRD): 
  - [GitHub - snarktank/ai-dev-tasks: A simple task management system for managing AI dev agents](https://github.com/snarktank/ai-dev-tasks)
  - [Legal agent income up-cert PRD creation - Amp](https://ampcode.com/threads/T-019b98b9-3fe1-77ea-9d43-235c62200559)
- TODO List / Task list / Progress tracking
  -  Not needed with Opus 4.5? [TODOs Are Done - Amp](https://ampcode.com/news/todos-are-done)
- **System 2 Thinking**
  - refers to a **slow, deliberate** approach to software development that prioritizes logic and architecture over intuitive, rapid generation.
  - This philosophy has emerged as a rigorous defense against "slop"—the influx of unmaintainable or hallucinated code that often results from **"System 1"** thinking (**fast, intuitive** = "vibe coding")
  - Terms borrowed from [Daniel Kohneman - Thinking, Fast and Slow](https://www.goodreads.com/en/book/show/11468377-thinking-fast-and-slow) book
- Tips for OpenCode: [Don't sign up the yearly plan, it's a trap : r/ZaiGLM](https://www.reddit.com/r/ZaiGLM/comments/1q2oqhx/comment/nxf1idh/)
  - Agent RPI-V8 [Claudette coding agent](https://gist.github.com/bizzkoot/bdf957cd745de8c788df3ca7f353daad#file-rpi-v8-opencode-agent-md)
  - Skill **Superpowers** [GitHub - obra/superpowers: An agentic skills framework & software development methodology that works.](https://github.com/obra/superpowers/tree/main)
  - MCP sequential-thinking
- Clavix - templates for better prompts. PRDs
  - [GitHub - ClavixDev/Clavix: Transform vague ideas into production-ready prompts. Analyze gaps, generate PRDs, and supercharge your AI coding workflow with the CLEAR framework.](https://github.com/ClavixDev/Clavix)

### Ralph Wiggum Bash loop:

- Knowledge Base: [NotebookLM](https://notebooklm.google.com/notebook/c92dbf13-8174-42eb-9f1d-15be3f7c842d)
- original post from Geoffrey Huntley: [Ralph Wiggum as a "software engineer"](https://ghuntley.com/ralph/)
- repo that Geoffrey supports: [GitHub - ghuntley - The Ralph Wiggum Technique—the AI development methodology that reduces software costs to less than a fast food worker's wage.](https://github.com/ghuntley/how-to-ralph-wiggum)
- Video where G.H. explains Ralph: [Ralph Wiggum (and why Claude Code's implementation isn't it) with Geoffrey Huntley and Dexter Horthy - YouTube](https://www.youtube.com/watch?v=O2bBWDoxO4s)
- Ryan Carson version: [GitHub - snarktank/ralph: Ralph is an autonomous AI agent loop that runs repeatedly until all PRD items are complete.](https://github.com/snarktank/ralph) / on [X.com](https://x.com/ryancarson/status/2008548371712135632)
  - G.H. wrote "it isn't it" on [X.com](https://x.com/GeoffreyHuntley/status/2008731415312236984)
- Ralph meme coin and technique explanation: [$RALPH - The Memecoin That's Helping AI Ship Code While You Sleep](https://ralphcoin.org/#technique)
- Ralph Wiggum from 1st principles: [The Ralph Wiggum Loop from 1st principles (by the creator of Ralph) - YouTube](https://www.youtube.com/watch?v=4Nna09dG_c0)
- Claude Code Ralph plugin (official, but not fixing context rot): [claude-code/plugins/ralph-wiggum at main · anthropics/claude-code · GitHub](https://github.com/anthropics/claude-code/tree/main/plugins/ralph-wiggum)
- Goose with Ralph loop: [Ralph Loop | goose](https://block.github.io/goose/docs/tutorials/ralph-loop)
- Detailed technical video: ["Ralph Wiggum" AI Agent will 10x Claude Code/Amp - YouTube](https://www.youtube.com/watch?v=RpvQH0r0ecM)

---

## Cloud Agents / Containerization / VM - Security

- OpenAI Codex Web
  - [Codex Web](https://chatgpt.com/codex)
  - NEW! (macOS only now) [Codex App](https://openai.com/pl-PL/codex/)
  - [Docs Codex Web](https://developers.openai.com/codex/cloud/)
  - Blog [Introducing Codex Web](https://openai.com/pl-PL/index/introducing-codex/)
- [Claude Code Web](https://claude.ai/code) (also in mobile app!)
- Cursor Cloud Agents [Cursor Agents](https://cursor.com/agents)
- Copilot Agents: [GitHub Copilot Agents](https://github.com/copilot/agents)
  - Docs: [About GitHub Copilot coding agent - GitHub Enterprise Cloud Docs](https://docs.github.com/en/enterprise-cloud@latest/copilot/concepts/agents/coding-agent/about-coding-agent)
  - Also in mobile GitHub app!
- Goose in Docker: [Building goose in Docker | goose](https://block.github.io/goose/docs/tutorials/goose-in-docker)

## Other AI Tools and Ecosystem

 - **Design / UX / FE with AI:**
   - Google: [Stitch - Design with AI](https://stitch.withgoogle.com/?pli=1)
   - Figma AI plugins
     - Figma MCP: [Guide to the Figma MCP server – Figma Learn - Help Center](https://help.figma.com/hc/en-us/articles/32132100833559-Guide-to-the-Figma-MCP-server)
   - Lovable
   - v0 from Vercel
   - GitHub Spark [GitHub Spark · Dream it. See it. Ship it. · GitHub](https://github.com/features/spark)
   - Tailwind & Shadcn popularity
 - **Code Reviews, GitHub integrations**
   - qodo - [AI Code Review for Teams – IDE, GitHub, GitLab & CLI](https://www.qodo.ai/) (open source)
   - CodeRabbit [AI Code Reviews | CodeRabbit | Try for Free](https://www.coderabbit.ai/)
     - GitLab Sef-Managed: [CodeRabbit GitLab](https://docs.coderabbit.ai/platforms/self-hosted-gitlab)
   - Gemini Code Assist: [Review GitHub code using Gemini Code Assist](https://developers.google.com/gemini-code-assist/docs/review-github-code)
     - Consumer - config files: [Customize Gemini Code Assist behavior in GitHub  |  Google for Developers](https://developers.google.com/gemini-code-assist/docs/customize-gemini-behavior-github#consumer)
     - Enterprise - Cloud Console: [Customize Gemini Code Assist behavior in GitHub  |  Google for Developers](https://developers.google.com/gemini-code-assist/docs/customize-gemini-behavior-github#enterprise)
  - Gemini CLI w CI/CD:
    - Google Course: [Sprawdzanie kodu i analiza bezpieczeństwa za pomocą interfejsu wiersza poleceń Gemini](https://codelabs.developers.google.com/gemini-cli-code-analysis?hl=pl#0)
    - GitLab MR Article: [Building an automated GitLab Merge Request Review Agent with Gemini CLI  | Medium](https://medium.com/google-cloud/building-an-automated-gitlab-merge-request-review-agent-with-gemini-cli-35855d53bec1)
  - GitHub Copilot:
    - [Configuring automatic code review by GitHub Copilot - GitHub Docs](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/configure-automatic-review)
    - [Using GitHub Copilot code review - GitHub Docs](https://docs.github.com/en/copilot/how-tos/use-copilot-agents/request-a-code-review/use-code-review)
   - OpenAI Codex [Use Codex in GitHub](https://developers.openai.com/codex/integrations/github/)
   - Cursor Bugbot: [Bugbot | Cursor Docs](https://cursor.com/docs/bugbot)
   - Cursor CLI in GH Actions: [Code Review with Cursor CLI | Cursor Docs](https://cursor.com/docs/cli/cookbook/code-review)
 - **Debugging, Security, Docs**
   - Sentry Seer: [Seer: AI debugging agent that works for you. | Sentry](https://sentry.io/product/seer/)
   - [Jam | Build a bug-free product.](https://jam.dev/)
   - [Mend.io - AI Powered Application Security](https://www.mend.io/)
   - [Mintlify - The Intelligent Documentation Platform](https://www.mintlify.com/)
   - Snyk: [Snyk AI-powered Developer Security Platform | AI-powered AppSec Tool & Security Platform | Snyk](https://snyk.io/)
   - Snyk Evo: [Security for Agentic AI Applications and Tools | Evo by Snyk | Evo](https://evo.ai.snyk.io/)
   - Snyk vs Mend: [Mend.io vs Snyk 2026 | Gartner Peer Insights](https://www.gartner.com/reviews/market/application-security-testing/compare/mend-io-vs-snyk)
   - **Markdown** is best for AI Docs, and supports many additional features, eg.
     - Diagrams with Mermaid [Mermaid | Diagramming and charting tool](https://mermaid.js.org/)
     - Slides with [Marp: Markdown Presentation Ecosystem](https://marp.app/)
     - **Confluence** synchronization in GH Action: [confluence-markdown-sync · Actions · GitHub Marketplace · GitHub](https://github.com/marketplace/actions/confluence-markdown-sync)
       - [GitHub - andygolubev/github-to-confluence-publisher](https://github.com/andygolubev/github-to-confluence-publisher)
       - [GitHub - mihaeu/cosmere: Sync your markdown files to Confluence](https://github.com/mihaeu/cosmere)

---
 
## Frameworks & Tools to Build Agents:

- **JAVA & AI**
  - [Spring AI](https://docs.spring.io/spring-ai/reference/index.html)
  - Implementation of official [OpenAI Java SDK](https://docs.spring.io/spring-ai/reference/api/chat/openai-sdk-chat.html)
  - [OpenAI Chat](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html)
  - Official OpenAI Java SDK:
    - [GitHub - openai/openai-java: The official Java library for the OpenAI API](https://github.com/openai/openai-java)
    - [Libraries | OpenAI API](https://platform.openai.com/docs/libraries?language=java&desktop-os=windows)
  - Microsoft [Semantic Kernel for Java](https://github.com/microsoft/semantic-kernel-java/tree/main)
- **UI Libraries**:
  - Vercel AI SDK UI [AI SDK UI: Overview](https://ai-sdk.dev/docs/ai-sdk-ui/overview)
  - assistant-ui [GitHub - assistant-ui/assistant-ui: Typescript/React Library for AI Chat💬🚀](https://github.com/assistant-ui/assistant-ui)
  - Protocol giving Agents ability to generate UI on the fly [AG-UI](https://github.com/ag-ui-protocol/ag-ui)
  - UI Components for AI tools, with full support of AG-UI [CopilotKit](https://github.com/CopilotKit/CopilotKit)
- **AI & Agentic Frameworks**:
  - Biggest Node.js framework [AI SDK by Vercel](https://ai-sdk.dev/docs/introduction)
  - Fastest growing Node.js AI Framework [Mastra AI](https://workos.com/blog/mastra-ai-quick-start)
  - Most Popular Framework (JS & Python)
    - RAG, Chat and Tools [LangChain](https://www.langchain.com/)
    - Multi-agent framework [LangGraph](https://www.langchain.com/langgraph)
  - Python Multi-agent framework [CrewAI Multi-Agent Platform](https://www.crewai.com/)
  - Microsoft's .Net & Python Framework [AutoGen](https://microsoft.github.io/autogen/stable/)
    - UI Low-Code [AutoGen Studio](https://microsoft.github.io/autogen/stable/)
  - Rust [GitHub - 0xPlaygrounds/rig: ⚙️🦀 Build modular and scalable LLM Applications in Rust](https://github.com/0xPlaygrounds/rig)
  - Tools for RAG / knowladge base [LlamaIndex](https://www.llamaindex.ai/)
  - Protocol for Agent to Agent communication [A2A Protocol](https://a2a-protocol.org/latest/)
    - Created by Google, now as open standard: [Google blog post announcing A2A](https://developers.googleblog.com/en/a2a-a-new-era-of-agent-interoperability/)
- **Platform-specific**
  - Google [Agent Development Kit](https://google.github.io/adk-docs/)
  - OpenAI
    - [Agents SDK](https://developers.openai.com/api/docs/guides/agents-sdk/)
    - [AgentKit]()
  - Microsoft
    - [Agent Framework](https://learn.microsoft.com/en-us/agent-framework/overview/?pivots=programming-language-csharp)
    - [Semantic Kernel](https://github.com/microsoft/semantic-kernel) (.Net, Python & Java)
    - [365 Agent SDK](https://github.com/microsoft/Agents/tree/main)
- **No-Code Tools**
  - Flowise, OpenSource Apache (based on LangChain and LangGraph = many agents working together) [Flowise - Build AI Agents, Visually](https://flowiseai.com/)
  - n8n for business automation & simple AI Agents [AI Workflow Automation Platform & Tools - n8n](https://n8n.io/)
  - MS 365 + Power Automate + [Microsoft Copilot Studio](https://www.microsoft.com/en-us/microsoft-365-copilot/microsoft-copilot-studio)
  - Google [Workspace Studio](https://workspace.google.com/studio/)

---

### JetBrains AI Ecostystem
 
 - Article: [Best Software Composition Analysis Tools - Qodana Blog](https://blog.jetbrains.com/qodana/2025/09/best-software-composition-analysis-tools/) 
   (Qodana, Mend, Snyk, OWASP, Black Duck, FOSSA)
 - **MCP Server:** [MCP Server | IntelliJ IDEA Documentation](https://www.jetbrains.com/help/idea/mcp-server.html)
 - **AI Assistant** - [AI Assistant in JetBrains IDEs](https://www.jetbrains.com/help/idea/ai-assistant-in-jetbrains-ides.html?utm_source=product&utm_medium=link&utm_campaign=IU&utm_content=2025.3)
   - **low quota** - only 10 credits in Pro for $10 (~10 agent requests): [Licensing and subscriptions | AI Assistant Documentation](https://www.jetbrains.com/help/ai-assistant/licensing-and-subscriptions.html#ai-quota)
   - **Junie** AI Agent (unified in Chat now): [Junie, the AI coding agent by JetBrains - IntelliJ IDEs Plugin | Marketplace](https://plugins.jetbrains.com/plugin/26104-junie-the-ai-coding-agent-by-jetbrains)
   - **Other CLI Agents** in JetBrains IDEs with ACP - [Agent Client Protocol Registry | JetBrains](https://www.jetbrains.com/help/ai-assistant/acp.html#install-agent-from-registry)
 - **GH Copilot** - higher quota, also on free tier (GPT-5 mini, Haiku 4.5)
   - [GitHub Copilot - Your AI Pair Programmer - IntelliJ IDEs Plugin | Marketplace](https://plugins.jetbrains.com/plugin/17718-github-copilot--your-ai-pair-programmer)
   - [GitHub Copilot app modernization - IntelliJ IDEs Plugin | Marketplace](https://plugins.jetbrains.com/plugin/28791-github-copilot-app-modernization) (legacy code, refactors)
 - **AI Unit Testing**: [Diffblue Cover - AI Agent for unit testing - IntelliJ IDEs Plugin | Marketplace](https://plugins.jetbrains.com/plugin/14946-diffblue-cover--ai-agent-for-unit-testing)
   - Does NOT use LLM. Own small RL model that generates tests automatically!
   - has mixed reviews, probably LLM based agent can write better tests nowadays
   - Specialized in Java, JUnit and TestNG
 - **SonarQube** static analysis (to control better AI generated code):
   - [SonarQube for IDE - IntelliJ IDEs Plugin | Marketplace](https://plugins.jetbrains.com/plugin/7973-sonarqube-for-ide)
   - [SonarQube Plans & Pricing | Static Code Analysis Tool | Sonar](https://www.sonarsource.com/plans-and-pricing/)
 - **Qodana** Static Analysis & CI/CD:
   - [Qodana: Static Code Analysis Tool by JetBrains](https://www.jetbrains.com/qodana/)
