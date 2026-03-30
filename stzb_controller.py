# -*- coding: utf-8 -*-
"""
stzb_controller.py
率土之滨游戏控制模块（基于 Win32 API）
支持：截图、点击、滑动、查找窗口
"""
import time
import ctypes
import win32gui
import win32api
import win32con
import win32ui
import win32process
from PIL import Image
import numpy as np

# ===== 窗口查找 =====

GAME_TITLE_KEYWORDS = ['率土之滨', 'Androws', '腾讯应用宝']
# Androws 进程名（应用宝桌面端）
GAME_PROCESS_NAME = 'Androws'

def find_game_hwnd():
    """查找游戏窗口句柄（找 Androws 进程的最大窗口）"""
    import win32process
    import psutil
    candidates = []
    # 找所有 Androws 进程的 pid
    androws_pids = set()
    for proc in psutil.process_iter(['pid', 'name']):
        if 'androws' in proc.info['name'].lower():
            androws_pids.add(proc.info['pid'])

    def cb(hwnd, _):
        try:
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            if pid not in androws_pids:
                return
            title = win32gui.GetWindowText(hwnd)
            rect = win32gui.GetWindowRect(hwnd)
            w = rect[2] - rect[0]
            h = rect[3] - rect[1]
            if w > 200 and h > 200:
                candidates.append((hwnd, title, rect, w * h))
        except:
            pass
    win32gui.EnumWindows(cb, None)
    if not candidates:
        return None, '', (0, 0, 0, 0)
    candidates.sort(key=lambda x: x[3], reverse=True)
    hwnd, title, rect, _ = candidates[0]
    return hwnd, title, rect


class StzController:
    def __init__(self, hwnd=None):
        if hwnd:
            self.hwnd = hwnd
        else:
            self.hwnd, self.title, self.rect = find_game_hwnd()
            if not self.hwnd:
                raise RuntimeError('未找到率土之滨游戏窗口')
        self.rect = win32gui.GetWindowRect(self.hwnd)
        print(f'[ctrl] 窗口: hwnd={self.hwnd} rect={self.rect}')

    @property
    def win_rect(self):
        self.rect = win32gui.GetWindowRect(self.hwnd)
        return self.rect

    def _rel_to_abs(self, x, y):
        """将相对坐标转为屏幕绝对坐标"""
        r = self.win_rect
        return r[0] + x, r[1] + y

    def screenshot(self):
        """截取游戏窗口画面，返回 PIL Image"""
        import win32con
        from PIL import ImageGrab
        # 确保窗口可见
        win32gui.ShowWindow(self.hwnd, win32con.SW_RESTORE)
        time.sleep(0.3)
        r = self.win_rect
        img = ImageGrab.grab(bbox=(r[0], r[1], r[2], r[3]))
        return img

    def click(self, x, y, delay=0.1):
        """在窗口内相对坐标 (x, y) 处点击"""
        lParam = win32api.MAKELONG(x, y)
        win32gui.SendMessage(self.hwnd, win32con.WM_LBUTTONDOWN, win32con.MK_LBUTTON, lParam)
        time.sleep(delay)
        win32gui.SendMessage(self.hwnd, win32con.WM_LBUTTONUP, 0, lParam)
        print(f'[ctrl] 点击 ({x}, {y})')

    def swipe(self, x1, y1, x2, y2, duration=0.5, steps=20):
        """从 (x1,y1) 滑动到 (x2,y2)"""
        for i in range(steps + 1):
            t = i / steps
            cx = int(x1 + (x2 - x1) * t)
            cy = int(y1 + (y2 - y1) * t)
            lParam = win32api.MAKELONG(cx, cy)
            win32gui.SendMessage(self.hwnd, win32con.WM_MOUSEMOVE, win32con.MK_LBUTTON, lParam)
            time.sleep(duration / steps)
        print(f'[ctrl] 滑动 ({x1},{y1}) -> ({x2},{y2})')

    def find_image(self, template_path, threshold=0.8):
        """
        在当前截图中查找模板图片，返回 (x, y) 中心坐标或 None
        需要 opencv-python
        """
        import cv2
        screen = np.array(self.screenshot())
        screen_gray = cv2.cvtColor(screen, cv2.COLOR_RGB2GRAY)
        tpl = cv2.imread(template_path, cv2.IMREAD_GRAYSCALE)
        if tpl is None:
            raise FileNotFoundError(f'模板图片不存在: {template_path}')
        res = cv2.matchTemplate(screen_gray, tpl, cv2.TM_CCOEFF_NORMED)
        _, max_val, _, max_loc = cv2.minMaxLoc(res)
        if max_val >= threshold:
            h, w = tpl.shape
            cx = max_loc[0] + w // 2
            cy = max_loc[1] + h // 2
            print(f'[ctrl] 找到图片 {template_path} at ({cx},{cy}) 相似度={max_val:.2f}')
            return cx, cy
        return None

    def click_image(self, template_path, threshold=0.8, delay=0.1):
        """查找并点击模板图片，找不到返回 False"""
        pos = self.find_image(template_path, threshold)
        if pos:
            self.click(pos[0], pos[1], delay)
            return True
        print(f'[ctrl] 未找到图片: {template_path}')
        return False


# ===== 快捷函数 =====
_ctrl = None

def get_controller():
    global _ctrl
    if _ctrl is None:
        _ctrl = StzController()
    return _ctrl


if __name__ == '__main__':
    ctrl = get_controller()
    print(f'窗口大小: {ctrl.win_rect}')
    img = ctrl.screenshot()
    img.save('screenshot.png')
    print('截图已保存到 screenshot.png')

