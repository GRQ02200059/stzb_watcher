#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
集成版数据抓取和分析系统
基于您现有的scrapy脚本，增加实时分析功能
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

class IntegratedGameScraper:
    """集成版游戏数据抓取器"""
    
    def __init__(self, output_dir="decompressed_data_report"):
        self.output_dir = output_dir
        self.is_running = False
        
        # 数据统计
        self.stats = {
            'total_packets': 0,
            'zlib_packets': 0,
            'successful_decompressions': 0,
            'failed_decompressions': 0,
            'battle_count': 0,
            'rank_count': 0,
            'system_count': 0
        }
        
        # 实时数据存储
        self.battle_data = []
        self.rank_data = []
        self.system_data = []
        self.all_data = []  # 存储所有数据用于后续分析
        
        # 创建目录
        os.makedirs(output_dir, exist_ok=True)
        os.makedirs("output", exist_ok=True)
        os.makedirs("visualizations", exist_ok=True)
        
        # 设置信号处理
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)
    
    def signal_handler(self, signum, frame):
        """处理中断信号"""
        print("\n正在停止抓取和分析...")
        self.is_running = False
        self.save_all_results()
        sys.exit(0)
    
    def process_packet(self, packet):
        """处理网络数据包"""
        if not self.is_running:
            return
        
        self.stats['total_packets'] += 1
        
        # 每100个数据包显示一次统计
        if self.stats['total_packets'] % 100 == 0:
            self.print_realtime_stats()
        
        if packet.haslayer(TCP) and packet.haslayer(Raw):
            payload = bytes(packet[Raw].load)
            
            # 检测zlib头部标识
            if b'\x78\x9c' in payload:
                self.stats['zlib_packets'] += 1
                print(f"[🔍] 检测到Zlib数据包 | 总包数: {self.stats['total_packets']} | Zlib包数: {self.stats['zlib_packets']}")
                
                try:
                    # 找到zlib数据开始位置
                    start_index = payload.find(b'\x78\x9c')
                    zlib_data = payload[start_index:]
                    
                    # 尝试解压缩
                    decompressed = zlib.decompress(zlib_data)
                    print(f"[📦] 解压缩成功 | 原始大小: {len(payload)} | 解压后: {len(decompressed)}字节")
                    
                    # 解析JSON数据
                    parsed_data = json.loads(decompressed.decode("utf-8"))
                    print(f"[📄] JSON解析成功 | 数据类型: {type(parsed_data)}")
                    
                    # 提取消息类型
                    msg_type = self.extract_message_type(payload)
                    print(f"[🏷️] 消息类型: {msg_type}")
                    
                    # 保存原始数据
                    self.save_raw_data(parsed_data, msg_type)
                    
                    # 实时分析数据
                    self.realtime_analyze(parsed_data, msg_type)
                    
                    self.stats['successful_decompressions'] += 1
                    print(f"[✅] 处理完成 | 成功解压: {self.stats['successful_decompressions']} | 失败: {self.stats['failed_decompressions']}")
                    print("-" * 80)
                    
                except zlib.error as e:
                    self.stats['failed_decompressions'] += 1
                    print(f"[❌] 解压失败: {str(e)}")
                    print(f"[📊] 失败统计 | 成功: {self.stats['successful_decompressions']} | 失败: {self.stats['failed_decompressions']}")
                    print("-" * 80)
                    
                except json.JSONDecodeError as e:
                    self.stats['failed_decompressions'] += 1
                    print(f"[❌] JSON解析失败: {str(e)}")
                    print(f"[📊] 失败统计 | 成功: {self.stats['successful_decompressions']} | 失败: {self.stats['failed_decompressions']}")
                    print("-" * 80)
                    
                except Exception as e:
                    self.stats['failed_decompressions'] += 1
                    print(f"[❌] 其他错误: {str(e)}")
                    print(f"[📊] 失败统计 | 成功: {self.stats['successful_decompressions']} | 失败: {self.stats['failed_decompressions']}")
                    print("-" * 80)
    
    def extract_message_type(self, payload):
        """提取消息类型"""
        try:
            if len(payload) >= 8:
                return payload[4:8].hex()
            else:
                return "unknown"
        except:
            return "unknown"
    
    def save_raw_data(self, data, msg_type):
        """保存原始数据"""
        timestamp = datetime.now().strftime("%Y%m%d%H%M%S%f")[:-3]
        filename = f"decompressed_{timestamp}_{msg_type}.json"
        filepath = os.path.join(self.output_dir, filename)
        
        try:
            with open(filepath, "w", encoding="utf-8") as f:
                json.dump(data, f, ensure_ascii=False, indent=2)
            
            # 存储到内存用于实时分析
            self.all_data.append({
                'filename': filename,
                'data': data,
                'msg_type': msg_type,
                'timestamp': datetime.now(),
                'size': len(str(data))
            })
            
        except Exception as e:
            print(f"[×] 保存文件失败: {str(e)}")
    
    def realtime_analyze(self, data, msg_type):
        """实时分析数据"""
        try:
            print(f"[🔬] 开始实时分析数据...")
            
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
            
            # 每处理10个数据包就更新一次结果
            if len(self.all_data) % 10 == 0:
                print(f"[💾] 更新实时结果... (已处理 {len(self.all_data)} 个数据包)")
                self.update_realtime_results()
            
            print(f"[✅] 实时分析完成")
            
        except Exception as e:
            print(f"[❌] 实时分析出错: {str(e)}")
    
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
            
            # 保存排行榜数据
            if self.rank_data:
                df_rank = pd.DataFrame(self.rank_data)
                df_rank.to_csv('output/rank_data_realtime.csv', index=False, encoding='utf-8-sig')
            
            # 生成实时统计
            self.print_realtime_stats()
            
        except Exception as e:
            print(f"❌ 更新实时结果出错: {str(e)}")
    
    def print_realtime_stats(self):
        """打印实时统计"""
        print(f"\n" + "="*60)
        print(f"📊 实时统计信息 - {datetime.now().strftime('%H:%M:%S')}")
        print(f"="*60)
        print(f"📦 数据包统计:")
        print(f"   总数据包: {self.stats['total_packets']}")
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
        print(f"="*60)
    
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
            
            # 生成可视化图表
            self.create_visualizations()
            
            # 生成最终报告
            self.generate_final_report()
            
            print("✅ 所有结果已保存完成")
            
        except Exception as e:
            print(f"❌ 保存结果出错: {str(e)}")
    
    def create_visualizations(self):
        """创建可视化图表"""
        try:
            # 战斗胜负统计
            if self.battle_data:
                attack_wins = sum(1 for b in self.battle_data if b['result'] == '攻击方胜利')
                defend_wins = sum(1 for b in self.battle_data if b['result'] == '防守方胜利')
                
                if attack_wins + defend_wins > 0:
                    plt.figure(figsize=(10, 6))
                    labels = ['攻击方胜利', '防守方胜利']
                    sizes = [attack_wins, defend_wins]
                    colors = ['#ff9999', '#66b3ff']
                    
                    plt.pie(sizes, labels=labels, colors=colors, autopct='%1.1f%%', startangle=90)
                    plt.title(f'战斗胜负统计 (总计: {len(self.battle_data)} 场)')
                    plt.axis('equal')
                    plt.savefig('visualizations/battle_stats.png', dpi=300, bbox_inches='tight')
                    plt.close()
                    print("✅ 战斗统计图已生成")
            
            # 排行榜前10名
            if self.rank_data:
                top_10 = sorted(self.rank_data, key=lambda x: x.get('score', 0), reverse=True)[:10]
                if top_10:
                    plt.figure(figsize=(12, 8))
                    names = [player['name'][:10] for player in top_10]
                    scores = [player['score'] for player in top_10]
                    
                    plt.barh(range(len(names)), scores, color='gold', edgecolor='orange')
                    plt.yticks(range(len(names)), names)
                    plt.title('排行榜前10名')
                    plt.xlabel('分数')
                    plt.gca().invert_yaxis()
                    plt.tight_layout()
                    plt.savefig('visualizations/rankings.png', dpi=300, bbox_inches='tight')
                    plt.close()
                    print("✅ 排行榜图已生成")
            
        except Exception as e:
            print(f"❌ 生成图表出错: {str(e)}")
    
    def generate_final_report(self):
        """生成最终报告"""
        try:
            report = []
            report.append("# 游戏数据抓取和分析报告")
            report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            report.append("")
            
            # 统计信息
            report.append("## 📊 数据统计")
            report.append(f"- 总数据包数: {self.stats['total_packets']}")
            report.append(f"- 成功解压数: {self.stats['successful_decompressions']}")
            report.append(f"- 解压失败数: {self.stats['failed_decompressions']}")
            report.append(f"- 战斗数据: {len(self.battle_data)} 条")
            report.append(f"- 排行榜数据: {len(self.rank_data)} 条")
            report.append(f"- 系统数据: {len(self.system_data)} 条")
            report.append("")
            
            # 战斗数据统计
            if self.battle_data:
                attack_wins = sum(1 for b in self.battle_data if b['result'] == '攻击方胜利')
                defend_wins = sum(1 for b in self.battle_data if b['result'] == '防守方胜利')
                
                report.append("## ⚔️ 战斗数据统计")
                report.append(f"- 总战斗数: {len(self.battle_data)}")
                report.append(f"- 攻击方胜利: {attack_wins} ({attack_wins/len(self.battle_data)*100:.1f}%)")
                report.append(f"- 防守方胜利: {defend_wins} ({defend_wins/len(self.battle_data)*100:.1f}%)")
                report.append("")
            
            # 排行榜数据统计
            if self.rank_data:
                report.append("## 🏆 排行榜数据统计")
                report.append(f"- 排行榜记录数: {len(self.rank_data)}")
                
                if len(self.rank_data) > 0:
                    top_5 = sorted(self.rank_data, key=lambda x: x.get('score', 0), reverse=True)[:5]
                    report.append("### 前5名玩家")
                    for i, player in enumerate(top_5, 1):
                        report.append(f"{i}. {player['name']} - 分数: {player['score']}")
                report.append("")
            
            # 保存报告
            with open('output/final_analysis_report.md', 'w', encoding='utf-8') as f:
                f.write('\n'.join(report))
            
            print("✅ 分析报告已生成")
            
        except Exception as e:
            print(f"❌ 生成报告出错: {str(e)}")
    
    def start_capture(self, host="42.186.64.43", port=None):
        """开始抓取数据"""
        print("🚀 开始实时数据抓取和分析")
        print("="*60)
        print(f"目标主机: {host}")
        print(f"端口: {port if port else '所有TCP端口'}")
        print("按 Ctrl+C 停止抓取")
        print("="*60)
        
        self.is_running = True
        
        try:
            # 构建过滤器
            if port:
                filter_str = f"src host {host} and tcp port {port}"
            else:
                filter_str = f"src host {host} and tcp"
            
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
    print("🎮 集成版游戏数据抓取和分析系统")
    print("="*60)
    print("功能:")
    print("  ✓ 实时抓取网络数据")
    print("  ✓ 自动解压缩和解析")
    print("  ✓ 实时分类和分析")
    print("  ✓ 实时生成结果文件")
    print("  ✓ 自动生成可视化图表")
    print("="*60)
    
    # 获取用户输入
    host = input("请输入目标主机IP (默认: 42.186.64.43): ").strip() or "42.186.64.43"
    port_input = input("请输入端口 (可选，直接回车跳过): ").strip()
    port = int(port_input) if port_input else None
    
    # 创建抓取器
    scraper = IntegratedGameScraper()
    
    # 开始抓取
    scraper.start_capture(host, port)

if __name__ == "__main__":
    main()
