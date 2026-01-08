package com.wanghuixian.a202305100122.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.wanghuixian.a202305100122.myapplication.room.Attachment
import com.wanghuixian.a202305100122.myapplication.room.Note
import com.wanghuixian.a202305100122.myapplication.room.NoteDatabase
import com.wanghuixian.a202305100122.myapplication.room.NoteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class NoteEditActivity : AppCompatActivity(), View.OnClickListener {
    // 控件声明
    private lateinit var tvNoteTitle: TextView
    private lateinit var etNoteContent: EditText
    private lateinit var llAttachment: LinearLayout

    // Room相关
    private lateinit var noteRepository: NoteRepository
    // 笔记参数
    private var noteId: Long = 0L
    private var noteName: String = ""
    // 附件列表
    private val attachmentList = mutableListOf<Attachment>()
    private val gson = Gson()

    // 请求码
    private val REQUEST_CODE_IMAGE = 101
    private val REQUEST_CODE_VIDEO = 102
    private val REQUEST_CODE_PERMISSION = 200

    // 媒体相关
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var voiceFile: File? = null
    private var isRecording = false

    // 临时文件路径
    private var currentPhotoPath: String = ""
    private var currentVideoPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_edit)

        // 初始化Room
        val db = NoteDatabase.getInstance(this)
        noteRepository = NoteRepository(db.noteDao(), db.userDao())

        // 获取参数
        noteId = intent.getLongExtra("NOTE_ID", 0L)
        noteName = intent.getStringExtra("NOTE_NAME").toString()

        // 初始化控件
        initView()
        initClickListener()

        // 加载原有笔记
        if (noteId > 0L) {
            loadNoteContent()
        }

        // 检查权限
        checkPermissions()
    }

    private fun initView() {
        tvNoteTitle = findViewById(R.id.tv_note_title)
        etNoteContent = findViewById(R.id.et_note_content)
        llAttachment = findViewById(R.id.ll_attachment)
        tvNoteTitle.text = noteName
    }

    private fun initClickListener() {
        findViewById<Button>(R.id.btn_save_note).setOnClickListener(this)
        findViewById<Button>(R.id.btn_add_img).setOnClickListener(this)
        findViewById<Button>(R.id.btn_add_video).setOnClickListener(this)
        findViewById<Button>(R.id.btn_add_voice).setOnClickListener(this)
        findViewById<Button>(R.id.btn_cancel_note)?.setOnClickListener { finish() }
    }

    private fun loadNoteContent() {
        lifecycleScope.launch(Dispatchers.IO) {
            val note = noteRepository.getNoteById(noteId)
            withContext(Dispatchers.Main) {
                note?.let {
                    etNoteContent.setText(it.noteContent)
                    // 解析附件
                    val type = object : TypeToken<List<Attachment>>() {}.type
                    attachmentList.clear()
                    attachmentList.addAll(gson.fromJson(it.attachments, type))
                    // 渲染附件
                    attachmentList.forEach { att ->
                        addAttachmentView(att.type, att.path)
                    }
                }
            }
        }
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_save_note -> saveNote()
            R.id.btn_add_img -> addImage()
            R.id.btn_add_video -> addVideo()
            R.id.btn_add_voice -> toggleVoiceRecording()
        }
    }

    // ====================== 权限检查（适配Android 13+） ======================
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        // Android 13+ 媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12及以下存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // 相机和录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val denied = mutableListOf<String>()
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    denied.add(permissions[i])
                }
            }
            if (denied.isNotEmpty()) {
                showToast("需要授予${denied.joinToString()}权限才能使用相关功能")
            }
        }
    }

    // ====================== 图片选择/拍摄（核心修复：区分URI类型） ======================
    private fun addImage() {
        // 1. 选择相册图片（跨应用URI）
        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // 2. 拍摄图片（应用私有URI）
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createImageFile()
        photoFile?.let {
            currentPhotoPath = it.absolutePath
            val photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        // 合并选择器
        val chooser = Intent.createChooser(pickIntent, "选择图片")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        startActivityForResult(chooser, REQUEST_CODE_IMAGE)
    }

    private fun createImageFile(): File? {
        return try {
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile("IMG_$time", ".jpg", dir)
        } catch (e: IOException) {
            showToast("创建图片文件失败：${e.message}")
            null
        }
    }

    // ====================== 视频选择/拍摄 ======================
    private fun addVideo() {
        // 1. 选择相册视频（跨应用URI）
        val pickIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        // 2. 拍摄视频（应用私有URI）
        val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        val videoFile = createVideoFile()
        videoFile?.let {
            currentVideoPath = it.absolutePath
            val videoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            captureIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60)
        }

        // 合并选择器
        val chooser = Intent.createChooser(pickIntent, "选择视频")
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        startActivityForResult(chooser, REQUEST_CODE_VIDEO)
    }

    private fun createVideoFile(): File? {
        return try {
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            File.createTempFile("VID_$time", ".mp4", dir)
        } catch (e: IOException) {
            showToast("创建视频文件失败：${e.message}")
            null
        }
    }

    // ====================== 语音录制 ======================
    private fun toggleVoiceRecording() {
        if (!isRecording) {
            startVoiceRecording()
        } else {
            stopVoiceRecording()
        }
    }

    private fun startVoiceRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            showToast("请先授予录音权限")
            return
        }

        try {
            stopVoicePlayback()
            voiceFile = createVoiceFile()
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(voiceFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            findViewById<Button>(R.id.btn_add_voice).text = "停止录音"
            showToast("开始录音...")
        } catch (e: Exception) {
            showToast("录音启动失败：${e.message}")
        }
    }

    private fun stopVoiceRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            findViewById<Button>(R.id.btn_add_voice).text = "语 音"
            showToast("录音完成")

            // 添加语音附件
            voiceFile?.let {
                val attachment = Attachment("语音", it.absolutePath)
                attachmentList.add(attachment)
                addAttachmentView(attachment.type, attachment.path)
            }
        } catch (e: Exception) {
            showToast("录音停止失败：${e.message}")
        }
    }

    private fun createVoiceFile(): File? {
        return try {
            val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            File.createTempFile("VOICE_$time", ".m4a", dir)
        } catch (e: IOException) {
            showToast("创建语音文件失败：${e.message}")
            null
        }
    }

    private fun playVoice(path: String) {
        try {
            stopVoicePlayback()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    showToast("语音播放完成")
                    stopVoicePlayback()
                }
            }
        } catch (e: Exception) {
            showToast("语音播放失败：${e.message}")
        }
    }

    private fun stopVoicePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
            mediaPlayer = null
        }
    }

    // ====================== 处理选择结果（核心修复：仅对跨应用URI申请权限） ======================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_IMAGE -> {
                    try {
                        if (data?.data != null) {
                            // 情况1：选择的相册图片（跨应用URI）- 申请持久化权限
                            val uri = data.data!!
                            // 仅申请READ权限（WRITE不需要）
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(uri, takeFlags)

                            val attachment = Attachment("图片", uri.toString())
                            attachmentList.add(attachment)
                            addAttachmentView(attachment.type, attachment.path)
                        } else if (currentPhotoPath.isNotEmpty()) {
                            // 情况2：拍摄的图片（应用私有URI）- 直接使用
                            val attachment = Attachment("图片", currentPhotoPath)
                            attachmentList.add(attachment)
                            addAttachmentView(attachment.type, attachment.path)
                            currentPhotoPath = "" // 清空临时路径
                        }
                        showToast("图片添加成功")
                    } catch (e: Exception) {
                        showToast("图片处理失败：${e.message}")
                        e.printStackTrace()
                    }
                }
                REQUEST_CODE_VIDEO -> {
                    try {
                        if (data?.data != null) {
                            // 情况1：选择的相册视频（跨应用URI）
                            val uri = data.data!!
                            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            contentResolver.takePersistableUriPermission(uri, takeFlags)

                            val attachment = Attachment("视频", uri.toString())
                            attachmentList.add(attachment)
                            addAttachmentView(attachment.type, attachment.path)
                        } else if (currentVideoPath.isNotEmpty()) {
                            // 情况2：拍摄的视频（应用私有URI）
                            val attachment = Attachment("视频", currentVideoPath)
                            attachmentList.add(attachment)
                            addAttachmentView(attachment.type, attachment.path)
                            currentVideoPath = "" // 清空临时路径
                        }
                        showToast("视频添加成功")
                    } catch (e: Exception) {
                        showToast("视频处理失败：${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    // ====================== 附件预览（适配两种URI类型） ======================
    private fun addAttachmentView(type: String, path: String) {
        // 创建附件项容器
        val itemLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                120
            ).apply {
                setMargins(10, 10, 10, 10)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundResource(android.R.drawable.dialog_frame)
            setPadding(10, 10, 10, 10)
        }

        // 预览图片/图标
        val ivPreview = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80)
            scaleType = ImageView.ScaleType.CENTER_CROP

            when (type) {
                "图片" -> {
                    val uri = Uri.parse(path)
                    if (uri.scheme == "content") {
                        // 跨应用URI：通过ContentResolver加载
                        try {
                            val input: InputStream? = contentResolver.openInputStream(uri)
                            val bitmap = android.graphics.BitmapFactory.decodeStream(input)
                            input?.close()
                            setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            setImageResource(android.R.drawable.ic_menu_gallery)
                        }
                    } else {
                        // 应用私有URI：直接加载
                        setImageURI(Uri.fromFile(File(path)))
                    }
                }
                "视频" -> setImageResource(R.drawable.ic_video)
                "语音" -> setImageResource(R.drawable.ic_voice)
                else -> setImageResource(android.R.drawable.ic_menu_add)
            }
        }

        // 类型文本
        val tvType = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = type
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, 5, 0, 0)
        }

        // 点击事件
        itemLayout.setOnClickListener {
            when (type) {
                "图片" -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(path), "image/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
                "视频" -> {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse(path), "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
                "语音" -> playVoice(path)
            }
        }

        // 添加到布局
        itemLayout.addView(ivPreview)
        itemLayout.addView(tvType)
        llAttachment.addView(itemLayout)
    }

    // ====================== 保存笔记 ======================
    private fun saveNote() {
        val content = etNoteContent.text.toString().trim()
        if (content.isEmpty()) {
            showToast("笔记内容不能为空！")
            return
        }

        hideSoftKeyboard()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        val attachmentsJson = gson.toJson(attachmentList)

        lifecycleScope.launch(Dispatchers.IO) {
            val note = if (noteId == 0L) {
                Note(noteName = noteName, noteContent = content, createTime = time, attachments = attachmentsJson)
            } else {
                Note(id = noteId, noteName = noteName, noteContent = content, createTime = time, attachments = attachmentsJson)
            }

            if (noteId == 0L) {
                noteRepository.insertNote(note)
            } else {
                noteRepository.updateNote(note)
            }

            withContext(Dispatchers.Main) {
                showToast("笔记保存成功！")
                finish()
            }
        }
    }

    // ====================== 工具方法 ======================
    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放资源
        if (isRecording) {
            stopVoiceRecording()
        }
        stopVoicePlayback()
        mediaRecorder?.release()
        mediaPlayer?.release()
    }
}