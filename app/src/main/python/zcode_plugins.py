"""
ZCODE plugin transform backend (batch anti-sepi, 2026-08).

PORTED FROM ZABACODE (GPLv3), same author (muzape28-blip) — see
docs/PLAN_BATCH_ANTI_SEPI.md §2.5. Logika kelas dipertahankan apa adanya
dari zabacode/plugins/implementations.py agar semantik & edge case tetap
identik (battle-tested). Hanya antarmuka entry (run/run_json/CLI) yang baru.

Antarmuka:
    run(plugin_id, code) -> dict  {ok, code, report}
    run_json(plugin_id, code) -> str (JSON — dipakai Chaquopy bridge & CLI)
    CLI: python3 zcode_plugins.py <plugin_id> <input_file>  → JSON ke stdout
"""
import ast
import json
import sys


class DuplicateLineDetector:
    """Detects duplicate significant lines and injects warnings."""

    @staticmethod
    def detect(code: str) -> tuple:
        lines = code.split('\n')
        seen = {}

        for i, line in enumerate(lines, 1):
            cleaned = line.strip()
            # Ignore comments, empty lines, and very short lines
            if not cleaned or cleaned.startswith('#') or len(cleaned) < 5:
                continue
            if cleaned in seen:
                seen[cleaned].append(i)
            else:
                seen[cleaned] = [i]

        duplicates = []
        for content, occurrences in seen.items():
            if len(occurrences) > 1:
                duplicates.append({
                    "content": content,
                    "lines": occurrences
                })

        report = []
        if not duplicates:
            return code, ["No significant duplicate lines found."]

        report.append("📊 [Duplicate Line Detector Report]")
        report_lines = []
        for dup in duplicates:
            occ_str = ", ".join(f"Line {ln}" for ln in dup["lines"])
            report.append(f"- '{dup['content']}' duplicated on: {occ_str}")
            report_lines.append(
                f"# WARNING: Duplicate line '{dup['content']}' found on: {occ_str}")

        if report_lines:
            updated_code = "\n".join(report_lines) + "\n\n" + code
        else:
            updated_code = code

        return updated_code, report


class SmartCommentGenerator:
    """Generates Python function docstrings dynamically based on parameters and returns."""

    @staticmethod
    def generate(code: str) -> tuple:
        report = []
        current = code
        # Process one function at a time, re-parsing after each insertion so
        # line numbers stay accurate. Inserting at a function shifts every
        # line below it, which corrupts offsets captured from a single
        # up-front parse.
        while True:
            try:
                tree = ast.parse(current)
            except SyntaxError as e:
                return current, [f"SyntaxError: {str(e)}"] + report

            target = None
            for node in ast.walk(tree):
                if isinstance(node, ast.FunctionDef) and ast.get_docstring(node) is None:
                    target = node
                    break
            if target is None:
                break

            lines = current.split('\n')
            body_start_line_idx = target.body[0].lineno - 1
            first_body_line = lines[body_start_line_idx]
            indent = len(first_body_line) - len(first_body_line.lstrip())
            indent_str = " " * indent

            params = [arg.arg for arg in target.args.args if arg.arg != 'self']
            param_lines = [f"{indent_str}    {p}: Type description." for p in params]
            param_block = f"\n{indent_str}Args:\n" + "\n".join(param_lines) if params else ""

            doc = (f'{indent_str}"""Docstring for {target.name}.'
                   f'\n{param_block}\n{indent_str}Returns:'
                   f'\n{indent_str}    Type: Description.\n{indent_str}"""')

            lines.insert(body_start_line_idx, doc)
            report.append(f"Generated docstring for function '{target.name}' at line {target.lineno}")
            current = '\n'.join(lines)

        if not report:
            return current, ["No functions missing docstrings found."]
        return current, report


class VariableTypeHintGenerator:
    """Adds type hint annotations to Python functions, importing typing package if necessary."""

    @staticmethod
    def infer_type_by_default(default_node) -> str:
        if isinstance(default_node, ast.Constant):
            val = default_node.value
            if isinstance(val, bool):
                return "bool"
            if isinstance(val, int):
                return "int"
            if isinstance(val, float):
                return "float"
            if isinstance(val, str):
                return "str"
            if val is None:
                return "Optional[Any]"
        elif isinstance(default_node, ast.List):
            return "list"
        elif isinstance(default_node, ast.Dict):
            return "dict"
        return "Any"

    @staticmethod
    def generate(code: str) -> tuple:
        report = []
        current = code
        has_any_imports = "from typing import Any" in code or "import typing" in code

        # Process one function per pass, re-parsing so line offsets stay valid
        # after each edit, and never overwrite an existing return annotation.
        skipped = set()
        while True:
            try:
                tree = ast.parse(current)
            except SyntaxError as e:
                return current, [f"SyntaxError: {str(e)}"] + report

            target = None
            for node in ast.walk(tree):
                if isinstance(node, ast.FunctionDef) and node.name not in skipped:
                    target = node
                    break
            if target is None:
                break

            args = target.args.args
            defaults = target.args.defaults

            defaults_map = {}
            for idx, default in enumerate(reversed(defaults)):
                arg_idx = len(args) - 1 - idx
                if arg_idx >= 0:
                    defaults_map[args[arg_idx].arg] = default

            needs_annotation = any(
                arg.arg != 'self' and arg.annotation is None for arg in args
            )
            if not needs_annotation and target.returns is not None:
                skipped.add(target.name)
                continue

            lines = current.split('\n')
            sig_start_idx = target.lineno - 1
            sig_end_idx = target.body[0].lineno - 1

            def_line = lines[sig_start_idx]
            indent = len(def_line) - len(def_line.lstrip())
            indent_str = " " * indent

            arg_strs = []
            for arg in args:
                if arg.arg == 'self':
                    arg_strs.append('self')
                    continue
                arg_repr = arg.arg
                if arg.annotation is None:
                    inferred = "Any"
                    if arg.arg in defaults_map:
                        inferred = VariableTypeHintGenerator.infer_type_by_default(
                            defaults_map[arg.arg])
                    arg_repr += f": {inferred}"

                if arg.arg in defaults_map:
                    default_node = defaults_map[arg.arg]
                    if isinstance(default_node, ast.Constant):
                        val_repr = repr(default_node.value)
                    elif isinstance(default_node, ast.List):
                        val_repr = "[]"
                    elif isinstance(default_node, ast.Dict):
                        val_repr = "{}"
                    else:
                        val_repr = "None"
                    arg_repr += f" = {val_repr}"
                arg_strs.append(arg_repr)

            # Preserve an existing return annotation instead of forcing -> Any:
            if target.returns is None:
                return_part = " -> Any:"
            else:
                return_part = ":"

            new_sig = f"{indent_str}def {target.name}({', '.join(arg_strs)}){return_part}"

            del lines[sig_start_idx:sig_end_idx]
            lines.insert(sig_start_idx, new_sig)
            report.append(f"Injected variable type hints into function '{target.name}' signature.")
            current = '\n'.join(lines)
            skipped.add(target.name)

        if report and not has_any_imports:
            current = "from typing import Any, Optional\n" + current

        if not report:
            return current, ["No functions found to add type hints."]
        return current, report


# ---------------------------------------------------------------------------
# Antarmuka ZCODE (baru — bukan bagian port)
# ---------------------------------------------------------------------------

_RUNNERS = {
    "docstring_generator": SmartCommentGenerator.generate,
    "type_hint_generator": VariableTypeHintGenerator.generate,
    "duplicate_line_detector": DuplicateLineDetector.detect,
}


def run(plugin_id: str, code: str) -> dict:
    """Eksekusi satu transform. Return {ok, code, report} — tidak pernah raise."""
    runner = _RUNNERS.get(plugin_id)
    if runner is None:
        return {"ok": False, "code": code,
                "report": f"Plugin '{plugin_id}' tidak dikenal backend."}
    try:
        new_code, report = runner(code)
        return {"ok": True, "code": new_code, "report": "\n".join(report)}
    except Exception as e:  # noqa: BLE001 — plugin tidak boleh crash-kan app
        return {"ok": False, "code": code, "report": f"Error: {e}"}


def run_json(plugin_id: str, code: str) -> str:
    """JSON string — satu format parse untuk Chaquopy bridge & subprocess."""
    return json.dumps(run(plugin_id, code), ensure_ascii=False)


if __name__ == "__main__":
    # Backend desktop/dev: python3 zcode_plugins.py <plugin_id> <input_file>
    if len(sys.argv) != 3:
        print(json.dumps({"ok": False, "code": "",
                          "report": "usage: zcode_plugins.py <plugin_id> <file>"}))
        sys.exit(2)
    with open(sys.argv[2], "r", encoding="utf-8", errors="replace") as f:
        _code = f.read()
    print(run_json(sys.argv[1], _code))
