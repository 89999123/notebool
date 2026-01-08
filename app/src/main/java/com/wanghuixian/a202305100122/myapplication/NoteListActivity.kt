package com.wanghuixian.a202305100122.myapplication



import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wanghuixian.a202305100122.myapplication.room.Note
import com.wanghuixian.a202305100122.myapplication.room.NoteDatabase
import com.wanghuixian.a202305100122.myapplication.room.NoteRepository
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class NoteListActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var etNoteSearch: EditText
    private lateinit var llNoteList: LinearLayout
    private lateinit var tvUserNickname: TextView // 昵称控件
    private lateinit var btnLogout: Button // 退出登录按钮
    private lateinit var noteRepository: NoteRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_list)

        // 获取登录传递的昵称并赋值
        val userNickname = intent.getStringExtra("USER_NICKNAME") ?: "用户"
        initView(userNickname)
        initRoom()
        initClickListener()
        loadAllNotes()
    }

    private fun initView(nickname: String) {
        etNoteSearch = findViewById(R.id.et_note_search)
        llNoteList = findViewById(R.id.ll_note_list)
        tvUserNickname = findViewById(R.id.tv_user_nickname)
        btnLogout = findViewById(R.id.btn_logout)
        tvUserNickname.text = nickname //展示昵称
    }

    private fun initRoom() {
        val db = NoteDatabase.getInstance(this)
        noteRepository = NoteRepository(db.noteDao(), db.userDao())
    }

    private fun initClickListener() {
        findViewById<Button>(R.id.btn_do_search).setOnClickListener(this)
        findViewById<Button>(R.id.btn_create_note).setOnClickListener(this)
        btnLogout.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_do_search -> doSearchNote()
            R.id.btn_create_note -> showCreateDialog()
            R.id.btn_logout -> doLogout()
        }
    }

    // 退出登录（返回登录页，清空栈）
    private fun doLogout() {
        AlertDialog.Builder(this)
            .setTitle("确认退出")
            .setMessage("确定要退出当前账号吗？")
            .setPositiveButton("退出") { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ========== 以下原有逻辑不变 ==========
    private fun loadAllNotes() {
        lifecycleScope.launch {
            noteRepository.allNotes.collect { noteList ->
                refreshNoteList(noteList)
            }
        }
    }

    private fun refreshNoteList(noteList: List<Note>) {
        llNoteList.removeAllViews()
        if (noteList.isEmpty()) {
            val emptyTip = TextView(this).apply {
                text = "暂无笔记，点击【创建新笔记】开始记录吧～"
                textSize = 16f
                setTextColor(0xFF999999.toInt())
                setPadding(0, 80, 0, 0)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            llNoteList.addView(emptyTip)
            return
        }

        noteList.forEach { note ->
            val noteItem = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 10 }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(15, 20, 15, 20)
                setOnClickListener {
                    val intent = Intent(this@NoteListActivity, NoteEditActivity::class.java)
                    intent.putExtra("NOTE_ID", note.id)
                    intent.putExtra("NOTE_NAME", note.noteName)
                    startActivity(intent)
                }
                setOnLongClickListener {
                    showDeleteDialog(note.id, note.noteName)
                    true
                }
            }

            val tvNoteName = TextView(this).apply {
                text = "${note.noteName}\n${note.createTime}"
                textSize = 16f
                setTextColor(0xFF333333.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { weight = 1f }
                setPadding(0, 0, 10, 0)
            }
            noteItem.addView(tvNoteName)
            llNoteList.addView(noteItem)
        }
    }

    private fun doSearchNote() {
        val searchKey = etNoteSearch.text.toString().trim()
        if (searchKey.isEmpty()) {
            showToast("请输入搜索关键词")
            loadAllNotes()
            return
        }
        hideSoftKeyboard()

        lifecycleScope.launch {
            noteRepository.searchNotes(searchKey).collect { filterList ->
                if (filterList.isEmpty()) showToast("未找到匹配的笔记")
                else showToast("找到 ${filterList.size} 条匹配笔记")
                refreshNoteList(filterList)
            }
        }
    }

    private fun showCreateDialog() {
        val etNoteName = EditText(this).apply {
            hint = "请输入笔记名称"
            textSize = 18f
            setPadding(20, 15, 20, 15)
        }

        AlertDialog.Builder(this)
            .setTitle("创建新笔记")
            .setView(etNoteName)
            .setPositiveButton("确 认") { _, _ ->
                val noteName = etNoteName.text.toString().trim()
                if (noteName.isEmpty()) {
                    showToast("笔记名称不能为空！")
                    return@setPositiveButton
                }
                val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
                lifecycleScope.launch {
                    noteRepository.insertNote(
                        Note(
                            noteName = noteName,
                            noteContent = "",
                            createTime = time
                        )
                    )
                }
                showToast("笔记创建成功！")
            }
            .setNegativeButton("取 消", null)
            .show()
    }

    private fun showDeleteDialog(noteId: Long, noteName: String) {
        AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除「$noteName」吗？删除后不可恢复")
            .setPositiveButton("删 除") { _, _ ->
                lifecycleScope.launch {
                    noteRepository.deleteNote(noteId)
                    showToast("笔记已删除")
                }
            }
            .setNegativeButton("取 消", null)
            .show()
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }
}