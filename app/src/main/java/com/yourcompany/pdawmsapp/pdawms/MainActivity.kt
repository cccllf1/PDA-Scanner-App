package com.yourcompany.pdawmsapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.Bundle
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.*
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    
    // UI控件 - 简化版本
    private lateinit var editServerUrl: EditText
    private lateinit var btnConnect: Button
    private lateinit var txtStatus: TextView
    private lateinit var txtCount: TextView
    private lateinit var recyclerScanResults: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var btnRefresh: TextView
    
    // 数据和网络
    private var websocket: WebSocketClient? = null
    private var isConnected = false
    private val scanResults = mutableListOf<ScanResult>()
    private lateinit var scanAdapter: ScanResultAdapter
    
    // 简化网络客户端配置
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    data class ScanResult(
        val id: Long,
        val barcode: String,
        val time: String,
        var status: String,
        val source: String = "PDA"
    )
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initViews()
        setupRecyclerView()
        registerScanReceiver()
        loadSavedSettings()
        
        Log.d("PDA_APP", "📱 Android 6.0 PDA扫码精简版已启动")
        updateStatus("✅ 应用已启动，请配置服务器连接", "ready")
    }
    
    private fun initViews() {
        editServerUrl = findViewById(R.id.editServerUrl)
        btnConnect = findViewById(R.id.btnConnect)
        txtStatus = findViewById(R.id.txtStatus)
        txtCount = findViewById(R.id.txtCount)
        recyclerScanResults = findViewById(R.id.recyclerScanResults)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        btnRefresh = findViewById(R.id.btnRefresh)
        
        btnConnect.setOnClickListener { toggleConnection() }
        btnRefresh.setOnClickListener { refreshDisplay() }
        
        // 长按清空记录
        txtCount.setOnLongClickListener {
            clearAllRecords()
            true
        }
    }
    
    private fun refreshDisplay() {
        refreshServerData()
    }
    

    
    private fun refreshServerData() {
        if (!isConnected) {
            android.widget.Toast.makeText(this, "❌ 请先连接服务器", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        updateStatus("🔄 正在从服务器获取最新数据...", "connecting")
        
        // 从服务器获取最新5条记录
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val serverUrl = editServerUrl.text.toString().trim()
                
                // 使用Chrome扩展相同的API端点
                val endpoints = listOf(
                    "http://$serverUrl/api/scan-results"  // 主要端点，与Chrome扩展一致
                )
                
                var success = false
                var responseBody: String? = null
                var successEndpoint = ""
                
                for (endpoint in endpoints) {
                    try {
                        Log.d("PDA_APP", "🔍 尝试端点: $endpoint")
                        val request = Request.Builder()
                            .url(endpoint)
                            .get()
                            .addHeader("User-Agent", "PDA-Android6-Simple/1.0")
                            .build()
                        
                        val response = okHttpClient.newCall(request).execute()
                        
                        if (response.isSuccessful) {
                            responseBody = response.body()?.string()
                            if (responseBody != null && responseBody.isNotEmpty()) {
                                Log.d("PDA_APP", "✅ 端点成功: $endpoint (响应长度: ${responseBody.length})")
                                Log.d("PDA_APP", "📥 响应内容预览: ${responseBody.take(200)}...")
                                success = true
                                successEndpoint = endpoint
                                break
                            }
                        } else {
                            Log.d("PDA_APP", "❌ 端点失败: $endpoint (状态码: ${response.code()})")
                        }
                    } catch (e: Exception) {
                        Log.d("PDA_APP", "❌ 端点异常: $endpoint (${e.message})")
                    }
                }
                
                if (success && !responseBody.isNullOrEmpty()) {
                    Log.d("PDA_APP", "📥 使用成功端点: $successEndpoint")
                    
                    runOnUiThread {
                        try {
                            // 显示数据来源信息
                            android.widget.Toast.makeText(this@MainActivity, "📡 从 $successEndpoint 获取数据", android.widget.Toast.LENGTH_LONG).show()
                            
                            // 解析服务器返回的数据
                            parseServerResponse(responseBody)
                            updateDisplay()
                            updateStatus("✅ 已获取最新数据 (来源: $successEndpoint)", "success")
                            
                            // 3秒后恢复连接状态
                            GlobalScope.launch(Dispatchers.Main) {
                                delay(3000)
                                if (isConnected) {
                                    updateStatus("✅ 已连接，可以继续扫码", "connected")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PDA_APP", "解析服务器数据失败", e)
                            updateStatus("❌ 数据解析失败", "error")
                            android.widget.Toast.makeText(this@MainActivity, "❌ 数据解析失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        updateStatus("❌ 所有API端点都失败", "error")
                        android.widget.Toast.makeText(this@MainActivity, "❌ 无法获取服务器数据，请检查网络", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("PDA_APP", "刷新数据失败", e)
                runOnUiThread {
                    updateStatus("❌ 刷新失败: ${e.message}", "error")
                    android.widget.Toast.makeText(this@MainActivity, "❌ 刷新失败", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun parseServerResponse(responseBody: String) {
        try {
            Log.d("PDA_APP", "🔄 开始解析服务器数据（Chrome扩展兼容模式）")
            Log.d("PDA_APP", "📥 服务器响应长度: ${responseBody.length}")
            Log.d("PDA_APP", "📥 响应内容前500字符: ${responseBody.take(500)}")
            
            // 清空当前记录
            scanResults.clear()
            
            // 使用简单的JSON解析（Android 6兼容）
            // 查找关键JSON结构：{"success":true,"results":[...]}
            
            var parsedData = false
            
            // 方法1：尝试解析标准JSON响应
            try {
                if (responseBody.contains("\"success\"") && responseBody.contains("\"results\"")) {
                    // 提取results数组内容
                    val resultsStart = responseBody.indexOf("\"results\":")
                    if (resultsStart > 0) {
                        val arrayStart = responseBody.indexOf("[", resultsStart)
                        val arrayEnd = responseBody.lastIndexOf("]")
                        
                        if (arrayStart > 0 && arrayEnd > arrayStart) {
                            val resultsArray = responseBody.substring(arrayStart + 1, arrayEnd)
                            Log.d("PDA_APP", "📋 找到results数组: ${resultsArray.take(200)}")
                            
                            // 解析每个记录对象
                            parseResultsArray(resultsArray)
                            parsedData = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("PDA_APP", "JSON解析失败，尝试其他方式: ${e.message}")
            }
            
            // 方法2：如果JSON解析失败，使用正则表达式提取
            if (!parsedData) {
                Log.d("PDA_APP", "🔄 使用正则表达式解析...")
                
                // 查找条码模式
                val patterns = listOf(
                    "\"barcode\"\\s*:\\s*\"([^\"]+)\"".toRegex(),  // JSON中的barcode字段
                    "\\b\\d{13}\\b".toRegex(),                    // 13位数字条码
                    "\\b\\d{12}\\b".toRegex(),                    // 12位数字条码  
                    "https?://[^\\s<>\"\\}\\]]+".toRegex()       // URL格式
                )
                
                val foundBarcodes = mutableSetOf<String>()
                
                for (pattern in patterns) {
                    val matches = pattern.findAll(responseBody)
                    for (match in matches) {
                        var barcode = if (match.groupValues.size > 1) {
                            match.groupValues[1]  // 提取第一个分组（JSON字段值）
                        } else {
                            match.value
                        }
                        
                        barcode = barcode.trim()
                        if (barcode.length >= 6) {
                            foundBarcodes.add(barcode)
                            if (foundBarcodes.size >= 10) break
                        }
                    }
                    if (foundBarcodes.size >= 10) break
                }
                
                // 按长度排序，取前5个
                val sortedBarcodes = foundBarcodes.sortedByDescending { it.length }.take(5)
                
                sortedBarcodes.forEachIndexed { index, barcode ->
                    val scanResult = ScanResult(
                        id = System.currentTimeMillis() + index,
                        barcode = barcode,
                        time = "服务器数据",
                        status = "success"
                    )
                    scanResults.add(scanResult)
                    Log.d("PDA_APP", "📋 添加服务器记录（正则）: $barcode")
                }
            }
            
            Log.d("PDA_APP", "✅ 解析完成，共获取 ${scanResults.size} 条记录")
        } catch (e: Exception) {
            Log.e("PDA_APP", "解析服务器响应失败", e)
            throw e
        }
    }
    
    private fun parseResultsArray(resultsArray: String) {
        try {
            // 简单解析JSON数组中的对象
            // 查找每个对象: {...}
            val objectPattern = "\\{[^}]+\\}".toRegex()
            val objects = objectPattern.findAll(resultsArray)
            
            var count = 0
            for (obj in objects) {
                if (count >= 5) break
                
                val objStr = obj.value
                Log.d("PDA_APP", "📋 解析对象: $objStr")
                
                // 提取barcode字段
                val barcodeMatch = "\"barcode\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(objStr)
                val barcode = barcodeMatch?.groupValues?.get(1)
                
                // 提取time字段（可能是time或timestamp）
                val timeMatch = "\"(?:time|timestamp)\"\\s*:\\s*\"([^\"]+)\"".toRegex().find(objStr)
                val time = timeMatch?.groupValues?.get(1) ?: "服务器记录"
                
                if (!barcode.isNullOrBlank()) {
                    val scanResult = ScanResult(
                        id = System.currentTimeMillis() + count,
                        barcode = barcode,
                        time = time,
                        status = "success"
                    )
                    scanResults.add(scanResult)
                    count++
                    Log.d("PDA_APP", "📋 添加服务器记录（JSON）: $barcode @ $time")
                }
            }
            
            Log.d("PDA_APP", "✅ JSON解析完成，添加了 $count 条记录")
        } catch (e: Exception) {
            Log.e("PDA_APP", "JSON数组解析失败", e)
            throw e
        }
    }
    
    private fun setupRecyclerView() {
        scanAdapter = ScanResultAdapter(scanResults)
        recyclerScanResults.adapter = scanAdapter
        recyclerScanResults.layoutManager = LinearLayoutManager(this)
        recyclerScanResults.isNestedScrollingEnabled = false  // 禁用嵌套滚动
        updateEmptyState()
    }
    
    private fun toggleConnection() {
        if (isConnected) {
            disconnect()
        } else {
            connect()
        }
    }
    
    private fun connect() {
        val serverUrl = editServerUrl.text.toString().trim()
        if (serverUrl.isEmpty()) {
            updateStatus("❌ 请输入服务器地址", "error")
            return
        }
        
        // 保存设置
        saveSettings()
        
        try {
            val wsUri = URI("ws://$serverUrl/ws/scan-results")
            
            updateStatus("🔄 正在连接服务器...", "connecting")
            btnConnect.text = "🔄 连接中..."
            btnConnect.isEnabled = false
            
            websocket = object : WebSocketClient(wsUri) {
                override fun onOpen(handshake: ServerHandshake?) {
                    runOnUiThread {
                        isConnected = true
                        updateStatus("✅ 已连接到服务器，可以开始扫码", "connected")
                        btnConnect.text = "🔌 断开连接"
                        btnConnect.isEnabled = true
                        editServerUrl.isEnabled = false
                        
                        // 连接成功后自动获取服务器最新数据
                        GlobalScope.launch(Dispatchers.Main) {
                            try {
                                delay(500)  // 等待连接稳定
                                refreshServerData()
                            } catch (e: Exception) {
                                Log.e("PDA_APP", "连接后自动刷新失败", e)
                            }
                        }
                    }
                    Log.d("PDA_APP", "✅ WebSocket连接成功: $serverUrl")
                }
                
                override fun onMessage(message: String?) {
                    Log.d("PDA_APP", "📨 收到服务器消息: $message")
                }
                
                override fun onClose(code: Int, reason: String?, remote: Boolean) {
                    runOnUiThread {
                        isConnected = false
                        updateStatus("🔌 连接已断开", "disconnected")
                        btnConnect.text = "🔗 连接服务器"
                        btnConnect.isEnabled = true
                        editServerUrl.isEnabled = true
                    }
                    Log.d("PDA_APP", "🔌 WebSocket连接断开: $reason")
                }
                
                override fun onError(ex: Exception?) {
                    runOnUiThread {
                        updateStatus("❌ 连接错误: ${ex?.message}", "error")
                        btnConnect.text = "🔗 重新连接"
                        btnConnect.isEnabled = true
                        editServerUrl.isEnabled = true
                    }
                    Log.e("PDA_APP", "❌ WebSocket错误", ex)
                }
            }
            
            websocket?.connect()
            
        } catch (e: Exception) {
            updateStatus("❌ 连接失败: ${e.message}", "error")
            btnConnect.text = "🔗 连接服务器"
            btnConnect.isEnabled = true
            Log.e("PDA_APP", "连接失败", e)
        }
    }
    
    private fun disconnect() {
        websocket?.close()
        websocket = null
        isConnected = false
        updateStatus("🔌 已断开连接", "disconnected")
        btnConnect.text = "🔗 连接服务器"
        editServerUrl.isEnabled = true
    }
    
    private fun registerScanReceiver() {
        val filter = IntentFilter().apply {
            // 通用扫码广播
            addAction("android.intent.action.SCANRESULT")
            addAction("android.intent.ACTION_DECODE_DATA")
            
            // Symbol/Zebra设备
            addAction("com.symbol.datawedge.api.RESULT_ACTION")
            
            // Honeywell设备
            addAction("com.honeywell.decode.intent.action.SCAN_RESULT")
            
            // Newland设备
            addAction("nlscan.action.SCANNER_RESULT")
            
            // iData设备 (重要!)
            addAction("android.intent.action.DECODE_DATA")
            addAction("scan.rcv.message")
            addAction("com.idata.scan.BARCODE_DATA")
            addAction("idata.scan.BARCODE_DATA") 
            
            // 其他常见设备
            addAction("com.android.server.scannerservice.broadcast")
            addAction("com.ubx.android.barcode.service.scan")
            addAction("com.android.decode")
            addAction("ACTION_BAR_SCANCFG")
        }
        
        registerReceiver(scanReceiver, filter)
        Log.d("PDA_APP", "📡 已注册多种PDA设备扫码广播接收器")
        
        // 输出注册的所有广播类型
        val actions = mutableListOf<String>()
        for (i in 0 until filter.countActions()) {
            actions.add(filter.getAction(i))
        }
        Log.d("PDA_APP", "📡 注册的广播类型: ${actions.joinToString(", ")}")
    }
    
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("PDA_APP", "📡 收到广播: action=${intent?.action}, extras=${intent?.extras?.keySet()}")
            try {
                val barcode = extractBarcodeFromIntent(intent)
                if (barcode.isNullOrBlank()) {
                    Log.w("PDA_APP", "⚠️ 未找到有效的扫码数据")
                    return
                }
                
                Log.d("PDA_APP", "🔍 成功解析扫码: $barcode from ${intent?.action}")
                
                // 立即在主线程处理，避免异步问题
                runOnUiThread {
                    try {
                        // 震动反馈
                        try {
                            @Suppress("DEPRECATION")
                            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(100)
                        } catch (e: Exception) {
                            Log.w("PDA_APP", "震动失败: ${e.message}")
                        }
                        
                        // 立即处理扫码结果
                        processScanResultSafely(barcode)
                    } catch (e: Exception) {
                        Log.e("PDA_APP", "主线程处理扫码失败", e)
                        android.widget.Toast.makeText(this@MainActivity, "❌ 扫码处理失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("PDA_APP", "扫码广播接收失败", e)
                try {
                    runOnUiThread {
                        android.widget.Toast.makeText(this@MainActivity, "❌ 扫码接收失败", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (ex: Exception) {
                    Log.e("PDA_APP", "显示错误提示失败", ex)
                }
            }
        }
    }
    
    private fun extractBarcodeFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        
        Log.d("PDA_APP", "🔍 解析Intent: action=${intent.action}")
        
        // 打印所有extras以便调试
        val extras = intent.extras
        if (extras != null) {
            val keySet = extras.keySet()
            Log.d("PDA_APP", "📦 Intent包含的extras: ${keySet.joinToString(", ")}")
            for (key in keySet) {
                val value = extras.get(key)
                Log.d("PDA_APP", "   $key = $value (${value?.javaClass?.simpleName})")
            }
        }
        
        // 尝试各种可能的扫码数据字段
        val result = intent.getStringExtra("value") 
            ?: intent.getStringExtra("barcode_string")
            ?: intent.getStringExtra("com.symbol.datawedge.data_string")
            ?: intent.getStringExtra("com.honeywell.decode.intent.extra.CODE_DATA")
            ?: intent.getStringExtra("SCAN_RESULT")
            ?: intent.getStringExtra("data")
            ?: intent.getStringExtra("code")
            ?: intent.getStringExtra("scannerdata")
            ?: intent.getStringExtra("barcode")
            ?: intent.getStringExtra("Barcode")  // 大写B
            ?: intent.getStringExtra("BARCODE")  // 全大写
            ?: intent.getStringExtra("scan_result")  // 小写
            ?: intent.getStringExtra("decode_data")  // iData常用
            ?: intent.getStringExtra("barcodeString")  // 驼峰命名
            ?: intent.getStringExtra("barcode_data")  // 另一种命名
            ?: extractByteArrayData(intent)
        
        if (result != null) {
            Log.d("PDA_APP", "✅ 成功提取扫码数据: $result")
        } else {
            Log.w("PDA_APP", "❌ 未能从Intent中提取扫码数据")
        }
        
        return result?.trim()
    }
    
    private fun extractByteArrayData(intent: Intent): String? {
        try {
            val barocode = intent.getByteArrayExtra("barocode")
            return if (barocode != null) {
                String(barocode, Charsets.UTF_8).trim()
            } else null
        } catch (e: Exception) {
            Log.w("PDA_APP", "解析字节数组失败: ${e.message}")
            return null
        }
    }
    
    private fun processScanResultSafely(barcode: String) {
        Log.d("PDA_APP", "🔄 开始处理扫码结果: $barcode")
        try {
            // 显示发送状态
            updateStatus("📤 正在发送: $barcode", "sending")
            
            // 发送到服务器
            sendToServer(barcode) { success ->
                try {
                    runOnUiThread {
                        try {
                            Log.d("PDA_APP", "📡 服务器响应: ${if (success) "成功" else "失败"}")
                            
                            // 无论发送成功与否，都立即添加到本地显示
                            addScanResultToLocal(barcode, success)
                            
                            if (success) {
                                updateStatus("✅ 发送成功: $barcode", "success")
                            } else {
                                updateStatus("❌ 发送失败: $barcode", "error")
                            }
                            
                            // 3秒后恢复连接状态
                            GlobalScope.launch(Dispatchers.Main) {
                                try {
                                    delay(3000)
                                    if (isConnected) {
                                        updateStatus("✅ 已连接，可以继续扫码", "connected")
                                    }
                                } catch (e: Exception) {
                                    Log.e("PDA_APP", "延迟更新状态失败", e)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PDA_APP", "UI更新失败", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PDA_APP", "回调处理失败", e)
                }
            }
        } catch (e: Exception) {
            Log.e("PDA_APP", "处理扫码结果失败", e)
            updateStatus("❌ 处理失败: ${e.message}", "error")
            android.widget.Toast.makeText(this, "❌ 处理扫码失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun processScanResult(barcode: String) {
        processScanResultSafely(barcode)
    }
    
    private fun addScanResultToLocal(barcode: String, sendSuccess: Boolean) {
        try {
            Log.d("PDA_APP", "📋 添加扫码记录到本地: $barcode (发送: ${if (sendSuccess) "成功" else "失败"})")
            
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val status = if (sendSuccess) "success" else "failed"
            
            val scanResult = ScanResult(
                id = System.currentTimeMillis(),
                barcode = barcode,
                time = timestamp,
                status = status
            )
            
            // 添加到列表开头（最新的在前面）
            scanResults.add(0, scanResult)
            
            // 保持最多5条记录
            if (scanResults.size > 5) {
                scanResults.removeAt(scanResults.size - 1)
            }
            
            // 立即更新显示
            updateDisplay()
            
            // 显示调试提示
            android.widget.Toast.makeText(this, "📋 已添加: $barcode", android.widget.Toast.LENGTH_SHORT).show()
            
            Log.d("PDA_APP", "✅ 本地记录已添加，当前总数: ${scanResults.size}")
        } catch (e: Exception) {
            Log.e("PDA_APP", "添加本地记录失败", e)
            android.widget.Toast.makeText(this, "❌ 添加记录失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendToServer(barcode: String, callback: (Boolean) -> Unit) {
        if (!isConnected) {
            callback(false)
            return
        }
        
        GlobalScope.launch(Dispatchers.IO) {  // 使用GlobalScope兼容老版本
            try {
                val serverUrl = editServerUrl.text.toString().trim()
                
                val requestBody = FormBody.Builder()
                    .add("room", "DEFAULT")  // 固定使用DEFAULT
                    .add("barcode", barcode)
                    .add("type", "pda-android6-simple")
                    .build()
                
                val request = Request.Builder()
                    .url("http://$serverUrl/mobile-scanner-result")
                    .post(requestBody)
                    .addHeader("User-Agent", "PDA-Android6-Simple/1.0")
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                val success = response.isSuccessful
                
                Log.d("PDA_APP", if (success) "✅ 发送成功: $barcode" else "❌ 发送失败: $barcode (${response.code()})")
                callback(success)
                
            } catch (e: Exception) {
                Log.e("PDA_APP", "发送到服务器失败", e)
                callback(false)
            }
        }
    }
    
    private fun updateStatus(message: String, type: String) {
        txtStatus.text = message
        txtStatus.setBackgroundColor(when (type) {
            "ready" -> Color.parseColor("#FFF3E0")
            "connecting" -> Color.parseColor("#E3F2FD")
            "connected" -> Color.parseColor("#E8F5E8")
            "disconnected" -> Color.parseColor("#F5F5F5")
            "success" -> Color.parseColor("#E8F5E8")
            "error" -> Color.parseColor("#FFEBEE")
            else -> Color.parseColor("#F5F5F5")
        })
        txtStatus.setTextColor(when (type) {
            "ready" -> Color.parseColor("#F57C00")
            "connecting" -> Color.parseColor("#1976D2")
            "connected" -> Color.parseColor("#388E3C")
            "disconnected" -> Color.parseColor("#616161")
            "success" -> Color.parseColor("#388E3C")
            "error" -> Color.parseColor("#D32F2F")
            else -> Color.parseColor("#616161")
        })
    }
    
    private fun updateDisplaySafely() {
        try {
            Log.d("PDA_APP", "🖥️ 更新UI显示，当前记录数: ${scanResults.size}")
            txtCount.text = "扫码记录: ${scanResults.size}/5"
            updateEmptyState()
            
            // 安全地更新RecyclerView
            try {
                scanAdapter.notifyDataSetChanged()
                Log.d("PDA_APP", "✅ RecyclerView更新成功")
            } catch (e: Exception) {
                Log.e("PDA_APP", "RecyclerView更新失败", e)
                // 如果适配器有问题，重新初始化
                try {
                    recyclerScanResults.adapter = scanAdapter
                } catch (ex: Exception) {
                    Log.e("PDA_APP", "重新设置适配器失败", ex)
                }
            }
        } catch (e: Exception) {
            Log.e("PDA_APP", "更新显示失败", e)
        }
    }
    
    private fun updateDisplay() {
        updateDisplaySafely()
    }
    
    private fun updateEmptyState() {
        if (scanResults.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            recyclerScanResults.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            recyclerScanResults.visibility = View.VISIBLE
        }
    }
    
    private fun clearAllRecords() {
        android.app.AlertDialog.Builder(this)  // 使用老版本AlertDialog
            .setTitle("清空记录")
            .setMessage("确定要清空所有扫码记录吗？")
            .setPositiveButton("确定") { _, _ ->
                scanResults.clear()
                updateDisplay()
                android.widget.Toast.makeText(this, "已清空所有记录", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun saveSettings() {
        val prefs = getSharedPreferences("pda_settings", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("server_url", editServerUrl.text.toString().trim())
            .apply()
    }
    
    private fun loadSavedSettings() {
        val prefs = getSharedPreferences("pda_settings", Context.MODE_PRIVATE)
        val savedServerUrl = prefs.getString("server_url", "192.168.11.252:8611") ?: ""
        
        editServerUrl.setText(savedServerUrl)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(scanReceiver)
            websocket?.close()
        } catch (e: Exception) {
            Log.e("PDA_APP", "清理资源失败", e)
        }
    }
} 