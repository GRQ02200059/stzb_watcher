
import json
import zlib

def process_hex_data_from_file(input_file: str, output_file: str = "output777.json"):
    """
    从文件中读取十六进制数据，解压缩并保存为 JSON 文件。

    参数:
        input_file (str): 包含十六进制数据的输入文件路径。
        output_file (str): 输出 JSON 文件的路径，默认为 'output45.json'。
    """
    try:
        # 从文件中读取十六进制数据
        with open(input_file, "r", encoding="utf-8") as f:
            tdata = f.read().strip()  # 读取文件内容并去除首尾空格

        # 将十六进制字符串转换为字节数据
        data = bytes.fromhex(tdata)

        # 解压缩数据
        decompressed = zlib.decompress(data)

        # 解析 JSON 数据
        parsed_data = json.loads(decompressed.decode("utf-8"))

        # 将解析后的数据保存为 JSON 文件
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(parsed_data, f, ensure_ascii=False, indent=4)

        print(f"数据已成功处理并保存到文件 '{output_file}' 中。")
    except Exception as e:
        print(f"处理数据时发生错误: {e}")

# 示例用法
if __name__ == "__main__":
    # 输入文件路径（包含十六进制数据的文件）
    input_file = "333"  # 替换为你的输入文件路径

    # 调用函数
    process_hex_data_from_file(input_file)