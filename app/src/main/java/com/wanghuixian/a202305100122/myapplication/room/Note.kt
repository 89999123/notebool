package com.wanghuixian.a202305100122.myapplication.room

import androidx.room.Entity
import androidx.room.PrimaryKey

// 附件数据类（嵌套存储）
data class Attachment(
    val type: String, // 图片/视频/语音
    val path: String  // 文件路径/URI
)

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteName: String,
    val noteContent: String,
    val createTime: String,
    // 新增：存储附件列表（JSON字符串）
    val attachments: String = "[]"
)