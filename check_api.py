import urllib.request, json, time
time.sleep(4)
res = urllib.request.urlopen('http://127.0.0.1:8080/api/simulate/heroes', timeout=10)
data = json.loads(res.read().decode('utf-8'))
skills = data.get('skills',[])
print('接口返回战法总数:', len(skills))
study = [s for s in skills if s.get('study')]
print('study=True战法数:', len(study))
test = [200241, 200643, 200674, 200755, 200800]
for tid in test:
    sk = next((s for s in skills if s['id']==tid), None)
    if sk:
        print('  ' + str(tid) + ' ' + sk['name'] + ': study=' + str(sk['study']))
    else:
        print('  ' + str(tid) + ': NOT FOUND')

