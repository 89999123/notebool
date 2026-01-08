package com.wanghuixian.a202305100122.myapplication.room

import androidx.room.Entity
import androidx.room.PrimaryKey

// 用户表，存储账号、密码、昵称
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val account: String,       // 登录账号（唯一）
    val nickname: String,      // 个人昵称
    val password: String       // 登录密码
)