# Running the AI Agent with a local LLM

NEXO Fabric's agent is provider-agnostic. Switch between Anthropic, OpenAI,
a local Ollama, or a keyword-based fallback entirely via `.env` — no code
changes.

This guide focuses on **Ollama**, which gives you a private, cost-free AI
agent on your own hardware.

## Which model to pick

The agent relies on **tool-calling** (function calling). Not every model
supports this natively. These do:

| Model | Size (Q4) | VRAM needed | Tool calling | Notes |
|-------|:---:|:---:|:---:|---|
| **`qwen2.5:7b`** | 5 GB | ~6 GB | ✅ native | Recommended — strong multilingual (incl. German) |
| `qwen2.5:14b` | 9 GB | ~10 GB | ✅ native | Better reasoning, still fits on 12 GB GPUs |
| `llama3.1:8b` | 5 GB | ~6 GB | ✅ native | Solid general-purpose |
| `llama3.2:3b` | 2 GB | ~3 GB | ✅ native | Fast, but hallucinates tool args |
| `hermes3:8b` | 5 GB | ~6 GB | ✅ fine-tuned | Specifically trained for tool use |
| `gemma2:9b` | 6 GB | ~7 GB | ❌ | No structured function calling |

## Install Ollama (Windows)

```powershell
irm https://ollama.com/install.ps1 | iex
```

Verify:
```bash
ollama --version
curl http://localhost:11434/api/version
```

Both should report version `0.21.x` or newer. Older versions have weaker
CUDA support and will silently fall back to CPU.

### Windows + WSL gotcha

If you previously installed Ollama inside a WSL distribution (for example
via `apt` in Ubuntu), that instance will keep binding port 11434 and
preventing the Windows-native Ollama from starting. Two fixes:

1. **Stop the WSL instance:**
   ```bash
   wsl --terminate Ubuntu
   ```
2. **Or start the Windows Ollama on a different port:**
   ```powershell
   $env:OLLAMA_HOST="127.0.0.1:11435"
   & "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" serve
   ```
   Then update your `.env` to point `OLLAMA_BASE_URL` at port 11435.

## Pull a model

```bash
ollama pull qwen2.5:7b
```

First pull is ~5 GB. Subsequent pulls are cached.

## Configure the backend

Edit `.env`:

```bash
NEXO_LLM_PROVIDER=ollama

# Docker container reaches the host's Ollama via host.docker.internal on
# Docker Desktop (Windows/macOS). On Linux, use the host's bridge IP or
# run Ollama with OLLAMA_HOST=0.0.0.0:11434 and bind the container network.
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_MODEL=qwen2.5:7b
```

Restart the backend:

```bash
docker compose -f docker/docker-compose.dev.yml --env-file .env up -d --force-recreate backend
```

The backend autowires `OllamaLlmProvider` via `@ConditionalOnProperty`. No
code changes are needed when switching providers.

## Verify

```bash
# First call is slow — Ollama loads the model into VRAM (~30–60 s)
curl -X POST http://localhost:8082/graphql \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"query":"mutation{agentChat(message:\"How many customers?\"){message toolCalls{tool}}}"}'

# Warm calls: 500 ms – 3 s depending on model + query
```

Check GPU usage:

```bash
ollama ps
# NAME           SIZE      PROCESSOR    CONTEXT
# qwen2.5:7b     5.6 GB    100% GPU     4096
```

If `PROCESSOR` says `100% CPU`, CUDA isn't wired up. Re-check that you're
using the Windows-native Ollama and that `nvidia-smi` reports your GPU.

## Switching providers at runtime

`.env` knows four providers:

```bash
NEXO_LLM_PROVIDER=none         # keyword fallback, no LLM calls
NEXO_LLM_PROVIDER=ollama       # local via Ollama
NEXO_LLM_PROVIDER=anthropic    # Claude API
NEXO_LLM_PROVIDER=openai       # GPT API
```

Each has its own set of config keys already in `.env.example`. Changing the
provider requires restarting the backend container:

```bash
docker compose -f docker/docker-compose.dev.yml --env-file .env up -d --force-recreate backend
```

Spring's `@ConditionalOnProperty` instantiates only the selected provider bean,
so unused providers don't start HTTP clients or hold API keys in memory.

## Troubleshooting

**Agent responds without using tools.** The configured model may not support
tool calling. Check the `toolCalls` array in the response; if it's always
empty, switch to one of the models in the table above.

**Agent responses are empty.** The tool-calling loop hit its round limit
(default 5) without returning text. This happens with smaller models that
keep invoking tools. Switch to a larger model, or check the backend logs
for `LLM tool-calling failed` warnings.

**Cold start is > 2 minutes.** Ollama is likely reading the model from disk
and copying it to GPU VRAM. First call after a server restart is always
slow; subsequent calls use the loaded model.

**German works but Swiss-German doesn't.** `llama3.2:3b` and smaller are not
reliable with dialects. `qwen2.5:7b` and up handle "Wieviel Kunde hämmer?"
style questions correctly.

## Testing against the seeded demo

Once Ollama is running and the backend is reconfigured, the AI test script
picks up automatically:

```bash
cd tests/e2e
./seed.sh        # idempotent
./test-ai.sh
```

Compare timings with and without a local LLM — the fallback answers in ~70 ms
but can only handle a handful of keyword patterns. A real LLM answers anything
you ask, in ~500 ms – 3 s with GPU, and uses the agent's tools correctly.
