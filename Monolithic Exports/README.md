# Monolithic Exports

This folder contains a self-contained exporter utility that aggregates the entire project into a single, unified Markdown (`.md`) file for comprehensive review, LLM context loading, auditing, and offline archiving.

---

## 🚀 Features

- **Whole-Project Aggregation**: Scans all directories across the workspace (.java, .json, .md, .gradle, .properties, .xml, etc.).
- **Interactive File Structure Tree**: Creates a visual tree graph with clickable anchor links navigating directly to each file's section.
- **Smart Formatting & Syntax Highlighting**: Auto-detects language formatting for code fences (Java, JSON, Markdown, Groovy, etc.).
- **Safe Markdown-in-Markdown Escaping**: Dynamically computes backtick fence lengths so embedded Markdown docs never break formatting.
- **Project Isolation**: Ignores `.git`, `.gradle`, `build/`, `.idea/`, `.vscode/`, binaries, and `Monolithic Exports` itself so running the tool never pollutes or alters project code.
- **Anchor Navigation**: Includes quick links back to the Table of Contents and Tree from every file block.

---

## 🛠️ Usage

### Quick Run (Windows Explorer)
Double-click:
```
run_export.bat
```

### Command Prompt or VS Code Terminal
```cmd
cd "Monolithic Exports"
run_export.bat
```

### PowerShell
```powershell
cd "Monolithic Exports"
.\run_export.ps1
```

### Python CLI directly
```bash
python export_monolith.py
```

#### Optional CLI Arguments:
- `--timestamped`: Creates a timestamped version (e.g. `PROJECT_MONOLITH_2026-10-25_1006.md`) instead of overwriting `PROJECT_MONOLITH.md`.
- `--output path/to/file.md`: Custom destination path.
- `--max-size-mb <MB>`: Maximum file size to dump in full text (defaults to 15 MB).
