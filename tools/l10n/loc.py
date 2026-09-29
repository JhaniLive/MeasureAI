"""Localization helper: replaces UI string literals in Kotlin with resource lookups and
accumulates the strings (en / hi / ur / te) in strings.json; `write` emits values*/strings.xml.

Each entry: (old_code, new_code, key, en, hi, ur, te). old_code must occur in the file; all
occurrences are replaced. Strings with format args use %1$s / %1$d placeholders.
"""
import io
import json
import os
import sys

ROOT = 'E:/main-projects/andriod/'
STORE = ROOT + 'tools/l10n/strings.json'
LANGS = {'en': 'values', 'hi': 'values-hi', 'ur': 'values-ur', 'te': 'values-te'}


def load():
    if os.path.exists(STORE):
        return json.load(io.open(STORE, encoding='utf-8'))
    return {}


def save(db):
    io.open(STORE, 'w', encoding='utf-8').write(json.dumps(db, ensure_ascii=False, indent=1))


def apply(path, entries, imports=()):
    db = load()
    full = ROOT + path
    s = io.open(full, encoding='utf-8').read()
    for e in entries:
        old, new, key, en, hi, ur, te = e
        if old is not None:
            assert old in s, (path, old[:90])
            s = s.replace(old, new)
        if key is None:
            continue  # code-only change
        if key in db and db[key]['en'] != en:
            raise SystemExit('key clash ' + key)
        db[key] = {'en': en, 'hi': hi, 'ur': ur, 'te': te}
    for imp in imports:
        line = 'import ' + imp + '\n'
        if line not in s:
            # after the first import line
            i = s.index('\nimport ') + 1
            s = s[:i] + line + s[i:]
    io.open(full, 'w', encoding='utf-8', newline='').write(s)
    save(db)
    print(path, len(entries), 'entries; total', len(db))


def esc(text):
    t = text.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
    t = t.replace('\\', '\\\\').replace("'", "\\'").replace('"', '\\"').replace('\n', '\\n')
    if t.startswith('@') or t.startswith('?'):
        t = '\\' + t
    return t


def write():
    db = load()
    for lang, folder in LANGS.items():
        d = ROOT + 'app/src/main/res/' + folder
        os.makedirs(d, exist_ok=True)
        lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
        if lang == 'en':
            lines.append('    <string name="app_name" translatable="false">MeasureAR</string>')
        for key in sorted(db):
            text = db[key][lang]
            has_args = '%1$' in text or '%2$' in text
            if not has_args and '%' in text:
                lines.append('    <string name="%s" formatted="false">%s</string>' % (key, esc(text)))
            else:
                lines.append('    <string name="%s">%s</string>' % (key, esc(text)))
        lines.append('</resources>')
        io.open(d + '/strings.xml', 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
    print('wrote', len(db), 'strings x', len(LANGS))


if __name__ == '__main__' and sys.argv[1:] == ['write']:
    write()
