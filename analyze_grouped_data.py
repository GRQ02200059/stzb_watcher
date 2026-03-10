#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析按消息类型分组的数据
"""

import json
import os
import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
from datetime import datetime
from collections import defaultdict, Counter
import warnings
warnings.filterwarnings('ignore')

# 设置中文字体
plt.rcParams['font.sans-serif'] = ['SimHei', 'Microsoft YaHei']
plt.rcParams['axes.unicode_minus'] = False

class GroupedDataAnalyzer:
    """分组数据分析器"""
    
    def __init__(self, data_dir="decompressed_data_report"):
        self.data_dir = data_dir
        self.message_types = {}
        self.analysis_results = {}
        
        # 创建输出目录
        os.makedirs("output", exist_ok=True)
        os.makedirs("visualizations", exist_ok=True)
    
    def scan_message_types(self):
        """扫描消息类型目录"""
        print("🔍 扫描消息类型目录...")
        
        if not os.path.exists(self.data_dir):
            print(f"❌ 数据目录 {self.data_dir} 不存在")
            return
        
        # 扫描所有子目录
        for item in os.listdir(self.data_dir):
            item_path = os.path.join(self.data_dir, item)
            if os.path.isdir(item_path):
                # 统计该目录下的JSON文件
                json_files = [f for f in os.listdir(item_path) if f.endswith('.json')]
                if json_files:
                    self.message_types[item] = {
                        'count': len(json_files),
                        'files': json_files,
                        'path': item_path
                    }
                    print(f"📁 {item}: {len(json_files)} 个文件")
        
        print(f"✅ 发现 {len(self.message_types)} 种消息类型")
    
    def analyze_message_type(self, msg_type, type_info):
        """分析特定消息类型"""
        print(f"\n🔬 分析消息类型: {msg_type}")
        print("-" * 50)
        
        files = type_info['files']
        path = type_info['path']
        
        # 分析前几个文件
        sample_files = files[:3]  # 只分析前3个文件作为样本
        
        analysis_result = {
            'msg_type': msg_type,
            'total_files': len(files),
            'sample_files': len(sample_files),
            'data_samples': [],
            'data_structure': {},
            'content_analysis': {}
        }
        
        for filename in sample_files:
            filepath = os.path.join(path, filename)
            try:
                with open(filepath, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                # 分析数据结构
                structure = self.analyze_data_structure(data)
                analysis_result['data_samples'].append({
                    'filename': filename,
                    'structure': structure,
                    'data': data
                })
                
            except Exception as e:
                print(f"  ❌ 分析文件 {filename} 失败: {str(e)}")
        
        # 综合分析结果
        if analysis_result['data_samples']:
            self.analyze_data_patterns(analysis_result)
            self.message_types[msg_type]['analysis'] = analysis_result
            print(f"  ✅ 分析完成: {msg_type}")
        else:
            print(f"  ❌ 没有有效数据: {msg_type}")
    
    def analyze_data_structure(self, data):
        """分析数据结构"""
        structure = {
            'type': type(data).__name__,
            'size': len(str(data)),
            'sample_data': None
        }
        
        if isinstance(data, list):
            structure['length'] = len(data)
            if len(data) > 0:
                first_item = data[0]
                structure['first_item_type'] = type(first_item).__name__
                
                if isinstance(first_item, dict):
                    structure['keys'] = list(first_item.keys())[:10]
                    structure['sample_data'] = {k: v for k, v in list(first_item.items())[:3]}
                elif isinstance(first_item, list):
                    structure['nested_length'] = len(first_item)
                    structure['sample_data'] = first_item[:3]
                else:
                    structure['sample_data'] = first_item
        
        elif isinstance(data, dict):
            structure['keys'] = list(data.keys())[:10]
            structure['sample_data'] = {k: v for k, v in list(data.items())[:3]}
        
        return structure
    
    def analyze_data_patterns(self, analysis_result):
        """分析数据模式"""
        samples = analysis_result['data_samples']
        
        # 分析数据类型分布
        type_counts = Counter()
        for sample in samples:
            structure = sample['structure']
            type_counts[structure['type']] += 1
        
        analysis_result['data_structure'] = {
            'type_distribution': dict(type_counts),
            'common_structure': self.find_common_structure(samples)
        }
        
        # 分析内容特征
        content_features = self.extract_content_features(samples)
        analysis_result['content_analysis'] = content_features
    
    def find_common_structure(self, samples):
        """查找共同的数据结构"""
        if not samples:
            return {}
        
        # 分析第一个样本作为基准
        base_structure = samples[0]['structure']
        common_keys = set(base_structure.get('keys', []))
        
        # 查找所有样本的共同键
        for sample in samples[1:]:
            structure = sample['structure']
            sample_keys = set(structure.get('keys', []))
            common_keys = common_keys.intersection(sample_keys)
        
        return {
            'common_keys': list(common_keys),
            'base_type': base_structure['type']
        }
    
    def extract_content_features(self, samples):
        """提取内容特征"""
        features = {
            'has_battle_data': False,
            'has_rank_data': False,
            'has_user_data': False,
            'has_system_data': False,
            'key_indicators': []
        }
        
        for sample in samples:
            data = sample['data']
            structure = sample['structure']
            
            # 检查是否包含战斗数据
            if self.contains_battle_data(data):
                features['has_battle_data'] = True
                features['key_indicators'].append('battle_data')
            
            # 检查是否包含排行榜数据
            if self.contains_rank_data(data):
                features['has_rank_data'] = True
                features['key_indicators'].append('rank_data')
            
            # 检查是否包含用户数据
            if self.contains_user_data(data):
                features['has_user_data'] = True
                features['key_indicators'].append('user_data')
            
            # 检查是否包含系统数据
            if self.contains_system_data(data):
                features['has_system_data'] = True
                features['key_indicators'].append('system_data')
        
        return features
    
    def contains_battle_data(self, data):
        """检查是否包含战斗数据"""
        if isinstance(data, list):
            for item in data:
                if isinstance(item, list) and len(item) > 0:
                    battle_obj = item[0] if isinstance(item[0], dict) else None
                    if battle_obj and 'battle_id' in battle_obj:
                        return True
        return False
    
    def contains_rank_data(self, data):
        """检查是否包含排行榜数据"""
        if isinstance(data, list) and len(data) > 0:
            first_item = data[0]
            if isinstance(first_item, list) and len(first_item) > 5:
                return True
        return False
    
    def contains_user_data(self, data):
        """检查是否包含用户数据"""
        if isinstance(data, list):
            for item in data:
                if isinstance(item, dict) and 'userid' in item:
                    return True
        return False
    
    def contains_system_data(self, data):
        """检查是否包含系统数据"""
        if isinstance(data, list) and len(data) > 0:
            first_item = data[0]
            if isinstance(first_item, (int, float, str)):
                return True
        return False
    
    def generate_analysis_report(self):
        """生成分析报告"""
        print("\n📄 生成分析报告...")
        
        report = []
        report.append("# 按消息类型分组的数据分析报告")
        report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        report.append("")
        
        # 总体统计
        total_files = sum(info['count'] for info in self.message_types.values())
        report.append("## 📊 总体统计")
        report.append(f"- 消息类型数: {len(self.message_types)}")
        report.append(f"- 总文件数: {total_files}")
        report.append("")
        
        # 各消息类型分析
        for msg_type, info in self.message_types.items():
            report.append(f"## 🏷️ 消息类型: {msg_type}")
            report.append(f"- 文件数量: {info['count']}")
            
            if 'analysis' in info:
                analysis = info['analysis']
                report.append(f"- 样本分析: {analysis['sample_files']} 个文件")
                
                # 数据结构
                structure = analysis['data_structure']
                report.append(f"- 数据类型分布: {structure['type_distribution']}")
                
                # 内容特征
                content = analysis['content_analysis']
                if content['key_indicators']:
                    report.append(f"- 内容特征: {', '.join(content['key_indicators'])}")
                
                # 共同结构
                common = structure['common_structure']
                if common.get('common_keys'):
                    report.append(f"- 共同键: {', '.join(common['common_keys'][:5])}")
            else:
                report.append("- 分析状态: 未分析")
            
            report.append("")
        
        # 保存报告
        report_file = "output/grouped_data_analysis_report.md"
        with open(report_file, 'w', encoding='utf-8') as f:
            f.write('\n'.join(report))
        
        print(f"✅ 分析报告已保存: {report_file}")
    
    def create_visualizations(self):
        """创建可视化图表"""
        print("\n📈 创建可视化图表...")
        
        if not self.message_types:
            print("❌ 没有数据可可视化")
            return
        
        # 消息类型分布图
        plt.figure(figsize=(12, 8))
        msg_types = list(self.message_types.keys())
        counts = [info['count'] for info in self.message_types.values()]
        
        plt.bar(msg_types, counts, color='skyblue', edgecolor='navy')
        plt.title('消息类型分布')
        plt.xlabel('消息类型')
        plt.ylabel('文件数量')
        plt.xticks(rotation=45)
        plt.tight_layout()
        plt.savefig('visualizations/message_type_distribution.png', dpi=300, bbox_inches='tight')
        plt.close()
        print("✅ 消息类型分布图已保存")
        
        # 饼图
        if len(msg_types) > 1:
            plt.figure(figsize=(10, 8))
            colors = plt.cm.Set3(range(len(msg_types)))
            plt.pie(counts, labels=msg_types, colors=colors, autopct='%1.1f%%', startangle=90)
            plt.title('消息类型占比')
            plt.axis('equal')
            plt.savefig('visualizations/message_type_pie.png', dpi=300, bbox_inches='tight')
            plt.close()
            print("✅ 消息类型饼图已保存")
    
    def run_analysis(self):
        """运行完整分析"""
        print("🚀 开始分析按消息类型分组的数据")
        print("="*60)
        
        # 扫描消息类型
        self.scan_message_types()
        
        if not self.message_types:
            print("❌ 没有找到消息类型数据")
            return
        
        # 分析每个消息类型
        for msg_type, info in self.message_types.items():
            self.analyze_message_type(msg_type, info)
        
        # 生成报告
        self.generate_analysis_report()
        
        # 创建可视化
        self.create_visualizations()
        
        print("\n✅ 分析完成！")
        print("📁 输出文件:")
        print("  - output/grouped_data_analysis_report.md - 分析报告")
        print("  - visualizations/message_type_distribution.png - 分布图")
        print("  - visualizations/message_type_pie.png - 饼图")

def main():
    """主函数"""
    analyzer = GroupedDataAnalyzer()
    analyzer.run_analysis()

if __name__ == "__main__":
    main()
