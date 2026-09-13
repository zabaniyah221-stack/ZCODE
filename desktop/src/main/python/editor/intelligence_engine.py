"""
Spike Intelligence Engine Core.

Main orchestrator integrating:
- Jedi: Autocompletion & Go-to Definition
- Parso: AST Parsing & Error Recovery
- Pyflakes: Static Analysis & Linting
- Cabe: McCabe Cyclomatic Complexity Analysis
"""
"""
Provenance: port dari PR #31 (head 8775958) — bagian dari ZCODE (GPLv3).
Adaptasi v1.0.23: lihat docs/RFC_V1023_SPIKE_INTELLIGENCE.md §8.
"""


import logging
from typing import Dict, Any, Optional, List, Tuple

from . import SpikeError

logger = logging.getLogger(__name__)

class SpikeIntelligenceEngine:
    """
    Main intelligence engine orchestrating all editor Python analysis features.

    Attributes:
        jedi_provider: Lazy-loaded JediCompletionProvider
        parso_parser: Lazy-loaded ParsoASTParser
        pyflakes_linter: Lazy-loaded PyflakesLinter
        cabe_analyzer: Lazy-loaded CabeComplexityAnalyzer
    """

    def __init__(self):
        self._jedi_provider = None
        self._parso_parser = None
        self._pyflakes_linter = None
        self._cabe_analyzer = None
        logger.info("Spike Intelligence Engine initialized (lazy load)")

    @property
    def jedi_provider(self):
        """Lazy load Jedi provider."""
        if self._jedi_provider is None:
            from .jedi_layer import JediCompletionProvider
            self._jedi_provider = JediCompletionProvider()
        return self._jedi_provider

    @property
    def parso_parser(self):
        """Lazy load Parso parser."""
        if self._parso_parser is None:
            from .parso_layer import ParsoASTParser
            self._parso_parser = ParsoASTParser()
        return self._parso_parser

    @property
    def pyflakes_linter(self):
        """Lazy load Pyflakes linter."""
        if self._pyflakes_linter is None:
            from .pyflakes_layer import PyflakesLinter
            self._pyflakes_linter = PyflakesLinter()
        return self._pyflakes_linter

    @property
    def cabe_analyzer(self):
        """Lazy load Cabe analyzer."""
        if self._cabe_analyzer is None:
            from .cabe_layer import CabeComplexityAnalyzer
            self._cabe_analyzer = CabeComplexityAnalyzer()
        return self._cabe_analyzer

    def autocomplete(self, code: str, position: Tuple[int, int], path: Optional[str] = None) -> List[Dict[str, Any]]:
        """Get code autocompletions at cursor position."""
        try:
            return self.jedi_provider.get_completions(code, position, path=path)
        except Exception as e:
            logger.error(f"Error in SpikeIntelligenceEngine.autocomplete: {e}")
            return []

    def goto_definition(self, code: str, position: Tuple[int, int], path: Optional[str] = None) -> List[Dict[str, Any]]:
        """Get definitions for symbol under cursor position."""
        try:
            return self.jedi_provider.goto_definition(code, position, path=path)
        except Exception as e:
            logger.error(f"Error in SpikeIntelligenceEngine.goto_definition: {e}")
            return []

    def parse_ast(self, code: str, version: Optional[str] = None) -> Dict[str, Any]:
        """Parse source code into AST structure."""
        try:
            return self.parso_parser.parse(code, version=version)
        except Exception as e:
            logger.error(f"Error in SpikeIntelligenceEngine.parse_ast: {e}")
            return {"type": "file_input", "errors": [{"message": str(e), "line": 1, "column": 0}], "nodes_count": 0}

    def lint(self, code: str, filename: str = "untitled.py") -> List[Dict[str, Any]]:
        """Run static analysis and linting on code."""
        try:
            return self.pyflakes_linter.lint(code, filename=filename)
        except Exception as e:
            logger.error(f"Error in SpikeIntelligenceEngine.lint: {e}")
            return []

    def analyze_complexity(self, code: str, threshold: int = 7) -> Dict[str, Any]:
        """Analyze cyclomatic complexity metrics."""
        try:
            return self.cabe_analyzer.analyze(code, threshold=threshold)
        except Exception as e:
            logger.error(f"Error in SpikeIntelligenceEngine.analyze_complexity: {e}")
            return {
                "cyclomatic_complexity": 0,
                "function_count": 0,
                "class_count": 0,
                "maintainability_index": 0.0,
                "error": str(e),
                "blocks": []
            }

    def health_check(self) -> Dict[str, Any]:
        """Check status of all sub-engines."""
        return {
            "jedi": {
                "installed": self.jedi_provider.is_available(),
                "status": "ok" if self.jedi_provider.is_available() else "fallback"
            },
            "parso": {
                "installed": self.parso_parser.is_available(),
                "status": "ok" if self.parso_parser.is_available() else "fallback"
            },
            "pyflakes": {
                "installed": self.pyflakes_linter.is_available(),
                "status": "ok" if self.pyflakes_linter.is_available() else "fallback"
            },
            "cabe": {
                "installed": self.cabe_analyzer.is_available(),
                "status": "ok" if self.cabe_analyzer.is_available() else "fallback"
            }
        }
