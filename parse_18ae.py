import json, glob, os

files = glob.glob('d:/nettest/capture_new/stzb_42.186.96.143/000018ae/*.json')
output = []
for fpath in sorted(files):
    fname = os.path.basename(fpath)
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        members = data[0]
        city_id = data[1]
        output.append(f'=== 文件: {fname} | 城池ID: {city_id} | 成员数: {len(members)} ===')
        for row in members:
            name    = row[0]
            uid     = row[1]
            level   = row[2]
            pos     = row[3]
            others  = row[4] if isinstance(row[4], str) else ''
            n_hero  = len(others.split(',')) if others else 0
            cur_hero = row[6]
            power   = row[7]
            flag    = row[8]
            cfg_id  = row[9]
            skin    = row[10]
            output.append(f'  {name}	uid={uid}	lv={level}	pos={pos}	武将={n_hero}+1	上阵={cur_hero}	战力={power}	flag={flag}	cfg={cfg_id}	skin={skin}')
        output.append('')
    except Exception as e:
        output.append(f'ERR {fname}: {e}')

with open('d:/nettest/000018ae_parsed.txt', 'w', encoding='utf-8') as f:
    f.write(os.linesep.join(output))
print(f'完成，共处理 {len(files)} 个文件')

