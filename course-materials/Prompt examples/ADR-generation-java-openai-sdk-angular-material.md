/create-adr create ADR documents based on @docs/PRD-Product-Requirements-Document.md

Make research how to implement functionalities from PRD using the below libraries:
https://github.com/openai/openai-java
https://developers.openai.com/api/docs/libraries?language=java

Research also if we should use Responses API or Completions API. Recommend one based on research data.
You should use endpoints from OpenRouter that are specified with example ENV keys in @.env.example
Docs from OpenRouter for Responses API: https://openrouter.ai/docs/api/reference/responses/overview

Use Maven and Spring Boot, research and explain in ADR the best way to initialize this project with them.

Use Angular and Angular Material for FE and Web app. Search for ready to use components to implement chat UI and maybe also streaming from our BE that is ready to use for Angular Material. ADR should also explain how to initialize Angular with them and how to use them together with Spring Boot and OpenAI Java SDK.
