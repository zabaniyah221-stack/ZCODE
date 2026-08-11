"""
ZCODE Fase 3 Tests — UI Redesign + SAMPLES (2026-08, docs/RENCANA_UPDATE_2026_08.md)
Covers: sidebar TOOLS (bukan lagi 🧩 PLUGINS + seksi seksi lama), topbar ikon vektor
polos (ZIcons), palette dua fungsi (Line & Find) + validasi receh go-to-line, import
file dari file manager HP (SAF copy), halaman SAMPLES 2 level + semua asset sample
lolos py_compile. Sandbox tanpa JDK/SDK → test struktural anti-regresi.
Run: python -m pytest test_zcode_fase3.py -v
"""
import py_compile
import re
from pathlib import Path

ROOT = Path(__file__).parent
APP = ROOT / "app"
JAVA = APP / "src/main/java/com/zaba/zcode"
UI = JAVA / "ui"
CORE = JAVA / "core"
ASSETS = APP / "src/main/assets"
DOCS = ROOT / "docs"
WORKBENCH = UI / "workbench/WorkbenchScreen.kt"
VM = JAVA / "WorkspaceViewModel.kt"
MAIN = JAVA / "MainActivity.kt"


def read(p): return p.read_text(encoding="utf-8", errors="replace") if p.exists() else ""


# ===================================================================
# Sidebar redesign — TOOLS tunggal, tanpa seksi lama
# ===================================================================

class TestDrawerRedesign:
    def test_drawer_tools_tanpa_emoji(self):
        txt = read(WORKBENCH)
        assert '"TOOLS"' in txt, "header TOOLS hilang"
        assert "🧩 PLUGINS" not in txt, "header lama 🧩 PLUGINS harus hilang"

    def test_label_baru_ada(self):
        txt = read(WORKBENCH)
        assert "INSTALL MODULES" in txt, "label INSTALL MODULES hilang"
        assert "SAMPLES" in txt, "item SAMPLES hilang"

    def test_seksi_lama_dibuang(self):
        txt = read(WORKBENCH)
        assert "DrawerSectionTitle" not in txt, "komponen DrawerSectionTitle harus dihapus"
        assert "FileRow" not in txt, "komponen FileRow (FILES MANAGER) harus dihapus"

    def test_urutan_drawer(self):
        # Rule #2 (meticulous): urutan implementasi harus persis hasil diskusi —
        # INSTALL MODULES → SAMPLES → TOOLS → About (paling bawah).
        # Anchor = call-site item (docstring di header file juga menyebut nama-nama
        # ini, jadi pencarian HARUS ke bentuk pemakaiannya, bukan kata polosnya).
        txt = read(WORKBENCH)
        i_pip = txt.index('DrawerItem("INSTALL MODULES")')
        i_samples = txt.index('DrawerItem("SAMPLES")')
        i_tools = txt.index('"TOOLS",')
        i_about = txt.index('DrawerItem("About & Contribute")')
        assert i_pip < i_samples < i_tools < i_about, "urutan drawer melenceng dari desain"

    def test_tools_isi_lengkap(self):
        txt = read(WORKBENCH)
        for kw in ["Symbol bar", "THEME", "Clear All Drafts & Files"]:
            assert kw in txt, f"isi TOOLS {kw} hilang"
        # THEME = cycle satu tombol via VM (bukan lagi 3 tombol tema)
        assert "vm.cycleTheme()" in txt
        assert "ButtonDefaults" not in txt, "tombol tema lama harus hilang"

    def test_theme_cycle_wraparound_di_vm(self):
        txt = read(VM)
        assert "fun cycleTheme()" in txt and "% order.size" in txt, \
            "cycleTheme harus wraparound (modulo jumlah tema)"


# ===================================================================
# Topbar — ikon vektor polos + SAF import + tap nama file
# ===================================================================

class TestTopbarIcons:
    def test_zicons_file(self):
        txt = read(UI / "components/ZIcons.kt")
        assert "addPathNodes" in txt, "ZIcons harus gambar path manual tanpa dependensi"
        for name in ["Search", "Add", "Play", "Folder"]:
            assert f"val {name}" in txt, f"ikon {name} hilang dari ZIcons"

    def test_topbar_pakai_zicons(self):
        txt = read(WORKBENCH)
        for kw in ["ZIcons.Folder", "ZIcons.Search", "ZIcons.Add", "ZIcons.Play"]:
            assert kw in txt, f"topbar {kw} hilang"

    def test_tap_nama_file_dialog_aksi(self):
        txt = read(WORKBENCH)
        assert "showFileActions" in txt, "dialog File Actions (tap nama file) hilang"
        assert "fileToRename = active" in txt and "fileToDelete = active" in txt

    def test_import_saf_wired(self):
        txt = read(WORKBENCH)
        assert "OpenDocument" in txt and '"text/*"' in txt, "SAF launcher text/* hilang"
        assert "vm.importExternalFile" in txt
        vm = read(VM)
        for kw in ["fun importExternalFile", "fun uniqueFileName", "fun createSampleFromAsset"]:
            assert kw in vm, f"VM {kw} hilang"

    def test_guard_lama_tetap(self):
        # Anti-regresi audit 2026-08: drawer swipe-only marker tetap ada
        # (menggantikan guard ≡ yang dihapus bersama ikonnya).
        assert "DRAWER-SWIPE-ONLY" in read(WORKBENCH)


# ===================================================================
# Palette — dua fungsi + validasi go-to-line receh
# ===================================================================

class TestPaletteRedesign:
    def test_dua_fungsi_saja(self):
        txt = read(WORKBENCH)
        assert '"line"' in txt and '"find"' in txt
        assert '"file"' not in re.sub(r'"fileToRename"|"fileToDelete"|"showFileActions"', "", txt), \
            "mode File di palette harus hilang (pindah file via tab/topbar)"

    def test_validasi_go_to_line(self):
        txt = read(WORKBENCH)
        assert "attemptJump" in txt and "lineError" in txt
        # Pesan receh hasil diskusi (tone playful disepakati user)
        assert "Baris $target nggak ada njiir" in txt, "pesan receh go-to-line hilang"

    def test_mode_rahasia_tetap_hidup(self):
        txt = read(WORKBENCH)
        assert 'query.startsWith(">")' in txt, "mode perintah '>' harus tetap hidup"
        assert "onGotoLine" in txt, "kontrak onGotoLine hilang"


# ===================================================================
# SAMPLES — halaman 2 level + 11 asset lolos compile
# ===================================================================

class TestSamples:
    SAMPLE_IDS = ["hello_world", "text_input", "simple_math", "functions_quadratic",
                  "for_loop_factorial", "while_loop_guess", "generators_squares",
                  "dictionaries_db", "numpy_basics", "numpy_stats", "web_fetch_json"]

    def test_route_halaman_samples(self):
        assert '"samples"' in read(MAIN), "route samples hilang di MainActivity"
        assert "SamplesScreen" in read(MAIN)

    def test_katalog_lengkap(self):
        txt = read(CORE / "samples/SampleLibrary.kt")
        for sid in self.SAMPLE_IDS:
            assert f'"{sid}"' in txt, f"sample {sid} hilang dari katalog"
        for cat in ['"basics"', '"numpy"', '"web"']:
            assert cat in txt, f"kategori {cat} hilang"

    def test_asset_sama_dengan_katalog(self):
        ids_assets = sorted(p.stem for p in (ASSETS / "samples").glob("*.py"))
        assert ids_assets == sorted(self.SAMPLE_IDS), \
            f"isian assets/samples tidak sinkron dengan katalog: {ids_assets}"

    def test_semua_sample_lolos_py_compile(self):
        # Rule #2 (meticulous): sample rusak syntax = test merah, bukan crash di HP user
        for p in sorted((ASSETS / "samples").glob("*.py")):
            py_compile.compile(str(p), doraise=True)

    def test_samples_screen_dua_level(self):
        txt = read(UI / "samples/SamplesScreen.kt")
        assert "activeCategory" in txt and "BackHandler" in txt, \
            "SamplesScreen harus 2 level + back hierarchy benar"


# ===================================================================
# Dokumentasi keputusan diarsip
# ===================================================================

class TestDokumentasi:
    def test_rencana_update_ada(self):
        doc = DOCS / "RENCANA_UPDATE_2026_08.md"
        assert doc.exists(), "docs/RENCANA_UPDATE_2026_08.md hilang"
        txt = read(doc)
        for kw in ["Pintu A", "Pintu B", "ZPLAY", "redesign"]:
            assert kw in txt, f"dok rencana kehilangan bahasan {kw}"

    def test_readme_nunjuk_ke_dok(self):
        assert "RENCANA_UPDATE_2026_08" in read(ROOT / "README.md"), \
            "README harus menunjuk ke dok rencana update"
