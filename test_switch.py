import urllib.request, json

pid = '42.186.96.143:3z5ddqy811007'
payload = json.dumps({'profile_id': pid})
print('发送 payload:', payload)
print('payload bytes:', payload.encode())

req = urllib.request.Request(
    'http://127.0.0.1:8080/api/switch_profile',
    data=payload.encode('utf-8'),
    headers={'Content-Type': 'application/json; charset=utf-8'},
    method='POST'
)
try:
    resp = urllib.request.urlopen(req)
    print('成功:', resp.read().decode())
except urllib.error.HTTPError as e:
    print('失败:', e.code, e.read().decode())

