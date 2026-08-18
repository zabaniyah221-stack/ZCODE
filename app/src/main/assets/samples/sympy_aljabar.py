# sympy — matematika simbolik: PR aljabar dikerjain HP (TESTED di ZCODE)
# Butuh: install sympy (pure Python, ringan)

from sympy import symbols, solve, expand, factor, diff

x = symbols("x")

print("ekspansi (x+2)^3 :", expand((x + 2) ** 3))
print("faktorkan x^2-9  :", factor(x**2 - 9))
print("akar x^2-5x+6=0  :", solve(x**2 - 5 * x + 6, x))
print("turunan x^3+2x   :", diff(x**3 + 2 * x, x))
