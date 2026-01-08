package com.wanghuixian.a202305100122.myapplication.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// 注解：声明这是Room的数据访问对象
@Dao
interface NoteDao {
    // 1. 新增笔记（插入数据）
    @Insert
    suspend fun insertNote(note: Note)

    // 2. 修改笔记（更新数据，根据ID匹配）
    @Update
    suspend fun updateNote(note: Note)

    // 3. 查询所有笔记（按创建时间倒序，最新的在最上面）
    @Query("SELECT * FROM notes ORDER BY createTime DESC")
    fun getAllNotes(): Flow<List<Note>>

    // 4. 根据ID查询单条笔记（编辑/查看时用）
    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteById(noteId: Long): Note?

    // 5. 根据关键词搜索笔记（匹配名称+内容）
    @Query("SELECT * FROM notes WHERE noteName LIKE '%' || :keyword || '%' OR noteContent LIKE '%' || :keyword || '%' ORDER BY createTime DESC")
    fun searchNotes(keyword: String): Flow<List<Note>>

    // 6. 删除笔记（根据ID）
    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNote(noteId: Long)
}