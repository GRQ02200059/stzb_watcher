# -*- coding: utf-8 -*-
"""
率土之滨 抓包脚本 v2
支持三种数据包类型:
  类型2: 明文，直接转字符串/JSON
  类型3: zlib 压缩
  类型5: 异或解密

数据包头部结构 (参考 buf[12] 为类型字段):
  buf[0:4]   - 包总长度 (大端 uint32)
  buf[4:8]   - 序列号 / session
  buf[8:12]  - 消息ID (大端 uint32) -> 存储目录名
  buf[12]    - 数据类型 (2=明文, 3=zlib, 5=xor)
  buf[13:16] - 保留/其他
  buf[16:]   - 数据体
"""
import json, os, zlib, struct, time, threading, subprocess
from datetime import datetime
from collections import defaultdict
from scapy.all import sniff, TCP, Raw
import signal, sys

# ===== 配置 =====
OUTPUT_BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'capture_new')
OUTPUT_DIR  = OUTPUT_BASE  # 初始值，绑定账号后会切换到子目录
GAME_PORT   = 8001
BIND_CMD_ID = 0xe6  # 主公簿 cmdId，用于二阶段优化目录名

# 绑定状态
_bind_lock    = threading.Lock()
_bound_src_ip = None   # 游戏服务器 IP:port
_bound_dst_ip = None   # 本机 IP:port
# 是否已完成精确绑定（拿到 role_id）
_role_bound   = False
_last_sniff_role_id = None  # 上次抓包识别到的 role_id，防止重复触发
_sniff_stop_event = threading.Event()
XOR_KEY     = None
HEADER_LEN  = 13
MIN_PKT_LEN = 14

os.makedirs(OUTPUT_DIR, exist_ok=True)

# ===== 统计 =====
stats       = defaultdict(int)
err_stats   = defaultdict(int)
type_names  = {2: 'plaintext', 3: 'zlib', 5: 'xor'}

# ===== TCP 流重组 =====
stream_bufs = defaultdict(bytearray)


def ensure_dir(msg_type_hex):
    d = os.path.join(OUTPUT_DIR, msg_type_hex)
    os.makedirs(d, exist_ok=True)
    return d


def save_json(msg_type_hex, data_type_name, parsed):
    ts  = datetime.now().strftime('%Y%m%d%H%M%S%f')[:-3]
    d   = ensure_dir(msg_type_hex)
    fn  = f'cap_{ts}_{msg_type_hex}_{data_type_name}.json'
    fp  = os.path.join(d, fn)
    with open(fp, 'w', encoding='utf-8') as f:
        json.dump(parsed, f, ensure_ascii=False, indent=2)
    return fn


def save_raw(msg_type_hex, data_type_name, raw_bytes):
    """无法解析为JSON时保存原始内容"""
    ts  = datetime.now().strftime('%Y%m%d%H%M%S%f')[:-3]
    d   = ensure_dir(msg_type_hex)
    fn  = f'cap_{ts}_{msg_type_hex}_{data_type_name}.txt'
    fp  = os.path.join(d, fn)
    with open(fp, 'wb') as f:
        f.write(raw_bytes)
    return fn


def try_parse_json(raw_bytes):
    """尝试将字节解析为JSON，返回 (ok, parsed)"""
    for enc in ('utf-8', 'gbk'):
        try:
            return True, json.loads(raw_bytes.decode(enc))
        except Exception:
            pass
    return False, None


def process_one_packet(buf, stream_key):
    """
    真实包头结构（实测）:
      buf[0:4]  = 包体长度（不含这4字节）
      buf[4:8]  = 序列号 seq（即消息类型ID，用作目录名）
      buf[7]    = XOR密钥（seq低字节）
      buf[8:12] = 随机字段
      buf[12]   = 数据类型 (5=XOR, 3=zlib, 2=明文)
      buf[13:]  = 数据体
    """
    if len(buf) < 14:
        return 0

    try:
        body_len = struct.unpack('>I', buf[0:4])[0]
        total    = body_len + 4
    except Exception:
        return 1

    if body_len < 4 or body_len > 10 * 1024 * 1024:
        return 1

    if len(buf) < total:
        return 0

    seq       = struct.unpack('>I', buf[4:8])[0]
    xor_key   = 0x98
    xor_key2  = buf[7]
    data_type = buf[12] if len(buf) > 12 else 0
    body      = bytes(buf[13:total])

    msg_type_hex = f'{seq:08x}'

    if not body:
        return total

    # ===== 类型 5: XOR =====
    if data_type == 5:
        best_dec = None
        best_key = xor_key
        best_parsed = None
        candidate_keys = [0x98, xor_key2, 0x00]
        seen = set()
        candidate_keys = [k for k in candidate_keys if not (k in seen or seen.add(k))]
        for key in candidate_keys:
            dec = bytes(b ^ key for b in body) if key != 0x00 else body
            ok, parsed = try_parse_json(dec)
            if ok:
                best_dec = dec; best_key = key; best_parsed = parsed
                break
            try:
                decomp = zlib.decompress(dec)
                ok2, p2 = try_parse_json(decomp)
                if ok2:
                    best_dec = decomp; best_key = key; best_parsed = p2
                    break
            except: pass
        if best_parsed is not None:
            tag = 'plain' if best_key == 0x00 else ('xor_zlib' if best_dec != (body if best_key == 0x00 else bytes(b ^ best_key for b in body)) else 'xor')
            fn = save_json(msg_type_hex, tag, best_parsed)
            stats[msg_type_hex] += 1
            print(f'[{tag} key=0x{best_key:02x}] {msg_type_hex} -> {fn} ({len(best_dec)}B): {str(best_parsed)[:60]}')
            w = _get_writer()
            if w: w.process_data(msg_type_hex, best_parsed, fn)
            _try_extract_role_from_data(seq, best_parsed, stream_key)
        else:
            fn = save_raw(msg_type_hex, 'xor_raw', body)
            print(f'[xor-raw tried:{[hex(k) for k in candidate_keys]}] {msg_type_hex} body={body[:8].hex()}')

    # ===== 类型 3: zlib =====
    elif data_type == 3:
        candidates = [body, body[4:]] if len(body) > 4 else [body]
        decomp_ok = False
        for body_try in candidates:
            try:
                decompressed = zlib.decompress(body_try)
                ok, parsed = try_parse_json(decompressed)
                if ok:
                    fn = save_json(msg_type_hex, 'zlib', parsed)
                    stats[msg_type_hex] += 1
                    print(f'[zlib] {msg_type_hex} -> {fn} ({len(decompressed)}B)')
                    w = _get_writer()
                    if w: w.process_data(msg_type_hex, parsed, fn)
                    _try_extract_role_from_data(seq, parsed, stream_key)
                else:
                    fn = save_raw(msg_type_hex, 'zlib_raw', decompressed)
                    print(f'[zlib-raw] {msg_type_hex} -> {fn}')
                decomp_ok = True
                break
            except zlib.error:
                pass
        if not decomp_ok:
            for body_try in candidates:
                for trim in [4, 8, 12, 16, 32]:
                    try:
                        decompressed = zlib.decompress(bytes(body_try[:-trim]))
                        ok, parsed = try_parse_json(decompressed)
                        if ok:
                            fn = save_json(msg_type_hex, 'zlib_trim', parsed)
                            stats[msg_type_hex] += 1
                            print(f'[zlib-trim-{trim}] {msg_type_hex} -> {fn}')
                            w = _get_writer()
                            if w: w.process_data(msg_type_hex, parsed, fn)
                            _try_extract_role_from_data(seq, parsed, stream_key)
                            decomp_ok = True
                            break
                    except Exception:
                        pass
                if decomp_ok:
                    break
            if not decomp_ok:
                err_stats['zlib_fail'] += 1
                print(f'[ERR-zlib] {msg_type_hex} len={len(body)} head={body[:8].hex()}')

    # ===== 类型 2: 明文 =====
    elif data_type == 2:
        ok, parsed = try_parse_json(body)
        if ok:
            fn = save_json(msg_type_hex, 'plain', parsed)
            stats[msg_type_hex] += 1
            print(f'[plain] {msg_type_hex} -> {fn} ({len(body)}B)')
            w = _get_writer()
            if w: w.process_data(msg_type_hex, parsed, fn)
            _try_extract_role_from_data(seq, parsed, stream_key)
        else:
            fn = save_raw(msg_type_hex, 'plain_str', body)
            stats[msg_type_hex] += 1
            print(f'[plain-str] {msg_type_hex} -> {fn} ({len(body)}B)')
            w = _get_writer()
            if w: w.process_data(msg_type_hex, body.decode('utf-8', errors='replace'), fn)

    # ===== 其他类型 =====
    else:
        tried_zlib = False
        if len(body) >= 2 and body[0] == 0x78 and body[1] in (0x9c, 0xda, 0x01, 0x5e):
            try:
                decompressed = zlib.decompress(body)
                ok, parsed = try_parse_json(decompressed)
                if ok:
                    fn = save_json(msg_type_hex, f'type{data_type}_zlib', parsed)
                    stats[msg_type_hex] += 1
                    print(f'[type{data_type}-zlib] {msg_type_hex} -> {fn}')
                    w = _get_writer()
                    if w: w.process_data(msg_type_hex, parsed, fn)
                    tried_zlib = True
            except Exception:
                pass
        if not tried_zlib:
            fn = save_raw(msg_type_hex, f'type{data_type}', body)
            stats[msg_type_hex] += 1
            print(f'[type{data_type}] {msg_type_hex} -> {fn} ({len(body)}B)')

    return total


PROFILE_FILE = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'current_profile.json')


def _try_extract_role_from_data(seq, parsed, stream_key):
    """
    从解析后的数据中尝试提取角色/服务器信息并触发绑定。
    0x1395包: [3, role_id, [], role_id, ..., role_name]  -> 索引[1]=role_id, [7]=role_name
    0xe66包:  [200, {'server': ['X5449', ...]}]          -> 索引[1]['server'][0]=server_name
    """
    global _last_sniff_role_id
    if not isinstance(parsed, list) or len(parsed) < 2:
        return
    # 从 stream_key 还原 server_ip
    server_ip = stream_key[0] if stream_key else ''
    src_ip = f'{stream_key[0]}:{stream_key[1]}' if stream_key else ''
    dst_ip = ''

    # 0x1395 包特征: [3, str, list, str, int, ...]
    if (len(parsed) >= 8 and parsed[0] == 3
            and isinstance(parsed[1], str) and isinstance(parsed[2], list)
            and isinstance(parsed[7], str)):
        role_id   = str(parsed[1])
        role_name = str(parsed[7])
        if role_id and role_id != _last_sniff_role_id:
            print(f'[bind] 识别到角色信息: role_id={role_id} role_name={role_name}')
            _last_sniff_role_id = role_id
            with _bind_lock:
                bound_src = _bound_src_ip
            _do_bind(src_ip, dst_ip, server_ip, role_name, '', role_id)
        return

    # 0xe66 包特征: [200, {'server': [...]}]
    if (parsed[0] == 200 and isinstance(parsed[1], dict)
            and 'server' in parsed[1]):
        srv = parsed[1]['server']
        server_name = str(srv[0]) if isinstance(srv, list) and srv else ''
        if server_name:
            # 只更新 server_name，复用已知 role_id
            if _last_sniff_role_id and _last_sniff_role_id != 'pending':
                print(f'[bind] 识别到服务器信息: server_name={server_name}')
                _do_bind(src_ip, dst_ip, server_ip, '', server_name, _last_sniff_role_id)
        return


def _do_bind(src_ip, dst_ip, server_ip, role_name='', server_name='', role_id=''):
    """执行绑定：切换输出目录、写 profile、触发 sniff 重启"""
    global OUTPUT_DIR, _role_bound
    import sys as _sys
    _sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import profile_manager

    # 没有真实 role_id 时先用 IP 注册临时账号，确保数据库和目录存在
    if not role_id:
        new_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               'capture_new', f'stzb_{server_ip.replace(":","_")}')
        os.makedirs(new_dir, exist_ok=True)
        OUTPUT_DIR = new_dir
        print(f'[bind] 报文目录切换为: {OUTPUT_DIR}（等待角色信息）')
        # 注册临时 profile，确保数据库文件和表结构存在
        try:
            p = profile_manager.register_profile(
                server_ip=server_ip,
                role_id='pending',
                role_name='（等待角色信息）',
                server_name=server_ip,
                src_ip=src_ip,
            )
            profile_manager.switch_profile(p['profile_id'])
            print(f'[bind] 临时数据库已建立: {p["db_path"]}')
        except Exception as _e:
            print(f'[bind] 临时数据库建立失败: {_e}')
        _sniff_stop_event.set()
        return

    p = profile_manager.register_profile(
        server_ip=server_ip,
        role_id=role_id,
        role_name=role_name,
        server_name=server_name or server_ip,
        src_ip=src_ip,
    )
    new_dir = p['cap_dir']
    os.makedirs(new_dir, exist_ok=True)
    OUTPUT_DIR = new_dir
    print(f'[bind] 报文目录切换为: {OUTPUT_DIR}')
    profile_manager.switch_profile(p['profile_id'])
    print(f'[bind] 角色: {role_name or "(未知)"}  服务器: {server_name or server_ip}  IP: {src_ip}')
    print(f'[bind] profile_id: {p["profile_id"]}')
    print(f'[bind] 数据库: {p["db_path"]}')
    if role_id:
        _role_bound = True
    _sniff_stop_event.set()


def try_bind_from_packet(packet, buf):
    """
    两阶段绑定：
    阶段1: 抓到第一个有效包，立即用服务器 IP 建子目录（不等主公簿包）
    阶段2: 后续若抓到主公簿包(seq==0xe6)，用角色名+服务器名优化目录名
    """
    global _bound_src_ip, _bound_dst_ip
    if len(buf) < 14:
        return
    from scapy.layers.inet import IP
    if not packet.haslayer(IP):
        return
    try:
        ip_layer  = packet[IP]
        tcp_layer = packet[TCP]
        src_ip    = f'{ip_layer.src}:{tcp_layer.sport}'
        dst_ip    = f'{ip_layer.dst}:{tcp_layer.dport}'

        with _bind_lock:
            already_bound = (_bound_src_ip is not None)

        # ---- 阶段1：第一个包到来，立即用 IP 建目录 ----
        if not already_bound:
            with _bind_lock:
                _bound_src_ip = src_ip
                _bound_dst_ip = dst_ip
            _do_bind(src_ip, dst_ip, ip_layer.src)
            return

        # ---- 阶段2：尝试解析主公簿包，用角色名优化目录名 ----
        if len(buf) < 17:
            return
        seq = struct.unpack('>I', buf[4:8])[0]
        if seq != BIND_CMD_ID:
            return
        data_type = buf[12]
        body = bytes(buf[17:])
        if data_type == 3:
            try: body = zlib.decompress(body)
            except:
                try: body = zlib.decompress(bytes(buf[13:]))
                except: return
        elif data_type == 5:
            body = bytes(b ^ 0x98 for b in body)
            try: body = zlib.decompress(body)
            except: pass
        ok, parsed = try_parse_json(body)
        if not ok or not isinstance(parsed, list) or len(parsed) < 2:
            return
        role_name = ''
        server_name = ''
        role_id = ''
        try:
            data_map = parsed[1] if isinstance(parsed[1], dict) else {}
            server = data_map.get('server', [])
            if isinstance(server, list) and server:
                server_name = str(server[0])
            log_data = data_map.get('log', {})
            if isinstance(log_data, dict):
                rn_raw = str(log_data.get('role_name', ''))
                role_name = rn_raw.split('#')[0] if '#' in rn_raw else rn_raw
                role_id = str(log_data.get('role_id', ''))
        except Exception:
            pass
        if role_name or server_name or role_id:
            # 只在抓包流量中的角色本身发生变化时才自动切换，不覆盖手动切换
            global _last_sniff_role_id
            if role_id and role_id != _last_sniff_role_id:
                print(f'[bind] 抓包角色变化: {_last_sniff_role_id} -> {role_id}')
                _last_sniff_role_id = role_id
                with _bind_lock:
                    _bound_src_ip = src_ip
                    _bound_dst_ip = dst_ip
                _do_bind(src_ip, dst_ip, ip_layer.src, role_name, server_name, role_id)
            elif not _last_sniff_role_id:
                # 首次识别，直接绑定
                _last_sniff_role_id = role_id
            with _bind_lock:
                _bound_src_ip = src_ip
                _bound_dst_ip = dst_ip
                _do_bind(src_ip, dst_ip, ip_layer.src, role_name, server_name, role_id)
    except Exception:
        pass


def process_packet(packet):
    """Scapy 回调：将 payload 加入对应流缓冲，尝试提取完整数据包"""
    if not (packet.haslayer(TCP) and packet.haslayer(Raw)):
        return

    from scapy.layers.inet import IP
    tcp     = packet[TCP]
    payload = bytes(packet[Raw].load)

    # 已绑定时：只处理绑定的那对 IP:port
    with _bind_lock:
        bound_src = _bound_src_ip
        bound_dst = _bound_dst_ip
    if bound_src and bound_dst:
        if packet.haslayer(IP):
            ip_layer = packet[IP]
            src = f'{ip_layer.src}:{tcp.sport}'
            dst = f'{ip_layer.dst}:{tcp.dport}'
            if src != bound_src or dst != bound_dst:
                return

    key = (packet[IP].src if packet.haslayer(IP) else '?', tcp.sport, tcp.dport)
    buf = stream_bufs[key]
    buf.extend(payload)

    # 循环提取完整数据包
    while len(buf) >= MIN_PKT_LEN:
        consumed = process_one_packet(buf, key)
        if consumed == 0:
            break
        elif consumed > 0:
            # 持续尝试从包中获取账号信息（包括账号切换）
                try_bind_from_packet(packet, buf)
            del buf[:consumed]
        else:
            del buf[:1]

    # 缓冲区过大时清空（防内存泄漏）
    if len(buf) > 5 * 1024 * 1024:
        print(f'[WARN] 流 {key} 缓冲区过大 ({len(buf)}B)，清空')
        buf.clear()


def print_statistics():
    print('\n' + '='*60)
    print('消息类型统计')
    print('='*60)
    total = sum(stats.values())
    print(f'总包数: {total}  |  错误: {dict(err_stats)}')
    for t, cnt in sorted(stats.items(), key=lambda x: x[1], reverse=True):
        print(f'  {t}: {cnt} 个 ({cnt/total*100:.1f}%)' if total else f'  {t}: {cnt}')
    print('='*60)


def signal_handler(signum, frame):
    print('\n[*] 停止抓取...')
    print_statistics()
    sys.exit(0)

signal.signal(signal.SIGINT,  signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

print('[*] 率土抓包 v2 启动（两阶段 IP 绑定方案）')
print(f'    输出目录 : {OUTPUT_DIR}')
print(f'    监听端口 : src port {GAME_PORT}（所有网卡）')
print(f'    绑定方式 : 抓到第一个包立即建目录，主公簿包到来后优化目录名')
print('[*] 按 Ctrl+C 停止\n')


def run_sniff():
    global _sniff_stop_event, stream_bufs
    while True:
        _sniff_stop_event.clear()
        stream_bufs.clear()
        with _bind_lock:
            bound = _bound_src_ip
        if bound:
            src_ip = bound.rsplit(':', 1)[0]
            bpf = f'src host {src_ip} and tcp and src port {GAME_PORT}'
            print(f'[sniff] 已绑定，精确过滤: {bpf}')
        else:
            bpf = f'tcp and src port {GAME_PORT}'
            print(f'[sniff] 未绑定，广播监听: {bpf}')
        try:
            sniff(
                filter=bpf,
                prn=process_packet,
                store=0,
                stop_filter=lambda p: _sniff_stop_event.is_set(),
            )
        except Exception as e:
            print(f'[!] 抓取出错: {e}')
            time.sleep(2)
        if _sniff_stop_event.is_set():
            print('[sniff] IP 已绑定/变更，重启抓包...')
            time.sleep(0.5)
            continue
        break


# 延迟导入 realtime_writer，避免循环依赖
_writer = None
def _get_writer():
    global _writer
    if _writer is None:
        try:
            import realtime_writer as _rw
            _writer = _rw.get_writer_instance()
        except Exception:
            pass
    return _writer


try:
    import realtime_writer as _rw
    _writer = _rw.get_writer_instance()
    print('[*] realtime_writer 已就绪，实时解析模式启动')
except Exception as _e:
    print(f'[!] realtime_writer 初始化失败: {_e}，将仅写文件')
    _writer = None

if __name__ == '__main__':
try:
    run_sniff()
except KeyboardInterrupt:
    print_statistics()
 