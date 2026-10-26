#!/usr/bin/env python3
"""
Monolithic Project Exporter
============================
Consolidates the entire project file tree, directory structure, metadata, 
and all source/documentation/configuration files (.md, .json, .java, .gradle, etc.) 
into a single, beautifully organized, self-contained monolithic Markdown file (.md).

Designed to run inside 'Monolithic Exports' and never mutate or clutter the rest of the project.
"""

import os
import sys
import argparse
import datetime
import mimetypes
from pathlib import Path

# Common directories to exclude from the monolithic export
DEFAULT_IGNORE_DIRS = {
    ".git",
    ".gradle",
    "build",
    ".idea",
    ".vscode",
    "bin",
    "run",
    "logs",
    "out",
    "target",
    "node_modules",
    "__pycache__",
    ".settings",
    "Monolithic Exports",  # Prevent self-recursion
}

# Known binary file extensions to avoid dumping raw bytes
BINARY_EXTENSIONS = {
    ".jar", ".zip", ".tar", ".gz", ".7z", ".rar",
    ".class", ".pyc", ".pyo", ".exe", ".dll", ".so", ".dylib",
    ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico", ".bmp", ".tiff",
    ".mp3", ".ogg", ".wav", ".mp4", ".mov", ".avi",
    ".pdf", ".doc", ".docx", ".xls", ".xlsx",
    ".db", ".sqlite", ".bin", ".dat", ".lock"
}

# Language mapping for Markdown syntax highlighting
LANGUAGE_MAP = {
    ".java": "java",
    ".json": "json",
    ".md": "markdown",
    ".markdown": "markdown",
    ".gradle": "groovy",
    ".groovy": "groovy",
    ".properties": "properties",
    ".xml": "xml",
    ".yml": "yaml",
    ".yaml": "yaml",
    ".toml": "toml",
    ".txt": "text",
    ".bat": "batch",
    ".cmd": "batch",
    ".ps1": "powershell",
    ".sh": "bash",
    ".bash": "bash",
    ".zsh": "bash",
    ".py": "python",
    ".js": "javascript",
    ".mjs": "javascript",
    ".cjs": "javascript",
    ".ts": "typescript",
    ".html": "html",
    ".htm": "html",
    ".css": "css",
    ".scss": "scss",
    ".sql": "sql",
    ".ini": "ini",
    ".cfg": "ini",
    ".conf": "ini",
    ".gitignore": "gitignore",
    ".gitattributes": "gitattributes",
    ".editorconfig": "ini",
}


def parse_gitignore(root_dir: Path):
    """Load ignore patterns from project root .gitignore if present."""
    gitignore_path = root_dir / ".gitignore"
    patterns = set()
    if gitignore_path.exists():
        try:
            with open(gitignore_path, "r", encoding="utf-8", errors="ignore") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#"):
                        # Normalize path syntax
                        p = line.rstrip("/\\")
                        if p.startswith("/"):
                            p = p[1:]
                        patterns.add(p)
        except Exception:
            pass
    return patterns


def is_binary_file(file_path: Path) -> bool:
    """Check if a file is binary based on extension or sniffing bytes."""
    ext = file_path.suffix.lower()
    if ext in BINARY_EXTENSIONS:
        return True
    
    # Fast byte sniff for null bytes in initial 4KB
    try:
        with open(file_path, "rb") as f:
            chunk = f.read(4096)
            if b"\x00" in chunk:
                return True
    except Exception:
        return True
    return False


def get_safe_markdown_fence(content: str) -> str:
    """
    Determine safe backtick fencing so internal backticks (e.g. in markdown files)
    do not break out of code blocks.
    """
    max_run = 0
    current_run = 0
    for char in content:
        if char == '`':
            current_run += 1
            if current_run > max_run:
                max_run = current_run
        else:
            current_run = 0
    
    fence_len = max(3, max_run + 1)
    return "`" * fence_len


def format_bytes(size: int) -> str:
    """Human readable file size format."""
    for unit in ["B", "KB", "MB", "GB"]:
        if size < 1024.0:
            return f"{size:,.1f} {unit}" if unit != "B" else f"{size} B"
        size /= 1024.0
    return f"{size:,.1f} TB"


def build_file_tree(files_rel_paths: list[str]) -> str:
    """Generate an ASCII tree graph of the project structure with anchor references."""
    tree = {}
    for path_str in files_rel_paths:
        parts = path_str.replace("\\", "/").split("/")
        curr = tree
        for part in parts:
            if part not in curr:
                curr[part] = {}
            curr = curr[part]

    lines = []

    def walk(d: dict, prefix: str = "", current_path: str = ""):
        items = sorted(d.keys(), key=lambda s: (len(d[s]) == 0, s.lower()))
        count = len(items)
        for i, key in enumerate(items):
            is_last = (i == count - 1)
            connector = "└── " if is_last else "├── "
            new_path = f"{current_path}/{key}" if current_path else key
            is_file = len(d[key]) == 0
            
            if is_file:
                anchor = make_anchor(new_path)
                lines.append(f"{prefix}{connector}[{key}](#{anchor})")
            else:
                lines.append(f"{prefix}{connector}📁 **{key}/**")
                extension = "    " if is_last else "│   "
                walk(d[key], prefix + extension, new_path)

    walk(tree)
    return "\n".join(lines)


def make_anchor(file_rel_path: str) -> str:
    """Make GitHub/standard Markdown compatible anchor slug."""
    clean = file_rel_path.replace("\\", "/").lower()
    # Replace non-alphanumeric (except dashes and underscores) with hyphen
    slug = "".join(c if (c.isalnum() or c in "-_") else "-" for c in clean)
    slug = "-".join(filter(None, slug.split("-")))
    return f"file-{slug}"


def scan_project(root_dir: Path, custom_ignore_dirs: set[str], max_size_mb: float):
    """Recursively scan root directory and collect all eligible files."""
    collected = []
    skipped_large = []
    
    for dirpath, dirnames, filenames in os.walk(root_dir):
        dp = Path(dirpath)
        
        # Prune ignored directories in-place
        dirnames[:] = [
            d for d in dirnames
            if d not in custom_ignore_dirs
            and not d.startswith(".")  # Exclude hidden directories by default (.git, .gradle, .idea, etc.)
        ]
        
        for fname in filenames:
            file_path = dp / fname
            rel_path = file_path.relative_to(root_dir)
            rel_str = str(rel_path).replace("\\", "/")
            
            # Check file size limit
            try:
                sz = file_path.stat().st_size
                if sz > max_size_mb * 1024 * 1024:
                    skipped_large.append((rel_str, sz))
                    continue
                collected.append((rel_str, file_path, sz))
            except Exception:
                continue
                
    collected.sort(key=lambda x: x[0].lower())
    return collected, skipped_large


def generate_monolith(
    root_dir: Path,
    output_path: Path,
    max_size_mb: float = 10.0,
    include_binary_summary: bool = True
):
    """Collects and writes all files into a structured monolithic markdown file."""
    start_time = datetime.datetime.now()
    gitignore_rules = parse_gitignore(root_dir)
    ignore_dirs = DEFAULT_IGNORE_DIRS | gitignore_rules

    print(f"[*] Scanning project directory: {root_dir}")
    files, skipped_large = scan_project(root_dir, ignore_dirs, max_size_mb)
    print(f"[+] Found {len(files)} files to process.")

    total_files = len(files)
    total_bytes = sum(sz for _, _, sz in files)
    total_lines = 0
    extension_counts = {}

    rel_paths = [r for r, _, _ in files]
    file_tree_md = build_file_tree(rel_paths)

    output_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"[*] Compiling monolithic file to: {output_path}")

    # First pass: collect metadata, line counts, and file contents
    processed_files = []
    for rel_str, fpath, fsize in files:
        ext = fpath.suffix.lower()
        ext_label = ext if ext else "[none]"
        is_bin = is_binary_file(fpath)
        lang = LANGUAGE_MAP.get(ext, "")
        
        try:
            mtime = datetime.datetime.fromtimestamp(fpath.stat().st_mtime).strftime('%Y-%m-%d %H:%M:%S')
        except Exception:
            mtime = "Unknown"

        lines_count = 0
        file_content = None
        read_error = None

        if is_bin:
            extension_counts[ext_label] = extension_counts.get(ext_label, {"count": 0, "size": 0, "lines": 0})
            extension_counts[ext_label]["count"] += 1
            extension_counts[ext_label]["size"] += fsize
        else:
            try:
                with open(fpath, "r", encoding="utf-8", errors="replace") as f:
                    file_content = f.read()
                lines_count = file_content.count("\n") + (1 if file_content and not file_content.endswith("\n") else 0)
                total_lines += lines_count
            except Exception as e:
                read_error = str(e)
            
            extension_counts[ext_label] = extension_counts.get(ext_label, {"count": 0, "size": 0, "lines": 0})
            extension_counts[ext_label]["count"] += 1
            extension_counts[ext_label]["size"] += fsize
            extension_counts[ext_label]["lines"] += lines_count

        processed_files.append({
            "rel_str": rel_str,
            "fpath": fpath,
            "fsize": fsize,
            "ext": ext,
            "ext_label": ext_label,
            "is_bin": is_bin,
            "lang": lang,
            "mtime": mtime,
            "lines": lines_count,
            "content": file_content,
            "error": read_error,
            "anchor": make_anchor(rel_str),
        })

    with open(output_path, "w", encoding="utf-8") as out:
        # Document Header
        out.write("# 📦 Monolithic Project Export\n\n")
        out.write("> **Notice**: This is an automatically generated, unified snapshot containing the full directory structure, metadata, and all source/resource files of the project in a single document for comprehensive review, search, and archive.\n\n")
        
        # Metadata Card
        out.write("## ℹ️ Export Overview\n\n")
        out.write("| Property | Details |\n")
        out.write("| :--- | :--- |\n")
        out.write(f"| **Project Root** | `{root_dir.name}` (`{root_dir.resolve()}`) |\n")
        out.write(f"| **Generated On** | {start_time.strftime('%Y-%m-%d %H:%M:%S')} |\n")
        out.write(f"| **Total Files Included** | **{total_files:,}** files |\n")
        out.write(f"| **Total Content Lines** | **{total_lines:,}** lines |\n")
        out.write(f"| **Total Uncompressed Size** | **{format_bytes(total_bytes)}** |\n")
        out.write(f"| **Target Destination** | `{output_path.name}` |\n\n")

        # Table of Contents
        out.write("<a id=\"table-of-contents\"></a>\n")
        out.write("## 📑 Table of Contents\n\n")
        out.write("1. [Interactive File Tree](#file-tree)\n")
        out.write("2. [File Inventory & Extension Stats](#file-inventory)\n")
        out.write("3. [Project File Contents](#file-contents)\n\n")
        out.write("---\n\n")

        # Visual File Tree Section
        out.write("<a id=\"file-tree\"></a>\n")
        out.write("## 🌳 File Structure Tree\n\n")
        out.write("Click on any file node below to navigate directly to its full contents:\n\n")
        out.write(f"```text\n📁 {root_dir.name}/\n```\n")
        out.write(f"{file_tree_md}\n\n")
        out.write("[⬆ Back to Table of Contents](#table-of-contents)\n\n")
        out.write("---\n\n")

        # File Inventory & Extension Stats
        out.write("<a id=\"file-inventory\"></a>\n")
        out.write("## 📊 File Inventory & Extension Statistics\n\n")
        out.write("### Extension Breakdown\n\n")
        out.write("| Extension | Files | Lines | Total Size |\n")
        out.write("| :--- | :---: | :---: | :---: |\n")
        for ext_name, stats in sorted(extension_counts.items(), key=lambda kv: kv[1]["size"], reverse=True):
            out.write(f"| `{ext_name}` | {stats['count']:,} | {stats['lines']:,} | {format_bytes(stats['size'])} |\n")
        out.write("\n")

        out.write("### Quick File Index\n\n")
        out.write("| # | File Path | Size | Lines | Type |\n")
        out.write("| :---: | :--- | :---: | :---: | :--- |\n")
        for idx, item in enumerate(processed_files, 1):
            line_str = f"{item['lines']:,}" if not item["is_bin"] else "—"
            type_str = f"`{item['lang']}`" if item['lang'] else ("*binary*" if item["is_bin"] else "plain text")
            out.write(f"| {idx} | [{item['rel_str']}](#{item['anchor']}) | {format_bytes(item['fsize'])} | {line_str} | {type_str} |\n")
        out.write("\n")
        out.write("[⬆ Back to Table of Contents](#table-of-contents) &nbsp;|&nbsp; [🌳 File Tree](#file-tree)\n\n")
        out.write("---\n\n")

        # Monolithic File Contents
        out.write("<a id=\"file-contents\"></a>\n")
        out.write("## 📂 Monolithic File Contents\n\n")

        for idx, item in enumerate(processed_files, 1):
            rel_str = item["rel_str"]
            anchor = item["anchor"]
            fsize = item["fsize"]
            mtime = item["mtime"]
            is_bin = item["is_bin"]
            lang = item["lang"]
            lines_in_file = item["lines"]
            content = item["content"]
            error = item["error"]

            # Section Anchor and Title
            out.write(f"<a id=\"{anchor}\"></a>\n")
            out.write(f"### File {idx}/{total_files}: `{rel_str}`\n\n")
            
            # Badge summary table
            out.write(f"- **Relative Path**: `{rel_str}`\n")
            out.write(f"- **File Size**: {format_bytes(fsize)}\n")
            out.write(f"- **Last Modified**: {mtime}\n")

            if is_bin:
                out.write(f"- **Type**: ⚠️ Binary / Non-text file\n\n")
                out.write("> *[Binary data omitted for readability. File exists on disk at original path.]*\n\n")
            elif error:
                out.write(f"> ❌ **Error reading file**: `{error}`\n\n")
            else:
                out.write(f"- **Line Count**: {lines_in_file:,} lines\n")
                out.write(f"- **Syntax Mode**: `{lang if lang else 'plain text'}`\n\n")
                
                # Safe code block wrapping
                fence = get_safe_markdown_fence(content)
                out.write(f"{fence}{lang}\n")
                out.write(content)
                if not content.endswith("\n"):
                    out.write("\n")
                out.write(f"{fence}\n\n")

            out.write(f"[⬆ Back to Top](#table-of-contents) &nbsp;|&nbsp; [📊 File Index](#file-inventory) &nbsp;|&nbsp; [🌳 File Tree](#file-tree)\n\n")
            out.write("---\n\n")

        # Extension stats note at the end
        if skipped_large:
            out.write("### ⚠️ Skipped Large Files\n\n")
            for sf, ssz in skipped_large:
                out.write(f"- `{sf}` ({format_bytes(ssz)})\n")
            out.write("\n")

    end_time = datetime.datetime.now()
    duration = (end_time - start_time).total_seconds()
    
    monolith_size = output_path.stat().st_size
    print(f"\n=======================================================")
    print(f" [SUCCESS] Monolithic Export Complete!")
    print(f"=======================================================")
    print(f" - Output File:     {output_path.resolve()}")
    print(f" - Size of Monolith: {format_bytes(monolith_size)}")
    print(f" - Files Ingested:  {total_files}")
    print(f" - Total Lines:     {total_lines:,}")
    print(f" - Time Elapsed:    {duration:.2f}s")
    print(f"=======================================================\n")


def main():
    parser = argparse.ArgumentParser(
        description="Generate a single unified Monolithic Markdown file containing the entire project."
    )
    # Default root is parent directory of this script's directory ("Monolithic Exports" -> Project Root)
    script_dir = Path(__file__).resolve().parent
    default_project_root = script_dir.parent

    parser.add_argument(
        "--root",
        type=Path,
        default=default_project_root,
        help="Root directory of the project to export (default: project root containing 'Monolithic Exports')"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=script_dir / "PROJECT_MONOLITH.md",
        help="Target output markdown file path (default: 'Monolithic Exports/PROJECT_MONOLITH.md')"
    )
    parser.add_argument(
        "--timestamped",
        action="store_true",
        help="Also create a timestamped copy (e.g. PROJECT_MONOLITH_2026-10-25_1005.md)"
    )
    parser.add_argument(
        "--max-size-mb",
        type=float,
        default=15.0,
        help="Max file size in MB to include in text dump (default: 15 MB)"
    )

    args = parser.parse_args()

    target_output = args.output
    if args.timestamped:
        ts = datetime.datetime.now().strftime("%Y-%m-%d_%H%M%S")
        target_output = args.output.parent / f"PROJECT_MONOLITH_{ts}.md"

    generate_monolith(
        root_dir=args.root,
        output_path=target_output,
        max_size_mb=args.max_size_mb
    )


if __name__ == "__main__":
    main()
