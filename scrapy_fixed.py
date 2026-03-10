#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
改进版抓取脚本 - 修复版本
"""

import json
import os
from datetime import datetime
from collections import defaultdict

from scapy.all import sniff, TCP, Raw
import zlib

# 全局变量维护zlib数据流状态
current_zlib_data = bytearray()
is_collecting = False
output_dir = "decompressed_data_report"  # 存储目录名
current_msg_type = None

# 消息类型统计
message_type_stats = defaultdict(int)

# 创建存储目录（如果不存在）
os.makedirs(output_dir, exist_ok=True)

# 创建按消息类型分组的子目录
def create_message_type_dirs():
    """创建消息类型目录"""
    message_types = [
        "000002bc",  # 战斗数据
        "000010ff",  # 系统状态
        "000015f95", # 其他类型
        "00018697",  # 新发现类型
        "000002c7",  # 新发现类型
        "unknown"    # 未知类型
    ]
    
    for msg_type in message_types:
        type_dir = os.path.join(output_dir, msg_type)
        os.makedirs(type_dir, exist_ok=True)
        print(f"[+] 创建消息类型目录: {type_dir}")

def ensure_message_type_dir(msg_type):
    """确保消息类型目录存在"""
    type_dir = os.path.join(output_dir, msg_type)
    if not os.path.exists(type_dir):
        os.makedirs(type_dir, exist_ok=True)
        print(f"[+] 动态创建消息类型目录: {type_dir}")
    return type_dir

# 初始化目录
create_message_type_dirs()

def print_statistics():
    """打印消息类型统计"""
    print("\n" + "="*60)
    print("📊 消息类型统计")
    print("="*60)
    total_count = sum(message_type_stats.values())
    print(f"总处理数据包: {total_count}")
    print("消息类型分布:")
    for msg_type, count in sorted(message_type_stats.items(), key=lambda x: x[1], reverse=True):
        percentage = (count / total_count * 100) if total_count > 0 else 0
        print(f"  {msg_type}: {count} 个 ({percentage:.1f}%)")
    print("="*60)

def generate_final_report():
    """生成最终报告"""
    print("\n📄 生成最终报告...")
    
    report = []
    report.append("# 游戏数据抓取报告")
    report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append("")
    
    # 统计信息
    total_count = sum(message_type_stats.values())
    report.append("## 📊 抓取统计")
    report.append(f"- 总处理数据包: {total_count}")
    report.append("")
    
    # 消息类型统计
    report.append("## 🏷️ 消息类型统计")
    for msg_type, count in sorted(message_type_stats.items(), key=lambda x: x[1], reverse=True):
        percentage = (count / total_count * 100) if total_count > 0 else 0
        report.append(f"- {msg_type}: {count} 个 ({percentage:.1f}%)")
    report.append("")
    
    # 文件统计
    report.append("## 📁 文件统计")
    for msg_type in message_type_stats.keys():
        type_dir = os.path.join(output_dir, msg_type)
        if os.path.exists(type_dir):
            files = [f for f in os.listdir(type_dir) if f.endswith('.json')]
            report.append(f"- {msg_type}/: {len(files)} 个文件")
    report.append("")
    
    # 保存报告
    report_file = os.path.join(output_dir, "capture_report.md")
    with open(report_file, 'w', encoding='utf-8') as f:
        f.write('\n'.join(report))
    
    print(f"✅ 报告已保存: {report_file}")

def process_packet(packet):
    global current_zlib_data, is_collecting, current_msg_type

    if packet.haslayer(TCP) and packet.haslayer(Raw):
        payload = bytes(packet[Raw].load)
        
        # 检测zlib头部标识（789c对应十六进制）
        if not is_collecting and b'\x78\x9c' in payload:
            is_collecting = True
            start_index = payload.find(b'\x78\x9c')
            current_zlib_data = bytearray(payload[start_index:])
            
            # 提取消息类型字段
            if len(payload) >= 8:
                msg_type = bytearray(payload[4:8])
                current_msg_type = msg_type
                print(f"[+] ZLIB头发现 | 类型: {msg_type.hex()}")
            else:
                current_msg_type = bytearray(b'\x00\x00\x00\x00')
                print(f"[+] ZLIB头发现 | 类型: 未知")
            
            print("[+] 检测到zlib头部，开始收集数据流")

        elif is_collecting:
            # 检测终止条件：小数据包、包含结束标识或数据包长度异常
            if (len(payload) < 10 or 
                b'\x00\x00\x00\x00' in payload or 
                len(current_zlib_data) > 100000):  # 防止数据过大
                print(f"[+] 停止收集，数据包长度: {len(payload)}, 总长度: {len(current_zlib_data)}")
                is_collecting = False
                
                try:
                    # 尝试解压缩
                    decompressed = zlib.decompress(current_zlib_data)
                    timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
                    
                    # 检查解压后的数据是否为空
                    if len(decompressed) == 0:
                        print(f"[×] 解压后数据为空")
                        return
                    
                    # 获取消息类型
                    msg_type = current_msg_type.hex() if current_msg_type else "unknown"
                    message_type_stats[msg_type] += 1
                    
                    # 确保消息类型目录存在
                    type_dir = ensure_message_type_dir(msg_type)
                    filename = f"decompressed_{timestamp}_{msg_type}.json"
                    filepath = os.path.join(type_dir, filename)
                    
                    # 解析JSON数据
                    parsed_data = json.loads(decompressed.decode("utf-8"))
                    
                    # 保存数据到对应消息类型目录
                    with open(filepath, "w", encoding="utf-8") as f:
                        json.dump(parsed_data, f, ensure_ascii=False, indent=4)
                    
                    print(f"[√] 解压成功：{len(decompressed)}字节 -> {msg_type}/{filename}")
                    print(f"[√] 消息类型: {msg_type} (总计: {message_type_stats[msg_type]})")
                    
                    # 每处理10个数据包显示统计
                    if sum(message_type_stats.values()) % 10 == 0:
                        print_statistics()
                    
                except zlib.error as e:
                    print(f"[×] 解压失败：{str(e)}")
                    print(f"[×] 数据长度: {len(current_zlib_data)}")
                    print(f"[×] 数据前100字节: {current_zlib_data[:100].hex()}")
                    print(f"[×] 数据后100字节: {current_zlib_data[-100:].hex()}")
                    # 尝试修复数据
                    if len(current_zlib_data) > 100:
                        # 尝试截取部分数据
                        try:
                            fixed_data = current_zlib_data[:-50]  # 去掉最后50字节
                            decompressed = zlib.decompress(fixed_data)
                            print(f"[√] 修复后解压成功，长度: {len(decompressed)}")
                            # 继续处理修复后的数据
                            timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
                            msg_type = current_msg_type.hex() if current_msg_type else "unknown"
                            message_type_stats[msg_type] += 1
                            type_dir = ensure_message_type_dir(msg_type)
                            filename = f"decompressed_{timestamp}_{msg_type}_fixed.json"
                            filepath = os.path.join(type_dir, filename)
                            parsed_data = json.loads(decompressed.decode("utf-8"))
                            with open(filepath, "w", encoding="utf-8") as f:
                                json.dump(parsed_data, f, ensure_ascii=False, indent=4)
                            print(f"[√] 修复数据已保存: {msg_type}/{filename}")
                        except Exception as fix_e:
                            print(f"[×] 修复失败: {str(fix_e)}")
                except json.JSONDecodeError as e:
                    print(f"[×] JSON解析失败：{str(e)}")
                    print(f"[×] 解压数据前200字符: {decompressed[:200]}")
                    print(f"[×] 解压数据后200字符: {decompressed[-200:]}")
                except Exception as e:
                    print(f"[×] 其他错误：{str(e)}")
                    print(f"[×] 错误类型: {type(e).__name__}")
                finally:
                    # 清理状态
                    current_zlib_data.clear()
                    current_msg_type = None
            else:
                # 继续收集数据
                current_zlib_data.extend(payload)
                print(f"[+] 收集数据中，当前长度: {len(current_zlib_data)}")


# 信号处理
import signal
import sys

def signal_handler(signum, frame):
    """处理中断信号"""
    print("\n🛑 正在停止抓取...")
    print_statistics()
    generate_final_report()
    print("👋 抓取已停止")
    sys.exit(0)

# 设置信号处理
signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

# 启动实时嗅探（过滤TCP流量）
print("🚀 开始监听网络流量...")
print("="*60)
print("过滤条件: src host 42.186.64.43 and tcp")
print("数据将按消息类型分组存储")
print("按 Ctrl+C 停止抓取")
print("="*60)

try:
    sniff(
        filter="src host 42.186.64.43 and tcp",
        prn=process_packet,
        store=0  # 不存储数据包以节省内存
    )
except KeyboardInterrupt:
    print("\n🛑 用户停止抓取")
    print_statistics()
    generate_final_report()
except Exception as e:
    print(f"\n❌ 抓取出错: {str(e)}")
    print_statistics()
    generate_final_report()


