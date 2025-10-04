import re
import json
import os
import requests
import time
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support import expected_conditions as EC
from selenium.webdriver.support.ui import WebDriverWait
from selenium.common.exceptions import NoSuchElementException, TimeoutException
from webdriver_manager.chrome import ChromeDriverManager
from selenium.webdriver.chrome.service import Service
from urllib.parse import urljoin


def setup_driver():
    """初始化浏览器配置"""
    chrome_options = webdriver.ChromeOptions()
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--no-sandbox")
    service = Service(ChromeDriverManager().install())
    return webdriver.Chrome(service=service, options=chrome_options)


def handle_pagination(driver):
    """分页控制逻辑"""
    try:
        next_btn = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable(
                (By.XPATH, '//*[@id="btn_next"]')
            )
        )
        driver.execute_script("arguments[0].scrollIntoView();", next_btn)
        next_btn.click()

        # 等待新内容加载
        return True
    except (TimeoutException, NoSuchElementException):
        return False


def sanitize_filename(name):
    """清理非法文件名"""
    return re.sub(r'[\\/*?:"<>|]', "_", name).strip()[:50]


def download_image(url, save_path):
    """下载图片文件"""
    try:
        response = requests.get(url, stream=True, timeout=15)
        if response.status_code == 200:
            with open(save_path, 'wb') as f:
                for chunk in response.iter_content(1024):
                    f.write(chunk)
            return True
    except Exception as e:
        print(f"下载失败: {str(e)}")
    return False


def main_crawler(start_url):
    driver = setup_driver()
    driver.get(start_url)

    # 初始化存储
    os.makedirs("game_cards", exist_ok=True)
    metadata = {}
    page_count = 1

    while True:
        print(f"\n正在处理第 {page_count} 页")

        try:
            # 等待主要内容加载

            # 处理当前页数据
            ul_element = driver.find_element(By.XPATH, '/html/body/div[3]/div[2]/div[2]/ul')
            items = ul_element.find_elements(By.XPATH, "./li")
            print(f"发现 {len(items)} 个卡片项")

            for idx, item in enumerate(items, 1):
                try:
                    img = item.find_element(By.TAG_NAME, 'img')
                    src = img.get_attribute('src')
                    alt = img.get_attribute('alt') or f"未命名_{page_count}_{idx}"

                    # 提取卡片ID
                    if (match := re.search(r'card_small_(\d+)\.jpg', src)):
                        card_id = match.group(1)

                        # 生成文件名
                        clean_alt = sanitize_filename(alt)
                        filename = f"{card_id}_{clean_alt}.jpg"
                        save_path = os.path.join("game_cards", filename)

                        # 处理重复文件名
                        if os.path.exists(save_path):
                            version = 1
                            while os.path.exists(save_path):
                                new_name = f"{card_id}_{clean_alt}_{version}.jpg"
                                save_path = os.path.join("game_cards", new_name)
                                version += 1

                        # 下载并保存元数据
                        if download_image(urljoin(start_url, src), save_path):
                            metadata[card_id] = {
                                "alt": alt,
                                "path": save_path,
                                "page": page_count,
                                "position": idx
                            }

                except NoSuchElementException:
                    print(f"第 {idx} 项缺少图片元素，跳过")
                    continue

            # 保存元数据
            with open('metadata.json', 'a+', encoding='utf-8') as f:
                json.dump(metadata, f, ensure_ascii=False, indent=2)

            # 尝试翻页
            page_count += 1
            if not handle_pagination(driver):
                print("\n已到达最后一页")
                break

            # 防止高频请求
            time.sleep(2)

        except TimeoutException:
            print("页面加载超时，终止爬取")
            break

    driver.quit()
    return metadata


if __name__ == "__main__":
    target_url = "https://stzb.163.com/card_list.html"
    result = main_crawler(target_url)
    print(f"\n总计抓取 {len(result)} 张卡片")
    print("示例数据:", dict(list(result.items())[:3]))