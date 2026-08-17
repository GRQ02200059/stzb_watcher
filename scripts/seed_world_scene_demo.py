import sqlite3
import time

import api_server
from world_scene.parser import parse_world_scene_packet
from world_scene.store import WorldSceneStore

# Build a realistic 31-slot 5026 payload with one WORLD_CITY tile, one army, one realMarch.
payload = [None] * 31
payload[0] = {}          # visualField
payload[1] = {}          # mapUsers
payload[3] = {}          # unions

# slot[6] armies: MapArmyTuple needs >=32 slots. index0=state, 1=userId, 2=from,3=to,
# 4=begin,5=end,9=targetType,10=reside,11=stay,16=heroType,27=morale,28=realMarchId,
# 29=buff,30=obstacle,31=battleShow,32=stateId
army = [0] * 33
army[0] = 3                       # state
army[1] = 100200300               # userId
army[2] = 100050100               # from wid (row10005 col0100)
army[3] = 100060200               # to wid
army[4] = int(time.time()) - 60   # begin
army[5] = int(time.time()) + 120  # end
army[9] = 1                       # targetType
army[10] = 0
army[11] = 100050100
army[16] = "1901,2005,2107"       # armyHeroType
army[27] = 92                     # morale
army[28] = 777001                 # realMarchId
army[29] = "1,2"
army[30] = 0
army[31] = "破敌·关羽张飞赵云"
army[32] = 5                       # stateId
payload[6] = {"555001": army}

payload[7] = []
payload[14] = {}         # worldChunks

# WORLD_CITY tuple (21 slots): 0=cityType,1=cityParam,2=userId,3=unionId,4=protectEnd,
# 6=name,7=belongCity,8=state,9=guardEnd,12=force,19=stateId,20=viewRangeAdd
city = [0] * 21
city[0] = 14                       # cityType 皇城
city[1] = 1
city[2] = 100200300
city[3] = 88001
city[4] = int(time.time()) + 3600
city[6] = "洛阳"
city[7] = 0
city[8] = 2
city[9] = int(time.time()) + 1800
city[12] = 1
city[19] = 9
city[20] = 3
payload[14] = {"100060200": {"0": city}}

payload[15] = {}
payload[18] = 123456                # serverOrderId (>0 => final frame)
payload[20] = None

# slot[29] realMarch: 14 slots. 0=last,1=current,2=next,3=start,4=next,5=end,
# 6=pathId,7=unitTimeCost,8=marchType,9=belongId
rm = [0] * 14
rm[0] = 100050100
rm[1] = 100055150
rm[2] = 100060200
rm[3] = int(time.time()) - 60
rm[4] = int(time.time()) + 20
rm[5] = int(time.time()) + 120
rm[6] = 42
rm[7] = 8
rm[8] = 0
rm[9] = 555001
payload[29] = {"777001": rm}

decoded_text = repr(payload)

packet = parse_world_scene_packet(
    cmd_id=5026,
    decoded_text=decoded_text,
    source="seed-demo",
    observed_at_ms=int(time.time() * 1000),
)
print("parsed:", "tiles", len(packet.tiles), "armies", len(packet.armies), "marches", len(packet.real_marches))

conn = sqlite3.connect(api_server._current_db_path)
store = WorldSceneStore(conn)
store.ensure_schema()
seq = store.apply_packet(packet)
print("applied seq:", seq)
conn.close()
