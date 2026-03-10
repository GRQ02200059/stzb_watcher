with open('index.html', encoding='utf-8') as f:
    c = f.read()
print('body tag:', '<body>' in c)
print('bg-grid div:', '<div class="bg-grid">' in c)
print('bg-glow div:', '<div class="bg-glow">' in c)
idx = c.find('<header>')
print('before header:', repr(c[max(0, idx-150):idx]))

