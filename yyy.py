import subprocess
import sys
import signal
from scapy.utils import RawPcapReader
from scapy.layers.inet import IP, TCP


def realtime_capture_and_parse():
    # 1. 定义adb命令（关键：使用-w -输出二进制流）
    adb_cmd = [
        "adb", "exec-out",
        "su -c 'tcpdump -i any -s 0 -w - -y LINUX_SLL \"tcp port 8001\"'"
    ]

    # 2. 启动进程（二进制模式，禁用缓冲）
    process = subprocess.Popen(
        adb_cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        bufsize=0,
        shell=False  # 禁用Shell解析
    )

    # 3. 捕获Ctrl+C信号
    def signal_handler(sig, frame):
        print("\n捕获终止...")
        process.terminate()
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)

    # 4. 实时解析PCAP流
    try:
        pcap_reader = RawPcapReader(process.stdout)
        packet_count = 0

        print("开始实时解析（Ctrl+C停止）...")
        for pkt_data, _ in pcap_reader:  # 自动处理PCAP文件头
            packet_count += 1

            # 解析IP/TCP层
            ip = IP(pkt_data)
            if not ip.haslayer(TCP):
                continue
            tcp = ip[TCP]

            # 输出关键信息
            print(f"\n[包 {packet_count}] {ip.src}:{tcp.sport} -> {ip.dst}:{tcp.dport}")
            print(f"SEQ: {tcp.seq} | ACK: {tcp.ack} | Flags: {tcp.flags}")

            # 显示前64字节载荷（HEX格式）
            if tcp.payload:
                payload = bytes(tcp.payload)[:64]
                print(f"Payload: {payload.hex(' ', 1)}")

    except Exception as e:
        print(f"解析错误: {str(e)}")
    finally:
        process.terminate()


if __name__ == "__main__":
    realtime_capture_and_parse()