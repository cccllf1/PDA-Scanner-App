package com.yourcompany.pdawmsapp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScanResultAdapter(private val scanResults: MutableList<MainActivity.ScanResult>) :
    RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtStatus: TextView = itemView.findViewById(R.id.txtItemStatus)
        val txtBarcode: TextView = itemView.findViewById(R.id.txtItemBarcode)
        val txtTime: TextView = itemView.findViewById(R.id.txtItemTime)
        val txtId: TextView = itemView.findViewById(R.id.txtItemId)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = scanResults[position]
        
        // 状态图标和文字
        val (statusIcon, statusText, statusColor) = when (result.status) {
            "success" -> Triple("✅", "发送成功", Color.parseColor("#4CAF50"))
            "failed" -> Triple("❌", "发送失败", Color.parseColor("#F44336"))
            "sending" -> Triple("⏳", "发送中...", Color.parseColor("#FF9800"))
            else -> Triple("❓", "未知状态", Color.parseColor("#9E9E9E"))
        }
        
        holder.txtStatus.text = "$statusIcon $statusText"
        holder.txtStatus.setTextColor(statusColor)
        
        // 条码内容
        holder.txtBarcode.text = result.barcode
        
        // 时间
        holder.txtTime.text = result.time
        
        // ID
        holder.txtId.text = "#${position + 1}"
        
        // 点击复制功能
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("扫码结果", result.barcode)
            clipboard.setPrimaryClip(clip)
            
            android.widget.Toast.makeText(context, "已复制: ${result.barcode}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount() = scanResults.size
} 