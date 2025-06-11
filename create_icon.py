from PIL import Image, ImageDraw
import os

# 创建不同尺寸的蓝色扫码器图标
sizes = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72, 
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

for folder, size in sizes.items():
    # 创建深蓝色背景
    img = Image.new('RGBA', (size, size), (21, 101, 192, 255))  # 深蓝色
    draw = ImageDraw.Draw(img)
    
    # 绘制扫码框
    frame_size = int(size * 0.6)
    margin = int((size - frame_size) / 2)
    
    # 白色扫码框
    draw.rectangle([margin, margin, margin + frame_size, margin + frame_size], 
                  outline=(255, 255, 255, 255), width=max(1, size//24))
    
    # 四个角的小方框
    corner_size = int(size * 0.08)
    positions = [
        (margin, margin),  # 左上
        (margin + frame_size - corner_size, margin),  # 右上
        (margin, margin + frame_size - corner_size),  # 左下
    ]
    
    for x, y in positions:
        draw.rectangle([x, y, x + corner_size, y + corner_size], 
                      fill=(255, 255, 255, 255))
    
    # 中心的一些点
    center = size // 2
    dot_size = max(1, size // 32)
    for i in range(3):
        for j in range(3):
            if (i + j) % 2 == 0:
                x = center - dot_size + int(i * dot_size * 0.8)
                y = center - dot_size + int(j * dot_size * 0.8)
                draw.rectangle([x, y, x + dot_size, y + dot_size], 
                              fill=(255, 255, 255, 255))
    
    # 扫描线
    line_y = center
    draw.rectangle([margin, line_y - 1, margin + frame_size, line_y + 1], 
                  fill=(255, 87, 34, 255))  # 橙红色扫描线
    
    # 保存文件
    folder_path = f'app/src/main/res/{folder}'
    os.makedirs(folder_path, exist_ok=True)
    
    # 删除旧的webp文件
    old_files = [f'{folder_path}/ic_launcher.webp', f'{folder_path}/ic_launcher_round.webp']
    for old_file in old_files:
        if os.path.exists(old_file):
            os.remove(old_file)
            
    img.save(f'{folder_path}/ic_launcher.png')
    img.save(f'{folder_path}/ic_launcher_round.png')

print('🎨 蓝色扫码器图标创建完成!') 