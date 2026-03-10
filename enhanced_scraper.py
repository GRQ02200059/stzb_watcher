#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
增强版游戏数据抓取器
整合所有抓取功能，支持实时抓取和数据分析
"""

import json
import os
import zlib
import time
from datetime import datetime
from scapy.all import *
from scapy.layers.inet import IP, TCP
from scapy.layers.l2 import Ether
import threading
import queue
import signal
import sys

class EnhancedGameScraper:
    """增强版游戏数据抓取器"""
    
    def __init__(self, output_dir="decompressed_data_report"):
        self.output_dir = output_dir
        self.is_running = False
        self.data_queue = queue.Queue()
        self.message_types = {}
        self.stats = {
            'total_packets': 0,
            'zlib_packets': 0,
            'successful_decompressions': 0,
            'failed_decompressions': 0,
            'message_types': {}
        }
        # TCP流重组缓冲区，key=(src_ip, src_port, dst_ip, dst_port)
        self.tcp_streams = {}
        
        # 创建输出目录
        os.makedirs(output_dir, exist_ok=True)
        
        # 设置信号处理
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)
    
    def signal_handler(self, signum, frame):
        """处理中断信号"""
        print("\n正在停止抓取...")
        self.is_running = False
        sys.exit(0)
    
    def try_decompress_stream(self, stream_key, buf):
        """尝试从缓冲区中找到并解压所有完整的zlib数据块"""
        offset = 0
        found_any = False
        while offset < len(buf):
            idx = buf.find(b'\x78\x9c', offset)
            if idx == -1:
                break
            # 提取消息类型（从zlib头前4字节）
            msg_type_raw = buf[max(0, idx-4):idx] if idx >= 4 else buf[:idx]
            msg_type = msg_type_raw[-4:].hex() if len(msg_type_raw) >= 4 else "unknown"
            # 尝试从idx开始解压，逐步扩展长度
            for end in range(idx + 2, len(buf) + 1):
                try:
                    decompressed = zlib.decompress(buf[idx:end])
                    try:
                        parsed_data = json.loads(decompressed.decode("utf-8"))
                        self.save_data(parsed_data, msg_type)
                        self.stats['successful_decompressions'] += 1
                        self.stats['message_types'][msg_type] = self.stats['message_types'].get(msg_type, 0) + 1
                        print(f"[√] 解压成功 | 类型: {msg_type} | 大小: {len(decompressed)}字节")
                        found_any = True
                        offset = end  # 跳过已处理的数据
                        break
                    except (json.JSONDecodeError, UnicodeDecodeError):
                        offset = end
                        break
                except zlib.error:
                    continue
            else:
                # 整个剩余buf都试过了还不行，等待更多数据
                break
            if not found_any or offset <= idx:
                offset = idx + 1
        return found_any

    def process_packet(self, packet):
        """处理网络数据包，含TCP流重组"""
        if not self.is_running:
            return

        self.stats['total_packets'] += 1

        if packet.haslayer(TCP) and packet.haslayer(Raw):
            tcp = packet[TCP]
            ip  = packet['IP'] if packet.haslayer('IP') else None
            if ip is None:
                return

            # 用四元组作为流标识
            stream_key = (ip.src, tcp.sport, ip.dst, tcp.dport)
            payload = bytes(tcp.payload)

            if b'\x78\x9c' not in payload and stream_key not in self.tcp_streams:
                return  # 不含zlib且无缓存，跳过

            # 追加到流缓冲区
            if stream_key not in self.tcp_streams:
                self.tcp_streams[stream_key] = b''
            self.tcp_streams[stream_key] += payload

            if b'\x78\x9c' not in self.tcp_streams[stream_key]:
                return

            self.stats['zlib_packets'] += 1

            # 尝试解压
            found = self.try_decompress_stream(stream_key, self.tcp_streams[stream_key])

            if found:
                # 清空已处理的流缓冲，保留尾部未处理部分
                self.tcp_streams[stream_key] = b''
            else:
                # 缓冲区超过 512KB 还没成功，清掉防止内存泄漏
                if len(self.tcp_streams[stream_key]) > 512 * 1024:
                    print(f"[!] 流 {stream_key} 缓冲区过大，清空")
                    self.tcp_streams[stream_key] = b''
    
    def extract_message_type(self, payload):
        """提取消息类型"""
        try:
            if len(payload) >= 8:
                # 从payload中提取消息类型字段
                msg_type = payload[4:8].hex()
                return msg_type
            else:
                return "unknown"
        except:
            return "unknown"
    
    def save_data(self, data, msg_type):
        """保存数据到文件"""
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
        filename = f"decompressed_{timestamp}_{msg_type}.json"
        filepath = os.path.join(self.output_dir, filename)
        
        try:
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            
            # 记录消息类型信息
            self.message_types[msg_type] = {
                'count': self.message_types.get(msg_type, {}).get('count', 0) + 1,
                'last_seen': timestamp,
                'sample_data': data[:100] if isinstance(data, (list, dict)) else str(data)[:100]
            }
            
        except Exception as e:
            print(f"[×] 保存文件失败: {str(e)}")
    
    def start_capture(self, host="42.186.64.43", port=None):
        """开始抓取数据"""
        print(f"开始抓取游戏数据...")
        print(f"目标主机: {host}")
        print(f"按 Ctrl+C 停止抓取")
        print("-" * 50)
        
        self.is_running = True
        
        # 构建过滤器
        if port:
            filter_str = f"src host {host} and tcp port {port}"
        else:
            filter_str = f"src host {host} and tcp"
        
        try:
            # 开始抓取
            sniff(filter=filter_str, prn=self.process_packet, store=0)
        except KeyboardInterrupt:
            print("\n抓取已停止")
        except Exception as e:
            print(f"抓取出错: {str(e)}")
        finally:
            self.is_running = False
            self.print_stats()
    
    def print_stats(self):
        """打印统计信息"""
        print("\n" + "="*50)
        print("抓取统计信息")
        print("="*50)
        print(f"总数据包数: {self.stats['total_packets']}")
        print(f"Zlib数据包数: {self.stats['zlib_packets']}")
        print(f"成功解压数: {self.stats['successful_decompressions']}")
        print(f"解压失败数: {self.stats['failed_decompressions']}")
        
        if self.stats['message_types']:
            print("\n消息类型统计:")
            for msg_type, count in self.stats['message_types'].items():
                print(f"  {msg_type}: {count} 次")
        
        if self.message_types:
            print("\n消息类型详情:")
            for msg_type, info in self.message_types.items():
                print(f"  {msg_type}:")
                print(f"    次数: {info['count']}")
                print(f"    最后出现: {info['last_seen']}")
                print(f"    样本数据: {info['sample_data']}")
    
    def analyze_existing_data(self):
        """分析现有数据"""
        print("分析现有数据...")
        
        if not os.path.exists(self.output_dir):
            print(f"数据目录 {self.output_dir} 不存在")
            return
        
        files = [f for f in os.listdir(self.output_dir) if f.endswith('.json')]
        print(f"发现 {len(files)} 个数据文件")
        
        # 按消息类型分组
        type_groups = defaultdict(list)
        
        for filename in files:
            if '_' in filename:
                parts = filename.split('_')
                if len(parts) >= 3:
                    msg_type = parts[2].replace('.json', '')
                    type_groups[msg_type].append(filename)
        
        print("\n消息类型分组:")
        for msg_type, file_list in type_groups.items():
            print(f"  {msg_type}: {len(file_list)} 个文件")
        
        return type_groups

def main():
    """主函数"""
    scraper = EnhancedGameScraper()
    
    print("游戏数据抓取器")
    print("1. 开始实时抓取")
    print("2. 分析现有数据")
    print("3. 退出")
    
    choice = input("请选择操作 (1-3): ").strip()
    
    if choice == "1":
        host = input("请输入目标主机IP (默认: 45.253.141.10): ").strip() or "45.253.141.10"
        port_input = input("请输入端口 (默认: 8001): ").strip() or "8001"
        
        try:
            port = int(port_input)
        except ValueError:
            print("端口格式错误，将使用默认设置")
            port = 8001
        
        scraper.start_capture(host, port)
    
    elif choice == "2":
        scraper.analyze_existing_data()
    
    elif choice == "3":
        print("退出程序")
    
    else:
        print("无效选择")

if __name__ == "__main__":
    import sys
    if len(sys.argv) == 3:
        # 支持命令行参数直接启动: python enhanced_scraper.py <host> <port>
        _host = sys.argv[1]
        _port = int(sys.argv[2])
        s = EnhancedGameScraper()
        s.start_capture(_host, _port)
    else:
        main()


