package com.wanghuixian.a202305100122.myapplication.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {
    // 注册用户（新增）
    @Insert
    suspend fun insertUser(user: User)

    // 根据账号查询用户（登录校验核心）
    @Query("SELECT * FROM users WHERE account = :account")
    suspend fun getUserByAccount(account: String): User?
}