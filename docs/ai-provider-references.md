# AI provider integration references

This client stores no provider credential in this document. It supports one native Gemini adapter and an OpenAI-compatible Chat Completions adapter for the configured HTTPS endpoint.

- [OpenAI models](https://developers.openai.com/api/docs/models/gpt-5.6-luna) — current model availability and model IDs; preset uses `gpt-5.6-luna`.
- [Google Gemini models](https://ai.google.dev/gemini-api/docs/models) — current model IDs; preset uses `gemini-3.8-flash` as the current Flash preset.
- [Google Gemini GenerateContent API reference](https://ai.google.dev/api/generate-content) — `models.generateContent`, request `contents`, and response candidates.
- [Google Gemini API reference](https://ai.google.dev/api) — REST authentication with `x-goog-api-key`.
- [DeepSeek Chat Completions API](https://api-docs.deepseek.com/api/create-chat-completion/) — OpenAI-style chat-completion request and response fields.
- [Kimi API overview](https://www.kimi.ai/help/kimi-api/api-overview) — Kimi API's OpenAI-compatible format.
- [Kimi model list](https://platform.kimi.ai/docs/api/list-models) — current Moonshot endpoint and model discovery.
- [Qwen OpenAI-compatible mode](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope) — DashScope compatibility mode.
- [MiniMax OpenAI-compatible model list](https://platform.minimax.io/docs/api-reference/models/openai/list-models) — current compatible endpoint and models; preset uses `MiniMax-M2.7`.
- [GLM API overview](https://docs.bigmodel.cn/cn/guide/start/model-overview) — GLM model and API guidance.
- [百度智能云千帆 AI 搜索 API](https://cloud.baidu.com/doc/qianfan-api/s/Hmbu8m06u) — `v2/ai_search/chat/completions` request format.

Provider names and default models are only convenience presets. Accounts, enabled models, pricing, service regions, and compatibility can change; users should verify them in the linked provider documentation before saving a configuration.
