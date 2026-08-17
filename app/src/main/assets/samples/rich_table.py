# Rich — terminal berwarna: tabel, panel, emoji ✨
# BUTUH: install rich dulu (INSTALL MODULES -> cari "rich")

from rich.console import Console
from rich.table import Table

c = Console()
t = Table(title="📊 Nilai Kelas")
t.add_column("Nama", style="cyan")
t.add_column("Nilai", justify="right", style="green")
t.add_row("Andi", "85")
t.add_row("Budi", "62")
t.add_row("Citra", "91")
c.print(t)
c.print("[bold magenta]Terminal ZCODE mendukung warna ANSI![/]")
