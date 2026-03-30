import urllib.request, json
res = urllib.request.urlopen('http://127.0.0.1:8080/api/simulate/heroes', timeout=10)
data = json.loads(res.read().decode('utf-8'))
heroes = data.get('heroes',[])
print('武将数量:', len(heroes))
for h in heroes[:8]:
    print(' ', h['id'], h['name'])

