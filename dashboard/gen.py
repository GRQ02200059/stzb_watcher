# -*- coding: utf-8 -*-
import os

base = 'd:/nettest/dashboard'
parts = ['p1.html', 'p1b.html', 'p2.html', 'p3a.js', 'p3b.js']

with open(os.path.join(base, 'index.html'), 'w', encoding='utf-8') as out:
    for p in parts:
        path = os.path.join(base, p)
        if os.path.exists(path):
            with open(path, 'r', encoding='utf-8') as f:
                out.write(f.read())
        else:
            print(f'Missing: {p}')

size = os.path.getsize(os.path.join(base, 'index.html'))
print(f'index.html: {size} bytes ({size//1024} KB)')
