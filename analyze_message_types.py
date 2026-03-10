#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
分析不同消息类型的数据内容
"""

import json
import os
from collections import Counter
import pandas as pd

def analyze_message_type_data():
    """分析不同消息类型的数据"""
    
    # 定义要分析的文件夹
    folders = [
        "0000000a", "000000ca", "00000e66", "000002bc", "000002c7", 
        "000002d4", "000002d5", "0000005c", "0000006f", "0000008f", 
        "000010ff", "00015f95", "0000029f", "00000064", "00000067", 
        "00000105", "00000106", "00000383", "unknown"
    ]
    
    base_path = "decompressed_data_report"
    
    analysis_results = []
    
    for folder in folders:
        folder_path = os.path.join(base_path, folder)
        
        if not os.path.exists(folder_path):
            print(f"文件夹不存在: {folder_path}")
            continue
            
        files = [f for f in os.listdir(folder_path) if f.endswith('.json')]
        
        if not files:
            print(f"文件夹为空: {folder}")
            continue
            
        print(f"\n分析消息类型: {folder}")
        print(f"文件数量: {len(files)}")
        
        # 分析前几个文件
        sample_files = files[:3]  # 取前3个文件作为样本
        
        folder_analysis = {
            'message_type': folder,
            'file_count': len(files),
            'sample_analysis': []
        }
        
        for file in sample_files:
            file_path = os.path.join(folder_path, file)
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                
                file_analysis = analyze_single_file(data, file)
                folder_analysis['sample_analysis'].append(file_analysis)
                
            except Exception as e:
                print(f"  读取文件 {file} 时出错: {e}")
        
        analysis_results.append(folder_analysis)
    
    return analysis_results

def analyze_single_file(data, filename):
    """分析单个文件的数据结构"""
    
    analysis = {
        'filename': filename,
        'data_type': type(data).__name__,
        'structure': {},
        'key_fields': [],
        'sample_content': {}
    }
    
    if isinstance(data, list):
        analysis['structure']['type'] = 'list'
        analysis['structure']['length'] = len(data)
        
        if len(data) > 0:
            first_item = data[0]
            analysis['structure']['first_item_type'] = type(first_item).__name__
            
            if isinstance(first_item, dict):
                analysis['key_fields'] = list(first_item.keys())[:10]  # 前10个字段
                # 获取样本内容
                for key in analysis['key_fields'][:5]:  # 前5个字段的样本
                    if key in first_item:
                        analysis['sample_content'][key] = str(first_item[key])[:100]  # 前100字符
    
    elif isinstance(data, dict):
        analysis['structure']['type'] = 'dict'
        analysis['key_fields'] = list(data.keys())[:10]
        
        # 获取样本内容
        for key in analysis['key_fields'][:5]:
            if key in data:
                analysis['sample_content'][key] = str(data[key])[:100]
    
    return analysis

def generate_report(analysis_results):
    """生成分析报告"""
    
    print("\n" + "="*80)
    print("消息类型数据分析报告")
    print("="*80)
    
    for result in analysis_results:
        msg_type = result['message_type']
        file_count = result['file_count']
        
        print(f"\n消息类型: {msg_type}")
        print(f"   文件数量: {file_count}")
        
        if result['sample_analysis']:
            first_sample = result['sample_analysis'][0]
            print(f"   数据结构: {first_sample['data_type']}")
            
            if first_sample['structure']:
                if 'type' in first_sample['structure']:
                    print(f"   数据类型: {first_sample['structure']['type']}")
                if 'length' in first_sample['structure']:
                    print(f"   数组长度: {first_sample['structure']['length']}")
                if 'first_item_type' in first_sample['structure']:
                    print(f"   元素类型: {first_sample['structure']['first_item_type']}")
            
            if first_sample['key_fields']:
                print(f"   主要字段: {', '.join(first_sample['key_fields'][:5])}")
            
            if first_sample['sample_content']:
                print("   样本内容:")
                for key, value in list(first_sample['sample_content'].items())[:3]:
                    print(f"     {key}: {value}")
        
        print("-" * 60)
    
    # 生成CSV报告
    csv_data = []
    for result in analysis_results:
        msg_type = result['message_type']
        file_count = result['file_count']
        
        if result['sample_analysis']:
            first_sample = result['sample_analysis'][0]
            data_type = first_sample['data_type']
            structure_type = first_sample['structure'].get('type', 'unknown')
            key_fields = ', '.join(first_sample['key_fields'][:5]) if first_sample['key_fields'] else ''
            
            csv_data.append({
                '消息类型': msg_type,
                '文件数量': file_count,
                '数据类型': data_type,
                '结构类型': structure_type,
                '主要字段': key_fields
            })
    
    df = pd.DataFrame(csv_data)
    df.to_csv('message_type_analysis.csv', index=False, encoding='utf-8-sig')
    print(f"\n详细分析报告已保存: message_type_analysis.csv")

def main():
    """主函数"""
    print("开始分析不同消息类型的数据...")
    
    analysis_results = analyze_message_type_data()
    generate_report(analysis_results)
    
    print(f"\n分析完成! 共分析了 {len(analysis_results)} 种消息类型")

if __name__ == "__main__":
    main()
