#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
详细日志版数据抓取器
显示每个数据包的详细信息和分析过程
"""

import json
import os
import zlib
import time
import threading
from datetime import datetime
from collections import defaultdict
import pandas as pd
import matplotlib.pyplot as plt
from scapy.all import *
from scapy.layers.inet import IP, TCP
from scapy.layers.l2 import Ether
import signal
import sys

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class DetailedGameScraper:
    """详细日志版游戏数据抓取器"""
    
    def __init__(self, output_dir="decompressed_data_report"):
        self.output_dir = output_dir
        self.is_running = False
        
        # 数据统计
        self.stats = {
            'total_packets': 0,
            'tcp_packets': 0,
            'zlib_packets': 0,
            'successful_decompressions': 0,
            'failed_decompressions': 0,
            'battle_count': 0,
            'rank_count': 0,
            'system_count': 0,
            'message_types': defaultdict(int)
        }
        
        # 实时数据存储
        self.battle_data = []
        self.rank_data = []
        self.system_data = []
        self.all_data = []
        
        # 创建目录
        os.makedirs(output_dir, exist_ok=True)
        os.makedirs("output", exist_ok=True)
        os.makedirs("visualizations", exist_ok=True)
        
        # 设置信号处理
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)
    
    def signal_handler(self, signum, frame):
        """处理中断信号"""
        print("\n🛑 正在停止抓取和分析...")
        self.is_running = False
        self.save_all_results()
        sys.exit(0)
    
    def process_packet(self, packet):
        """处理网络数据包"""
        if not self.is_running:
            return
        
        self.stats['total_packets'] += 1
        
        # 每50个数据包显示一次统计
        if self.stats['total_packets'] % 50 == 0:
            self.print_packet_stats()
        
        # 检查是否为TCP数据包
        if packet.haslayer(TCP):
            self.stats['tcp_packets'] += 1
            print(f"[📦] TCP数据包 #{self.stats['total_packets']} | 大小: {len(packet)} 字节")
            
            # 检查是否有原始数据
            if packet.haslayer(Raw):
                payload = bytes(packet[Raw].load)
                print(f"[📄] 原始数据大小: {len(payload)} 字节")
                
                # 显示数据包头部信息
                self.print_packet_info(packet, payload)
                
                # 检测zlib头部标识
                if b'\x78\x9c' in payload:
                    self.stats['zlib_packets'] += 1
                    print(f"[🔍] 检测到Zlib数据包 #{self.stats['zlib_packets']}")
                    
                    # 处理zlib数据
                    self.process_zlib_data(payload, packet)
                else:
                    print(f"[ℹ️] 非Zlib数据包，跳过")
            else:
                print(f"[ℹ️] 无原始数据，跳过")
        else:
            print(f"[ℹ️] 非TCP数据包，跳过")
        
        print("-" * 80)
    
    def print_packet_info(self, packet, payload):
        """打印数据包信息"""
        try:
            # 显示IP信息
            if packet.haslayer(IP):
                src_ip = packet[IP].src
                dst_ip = packet[IP].dst
                print(f"[🌐] IP信息: {src_ip} -> {dst_ip}")
            
            # 显示TCP信息
            if packet.haslayer(TCP):
                src_port = packet[TCP].sport
                dst_port = packet[TCP].dport
                print(f"[🔌] TCP端口: {src_port} -> {dst_port}")
            
            # 显示数据包前32字节的十六进制
            hex_data = payload[:32].hex()
            print(f"[🔢] 数据头部: {hex_data}")
            
            # 检查是否有特殊标识
            if b'\x78\x9c' in payload:
                zlib_pos = payload.find(b'\x78\x9c')
                print(f"[🎯] Zlib标识位置: {zlib_pos}")
            
        except Exception as e:
            print(f"[❌] 解析数据包信息失败: {str(e)}")
    
    def process_zlib_data(self, payload, packet):
        """处理Zlib数据"""
        try:
            # 找到zlib数据开始位置
            start_index = payload.find(b'\x78\x9c')
            zlib_data = payload[start_index:]
            
            print(f"[📦] Zlib数据大小: {len(zlib_data)} 字节")
            print(f"[🔍] 开始解压缩...")
            
            # 尝试解压缩
            decompressed = zlib.decompress(zlib_data)
            print(f"[✅] 解压缩成功: {len(decompressed)} 字节")
            
            # 显示解压后的数据前100字符
            preview = decompressed[:100].decode('utf-8', errors='ignore')
            print(f"[👀] 数据预览: {preview}...")
            
            # 解析JSON数据
            print(f"[📄] 开始解析JSON...")
            parsed_data = json.loads(decompressed.decode("utf-8"))
            print(f"[✅] JSON解析成功")
            
            # 提取消息类型
            msg_type = self.extract_message_type(payload)
            self.stats['message_types'][msg_type] += 1
            print(f"[🏷️] 消息类型: {msg_type} (总计: {self.stats['message_types'][msg_type]})")
            
            # 分析数据结构
            self.analyze_data_structure(parsed_data, msg_type)
            
            # 保存原始数据
            self.save_raw_data(parsed_data, msg_type)
            
            # 实时分析数据
            self.realtime_analyze(parsed_data, msg_type)
            
            self.stats['successful_decompressions'] += 1
            print(f"[🎉] 处理完成! 成功解压: {self.stats['successful_decompressions']}")
            
        except zlib.error as e:
            self.stats['failed_decompressions'] += 1
            print(f"[❌] 解压失败: {str(e)}")
            print(f"[📊] 失败统计: {self.stats['failed_decompressions']}")
            
        except json.JSONDecodeError as e:
            self.stats['failed_decompressions'] += 1
            print(f"[❌] JSON解析失败: {str(e)}")
            print(f"[📊] 失败统计: {self.stats['failed_decompressions']}")
            
        except Exception as e:
            self.stats['failed_decompressions'] += 1
            print(f"[❌] 其他错误: {str(e)}")
            print(f"[📊] 失败统计: {self.stats['failed_decompressions']}")
    
    def extract_message_type(self, payload):
        """提取消息类型"""
        try:
            if len(payload) >= 8:
                msg_type = payload[4:8].hex()
                return msg_type
            else:
                return "unknown"
        except:
            return "unknown"
    
    def analyze_data_structure(self, data, msg_type):
        """分析数据结构"""
        print(f"[🔬] 分析数据结构...")
        
        if isinstance(data, list):
            print(f"[📊] 数据类型: 数组 (长度: {len(data)})")
            
            if len(data) > 0:
                first_item = data[0]
                print(f"[📋] 第一个元素类型: {type(first_item).__name__}")
                
                if isinstance(first_item, dict):
                    print(f"[📝] 对象键数量: {len(first_item)}")
                    keys = list(first_item.keys())[:5]
                    print(f"[🔑] 主要键: {keys}")
                    
                    # 根据键名判断数据类型
                    if 'battle_id' in first_item:
                        print(f"[⚔️] 检测到战斗数据!")
                        print(f"[🎯] 战斗ID: {first_item.get('battle_id')}")
                        print(f"[👥] 攻击方: {first_item.get('attack_name', '未知')}")
                        print(f"[🛡️] 防守方: {first_item.get('defend_name', '未知')}")
                    
                    elif 'userid' in first_item:
                        print(f"[👤] 检测到用户数据!")
                        print(f"[🆔] 用户ID: {first_item.get('userid')}")
                    
                    else:
                        print(f"[⚙️] 检测到系统数据!")
                
                elif isinstance(first_item, list):
                    print(f"[📊] 嵌套数组 (内层长度: {len(first_item)})")
                    if len(first_item) > 5:
                        print(f"[🏆] 可能是排行榜数据!")
                    else:
                        print(f"[📋] 简单数组数据")
                
                elif isinstance(first_item, (int, float)):
                    print(f"[🔢] 数值数组")
                    print(f"[📈] 数值范围: {min(data[:10]) if data else 'N/A'} - {max(data[:10]) if data else 'N/A'}")
                
                elif isinstance(first_item, str):
                    print(f"[📝] 字符串数组")
                    print(f"[💬] 第一个字符串: {first_item[:50]}...")
        
        elif isinstance(data, dict):
            print(f"[📝] 数据类型: 对象 (键数量: {len(data)})")
            keys = list(data.keys())[:5]
            print(f"[🔑] 主要键: {keys}")
        
        else:
            print(f"[❓] 未知数据类型: {type(data)}")
    
    def save_raw_data(self, data, msg_type):
        """保存原始数据"""
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
        filename = f"decompressed_{timestamp}_{msg_type}.json"
        filepath = os.path.join(self.output_dir, filename)
        
        try:
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            
            print(f"[💾] 数据已保存: {filename}")
            
            # 存储到内存用于实时分析
            self.all_data.append({
                'filename': filename,
                'data': data,
                'msg_type': msg_type,
                'timestamp': datetime.now(),
                'size': len(str(data))
            })
            
        except Exception as e:
            print(f"[❌] 保存文件失败: {str(e)}")
    
    def realtime_analyze(self, data, msg_type):
        """实时分析数据"""
        print(f"[🔬] 开始实时分析...")
        
        # 分类数据
        data_type = self.classify_data(data)
        print(f"[📊] 数据分类: {data_type}")
        
        if data_type == 'battle':
            print(f"[⚔️] 分析战斗数据...")
            self.analyze_battle_data(data)
        elif data_type == 'rank':
            print(f"[🏆] 分析排行榜数据...")
            self.analyze_rank_data(data)
        else:
            print(f"[⚙️] 分析系统数据...")
            self.analyze_system_data(data, msg_type)
        
        # 每处理5个数据包就更新一次结果
        if len(self.all_data) % 5 == 0:
            print(f"[💾] 更新实时结果... (已处理 {len(self.all_data)} 个数据包)")
            self.update_realtime_results()
        
        print(f"[✅] 实时分析完成")
    
    def classify_data(self, data):
        """分类数据"""
        if isinstance(data, list) and len(data) > 0:
            first_item = data[0]
            
            if isinstance(first_item, dict):
                if 'battle_id' in first_item:
                    return 'battle'
                elif 'userid' in first_item:
                    return 'user'
                else:
                    return 'system'
            
            elif isinstance(first_item, list):
                if len(first_item) > 5:
                    return 'rank'
                else:
                    return 'system'
            
            elif isinstance(first_item, (int, float)):
                return 'system'
        
        return 'system'
    
    def analyze_battle_data(self, data):
        """分析战斗数据"""
        self.stats['battle_count'] += 1
        print(f"[⚔️] 战斗数据计数: {self.stats['battle_count']}")
        
        battle_found = False
        if isinstance(data, list):
            for item in data:
                if isinstance(item, list) and len(item) > 0:
                    battle_obj = item[0] if isinstance(item[0], dict) else None
                    
                    if battle_obj and 'battle_id' in battle_obj:
                        battle_found = True
                        battle_info = {
                            'battle_id': battle_obj.get('battle_id'),
                            'time': battle_obj.get('time'),
                            'result': '攻击方胜利' if battle_obj.get('result', 0) == 1 else '防守方胜利',
                            'attack_name': battle_obj.get('attack_name', ''),
                            'attack_union': battle_obj.get('attack_union_name', ''),
                            'defend_name': battle_obj.get('defend_name', ''),
                            'defend_union': battle_obj.get('defend_union_name', ''),
                            'location': battle_obj.get('wid_name', ''),
                            'timestamp': datetime.now()
                        }
                        
                        self.battle_data.append(battle_info)
                        print(f"[⚔️] 战斗记录: {battle_info['attack_name']} vs {battle_info['defend_name']} - {battle_info['result']}")
                        print(f"[📊] 战斗总数: {len(self.battle_data)}")
        
        if not battle_found:
            print(f"[⚠️] 未找到战斗数据")
    
    def analyze_rank_data(self, data):
        """分析排行榜数据"""
        self.stats['rank_count'] += 1
        print(f"[🏆] 排行榜数据计数: {self.stats['rank_count']}")
        
        rank_found = False
        if isinstance(data, list):
            for item in data:
                if isinstance(item, list) and len(item) > 0:
                    for rank_item in item:
                        if isinstance(rank_item, list) and len(rank_item) > 5:
                            try:
                                player_id = rank_item[0] if len(rank_item) > 0 else 0
                                player_data = rank_item[1] if len(rank_item) > 1 else []
                                
                                if isinstance(player_data, list) and len(player_data) > 5:
                                    rank_found = True
                                    rank_info = {
                                        'player_id': player_id,
                                        'name': player_data[3] if len(player_data) > 3 else '',
                                        'score': player_data[5] if len(player_data) > 5 else 0,
                                        'timestamp': datetime.now()
                                    }
                                    
                                    self.rank_data.append(rank_info)
                                    print(f"[🏆] 排行榜记录: {rank_info['name']} - 分数: {rank_info['score']}")
                                    print(f"[📊] 排行榜总数: {len(self.rank_data)}")
                            except Exception as e:
                                continue
        
        if not rank_found:
            print(f"[⚠️] 未找到排行榜数据")
    
    def analyze_system_data(self, data, msg_type):
        """分析系统数据"""
        self.stats['system_count'] += 1
        self.system_data.append({
            'data': data,
            'msg_type': msg_type,
            'timestamp': datetime.now()
        })
        print(f"[⚙️] 系统数据: {msg_type} | 计数: {self.stats['system_count']}")
        print(f"[📊] 系统数据总数: {len(self.system_data)}")
    
    def update_realtime_results(self):
        """更新实时结果"""
        try:
            # 保存战斗数据
            if self.battle_data:
                df_battle = pd.DataFrame(self.battle_data)
                df_battle.to_csv('output/battle_data_realtime.csv', index=False, encoding='utf-8-sig')
                print(f"[💾] 战斗数据已保存: {len(self.battle_data)} 条")
            
            # 保存排行榜数据
            if self.rank_data:
                df_rank = pd.DataFrame(self.rank_data)
                df_rank.to_csv('output/rank_data_realtime.csv', index=False, encoding='utf-8-sig')
                print(f"[💾] 排行榜数据已保存: {len(self.rank_data)} 条")
            
        except Exception as e:
            print(f"[❌] 更新实时结果出错: {str(e)}")
    
    def print_packet_stats(self):
        """打印数据包统计"""
        print(f"\n" + "="*80)
        print(f"📊 数据包统计 - {datetime.now().strftime('%H:%M:%S')}")
        print(f"="*80)
        print(f"📦 数据包统计:")
        print(f"   总数据包: {self.stats['total_packets']}")
        print(f"   TCP数据包: {self.stats['tcp_packets']}")
        print(f"   Zlib数据包: {self.stats['zlib_packets']}")
        print(f"   成功解压: {self.stats['successful_decompressions']}")
        print(f"   解压失败: {self.stats['failed_decompressions']}")
        print(f"   成功率: {(self.stats['successful_decompressions']/(self.stats['successful_decompressions']+self.stats['failed_decompressions'])*100):.1f}%" if (self.stats['successful_decompressions']+self.stats['failed_decompressions']) > 0 else "   成功率: 0%")
        print(f"")
        print(f"📊 数据分析统计:")
        print(f"   战斗数据: {len(self.battle_data)} 条")
        print(f"   排行榜数据: {len(self.rank_data)} 条")
        print(f"   系统数据: {len(self.system_data)} 条")
        print(f"   总数据文件: {len(self.all_data)} 个")
        print(f"")
        print(f"🏷️ 消息类型统计:")
        for msg_type, count in self.stats['message_types'].items():
            print(f"   {msg_type}: {count} 次")
        print(f"="*80)
    
    def save_all_results(self):
        """保存所有结果"""
        print("\n💾 保存所有分析结果...")
        
        try:
            # 保存战斗数据
            if self.battle_data:
                df_battle = pd.DataFrame(self.battle_data)
                df_battle.to_csv('output/battle_data_final.csv', index=False, encoding='utf-8-sig')
                print(f"✅ 战斗数据已保存: {len(self.battle_data)} 条记录")
            
            # 保存排行榜数据
            if self.rank_data:
                df_rank = pd.DataFrame(self.rank_data)
                df_rank.to_csv('output/rank_data_final.csv', index=False, encoding='utf-8-sig')
                print(f"✅ 排行榜数据已保存: {len(self.rank_data)} 条记录")
            
            # 生成最终报告
            self.generate_final_report()
            
            print("✅ 所有结果已保存完成")
            
        except Exception as e:
            print(f"❌ 保存结果出错: {str(e)}")
    
    def generate_final_report(self):
        """生成最终报告"""
        try:
            report = []
            report.append("# 详细抓取日志分析报告")
            report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            report.append("")
            
            # 统计信息
            report.append("## 📊 抓取统计")
            report.append(f"- 总数据包数: {self.stats['total_packets']}")
            report.append(f"- TCP数据包数: {self.stats['tcp_packets']}")
            report.append(f"- Zlib数据包数: {self.stats['zlib_packets']}")
            report.append(f"- 成功解压数: {self.stats['successful_decompressions']}")
            report.append(f"- 解压失败数: {self.stats['failed_decompressions']}")
            report.append(f"- 成功率: {(self.stats['successful_decompressions']/(self.stats['successful_decompressions']+self.stats['failed_decompressions'])*100):.1f}%" if (self.stats['successful_decompressions']+self.stats['failed_decompressions']) > 0 else "- 成功率: 0%")
            report.append("")
            
            # 消息类型统计
            report.append("## 🏷️ 消息类型统计")
            for msg_type, count in self.stats['message_types'].items():
                report.append(f"- {msg_type}: {count} 次")
            report.append("")
            
            # 数据分析统计
            report.append("## 📊 数据分析统计")
            report.append(f"- 战斗数据: {len(self.battle_data)} 条")
            report.append(f"- 排行榜数据: {len(self.rank_data)} 条")
            report.append(f"- 系统数据: {len(self.system_data)} 条")
            report.append(f"- 总数据文件: {len(self.all_data)} 个")
            report.append("")
            
            # 保存报告
            with open('output/detailed_analysis_report.md', 'w', encoding='utf-8') as f:
                f.write('\n'.join(report))
            
            print("✅ 详细分析报告已生成")
            
        except Exception as e:
            print(f"❌ 生成报告出错: {str(e)}")
    
    def start_capture(self, host="45.253.141.20", port=None):
        """开始抓取数据"""
        print("🚀 开始详细日志数据抓取")
        print("="*80)
        print(f"目标主机: {host}")
        print(f"端口: {port if port else '所有TCP端口'}")
        print("按 Ctrl+C 停止抓取")
        print("="*80)
        
        self.is_running = True
        
        try:
            # 构建过滤器
            if port:
                filter_str = f"src host {host} and tcp port {port}"
            else:
                filter_str = f"src host {host} and tcp"
            
            print(f"🔍 过滤器: {filter_str}")
            print("="*80)
            
            # 开始抓取
            sniff(filter=filter_str, prn=self.process_packet, store=0)
            
        except KeyboardInterrupt:
            print("\n⏹️ 停止抓取")
        except Exception as e:
            print(f"❌ 抓取出错: {str(e)}")
        finally:
            self.is_running = False
            self.save_all_results()

def main():
    """主函数"""
    print("🎮 详细日志版游戏数据抓取器")
    print("="*80)
    print("功能特性:")
    print("  ✓ 详细数据包日志")
    print("  ✓ 实时解压缩过程")
    print("  ✓ 数据结构分析")
    print("  ✓ 消息类型识别")
    print("  ✓ 实时数据分类")
    print("  ✓ 自动保存结果")
    print("="*80)
    
    # 获取用户输入
    host = input("请输入目标主机IP (默认: 42.186.64.43): ").strip() or "42.186.64.43"
    port_input = input("请输入端口 (可选，直接回车跳过): ").strip()
    port = int(port_input) if port_input else None
    
    # 创建抓取器
    scraper = DetailedGameScraper()
    
    # 开始抓取
    scraper.start_capture(host, port)

if __name__ == "__main__":
    main()


