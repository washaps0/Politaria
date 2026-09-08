from pathlib import Path

p = Path('server/plugins/Skript/scripts/countries_storage.sk')
s = p.read_text(encoding='utf-8')

old = 'if click type is right mouse button or click type is right mouse button with shift:'
new = '''set {_right-click} to false
\t\t\tif click type is right mouse button:
\t\t\t\tset {_right-click} to true
\t\t\tif click type is right mouse button with shift:
\t\t\t\tset {_right-click} to true
\t\t\tif {_right-click} is true:'''

count = s.count(old)
if count != 2:
    raise SystemExit(f'Expected 2 broken click conditions, found {count}')

p.write_text(s.replace(old, new), encoding='utf-8')
print('Fixed both right-click conditions')
