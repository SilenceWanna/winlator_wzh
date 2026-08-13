# Repository Working Rules

## Development logs

- After each exported runtime, input, frame-rating, test, or build log is analyzed, archive it in the same development checkpoint.
- Run `scripts/archive-development-logs.ps1` from the repository root. The script must finish without credential or file-size warnings before committing.
- Preserve previous evidence. Do not replace uniquely named historical samples; use a new snapshot ID and commit the generated manifest.
- Update the relevant document in `docs/`, then commit and push the log archive and document together to `origin/main` at the next key checkpoint.

## Game files

- Do not commit complete commercial game files, modified game packages, or game archives to this repository.
- Keep game content under the workspace-level `games/` directory. Record only non-distributable inventory metadata, checksums, configuration, and test results needed for research reproducibility.
