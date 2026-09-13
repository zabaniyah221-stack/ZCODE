"""
Jedi Layer Wrapper for ZCODE Engine Spike Intelligence.

Handles autocompletion, goto definition, and signature help safely with input validation.
"""
"""
Provenance: port dari PR #31 (head 8775958) — bagian dari ZCODE (GPLv3).
Adaptasi v1.0.23: lihat docs/RFC_V1023_SPIKE_INTELLIGENCE.md §8.
"""


import logging
from typing import List, Dict, Any, Tuple, Optional
from . import JediError

logger = logging.getLogger(__name__)

class JediCompletionProvider:
    """Wrapper around Jedi autocompletion library."""

    def __init__(self):
        self._jedi = None

    def _ensure_jedi(self):
        if self._jedi is None:
            try:
                import jedi
                self._jedi = jedi
            except ImportError as e:
                logger.warning(f"Jedi library not available: {e}")
                raise JediError(f"Jedi library is not installed: {e}") from e

    def is_available(self) -> bool:
        """Check if Jedi library is installed and importable."""
        try:
            self._ensure_jedi()
            return True
        except JediError:
            return False

    def _validate_position(self, code: str, position: Tuple[int, int]) -> Tuple[int, int]:
        """
        Validate and sanitize (line, column) tuple.
        Line is 1-based, column is 0-based.
        """
        if not isinstance(position, (tuple, list)) or len(position) < 2:
            return (1, 0)

        line, col = int(position[0]), int(position[1])
        lines = code.splitlines() or [""]

        # Clamp line to [1, len(lines)]
        line = max(1, min(line, len(lines)))

        # Clamp col to [0, len(line_content)]
        current_line_len = len(lines[line - 1]) if line <= len(lines) else 0
        col = max(0, min(col, current_line_len))

        return (line, col)

    def get_completions(self, code: str, position: Tuple[int, int], path: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Get autocompletions at line, column.
        Returns a JSON-serializable list of completion dicts.
        """
        if not code:
            return []

        try:
            self._ensure_jedi()
            line, col = self._validate_position(code, position)
            script = self._jedi.Script(code, path=path or "untitled.py")
            completions = script.complete(line, col)

            result = []
            for c in completions:
                result.append({
                    "name": getattr(c, "name", ""),
                    "complete": getattr(c, "complete", ""),
                    "type": getattr(c, "type", "symbol"),
                    "description": getattr(c, "description", ""),
                    "docstring": getattr(c, "docstring", lambda: "")() if callable(getattr(c, "docstring", None)) else getattr(c, "docstring", ""),
                    "name_with_symbols": getattr(c, "name_with_symbols", getattr(c, "name", ""))
                })
            return result
        except JediError:
            return []
        except Exception as e:
            logger.error(f"Error during Jedi autocompletion: {e}")
            return []

    def goto_definition(self, code: str, position: Tuple[int, int], path: Optional[str] = None) -> List[Dict[str, Any]]:
        """
        Get definitions for the symbol under cursor.
        Returns JSON-serializable definitions list.
        """
        if not code:
            return []

        try:
            self._ensure_jedi()
            line, col = self._validate_position(code, position)
            script = self._jedi.Script(code, path=path or "untitled.py")

            definitions = []
            if hasattr(script, "goto"):
                definitions = script.goto(line, col)
            elif hasattr(script, "goto_definitions"):
                definitions = script.goto_definitions(line, col)

            result = []
            for d in definitions:
                result.append({
                    "name": getattr(d, "name", ""),
                    "type": getattr(d, "type", ""),
                    "module_name": getattr(d, "module_name", ""),
                    "line": getattr(d, "line", None),
                    "column": getattr(d, "column", None),
                    "full_name": getattr(d, "full_name", ""),
                    "docstring": getattr(d, "docstring", lambda: "")() if callable(getattr(d, "docstring", None)) else getattr(d, "docstring", "")
                })
            return result
        except JediError:
            return []
        except Exception as e:
            logger.error(f"Error during Jedi goto_definition: {e}")
            return []
