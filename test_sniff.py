#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
测试抓包脚本 - 需要管理员权限运行
"""
from scapy.all import sniff, TCP, Raw, conf
import zlib

TARGET_IP = "45.253.141.10"
IFACE = "\\Device\\NPF_{190B681E-839D-464F-9B3D-8B2BF633A6BB}"

print(f"默认网卡: {conf.iface}")
print(f"目标IP: {TARGET_IP}")
print(f"监听网卡: {IFACE}")
print("-" * 50)

count = [0]

def cb(p):
    count[0] += 1
    print(f"[{count[0]}] {p.summary()}")
    if p.haslayer(TCP) and p.haslayer(Raw):
        payload = bytes(p[Raw].load)
        print(f"    payload ({len(payload)} bytes): {payload[:40].hex()}")
        if b'\x78\x9c' in payload:
            print("    *** 发现 zlib 数据! ***")
            idx = payload.find(b'\x78\x9c')
            try:
                dec = zlib.decompress(payload[idx:])
                print(f"    解压成功! {len(dec)} bytes")
            except Exception as e:
                print(f"    解压失败: {e}")

print("[模式1] 默认网卡，过滤目标IP (15秒):")
sniff(filter=f"host {TARGET_IP} and tcp", timeout=15, prn=cb, store=0)
print(f"=> {count[0]} 个包\n")

count[0] = 0
print("[模式2] 指定物理网卡，过滤目标IP (15秒):")
sniff(iface=IFACE, filter=f"host {TARGET_IP} and tcp", timeout=15, prn=cb, store=0)
print(f"=> {count[0]} 个包\n")

count[0] = 0
print("[模式3] 指定物理网卡，不过滤，抓所有TCP (5秒):")
sniff(iface=IFACE, filter="tcp", timeout=5, prn=cb, store=0)
print(f"=> {count[0]} 个包\n")

print("完成。按回车退出...")
input()

