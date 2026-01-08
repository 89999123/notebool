package com.wanghuixian.a202305100122.myapplication.room

import kotlinx.coroutines.flow.Flow

// ✅ 构造方法接收2个参数：noteDao + userDao（必须和调用处一致）
class NoteRepository(
    private val noteDao: NoteDao,
    private val userDao: UserDao
) {
    // ========== 笔记相关操作（原有逻辑不变） ==========
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    suspend fun insertNote(note: Note) = noteDao.insertNote(note)
    suspend fun updateNote(note: Note) = noteDao.updateNote(note)
    suspend fun getNoteById(noteId: Long) = noteDao.getNoteById(noteId)
    fun searchNotes(keyword: String): Flow<List<Note>> = noteDao.searchNotes(keyword)
    suspend fun deleteNote(noteId: Long) = noteDao.deleteNote(noteId)

    // ========== 用户相关操作（新增，适配登录/注册） ==========
    suspend fun registerUser(user: User) = userDao.insertUser(user)
    suspend fun loginCheck(account: String) = userDao.getUserByAccount(account)
}