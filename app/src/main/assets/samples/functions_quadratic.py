# Functions — pemecah persamaan kuadrat ax² + bx + c = 0 🔍
# Pakai cmath biar akar kompleks pun kebaca (nggak cuma real).
import cmath


def solve_quadratic(a, b, c):
    """Balikin dua solusi persamaan kuadrat."""
    diskriminan = (b ** 2) - (4 * a * c)
    x1 = (-b - cmath.sqrt(diskriminan)) / (2 * a)
    x2 = (-b + cmath.sqrt(diskriminan)) / (2 * a)
    return x1, x2


# Coba: x² - 5x + 6 = 0  →  x = 2 dan x = 3
akar1, akar2 = solve_quadratic(1, -5, 6)
print("x² - 5x + 6 = 0")
print("Solusi:", akar1, "dan", akar2)

# Coba yang kompleks: x² + 1 = 0  →  x = ±i
akar1, akar2 = solve_quadratic(1, 0, 1)
print("x² + 1 = 0")
print("Solusi (kompleks!):", akar1, "dan", akar2)
