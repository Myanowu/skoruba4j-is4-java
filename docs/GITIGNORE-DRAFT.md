# Git ignore

The file that Git uses is the repository root [`.gitignore`](../.gitignore). Do not keep a second list here.

It excludes private profiles (`application-local.yml` and any other `application-*.yml` except `application-oss.yml`), keystores (`*.p12`, `*.pfx`), `secrets/`, `dist/`, `data/`, `*.sqlite*`, local config overlays, and `.cursor/`.
