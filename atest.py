# import subprocess
# import sys
#
# adb_cmd = '''adb shell "tcpdump -i any 'tcp' -n  port 8001 -w sdcard/temp.pcap"'''
# process = subprocess.Popen(adb_cmd, shell=True)
# # 拉取文件
# subprocess.run('adb pull /sdcard/temp.pcap capture.pcap', shell=True)
# from scapy.all import sniff
#
# def packet_handler(pkt):
#     print(pkt.summary())
#
# # 在另一个线程中运行
# sniff(offline='capture.pcap', prn=packet_handler, store=0)
import subprocess
import sys
import threading

from scapy.utils import RawPcapReader

adb_cmd = [
    'adb', 'exec-out',
    "tcpdump -i any -n -s 0 -w - tcp port 8001 -l -c 100"
]
process = subprocess.Popen(
    adb_cmd,
    shell=False,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    bufsize=0,
    universal_newlines=False  # 或 text=False (Python 3.7+)

)

# 实时打印错误信息

import binascii

# 读取前24字节验证文件头
header = process.stdout.read(24)
magic = binascii.hexlify(header[:4]).decode()
print(f"PCAP 文件头: {magic}")

# 标准 PCAP 文件头应为：
# - 小端模式: d4c3b2a1
# - 大端模式: a1b2c3d4
if magic not in ["d4c3b2a1", "a1b2c3d4"]:
    print("错误：非标准PCAP文件头!")
    sys.exit(1)
# 捕获标准输出
try:
    while True:
        line = process.stdout.readline()
        pcap_reader = RawPcapReader(process.stdout)

        if not line:
            break
        print("ooooooo",line.strip())
except KeyboardInterrupt:
    print("\n捕获已终止")
finally:
    process.terminate()