#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时数据抓取和分析系统
一边抓取数据，一边分析，一边存储结果
"""

import json
import os
import zlib
import time
import threading
import queue
from datetime import datetime
from collections import defaultdict, Counter
import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
from scapy.all import *
from scapy.layers.inet import IP, TCP
from scapy.layers.l2 import Ether
import signal
import sys

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class RealtimeGameAnalyzer:
    """实时游戏数据分析器"""
    
    def __init__(self, output_dir="decompressed_data_report"):
        self.output_dir = output_dir
        self.is_running = False
        self.data_queue = queue.Queue()
        self.analysis_queue = queue.Queue()
        
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
        
        # 创建目录
        os.makedirs(output_dir, exist_ok=True)
        os.makedirs("output", exist_ok=True)
        os.makedirs("visualizations", exist_ok=True)
        
        # 设置信号处理
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)
    
    def signal_handler(self, signum, frame):
        """处理中断信号"""
        print("\n正在停止实时分析...")
        self.is_running = False
        self.save_final_results()
        sys.exit(0)
    
    def process_packet(self, packet):
        """处理网络数据包"""
        if not self.is_running:
            return
        
        self.stats['total_packets'] += 1
        
        if packet.haslayer(TCP) and packet.haslayer(Raw):
            payload = bytes(packet[Raw].load)
            
            # 检测zlib头部标识
            if b'\x78\x9c' in payload:
                self.stats['zlib_packets'] += 1
                
                try:
                    # 找到zlib数据开始位置
                    start_index = payload.find(b'\x78\x9c')
                    zlib_data = payload[start_index:]
                    
                    # 尝试解压缩
                    decompressed = zlib.decompress(zlib_data)
                    
                    # 解析JSON数据
                    parsed_data = json.loads(decompressed.decode("utf-8"))
                    
                    # 提取消息类型
                    msg_type = self.extract_message_type(payload)
                    
                    # 将数据放入队列进行实时分析
                    self.data_queue.put({
                        'data': parsed_data,
                        'msg_type': msg_type,
                        'timestamp': datetime.now(),
                        'size': len(decompressed)
                    })
                    
                    self.stats['successful_decompressions'] += 1
                    print(f"[√] 解压成功 | 类型: {msg_type} | 大小: {len(decompressed)}字节")
                    
                except zlib.error as e:
                    self.stats['failed_decompressions'] += 1
                    print(f"[×] 解压失败: {str(e)}")
                    
                except json.JSONDecodeError as e:
                    self.stats['failed_decompressions'] += 1
                    print(f"[×] JSON解析失败: {str(e)}")
                    
                except Exception as e:
                    self.stats['failed_decompressions'] += 1
                    print(f"[×] 其他错误: {str(e)}")
    
    def extract_message_type(self, payload):
        """提取消息类型"""
        try:
            if len(payload) >= 8:
                return payload[4:8].hex()
            else:
                return "unknown"
        except:
            return "unknown"
    
    def realtime_analyzer(self):
        """实时数据分析线程"""
        print("🔍 实时分析线程启动...")
        
        while self.is_running:
            try:
                # 从队列获取数据
                data_item = self.data_queue.get(timeout=1)
                
                # 分析数据类型
                data_type = self.classify_data(data_item['data'])
                
                # 根据类型进行不同处理
                if data_type == 'battle':
                    self.process_battle_data(data_item)
                elif data_type == 'rank':
                    self.process_rank_data(data_item)
                else:
                    self.process_system_data(data_item)
                
                # 定期保存和更新结果
                if len(self.battle_data) % 10 == 0 or len(self.rank_data) % 10 == 0:
                    self.save_realtime_results()
                
                # 定期更新可视化
                if len(self.battle_data) % 20 == 0:
                    self.update_visualizations()
                
            except queue.Empty:
                continue
            except Exception as e:
                print(f"❌ 实时分析出错: {str(e)}")
    
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
    
    def process_battle_data(self, data_item):
        """处理战斗数据"""
        data = data_item['data']
        self.stats['battle_count'] += 1
        
        if isinstance(data, list):
            for item in data:
                if isinstance(item, list) and len(item) > 0:
                    battle_obj = item[0] if isinstance(item[0], dict) else None
                    
                    if battle_obj and 'battle_id' in battle_obj:
                        battle_info = {
                            'battle_id': battle_obj.get('battle_id'),
                            'time': battle_obj.get('time'),
                            'result': '攻击方胜利' if battle_obj.get('result', 0) == 1 else '防守方胜利',
                            'attack_name': battle_obj.get('attack_name', ''),
                            'attack_union': battle_obj.get('attack_union_name', ''),
                            'defend_name': battle_obj.get('defend_name', ''),
                            'defend_union': battle_obj.get('defend_union_name', ''),
                            'location': battle_obj.get('wid_name', ''),
                            'attack_heroes': battle_obj.get('attack_all_hero_info', ''),
                            'defend_heroes': battle_obj.get('defend_all_hero_info', ''),
                            'timestamp': data_item['timestamp']
                        }
                        
                        self.battle_data.append(battle_info)
                        print(f"⚔️ 战斗数据: {battle_info['attack_name']} vs {battle_info['defend_name']} - {battle_info['result']}")
    
    def process_rank_data(self, data_item):
        """处理排行榜数据"""
        data = data_item['data']
        self.stats['rank_count'] += 1
        
        if isinstance(data, list):
            for item in data:
                if isinstance(item, list) and len(item) > 0:
                    for rank_item in item:
                        if isinstance(rank_item, list) and len(rank_item) > 5:
                            try:
                                player_id = rank_item[0] if len(rank_item) > 0 else 0
                                player_data = rank_item[1] if len(rank_item) > 1 else []
                                
                                if isinstance(player_data, list) and len(player_data) > 5:
                                    rank_info = {
                                        'player_id': player_id,
                                        'name': player_data[3] if len(player_data) > 3 else '',
                                        'level': player_data[2] if len(player_data) > 2 else 0,
                                        'rank': player_data[4] if len(player_data) > 4 else 0,
                                        'score': player_data[5] if len(player_data) > 5 else 0,
                                        'timestamp': data_item['timestamp']
                                    }
                                    
                                    self.rank_data.append(rank_info)
                                    print(f"🏆 排行榜数据: {rank_info['name']} - 分数: {rank_info['score']}")
                            except Exception as e:
                                continue
    
    def process_system_data(self, data_item):
        """处理系统数据"""
        self.stats['system_count'] += 1
        self.system_data.append(data_item)
        print(f"⚙️ 系统数据: {data_item['msg_type']} - 大小: {data_item['size']}字节")
    
    def save_realtime_results(self):
        """保存实时结果"""
        try:
            # 保存战斗数据
            if self.battle_data:
                df_battle = pd.DataFrame(self.battle_data)
                df_battle.to_csv('output/battle_data_realtime.csv', index=False, encoding='utf-8-sig')
            
            # 保存排行榜数据
            if self.rank_data:
                df_rank = pd.DataFrame(self.rank_data)
                df_rank.to_csv('output/rank_data_realtime.csv', index=False, encoding='utf-8-sig')
            
            # 保存统计信息
            stats_file = 'output/realtime_stats.json'
            with open(stats_file, 'w', encoding='utf-8') as f:
                json.dump(self.stats, f, ensure_ascii=False, indent=2, default=str)
            
        except Exception as e:
            print(f"❌ 保存实时结果出错: {str(e)}")
    
    def update_visualizations(self):
        """更新可视化图表"""
        try:
            # 战斗胜负统计
            if self.battle_data:
                attack_wins = sum(1 for b in self.battle_data if b['result'] == '攻击方胜利')
                defend_wins = sum(1 for b in self.battle_data if b['result'] == '防守方胜利')
                
                if attack_wins + defend_wins > 0:
                    plt.figure(figsize=(8, 6))
                    labels = ['攻击方胜利', '防守方胜利']
                    sizes = [attack_wins, defend_wins]
                    colors = ['#ff9999', '#66b3ff']
                    
                    plt.pie(sizes, labels=labels, colors=colors, autopct='%1.1f%%', startangle=90)
                    plt.title(f'实时战斗胜负统计 (总计: {len(self.battle_data)} 场)')
                    plt.axis('equal')
                    plt.savefig('visualizations/realtime_battle_stats.png', dpi=300, bbox_inches='tight')
                    plt.close()
            
            # 排行榜前10名
            if self.rank_data:
                top_10 = sorted(self.rank_data, key=lambda x: x.get('score', 0), reverse=True)[:10]
                if top_10:
                    plt.figure(figsize=(10, 6))
                    names = [player['name'][:8] for player in top_10]
                    scores = [player['score'] for player in top_10]
                    
                    plt.barh(range(len(names)), scores, color='gold', edgecolor='orange')
                    plt.yticks(range(len(names)), names)
                    plt.title('实时排行榜前10名')
                    plt.xlabel('分数')
                    plt.gca().invert_yaxis()
                    plt.tight_layout()
                    plt.savefig('visualizations/realtime_rankings.png', dpi=300, bbox_inches='tight')
                    plt.close()
            
        except Exception as e:
            print(f"❌ 更新可视化出错: {str(e)}")
    
    def print_realtime_stats(self):
        """打印实时统计信息"""
        print("\n" + "="*60)
        print("📊 实时统计信息")
        print("="*60)
        print(f"总数据包数: {self.stats['total_packets']}")
        print(f"Zlib数据包数: {self.stats['zlib_packets']}")
        print(f"成功解压数: {self.stats['successful_decompressions']}")
        print(f"解压失败数: {self.stats['failed_decompressions']}")
        print(f"战斗数据: {self.stats['battle_count']} 个")
        print(f"排行榜数据: {self.stats['rank_count']} 个")
        print(f"系统数据: {self.stats['system_count']} 个")
        print(f"战斗记录: {len(self.battle_data)} 条")
        print(f"排行榜记录: {len(self.rank_data)} 条")
        print("="*60)
    
    def save_final_results(self):
        """保存最终结果"""
        print("\n💾 保存最终结果...")
        
        # 保存所有数据
        self.save_realtime_results()
        
        # 生成最终报告
        self.generate_final_report()
        
        print("✅ 最终结果已保存")
    
    def generate_final_report(self):
        """生成最终分析报告"""
        report = []
        report.append("# 实时游戏数据分析报告")
        report.append(f"分析时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")
        
        # 统计信息
        report.append("## 📊 数据统计")
        report.append(f"- 总数据包数: {self.stats['total_packets']}")
        report.append(f"- 成功解压数: {self.stats['successful_decompressions']}")
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
        with open('output/realtime_analysis_report.md', 'w', encoding='utf-8') as f:
            f.write('\n'.join(report))
    
    def start_realtime_analysis(self, host="42.186.64.43", port=None):
        """开始实时分析"""
        print("🚀 启动实时数据抓取和分析系统")
        print("="*60)
        print(f"目标主机: {host}")
        print(f"端口: {port if port else '所有TCP端口'}")
        print("按 Ctrl+C 停止分析")
        print("="*60)
        
        self.is_running = True
        
        # 启动分析线程
        analyzer_thread = threading.Thread(target=self.realtime_analyzer)
        analyzer_thread.daemon = True
        analyzer_thread.start()
        
        # 启动统计显示线程
        stats_thread = threading.Thread(target=self.stats_display_loop)
        stats_thread.daemon = True
        stats_thread.start()
        
        try:
            # 构建过滤器
            if port:
                filter_str = f"src host {host} and tcp port {port}"
            else:
                filter_str = f"src host {host} and tcp"
            
            # 开始抓取
            sniff(filter=filter_str, prn=self.process_packet, store=0)
            
        except KeyboardInterrupt:
            print("\n⏹️ 停止实时分析")
        except Exception as e:
            print(f"❌ 抓取出错: {str(e)}")
        finally:
            self.is_running = False
            self.save_final_results()
    
    def stats_display_loop(self):
        """统计信息显示循环"""
        while self.is_running:
            time.sleep(30)  # 每30秒显示一次统计
            if self.is_running:
                self.print_realtime_stats()

def main():
    """主函数"""
    print("🎮 实时游戏数据抓取和分析系统")
    print("="*60)
    
    # 获取用户输入
    host = input("请输入目标主机IP (默认: 42.186.64.43): ").strip() or "42.186.64.43"
    port_input = input("请输入端口 (可选，直接回车跳过): ").strip()
    port = int(port_input) if port_input else None
    
    # 创建分析器
    analyzer = RealtimeGameAnalyzer()
    
    # 开始实时分析
    analyzer.start_realtime_analysis(host, port)

if __name__ == "__main__":
    main()
