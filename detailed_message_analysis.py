#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
详细分析不同消息类型的数据内容
"""

import json
import os

def analyze_specific_message_types():
    """分析具体的消息类型数据"""
    
    # 分析几个重要的消息类型
    message_types = {
        "0000000a": "个人战报",
        "0000005c": "同盟战报", 
        "000000ca": "未知类型",
        "000002bc": "未知类型",
        "000002c7": "未知类型",
        "00000e66": "未知类型",
        "00015f95": "未知类型",
        "unknown": "未知类型"
    }
    
    base_path = "decompressed_data_report"
    
    for msg_type, description in message_types.items():
        folder_path = os.path.join(base_path, msg_type)
        
        if not os.path.exists(folder_path):
            continue
            
        files = [f for f in os.listdir(folder_path) if f.endswith('.json')]
        
        if not files:
            continue
            
        print(f"\n{'='*60}")
        print(f"消息类型: {msg_type} ({description})")
        print(f"文件数量: {len(files)}")
        print(f"{'='*60}")
        
        # 分析第一个文件
        first_file = files[0]
        file_path = os.path.join(folder_path, first_file)
        
        try:
            with open(file_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
            
            print(f"文件: {first_file}")
            print(f"数据类型: {type(data).__name__}")
            
            if isinstance(data, list):
                print(f"数组长度: {len(data)}")
                
                if len(data) > 0:
                    print(f"第一个元素类型: {type(data[0]).__name__}")
                    
                    if isinstance(data[0], dict):
                        print(f"第一个元素字段: {list(data[0].keys())[:10]}")
                        # 显示第一个元素的内容
                        print("第一个元素内容:")
                        for key, value in list(data[0].items())[:5]:
                            print(f"  {key}: {value}")
                    
                    elif isinstance(data[0], list):
                        print(f"第一个元素长度: {len(data[0])}")
                        if len(data[0]) > 0:
                            print(f"第一个子元素类型: {type(data[0][0]).__name__}")
                            if isinstance(data[0][0], dict):
                                print(f"第一个子元素字段: {list(data[0][0].keys())[:10]}")
                
                # 如果是个人战报或同盟战报，显示更多信息
                if msg_type in ["0000000a", "0000005c"]:
                    print(f"\n战报数据分析:")
                    if len(data) > 1 and isinstance(data[1], list):
                        print(f"战斗记录数量: {len(data[1])}")
                        if len(data[1]) > 0 and isinstance(data[1][0], dict):
                            battle_keys = list(data[1][0].keys())
                            print(f"战斗记录字段: {battle_keys[:10]}")
            
            elif isinstance(data, dict):
                print(f"字典字段: {list(data.keys())[:10]}")
                # 显示前几个字段的内容
                print("字典内容:")
                for key, value in list(data.items())[:5]:
                    print(f"  {key}: {value}")
            
            elif isinstance(data, str):
                print(f"字符串长度: {len(data)}")
                print(f"字符串内容: {data[:200]}...")
                
        except Exception as e:
            print(f"读取文件时出错: {e}")

def main():
    """主函数"""
    print("开始详细分析不同消息类型的数据...")
    analyze_specific_message_types()
    print(f"\n分析完成!")

if __name__ == "__main__":
    main()







