import json
import os
from datetime import datetime

from scapy.all import sniff, TCP, Raw
import zlib

# 全局变量维护zlib数据流状态
current_zlib_data = bytearray()
is_collecting = False
output_dir = "decompressed_data_report"  # 存储目录名
current_msg_type = None

# 创建存储目录（如果不存在）
os.makedirs(output_dir, exist_ok=True)

def process_packet(packet):
    global current_zlib_data, is_collecting,current_msg_type

    if packet.haslayer(TCP) and packet.haslayer(Raw):
        print("aaaa")
        payload = bytes(packet[Raw].load)
        print(payload)
        # 检测zlib头部标识（789c对应十六进制）
        if not is_collecting and b'\x78\x9c' in payload:
            is_collecting = True
            start_index = payload.find(b'\x78\x9c')
            current_zlib_data = bytearray(payload[start_index:])
            length = bytearray(payload[0:4])
            # length = int.from_bytes(payload[0:8], 'big')
            # # 提取消息类型字段（后8字节大端）
            msg_type = bytearray(payload[4:8])
            current_msg_type = bytearray(msg_type)
            # msg_type = int.from_bytes(payload[8:16], 'big')
            print(f"[+] ZLIB头发现 | 长度: {length} | 类型: {msg_type}")
            print("[+] 检测到zlib头部，开始收集数据流")

        elif is_collecting:
            # 检测空报文终止条件（这里假设空报文payload长度为0）
            if len(payload) < 30:
                print("stop collect----")
                is_collecting = False
                try:
                    decompressed = zlib.decompress(current_zlib_data)
                    timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
                    filename = f"decompressed_{timestamp}.json"
                    filepath = os.path.join(output_dir, filename)
                    parsed_data = json.loads(decompressed.decode("utf-8"))

                    # 将解析后的数据保存为 JSON 文件
                    if current_msg_type == bytearray(b'\x00\x00\x00\\'):
                        with open(filepath, "w", encoding="utf-8") as f:
                            json.dump(parsed_data, f, ensure_ascii=False, indent=4)
                        # 写入文件
                        print(f"[√] 解压成功：{len(decompressed)}字节 -> {filename}")
                    else:
                        print(current_msg_type)
                    # 在此处添加业务逻辑处理
                    current_zlib_data.clear()
                except zlib.error as e:
                    print(f"解压失败：{str(e)}")
            else:
                current_zlib_data.extend(payload)


# 启动实时嗅探（过滤TCP流量）
sniff(
    filter="src host 42.186.64.43 and tcp",
    prn=process_packet,
    store=0  # 不存储数据包以节省内存
)