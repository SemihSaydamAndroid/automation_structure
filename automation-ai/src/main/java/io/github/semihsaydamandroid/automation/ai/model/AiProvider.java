package io.github.semihsaydamandroid.automation.ai.model;

/** LLM backends. Open models run on Ollama (laptop) or vLLM (cluster GPU) via their APIs. */
public enum AiProvider {
    /** AI features disabled; rule-based fallbacks only. */
    NONE,
    /** Ollama: Llama, Qwen, Gemma, Mistral... on a laptop or in the cluster. */
    OLLAMA,
    /** Any OpenAI-compatible endpoint: vLLM, llama.cpp server, LM Studio, LocalAI, OpenAI, Azure OpenAI. */
    OPENAI_COMPATIBLE,
    /** Anthropic Claude. */
    ANTHROPIC
}
