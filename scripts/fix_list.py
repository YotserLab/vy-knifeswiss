with open("app/src/main/java/com/example/data/DefaultShoppingItems.kt", "r") as f:
    content = f.read()

extra_items = [
    "Açafrão da terra", "Amido de arroz", "Bebida de aveia", "Bebida de soja", 
    "Chá branco", "Ervilha torta", "Levedura nutricional", "Missô", 
    "Semente de melancia", "Tahine"
]

lines = content.split('\n')
for i, line in enumerate(lines):
    if line.strip() == ")":
        for item in extra_items:
            lines.insert(i, f'    "{item}",')
        break

with open("app/src/main/java/com/example/data/DefaultShoppingItems.kt", "w") as f:
    f.write('\n'.join(lines))
