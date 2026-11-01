<!-- Thanks for contributing to edge0! Keep this short but complete. -->

## What & why

<!-- What does this PR change, and what problem does it solve? Link issues: "Fixes #NN". -->

Fixes #

## Area

- [ ] Python framework (`python/`)
- [ ] macOS (`macos/`)
- [ ] iOS (`ios/`)
- [ ] Android (`android/`)
- [ ] Windows (`windows/`)
- [ ] Docs / other

## How was it tested?

<!-- Commands you ran, hardware/chip, model tier. Paste bench.py numbers for perf changes. -->

## Checklist

- [ ] `pytest -m 'not slow'` passes locally (Python changes; CI runs this on macOS + MLX)
- [ ] Repo hygiene passes: `pytest tests/test_repo_hygiene.py` (no hardcoded paths, no secrets, **no MLX imports outside `backends/mlx/`**)
- [ ] Added/updated tests for new behavior
- [ ] Did **not** bump `mlx` past the pinned `0.30.6` (see the note in `python/pyproject.toml` / issue #8) unless this PR is specifically about that
- [ ] Updated relevant docs / READMEs (incl. translations if user-facing)
