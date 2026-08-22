"""
ZCODE plugin transform backend (batch anti-sepi, 2026-08).

DERIVED FROM ZABACODE under GPLv3; copyright Zaqi (muzape28-blip) and
ZABACODE Contributors. Source revision and modification notice: root NOTICE.
Logika kelas berasal dari zabacode/plugins/implementations.py dan kemudian
diadaptasi untuk bridge ZCODE (run/run_json/CLI) serta diperbaiki lebih lanjut.

Antarmuka:
    run(plugin_id, code) -> dict  {ok, code, report}
    run_json(plugin_id, code) -> str (JSON — dipakai Chaquopy bridge & CLI)
    CLI: python3 zcode_plugins.py <plugin_id> <input_file>  → JSON ke stdout
"""
import ast
import io
import json
import keyword
import sys
import tokenize


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
    """Insert annotations without reconstructing signatures or defaults."""

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
        elif isinstance(default_node, ast.List):
            return "list"
        elif isinstance(default_node, ast.Dict):
            return "dict"
        elif isinstance(default_node, ast.Set):
            return "set"
        elif isinstance(default_node, ast.Tuple):
            return "tuple"
        return "Any"

    @staticmethod
    def _line_offsets(code: str) -> list[int]:
        offsets = [0]
        for line in code.splitlines(keepends=True):
            offsets.append(offsets[-1] + len(line))
        return offsets

    @staticmethod
    def _offset(offsets: list[int], position: tuple[int, int]) -> int:
        line, column = position
        return offsets[line - 1] + column

    @staticmethod
    def _signature_colons(code: str, functions: list[ast.AST]) -> dict[int, int]:
        """Map node identity to the source offset of its header colon."""
        offsets = VariableTypeHintGenerator._line_offsets(code)
        tokens = list(tokenize.generate_tokens(io.StringIO(code).readline))
        result = {}
        for node in functions:
            start_line = node.lineno
            start_col = node.col_offset
            def_index = next((
                index for index, token in enumerate(tokens)
                if token.type == tokenize.NAME and token.string == "def" and
                (token.start[0] > start_line or
                 (token.start[0] == start_line and token.start[1] >= start_col))
            ), None)
            if def_index is None:
                continue
            depth = 0
            for token in tokens[def_index + 1:]:
                if token.type == tokenize.OP:
                    if token.string in "([{":
                        depth += 1
                    elif token.string in ")]}":
                        depth -= 1
                    elif token.string == ":" and depth == 0:
                        result[id(node)] = VariableTypeHintGenerator._offset(offsets, token.start)
                        break
        return result

    @staticmethod
    def _typing_import_offset(code: str, tree: ast.Module) -> int:
        offsets = VariableTypeHintGenerator._line_offsets(code)
        insert_line = 1
        lines = code.splitlines(keepends=True)
        while insert_line <= len(lines) and (
            (insert_line == 1 and lines[insert_line - 1].startswith("#!")) or
            (insert_line <= 2 and "coding" in lines[insert_line - 1][:40])
        ):
            insert_line += 1
        body_index = 0
        if tree.body and isinstance(tree.body[0], ast.Expr) and isinstance(
            getattr(tree.body[0], "value", None), ast.Constant
        ) and isinstance(tree.body[0].value.value, str):
            insert_line = max(insert_line, getattr(tree.body[0], "end_lineno", tree.body[0].lineno) + 1)
            body_index = 1
        while body_index < len(tree.body):
            node = tree.body[body_index]
            if not (isinstance(node, ast.ImportFrom) and node.module == "__future__"):
                break
            insert_line = max(insert_line, getattr(node, "end_lineno", node.lineno) + 1)
            body_index += 1
        return offsets[min(insert_line - 1, len(offsets) - 1)]

    @staticmethod
    def generate(code: str) -> tuple:
        try:
            tree = ast.parse(code)
        except SyntaxError as exc:
            return code, [f"SyntaxError: {exc}"]

        functions = [
            node for node in ast.walk(tree)
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
        ]
        if not functions:
            return code, ["No functions found to add type hints."]

        offsets = VariableTypeHintGenerator._line_offsets(code)
        colons = VariableTypeHintGenerator._signature_colons(code, functions)
        edits: list[tuple[int, str]] = []
        report = []
        uses_any = False

        for function in functions:
            positional = list(function.args.posonlyargs) + list(function.args.args)
            defaults_map = {}
            for arg, default in zip(positional[-len(function.args.defaults):], function.args.defaults):
                defaults_map[id(arg)] = default
            for arg, default in zip(function.args.kwonlyargs, function.args.kw_defaults):
                if default is not None:
                    defaults_map[id(arg)] = default
            all_args = positional + list(function.args.kwonlyargs)
            if function.args.vararg is not None:
                all_args.append(function.args.vararg)
            if function.args.kwarg is not None:
                all_args.append(function.args.kwarg)

            changed = False
            for arg in all_args:
                if arg.arg in {"self", "cls"} or arg.annotation is not None:
                    continue
                annotation = VariableTypeHintGenerator.infer_type_by_default(defaults_map.get(id(arg)))
                uses_any = uses_any or annotation == "Any"
                end = (arg.end_lineno, arg.end_col_offset)
                edits.append((VariableTypeHintGenerator._offset(offsets, end), f": {annotation}"))
                changed = True
            if function.returns is None and id(function) in colons:
                edits.append((colons[id(function)], " -> Any"))
                uses_any = True
                changed = True
            if changed:
                report.append(f"Injected safe type hints into function '{function.name}'.")

        if not edits:
            return code, ["No functions found to add type hints."]

        has_any = any(
            isinstance(node, ast.ImportFrom) and node.module == "typing" and
            any(alias.name in {"Any", "*"} and (alias.asname in {None, "Any"}) for alias in node.names)
            for node in tree.body
        )
        if uses_any and not has_any:
            edits.append((VariableTypeHintGenerator._typing_import_offset(code, tree), "from typing import Any\n"))

        current = code
        for offset, text in sorted(edits, key=lambda item: item[0], reverse=True):
            current = current[:offset] + text + current[offset:]
        try:
            ast.parse(current)
        except SyntaxError as exc:
            return code, [f"Transform dibatalkan karena hasil tidak valid: {exc}"]
        return current, report


# ---------------------------------------------------------------------------
class OrganizeImports:
    """Read-only gate until import edits have preview + transactional apply."""

    @staticmethod
    def generate(code: str) -> tuple:
        # Import removal/reordering is not semantics-preserving in Python:
        # imports may register plugins, mutate process state, or be ordered after a
        # module docstring and __future__ statements. Until ZCODE has a preview +
        # transactional edit flow, keep this action read-only rather than corrupt
        # valid programs while claiming to "optimize" them.
        try:
            ast.parse(code)
        except Exception as exc:
            return code, [f"Gagal parse AST karena kesalahan sintaksis: {exc}"]
        return code, [
            "Safe mode: imports tidak diubah otomatis karena urutan dan side effect harus dipreview."
        ]



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
    """Rename NAME tokens while preserving strings, comments, and formatting."""

    @staticmethod
    def rename(code: str, params: str) -> tuple:
        parts = params.split(":")
        if len(parts) != 2:
            raise ValueError("Format parameter salah.")
        old, new = (part.strip() for part in parts)
        if not old.isidentifier() or keyword.iskeyword(old):
            raise ValueError("Nama simbol lama bukan identifier Python yang valid.")
        if not new.isidentifier() or keyword.iskeyword(new):
            raise ValueError("Nama simbol baru bukan identifier Python yang valid.")
        if old == new:
            return code, ["Nama lama dan nama baru sama; source tidak diubah."]

        try:
            tree = ast.parse(code)
        except SyntaxError as exc:
            raise ValueError(f"Rename membutuhkan source yang valid: {exc}") from exc

        # Python 3.11 tokenize emits an entire f-string as one STRING token. A
        # token-only rename would therefore leave references inside expressions
        # inconsistent. Fail closed until the semantic refactor engine handles it.
        fstring_uses_old = any(
            isinstance(parent, ast.JoinedStr) and any(
                isinstance(child, ast.Name) and child.id == old
                for child in ast.walk(parent)
            )
            for parent in ast.walk(tree)
        )
        if fstring_uses_old:
            raise ValueError(
                "Rename dibatalkan: simbol dipakai di ekspresi f-string yang belum dapat diubah aman."
            )

        offsets = VariableTypeHintGenerator._line_offsets(code)
        replacements = []
        try:
            tokens = tokenize.generate_tokens(io.StringIO(code).readline)
            for token in tokens:
                if token.type == tokenize.NAME and token.string == old:
                    start = VariableTypeHintGenerator._offset(offsets, token.start)
                    end = VariableTypeHintGenerator._offset(offsets, token.end)
                    replacements.append((start, end))
        except (tokenize.TokenError, IndentationError) as exc:
            raise ValueError(f"Rename dibatalkan karena token source tidak valid: {exc}") from exc

        if not replacements:
            return code, [f"Simbol '{old}' tidak ditemukan; source tidak diubah."]
        current = code
        for start, end in reversed(replacements):
            current = current[:start] + new + current[end:]
        try:
            ast.parse(current)
        except SyntaxError as exc:
            raise ValueError(f"Rename dibatalkan karena hasil tidak valid: {exc}") from exc
        return current, [
            f"Simbol '{old}' diganti menjadi '{new}' pada {len(replacements)} token; string dan komentar tidak diubah."
        ]



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
