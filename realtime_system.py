#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
实时游戏数据抓取和分析系统
完整版本：实时抓取 + 实时日志 + 实时分析 + 实时可视化
"""

import json
import os
import zlib
import time
import threading
import queue
from datetime import datetime
from collections import defaultdict, Counter
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from scapy.all import *
from scapy.layers.inet import IP, TCP
from scapy.layers.l2 import Ether
import signal
import sys
import warnings
warnings.filterwarnings('ignore')

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class RealtimeGameSystem:
    """实时游戏数据抓取和分析系统"""
    
    def __init__(self, output_dir="decompressed_data_report"):
        self.output_dir = output_dir
        self.is_running = False
        
        # 数据队列
        self.packet_queue = queue.Queue()
        self.analysis_queue = queue.Queue()
        
        # 数据统计
        self.stats = {
            'start_time': datetime.now(),
            'total_packets': 0,
            'tcp_packets': 0,
            'zlib_packets': 0,
            'successful_decompressions': 0,
            'failed_decompressions': 0,
            'battle_count': 0,
            'rank_count': 0,
            'system_count': 0,
            'message_types': defaultdict(int),
            'last_update': datetime.now()
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
        os.makedirs("logs", exist_ok=True)
        
        # 设置信号处理
        signal.signal(signal.SIGINT, self.signal_handler)
        signal.signal(signal.SIGTERM, self.signal_handler)
        
        # 启动时间
        self.start_time = time.time()
    
    def signal_handler(self, signum, frame):
        """处理中断信号"""
        print("\n🛑 正在停止实时系统...")
        self.is_running = False
        self.save_final_results()
        sys.exit(0)
    
    def log_message(self, level, message, data=None):
        """统一日志记录"""
        timestamp = datetime.now().strftime('%H:%M:%S.%f')[:-3]
        log_entry = f"[{timestamp}] [{level}] {message}"
        
        # 打印到控制台
        print(log_entry)
        
        # 保存到日志文件
        log_file = f"logs/realtime_{datetime.now().strftime('%Y%m%d')}.log"
        with open(log_file, 'a', encoding='utf-8') as f:
            f.write(log_entry + '\n')
            if data:
                f.write(f"   数据: {str(data)[:200]}...\n")
    
    def process_packet(self, packet):
        """处理网络数据包"""
        if not self.is_running:
            return
        
        self.stats['total_packets'] += 1
        
        # 每100个数据包显示统计
        if self.stats['total_packets'] % 100 == 0:
            self.print_realtime_stats()
        
        if packet.haslayer(TCP):
            self.stats['tcp_packets'] += 1
            
            if packet.haslayer(Raw):
                payload = bytes(packet[Raw].load)
                
                # 检测zlib数据
                if b'\x78\x9c' in payload:
                    self.stats['zlib_packets'] += 1
                    self.log_message("INFO", f"检测到Zlib数据包 #{self.stats['zlib_packets']}")
                    
                    # 将数据包放入队列进行异步处理
                    self.packet_queue.put({
                        'packet': packet,
                        'payload': payload,
                        'timestamp': datetime.now()
                    })
    
    def packet_processor(self):
        """数据包处理线程"""
        self.log_message("INFO", "数据包处理线程启动")
        
        while self.is_running:
            try:
                # 从队列获取数据包
                packet_data = self.packet_queue.get(timeout=1)
                
                # 处理数据包
                self.process_zlib_packet(packet_data)
                
            except queue.Empty:
                continue
            except Exception as e:
                self.log_message("ERROR", f"数据包处理出错: {str(e)}")
    
    def process_zlib_packet(self, packet_data):
        """处理Zlib数据包"""
        packet = packet_data['packet']
        payload = packet_data['payload']
        
        try:
            # 找到zlib数据开始位置
            start_index = payload.find(b'\x78\x9c')
            zlib_data = payload[start_index:]
            
            self.log_message("INFO", f"开始解压缩Zlib数据，大小: {len(zlib_data)} 字节")
            
            # 解压缩
            decompressed = zlib.decompress(zlib_data)
            self.log_message("SUCCESS", f"解压缩成功，解压后大小: {len(decompressed)} 字节")
            
            # 解析JSON
            parsed_data = json.loads(decompressed.decode("utf-8"))
            self.log_message("SUCCESS", f"JSON解析成功，数据类型: {type(parsed_data)}")
            
            # 提取消息类型
            msg_type = self.extract_message_type(payload)
            self.stats['message_types'][msg_type] += 1
            self.log_message("INFO", f"消息类型: {msg_type} (总计: {self.stats['message_types'][msg_type]})")
            
            # 保存原始数据
            self.save_raw_data(parsed_data, msg_type)
            
            # 将数据放入分析队列
            self.analysis_queue.put({
                'data': parsed_data,
                'msg_type': msg_type,
                'timestamp': datetime.now(),
                'size': len(decompressed)
            })
            
            self.stats['successful_decompressions'] += 1
            
        except zlib.error as e:
            self.stats['failed_decompressions'] += 1
            self.log_message("ERROR", f"解压缩失败: {str(e)}")
            
        except json.JSONDecodeError as e:
            self.stats['failed_decompressions'] += 1
            self.log_message("ERROR", f"JSON解析失败: {str(e)}")
            
        except Exception as e:
            self.stats['failed_decompressions'] += 1
            self.log_message("ERROR", f"处理数据包失败: {str(e)}")
    
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
            
            self.log_message("SUCCESS", f"数据已保存: {filename}")
            
            # 存储到内存
            self.all_data.append({
                'filename': filename,
                'data': data,
                'msg_type': msg_type,
                'timestamp': datetime.now(),
                'size': len(str(data))
            })
            
        except Exception as e:
            self.log_message("ERROR", f"保存文件失败: {str(e)}")
    
    def realtime_analyzer(self):
        """实时数据分析线程"""
        self.log_message("INFO", "实时分析线程启动")
        
        while self.is_running:
            try:
                # 从队列获取数据
                analysis_data = self.analysis_queue.get(timeout=1)
                
                # 分析数据
                self.analyze_data_realtime(analysis_data)
                
            except queue.Empty:
                continue
            except Exception as e:
                self.log_message("ERROR", f"实时分析出错: {str(e)}")
    
    def analyze_data_realtime(self, analysis_data):
        """实时分析数据"""
        data = analysis_data['data']
        msg_type = analysis_data['msg_type']
        
        self.log_message("INFO", f"开始分析数据，类型: {msg_type}")
        
        # 分类数据
        data_type = self.classify_data(data)
        self.log_message("INFO", f"数据分类: {data_type}")
        
        if data_type == 'battle':
            self.analyze_battle_data_realtime(data)
        elif data_type == 'rank':
            self.analyze_rank_data_realtime(data)
        else:
            self.analyze_system_data_realtime(data, msg_type)
        
        # 每处理5个数据就更新结果
        if len(self.all_data) % 5 == 0:
            self.update_realtime_results()
        
        self.log_message("SUCCESS", f"数据分析完成")
    
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
    
    def analyze_battle_data_realtime(self, data):
        """实时分析战斗数据"""
        self.stats['battle_count'] += 1
        self.log_message("INFO", f"分析战斗数据 #{self.stats['battle_count']}")
        
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
                            'timestamp': datetime.now()
                        }
                        
                        self.battle_data.append(battle_info)
                        self.log_message("BATTLE", f"战斗记录: {battle_info['attack_name']} vs {battle_info['defend_name']} - {battle_info['result']}")
    
    def analyze_rank_data_realtime(self, data):
        """实时分析排行榜数据"""
        self.stats['rank_count'] += 1
        self.log_message("INFO", f"分析排行榜数据 #{self.stats['rank_count']}")
        
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
                                        'score': player_data[5] if len(player_data) > 5 else 0,
                                        'timestamp': datetime.now()
                                    }
                                    
                                    self.rank_data.append(rank_info)
                                    self.log_message("RANK", f"排行榜记录: {rank_info['name']} - 分数: {rank_info['score']}")
                            except Exception as e:
                                continue
    
    def analyze_system_data_realtime(self, data, msg_type):
        """实时分析系统数据"""
        self.stats['system_count'] += 1
        self.system_data.append({
            'data': data,
            'msg_type': msg_type,
            'timestamp': datetime.now()
        })
        self.log_message("INFO", f"系统数据: {msg_type} (总计: {self.stats['system_count']})")
    
    def update_realtime_results(self):
        """更新实时结果"""
        try:
            # 保存战斗数据
            if self.battle_data:
                df_battle = pd.DataFrame(self.battle_data)
                df_battle.to_csv('output/battle_data_realtime.csv', index=False, encoding='utf-8-sig')
                self.log_message("SUCCESS", f"战斗数据已更新: {len(self.battle_data)} 条")
            
            # 保存排行榜数据
            if self.rank_data:
                df_rank = pd.DataFrame(self.rank_data)
                df_rank.to_csv('output/rank_data_realtime.csv', index=False, encoding='utf-8-sig')
                self.log_message("SUCCESS", f"排行榜数据已更新: {len(self.rank_data)} 条")
            
            # 更新可视化
            self.update_visualizations()
            
        except Exception as e:
            self.log_message("ERROR", f"更新实时结果失败: {str(e)}")
    
    def update_visualizations(self):
        """更新可视化图表"""
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
                    plt.title(f'实时战斗胜负统计 (总计: {len(self.battle_data)} 场)')
                    plt.axis('equal')
                    plt.savefig('visualizations/realtime_battle_stats.png', dpi=300, bbox_inches='tight')
                    plt.close()
                    self.log_message("SUCCESS", "战斗统计图已更新")
            
            # 排行榜前10名
            if self.rank_data:
                top_10 = sorted(self.rank_data, key=lambda x: x.get('score', 0), reverse=True)[:10]
                if top_10:
                    plt.figure(figsize=(12, 8))
                    names = [player['name'][:10] for player in top_10]
                    scores = [player['score'] for player in top_10]
                    
                    plt.barh(range(len(names)), scores, color='gold', edgecolor='orange')
                    plt.yticks(range(len(names)), names)
                    plt.title('实时排行榜前10名')
                    plt.xlabel('分数')
                    plt.gca().invert_yaxis()
                    plt.tight_layout()
                    plt.savefig('visualizations/realtime_rankings.png', dpi=300, bbox_inches='tight')
                    plt.close()
                    self.log_message("SUCCESS", "排行榜图已更新")
            
        except Exception as e:
            self.log_message("ERROR", f"更新可视化失败: {str(e)}")
    
    def print_realtime_stats(self):
        """打印实时统计"""
        runtime = time.time() - self.start_time
        print(f"\n" + "="*80)
        print(f"📊 实时系统统计 - 运行时间: {runtime:.1f}秒")
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
    
    def save_final_results(self):
        """保存最终结果"""
        self.log_message("INFO", "保存最终结果...")
        
        try:
            # 保存战斗数据
            if self.battle_data:
                df_battle = pd.DataFrame(self.battle_data)
                df_battle.to_csv('output/battle_data_final.csv', index=False, encoding='utf-8-sig')
                self.log_message("SUCCESS", f"战斗数据已保存: {len(self.battle_data)} 条记录")
            
            # 保存排行榜数据
            if self.rank_data:
                df_rank = pd.DataFrame(self.rank_data)
                df_rank.to_csv('output/rank_data_final.csv', index=False, encoding='utf-8-sig')
                self.log_message("SUCCESS", f"排行榜数据已保存: {len(self.rank_data)} 条记录")
            
            # 生成最终报告
            self.generate_final_report()
            
            self.log_message("SUCCESS", "所有结果已保存完成")
            
        except Exception as e:
            self.log_message("ERROR", f"保存结果出错: {str(e)}")
    
    def generate_final_report(self):
        """生成最终报告"""
        try:
            runtime = time.time() - self.start_time
            report = []
            report.append("# 实时游戏数据抓取和分析报告")
            report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            report.append(f"运行时间: {runtime:.1f} 秒")
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
            with open('output/realtime_analysis_report.md', 'w', encoding='utf-8') as f:
                f.write('\n'.join(report))
            
            self.log_message("SUCCESS", "分析报告已生成")
            
        except Exception as e:
            self.log_message("ERROR", f"生成报告出错: {str(e)}")
    
    def start_realtime_system(self, host="42.186.64.43", port=None):
        """启动实时系统"""
        print("🚀 启动实时游戏数据抓取和分析系统")
        print("="*80)
        print(f"目标主机: {host}")
        print(f"端口: {port if port else '所有TCP端口'}")
        print("按 Ctrl+C 停止系统")
        print("="*80)
        
        self.is_running = True
        
        # 启动数据包处理线程
        packet_thread = threading.Thread(target=self.packet_processor)
        packet_thread.daemon = True
        packet_thread.start()
        
        # 启动实时分析线程
        analysis_thread = threading.Thread(target=self.realtime_analyzer)
        analysis_thread.daemon = True
        analysis_thread.start()
        
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
            
            self.log_message("INFO", f"开始抓取，过滤器: {filter_str}")
            
            # 开始抓取
            sniff(filter=filter_str, prn=self.process_packet, store=0)
            
        except KeyboardInterrupt:
            self.log_message("INFO", "用户停止系统")
        except Exception as e:
            self.log_message("ERROR", f"系统运行出错: {str(e)}")
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
    print("="*80)
    print("功能特性:")
    print("  ✓ 实时网络数据抓取")
    print("  ✓ 详细实时日志记录")
    print("  ✓ 实时数据分析和分类")
    print("  ✓ 实时结果文件更新")
    print("  ✓ 实时可视化图表")
    print("  ✓ 多线程并发处理")
    print("="*80)
    
    # 获取用户输入
    host = input("请输入目标主机IP (默认: 42.186.64.43): ").strip() or "42.186.64.43"
    port_input = input("请输入端口 (可选，直接回车跳过): ").strip()
    port = int(port_input) if port_input else None
    
    # 创建实时系统
    system = RealtimeGameSystem()
    
    # 启动系统
    system.start_realtime_system(host, port)

if __name__ == "__main__":
    main()


