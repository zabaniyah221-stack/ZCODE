"""
Parso Layer Wrapper for ZCODE Engine Spike Intelligence.

Handles AST parsing and syntax analysis with error recovery.
"""
"""
Provenance: port dari PR #31 (head 8775958) — bagian dari ZCODE (GPLv3).
Adaptasi v1.0.23: lihat docs/RFC_V1023_SPIKE_INTELLIGENCE.md §8.
"""


import logging
from typing import Dict, Any, Optional, List
from . import ParsoError

logger = logging.getLogger(__name__)

class ParsoASTParser:
    """Wrapper around Parso AST parser."""

    def __init__(self):
        self._parso = None

    def _ensure_parso(self):
        if self._parso is None:
            try:
                import parso
                self._parso = parso
            except ImportError as e:
                logger.warning(f"Parso library not available: {e}")
                raise ParsoError(f"Parso library is not installed: {e}") from e

    def is_available(self) -> bool:
        """Check if Parso is available."""
        try:
            self._ensure_parso()
            return True
        except ParsoError:
            return False

    def parse(self, code: str, version: Optional[str] = None) -> Dict[str, Any]:
        """
        Parse Python source code into an AST metadata structure.
        """
        if not code:
            return {"type": "file_input", "errors": [], "nodes_count": 0}

        try:
            self._ensure_parso()
            grammar = self._parso.load_grammar(version=version) if version else self._parso.load_grammar()
            module = grammar.parse(code)

            # Extract syntax errors if available
            errors = []
            if hasattr(grammar, "_tokenizer"):
                try:
                    for err in grammar.iter_errors(module):
                        errors.append({
                            "message": getattr(err, "message", "Syntax error"),
                            "line": getattr(err, "start_pos", (1, 0))[0],
                            "column": getattr(err, "start_pos", (1, 0))[1]
                        })
                except Exception:
                    pass

            return {
                "type": getattr(module, "type", "file_input"),
                "errors": errors,
                "nodes_count": len(list(getattr(module, "children", [])))
            }
        except ParsoError:
            return {"type": "file_input", "errors": [], "nodes_count": 0}
        except Exception as e:
            logger.error(f"Error during Parso parsing: {e}")
            return {"type": "file_input", "errors": [{"message": str(e), "line": 1, "column": 0}], "nodes_count": 0}
