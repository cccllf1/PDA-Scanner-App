# 📱 PDA扫码广播器

专业的仓库管理PDA扫码工具，支持实时数据广播到服务器。

## ✨ 主要功能

- 🔍 **多格式扫码支持**：兼容各种PDA设备的扫码广播
- 🌐 **实时服务器连接**：WebSocket连接，即时数据传输
- 📊 **扫码记录管理**：本地记录显示，支持点击复制
- ⚡ **快速响应**：震动反馈，状态实时更新
- 🔧 **灵活配置**：服务器地址和房间配置

## 🎯 支持的PDA广播格式

- `android.intent.action.SCANRESULT`
- `android.intent.ACTION_DECODE_DATA`
- `com.symbol.datawedge.api.RESULT_ACTION`
- `com.honeywell.decode.intent.action.SCAN_RESULT`
- `nlscan.action.SCANNER_RESULT`
- `scan.rcv.message`
- 更多格式...

## 🚀 使用方法

1. **配置服务器**：输入服务器IP和端口 (如: 192.168.11.252:8611)
2. **设置房间**：输入房间或区域名称 (如: DEFAULT)
3. **连接服务器**：点击连接按钮建立WebSocket连接
4. **开始扫码**：使用PDA设备扫码，数据自动发送到服务器
5. **查看记录**：点击扫码记录可复制条码内容

## 🔗 服务器端API

应用会将扫码数据发送到以下端点：
- **HTTP POST**: `/mobile-scanner-result`
- **WebSocket**: `/ws/scan-results`

### 数据格式

```json
{
  "room": "DEFAULT",
  "barcode": "1234567890123",
  "type": "pda-android"
}
```

## ⚙️ 技术特性

- **现代UI设计**：Material Design组件
- **网络层**：OkHttp + WebSocket
- **数据显示**：RecyclerView高效列表
- **错误处理**：完善的连接和数据处理机制
- **本地存储**：扫码记录本地缓存

## 📋 系统要求

- Android 6.0+ (API 23+)
- 网络权限
- 摄像头权限（PDA扫码）
- 震动权限

## 🛠️ 开发环境

- Android Studio
- Kotlin
- Gradle 8.0+
- Target SDK 35

## 📞 支持

如有问题请联系开发团队或查看服务器日志。

---

© 2025 仓库管理系统 - PDA扫码广播器 