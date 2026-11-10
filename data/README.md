# data/

`data/generated/interactions.tsv` is the interaction and medicine-name file bundled in the
Android, Wear OS and web apps. It is **not in this repository**: it is derived from third-party
sources whose licenses don't allow (or don't clearly allow) redistribution. See [NOTICE.md](../NOTICE.md).

To build it:

1. Download the sources into `tools/raw/` (the URLs are listed at the top of
   `tools/build_interactions.py`).
2. Run:

```bash
python -I tools/build_interactions.py tools/raw data/generated/interactions.tsv
```

The apps won't compile without this file (Android/Wear read it from `assets/`, the web serves it
next to `index.html`).
