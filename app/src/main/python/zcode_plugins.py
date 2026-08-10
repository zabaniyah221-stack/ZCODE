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
class OrganizeImports:
    """Sorts imports according to PEP-8 (stdlib -> third-party -> local) and removes unused ones."""

    STDLIB_MODULES = {
        "os", "sys", "math", "json", "time", "random", "datetime", "urllib", "collections",
        "itertools", "functools", "re", "xml", "csv", "hashlib", "socket", "threading",
        "multiprocessing", "subprocess", "logging", "ast", "typing", "shutil", "tempfile",
        "io", "pathlib", "argparse", "uuid", "base64", "select", "selectors", "asyncio"
    }

    @staticmethod
    def generate(code: str) -> tuple:
        report = []
        try:
            tree = ast.parse(code)
        except Exception as e:
            return code, [f"Gagal parse AST karena kesalahan sintaksis: {str(e)}"]

        # 1. Kumpulkan semua nama yang dipakai di seluruh file (selain statement import)
        used_names = set()
        class NameVisitor(ast.NodeVisitor):
            def visit_Name(self, node):
                used_names.add(node.id)
                self.generic_visit(node)
            def visit_Attribute(self, node):
                if isinstance(node.value, ast.Name):
                    used_names.add(node.value.id)
                used_names.add(node.attr)
                self.generic_visit(node)

        visitor = NameVisitor()
        for node in tree.body:
            if not isinstance(node, (ast.Import, ast.ImportFrom)):
                visitor.visit(node)

        # 2. Cari semua statement import di level paling atas
        imports_stdlib = []
        imports_thirdparty = []
        imports_local = []

        import_nodes = []
        removed_lines_intervals = []

        for node in tree.body:
            if isinstance(node, (ast.Import, ast.ImportFrom)):
                import_nodes.append(node)
                end_line = getattr(node, "end_lineno", node.lineno)
                removed_lines_intervals.append((node.lineno - 1, end_line))

        if not import_nodes:
            return code, ["Tidak ada statement import yang ditemukan."]

        for node in import_nodes:
            used_names_in_node = []
            for name_alias in node.names:
                imported_name = name_alias.asname or name_alias.name.split(".")[0]
                if imported_name in used_names:
                    used_names_in_node.append(name_alias)
                else:
                    report.append(f"Membuang import tidak terpakai: '''{name_alias.name}'''")

            if not used_names_in_node:
                continue

            module_name = ""
            if isinstance(node, ast.Import):
                module_name = node.names[0].name.split(".")[0]
            elif isinstance(node, ast.ImportFrom):
                if node.module:
                    module_name = node.module.split(".")[0]

            is_stdlib = module_name in OrganizeImports.STDLIB_MODULES
            is_local = isinstance(node, ast.ImportFrom) and node.level > 0

            import_str = ""
            if isinstance(node, ast.Import):
                import_str = "import " + ", ".join(f"{n.name} as {n.asname}" if n.asname else n.name for n in used_names_in_node)
            elif isinstance(node, ast.ImportFrom):
                dots = "." * node.level
                import_str = f"from {dots}{node.module or '''''' } import " + ", ".join(f"{n.name} as {n.asname}" if n.asname else n.name for n in used_names_in_node)

            if is_stdlib:
                imports_stdlib.append(import_str)
            elif is_local:
                imports_local.append(import_str)
            else:
                imports_thirdparty.append(import_str)

        imports_stdlib.sort()
        imports_thirdparty.sort()
        imports_local.sort()

        import_blocks = []
        if imports_stdlib:
            import_blocks.append("\n".join(imports_stdlib))
        if imports_thirdparty:
            import_blocks.append("\n".join(imports_thirdparty))
        if imports_local:
            import_blocks.append("\n".join(imports_local))

        new_imports_text = "\n\n".join(import_blocks)

        lines = code.split("\n")
        to_delete = set()
        for start, end in removed_lines_intervals:
            for l in range(start, end):
                to_delete.add(l)

        cleaned_lines = [line for idx, line in enumerate(lines) if idx not in to_delete]

        while cleaned_lines and not cleaned_lines[0].strip():
            cleaned_lines.pop(0)

        new_code = new_imports_text + "\n\n" + "\n".join(cleaned_lines)
        report.append("Imports berhasil diurutkan berdasarkan standar PEP-8.")
        return new_code, report



class OutlineGenerator:
    """Parses Python AST to extract a list of classes and functions/methods."""

    @staticmethod
    def generate(code: str) -> tuple:
        try:
            tree = ast.parse(code)
        except Exception as e:
            return code, [f"Error: {str(e)}"]

        symbols = []
        class OutlineVisitor(ast.NodeVisitor):
            def __init__(self):
                self.current_class = None

            def visit_ClassDef(self, node):
                symbols.append(f"CLASS:{node.name}:{node.lineno}")
                old_class = self.current_class
                self.current_class = node.name
                self.generic_visit(node)
                self.current_class = old_class

            def visit_FunctionDef(self, node):
                prefix = f"METHOD:{self.current_class}." if self.current_class else "FUNC:"
                symbols.append(f"{prefix}{node.name}:{node.lineno}")

            def visit_AsyncFunctionDef(self, node):
                prefix = f"METHOD:{self.current_class}." if self.current_class else "FUNC:"
                symbols.append(f"{prefix}{node.name}:{node.lineno}")

        visitor = OutlineVisitor()
        visitor.visit(tree)
        return code, symbols



class GoToDefinition:
    """Finds the line number of a symbol definition (function, class, or variable) inside the same file."""

    @staticmethod
    def find(code: str, symbol: str) -> tuple:
        symbol = symbol.strip()
        if not symbol:
            return code, ["0"]
        try:
            tree = ast.parse(code)
        except Exception as e:
            return code, ["0"]

        for node in ast.walk(tree):
            if isinstance(node, ast.FunctionDef) and node.name == symbol:
                return code, [str(node.lineno)]
            if isinstance(node, ast.ClassDef) and node.name == symbol:
                return code, [str(node.lineno)]
            if isinstance(node, ast.AsyncFunctionDef) and node.name == symbol:
                return code, [str(node.lineno)]
            if isinstance(node, ast.Assign):
                for target in node.targets:
                    if isinstance(target, ast.Name) and target.id == symbol:
                        return code, [str(node.lineno)]
        return code, ["0"]


class RenameSymbol:
    """Renames all occurrences of a symbol inside the same file safely."""

    @staticmethod
    def rename(code: str, params: str) -> tuple:
        parts = params.split(":")
        if len(parts) != 2:
            return code, ["Format parameter salah."]
        old = parts[0].strip()
        new = parts[1].strip()

        if not old or not new:
            return code, ["Nama simbol kosong."]

        import re
        pattern = re.compile(r'\b' + re.escape(old) + r'\b')
        lines = code.split('\n')
        new_lines = []
        for line in lines:
            if "#" in line:
                code_part, comment_part = line.split("#", 1)
                code_part = pattern.sub(new, code_part)
                new_lines.append(code_part + "#" + comment_part)
            else:
                new_lines.append(pattern.sub(new, line))

        final_code = '\n'.join(new_lines)
        return final_code, [f"Simbol '{old}' berhasil diganti menjadi '{new}'."]



# Antarmuka ZCODE (baru — bukan bagian port)
# ---------------------------------------------------------------------------

_RUNNERS = {
    "docstring_generator": SmartCommentGenerator.generate,
    "type_hint_generator": VariableTypeHintGenerator.generate,
    "duplicate_line_detector": DuplicateLineDetector.detect,
    "organize_imports": OrganizeImports.generate,
    "outline_generator": OutlineGenerator.generate,
}

# Runner yang butuh param ekstra (dipisah agar run() single-arg tetap aman)
_RUNNERS_WITH_PARAM = {
    "go_to_definition": GoToDefinition.find,
    "rename_symbol": RenameSymbol.rename,
}


def run(plugin_id: str, code: str) -> dict:
    """Eksekusi satu transform. Return {ok, code, report} — tidak pernah raise."""
    runner = _RUNNERS.get(plugin_id)
    if runner is None:
        # Coba runner dengan param tapi dipanggil tanpa param → beri pesan jelas
        if plugin_id in _RUNNERS_WITH_PARAM:
            return {"ok": False, "code": code,
                    "report": f"Plugin '{plugin_id}' butuh parameter (gunakan run_with_param)."}
        return {"ok": False, "code": code,
                "report": f"Plugin '{plugin_id}' tidak dikenal backend."}
    try:
        new_code, report = runner(code)
        return {"ok": True, "code": new_code, "report": "\n".join(report)}
    except Exception as e:  # noqa: BLE001 — plugin tidak boleh crash-kan app
        return {"ok": False, "code": code, "report": f"Error: {e}"}


def run_with_param(plugin_id: str, code: str, param: str) -> dict:
    """Eksekusi plugin yang butuh param (go_to_definition, rename_symbol)."""
    runner = _RUNNERS_WITH_PARAM.get(plugin_id)
    if runner is None:
        # fallback ke runner biasa (abaikan param)
        return run(plugin_id, code)
    try:
        new_code, report = runner(code, param)
        return {"ok": True, "code": new_code, "report": "\n".join(report)}
    except Exception as e:
        return {"ok": False, "code": code, "report": f"Error: {e}"}


def run_json(plugin_id: str, code: str) -> str:
    """JSON string — satu format parse untuk Chaquopy bridge & subprocess."""
    return json.dumps(run(plugin_id, code), ensure_ascii=False)


def run_json_with_param(plugin_id: str, code: str, param: str) -> str:
    """JSON string untuk runner dengan param (dipakai Chaquopy & subprocess)."""
    return json.dumps(run_with_param(plugin_id, code, param), ensure_ascii=False)


if __name__ == "__main__":
    # Backend desktop/dev: python3 zcode_plugins.py <plugin_id> <input_file> [param_file]
    if len(sys.argv) not in (3, 4):
        print(json.dumps({"ok": False, "code": "",
                          "report": "usage: zcode_plugins.py <plugin_id> <file> [param_file]"}))
        sys.exit(2)
    with open(sys.argv[2], "r", encoding="utf-8", errors="replace") as f:
        _code = f.read()
    if len(sys.argv) == 4:
        with open(sys.argv[3], "r", encoding="utf-8", errors="replace") as pf:
            _param = pf.read()
        print(run_json_with_param(sys.argv[1], _code, _param))
    else:
        print(run_json(sys.argv[1], _code))
