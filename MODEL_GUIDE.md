# OpenAI Model Guide

This plugin can use several different OpenAI models for moderation.
Below is a quick overview to help you decide which one fits your server best.

- **omni-moderation-latest** – default moderation endpoint. Fast and inexpensive.
- **gpt-4.1-mini** – lightweight chat model for extra nuance at a lower cost.
- **gpt-4.1** – more capable chat model offering the highest accuracy.
- **o3** – reasoning model with no token limit, suited to complex or long messages.
- **o4-mini** – faster reasoning model that still handles nuanced cases well.

When using `o3` or `o4-mini` you can raise `thinking-effort` in `config.yml`
to improve accuracy.
