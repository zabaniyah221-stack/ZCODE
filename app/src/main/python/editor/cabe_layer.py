"""
Cabe Layer Wrapper for ZCODE Engine Spike Intelligence.

Handles McCabe cyclomatic complexity analysis and maintainability metrics.

Provenance: port PR #31 dengan SATU perbaikan penting — pemanggilan
`preorder`. Bug PR #31: `visitor.preorder(tree)` (missing 1 required
positional argument: 'visitor') sehingga `analyze_complexity` selalu jatuh
ke fallback saat mccabe terpasang. Pola kanonik diverifikasi langsung dari
source mccabe (`McCabeChecker.run()`): `visitor.preorder(tree, visitor)` —
visitor mem-pass DIRINYA SENDIRI karena method visit* hidup di
PathGraphingAstVisitor. (Catatan: usulan fix di REVIEW_PR30_31_32 §3
"preorder(tree, mccabe.ASTVisitor())" juga salah — ASTVisitor polos tidak
punya visit* sehingga graphs tak pernah terbentuk dan complexity diam-diam
0. Jangan dikembalikan ke salah satu bentuk itu.)
"""

import logging
import ast
from typing import Dict, Any, List
from . import CabeError

logger = logging.getLogger(__name__)

class CabeComplexityAnalyzer:
    """Wrapper around McCabe complexity metric library."""

    def __init__(self):
        self._mccabe = None

    def _ensure_mccabe(self):
        if self._mccabe is None:
            try:
                import mccabe
                self._mccabe = mccabe
            except ImportError as e:
                logger.warning(f"McCabe library not available: {e}")
                raise CabeError(f"McCabe library is not installed: {e}") from e

    def is_available(self) -> bool:
        """Check if McCabe library is available."""
        try:
            self._ensure_mccabe()
            return True
        except CabeError:
            return False

    def analyze(self, code: str, threshold: int = 7) -> Dict[str, Any]:
        """
        Analyze cyclomatic complexity of Python code.
        """
        if not code or not code.strip():
            return {
                "cyclomatic_complexity": 1,
                "function_count": 0,
                "class_count": 0,
                "maintainability_index": 100.0,
                "blocks": []
            }

        try:
            tree = ast.parse(code)
            function_count = sum(1 for node in ast.walk(tree) if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)))
            class_count = sum(1 for node in ast.walk(tree) if isinstance(node, ast.ClassDef))

            blocks = []
            max_cc = 1

            try:
                self._ensure_mccabe()
                visitor = self._mccabe.PathGraphingAstVisitor()
                # Kanonik (mccabe.McCabeChecker.run): visitor mem-pass dirinya.
                visitor.preorder(tree, visitor)
                for graph in visitor.graphs.values():
                    cc = graph.complexity()
                    max_cc = max(max_cc, cc)
                    blocks.append({
                        # graph.name = "lineno:col: 'entity'" (format mentah);
                        # graph.entity = nama bersih untuk UI.
                        "name": graph.entity,
                        "line": graph.lineno,
                        "complexity": cc,
                        "is_high_complexity": cc > threshold
                    })
            except CabeError:
                # Built-in AST fallback for cyclomatic complexity calculation
                blocks, max_cc = self._fallback_complexity(tree, threshold)

            # Estimate maintainability index (0 to 100)
            lines_count = len(code.splitlines())
            mi = max(0.0, min(100.0, 171.0 - 5.2 * (max_cc ** 0.5) - 0.23 * lines_count))

            return {
                "cyclomatic_complexity": max_cc,
                "function_count": function_count,
                "class_count": class_count,
                "maintainability_index": round(mi, 2),
                "blocks": blocks
            }
        except SyntaxError as se:
            return {
                "cyclomatic_complexity": 0,
                "function_count": 0,
                "class_count": 0,
                "maintainability_index": 0.0,
                "error": f"SyntaxError: {se.msg} at line {se.lineno}",
                "blocks": []
            }
        except Exception as e:
            logger.error(f"Error during Cabe complexity analysis: {e}")
            return {
                "cyclomatic_complexity": 0,
                "function_count": 0,
                "class_count": 0,
                "maintainability_index": 0.0,
                "error": str(e),
                "blocks": []
            }

    def _fallback_complexity(self, tree: ast.AST, threshold: int) -> tuple:
        """Fallback cyclomatic complexity calculator using standard AST walk."""
        blocks = []
        max_cc = 1
        for node in ast.walk(tree):
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                # Base complexity = 1 + number of decision points (if, for, while, try, except, with, etc.)
                decision_points = sum(
                    1 for child in ast.walk(node)
                    if isinstance(child, (ast.If, ast.For, ast.While, ast.ExceptHandler, ast.With, ast.BoolOp, ast.IfExp))
                )
                cc = 1 + decision_points
                max_cc = max(max_cc, cc)
                blocks.append({
                    "name": node.name,
                    "line": node.lineno,
                    "complexity": cc,
                    "is_high_complexity": cc > threshold
                })
        return blocks, max_cc
