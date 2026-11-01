# Contributing to edge0

Thanks for your interest in contributing! edge0 is an open-source streaming MoE
inference framework, and contributions of all kinds are welcome — bug reports,
performance measurements, documentation fixes, new model support, and new
platform/backend work.

This is a multi-platform monorepo. Please read the section for the part you're
touching.

## Ways to contribute

- **Report a bug / performance issue / request a feature or model** — open an
  issue using one of the [issue forms](.github/ISSUE_TEMPLATE/). The forms ask
  for the platform, chip, memory, and (for the Python runtime) the `mlx`
  version — these fields resolve the large majority of reports, so please fill
  them in.
- **Ask a question or share results** — please use
  [GitHub Discussions](https://github.com/Edge0-AI/Edge0/discussions) rather
  than an issue.
- **Send a pull request** — see the workflow below.

## Repository layout

| Path | What | Stack |
|---|---|---|
| [`python/`](python/) | Reference framework + CLI + OpenAI-compatible server | Python + MLX (Apple Silicon) |
| [`macos/`](macos/) | Desktop app / CLI / daemon | Rust |
| [`ios/`](ios/) | iPhone on-device app | Swift + MLX Swift |
| [`android/`](android/) | On-device app + native engine | Kotlin + native |
| [`windows/`](windows/) | Desktop app + native engine | C++ + Vulkan |
| [`docs/`](docs/) | Architecture & design docs | — |

Each platform directory has its own README with build and test instructions.
The sections below focus on the **Python framework**, which is the easiest place
to start.

## Development setup (Python framework)

Requires macOS on Apple Silicon (the MLX backend has no wheels on other
platforms yet — a CUDA backend slot is reserved under
`python/src/edge0/backends/cuda/`).

```bash
cd python
python3.12 -m venv .venv
.venv/bin/pip install -e '.[dev,fetch]'
```

> **Do not bump `mlx` past the pinned `0.30.6`.** `0.30.4` and older mis-gate
> the NAX matmul kernels on Apple A18 / A18 Pro (silently wrong numbers →
> garbled output, [#8](https://github.com/Edge0-AI/Edge0/issues/8)), and
> `mlx>=0.31.2` breaks the streaming pool threads. `mlx-lm` stays `0.31.0`.
> See the note in [`python/pyproject.toml`](python/pyproject.toml). Only touch
> these pins in a PR that is specifically about the upgrade.

## Running the tests

```bash
cd python
pytest -m 'not slow'              # unit tests (no real weights) — what CI runs
pytest tests/test_repo_hygiene.py # repo hygiene (pure Python, runs anywhere)

# Real-weight tests (need a downloaded checkpoint; skipped if absent):
EDGE0_8B_MODEL=/path/to/edge0-8b pytest -m slow -q
.venv/bin/python scripts/e2e_smoke.py \
  --qwen-dir /path/to/edge0-35b --ling-dir /path/to/edge0-8b
```

## What CI checks (and how to pass it)

Every PR runs two jobs ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)):

1. **Unit tests (macOS + MLX)** — `pytest -m 'not slow'`.
2. **Repo hygiene (Linux, pure Python)** — `tests/test_repo_hygiene.py`, which
   enforces the "no dependency on the author's machine" rules across the
   *entire* repo:
   - **No hardcoded local paths** — nothing that points at a specific machine's
     filesystem (absolute home directories, network mounts, cluster node names,
     etc.). Use env vars, fixtures, or relative paths.
   - **Backend boundary** — `import mlx` / `from mlx ...` may appear **only**
     under `python/src/edge0/backends/mlx/`. Framework code must go through the
     `edge0.backends` facade (`core` / `nn` / `io` / `quant`). This keeps the
     core backend-agnostic so other backends can plug in.
   - **No secrets** — no API keys or private keys in the tree.
   - **Docs links resolve** — e.g. the README `NOTICE` link must point to a real
     file.

You can run both locally before pushing (see above).

## Adding a new model

edge0 is designed to take new MoE checkpoints through a documented path. Start
with [`docs/adding-a-model.md`](docs/adding-a-model.md): implement the
three-part adapter contract (`Config` / `build_model` / `build_engine`),
register it, and map the checkpoint's `model_type` via `TYPE_ALIASES`. Please
include a short note on the architecture (layers / experts / routing width /
attention type) in your PR.

## Pull request workflow

1. **Fork** the repo and create a feature branch off `main`
   (e.g. `fix/streaming-oob`, `docs/clarify-memory`, `feat/tool-calling`).
2. Make your change; **add or update tests** for new behavior.
3. Run `pytest -m 'not slow'` and `pytest tests/test_repo_hygiene.py` locally.
4. Open a PR against `Edge0-AI/Edge0:main` and fill in the
   [PR template](.github/PULL_REQUEST_TEMPLATE.md). Link the issue it closes
   (`Fixes #NN`).
5. Keep PRs focused — one logical change per PR is easier to review and merge.

### Commit / PR style

This repo uses conventional-commit-style prefixes, e.g.
`fix(streaming): ...`, `feat(server): ...`, `docs(readme): ...`,
`perf(streaming): ...`, `chore: ...`. Please follow the same convention.

## Reporting security issues

Do not open a public issue for security problems. Email **samuel@edge0.ai**
instead.

## License

By contributing, you agree that your contributions will be licensed under the
project's [Apache-2.0](LICENSE) license.
