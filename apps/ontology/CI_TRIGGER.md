# CI trigger

This file exists to verify the PULSE Ontology GitHub Actions workflow on `main`.

The workflow runs automatically when files under `apps/ontology/` change, and it can also be started manually with `workflow_dispatch`.

Core validation now includes domain adapters, Chrysalis modernization and the SQLite Edge queue.