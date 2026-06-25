/**
 * Warstwa integracji: adaptery implementujące porty przez OpenAI Java SDK.
 * OpenRouterVisionAdapter, OpenRouterDecisionAdapter, PolicyDocumentLoader,
 * PromptTemplateProvider, OpenAiClientConfig.
 * Tylko ta warstwa ma dostęp do OpenAI Java SDK.
 * Zależy od: application (porty) oraz domain.
 */
package pl.nbp.copilot.integration;
