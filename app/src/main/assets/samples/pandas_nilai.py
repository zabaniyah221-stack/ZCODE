# pandas — olah tabel nilai ala spreadsheet (TESTED di ZCODE)
# Butuh: install pandas (±15MB, menarik numpy)

import pandas as pd

df = pd.DataFrame({
    "nama": ["Ani", "Budi", "Citra", "Dedi"],
    "mtk": [85, 72, 90, 65],
    "ipa": [78, 88, 95, 70],
})
df["rata"] = df[["mtk", "ipa"]].mean(axis=1)
df["lulus"] = df["rata"] >= 75

print(df.to_string(index=False))
print("\nRata-rata kelas:", round(df["rata"].mean(), 1))
print("Terbaik:", df.loc[df["rata"].idxmax(), "nama"])
