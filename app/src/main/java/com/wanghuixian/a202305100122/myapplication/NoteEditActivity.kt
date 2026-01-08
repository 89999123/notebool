package com.wanghuixian.a202305100122.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
// Room 相关类导入
import com.wanghuixian.a202305100122.myapplication.room.Note
import com.wanghuixian.a202305100122.myapplication.room.NoteDatabase
import com.wanghuixian.a202305100122.myapplication.room.NoteRepository

class NoteEditActivity : AppCompatActivity(), View.OnClickListener {
    // 控件声明
    private lateinit var tvNoteTitle: TextView
    private lateinit var etNoteContent: EditText
    private lateinit var llAttachment: LinearLayout

    // Room相关
    private lateinit var noteRepository: NoteRepository
    // 笔记参数：ID（0=新建，>0=编辑）、名称
    private var noteId: Long = 0L
    private var noteName: String = ""

    // 媒体相关常量
    private val REQUEST_CODE_IMAGE = 101
    private val REQUEST_CODE_VIDEO = 102
    private val REQUEST_CODE_PERMISSION = 200
    private val REQUEST_CODE_RECORD_VOICE = 103

    // 语音录制相关
    private var mediaRecorder: MediaRecorder? = null
    private var voiceFile: File? = null
    private var isRecording = false

    // 临时文件路径（图片/视频）
    private var currentPhotoPath: String = ""
    private var currentVideoPath: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_edit)

        // 初始化Room
        val db = NoteDatabase.getInstance(this)
        noteRepository = NoteRepository(db.noteDao(), db.userDao())

        noteId = intent.getLongExtra("NOTE_ID", 0L)
        noteName = intent.getStringExtra("NOTE_NAME").toString()

        // 初始化控件+事件
        initView()
        initClickListener()
        // 编辑模式：加载原有笔记内容
        if (noteId > 0L) {
            loadNoteContent()
        }

        // 检查权限
        checkPermissions()
    }

    /** 绑定控件 + 初始化标题 */
    private fun initView() {
        tvNoteTitle = findViewById(R.id.tv_note_title)
        etNoteContent = findViewById(R.id.et_note_content)
        llAttachment = findViewById(R.id.ll_attachment)
        tvNoteTitle.text = noteName

        // 绑定返回按钮
        findViewById<Button>(R.id.btn_cancel_note).setOnClickListener(this)
    }

    /** 绑定所有功能按钮点击事件 */
    private fun initClickListener() {
        findViewById<Button>(R.id.btn_save_note).setOnClickListener(this)
        findViewById<Button>(R.id.btn_add_img).setOnClickListener(this)
        findViewById<Button>(R.id.btn_add_video).setOnClickListener(this)
        findViewById<Button>(R.id.btn_add_voice).setOnClickListener(this)
    }

    /** 编辑模式：加载数据库中的笔记内容 */
    private fun loadNoteContent() {
        lifecycleScope.launch {
            val note = noteRepository.getNoteById(noteId)
            note?.let {
                etNoteContent.setText(it.noteContent)
            }
        }
    }

    /** 统一处理所有按钮点击事件 */
    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_save_note -> saveNote()  // 保存笔记（核心）
            R.id.btn_add_img -> addImage()    // 添加图片附件
            R.id.btn_add_video -> addVideo()  // 添加视频附件
            R.id.btn_add_voice -> toggleVoiceRecording()  // 录制/停止语音
            R.id.btn_cancel_note -> finish() // 返回按钮逻辑
        }
    }

    // ====================== 权限检查（适配Android 13+） ======================
    private fun checkPermissions() {
        val permissions = mutableListOf<String>()

        // Android 13+ 拆分媒体权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            // Android 12及以下：传统存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            ) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        // 相机权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }
        // 录音权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                REQUEST_CODE_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSION) {
            val deniedPermissions = mutableListOf<String>()
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permissions[i])
                }
            }
            if (deniedPermissions.isNotEmpty()) {
                showToast("需要授予${deniedPermissions.joinToString()}权限才能使用相关功能")
            }
        }
    }

    // ====================== 图片选择/拍摄（修复URI权限） ======================
    private fun addImage() {
        // 使用ACTION_OPEN_DOCUMENT替代ACTION_GET_CONTENT，获取持久化权限
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            // 申请持久化URI权限
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            flags = flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        // 同时支持拍摄
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createImageFile()
        photoFile?.let {
            currentPhotoPath = it.absolutePath
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        }

        val chooserIntent = Intent.createChooser(intent, "选择图片")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        startActivityForResult(chooserIntent, REQUEST_CODE_IMAGE)
    }

    private fun createImageFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            File.createTempFile(
                "IMG_${timeStamp}_",
                ".jpg",
                storageDir
            )
        } catch (e: IOException) {
            showToast("创建图片文件失败：${e.message}")
            null
        }
    }

    // ====================== 视频选择/拍摄（修复URI权限） ======================
    private fun addVideo() {
        // 使用ACTION_OPEN_DOCUMENT替代ACTION_GET_CONTENT，获取持久化权限
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            // 申请持久化URI权限
            flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
            flags = flags or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        }
        // 同时支持拍摄
        val captureIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
        val videoFile = createVideoFile()
        videoFile?.let {
            currentVideoPath = it.absolutePath
            val videoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )
            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, videoUri)
            captureIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60) // 限制60秒
        }

        val chooserIntent = Intent.createChooser(intent, "选择视频")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(captureIntent))
        startActivityForResult(chooserIntent, REQUEST_CODE_VIDEO)
    }

    private fun createVideoFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            File.createTempFile(
                "VID_${timeStamp}_",
                ".mp4",
                storageDir
            )
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showToast("请先授予录音权限")
            return
        }

        try {
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
        } catch (e: IOException) {
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
            // 添加语音附件预览
            addAttachmentView("语音", voiceFile?.absolutePath)
        } catch (e: Exception) {
            showToast("录音停止失败：${e.message}")
        }
    }

    private fun createVoiceFile(): File? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val storageDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            File.createTempFile(
                "VOICE_${timeStamp}_",
                ".m4a",
                storageDir
            )
        } catch (e: IOException) {
            showToast("创建语音文件失败：${e.message}")
            null
        }
    }

    // ====================== 处理选择结果（修复URI权限） ======================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_IMAGE -> {
                    // 处理图片选择/拍摄结果
                    data?.data?.let { uri ->
                        // 获取持久化URI权限
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                        addAttachmentView("图片", uri.toString())
                        showToast("图片添加成功")
                    } ?: run {
                        // 拍摄的图片（自己应用目录，直接用路径）
                        if (currentPhotoPath.isNotEmpty()) {
                            addAttachmentView("图片", currentPhotoPath)
                            showToast("图片添加成功")
                        }
                    }
                }
                REQUEST_CODE_VIDEO -> {
                    // 处理视频选择/拍摄结果
                    data?.data?.let { uri ->
                        // 获取持久化URI权限
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        contentResolver.takePersistableUriPermission(uri, takeFlags)
                        addAttachmentView("视频", uri.toString())
                        showToast("视频添加成功")
                    } ?: run {
                        // 拍摄的视频（自己应用目录，直接用路径）
                        if (currentVideoPath.isNotEmpty()) {
                            addAttachmentView("视频", currentVideoPath)
                            showToast("视频添加成功")
                        }
                    }
                }
            }
        }
    }

    // ====================== 核心功能：保存笔记（新增/修改 二合一） ======================
    private fun saveNote() {
        val noteContent = etNoteContent.text.toString().trim()
        if (noteContent.isEmpty()) {
            showToast("笔记内容不能为空！")
            return
        }
        hideSoftKeyboard()

        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date())
        lifecycleScope.launch {
            if (noteId == 0L) {
                // 模式1：新建笔记 → 插入数据库
                noteRepository.insertNote(Note(noteName = noteName, noteContent = noteContent, createTime = time))
            } else {
                // 模式2：编辑笔记 → 更新数据库
                noteRepository.updateNote(Note(id = noteId, noteName = noteName, noteContent = noteContent, createTime = time))
            }
            showToast("$noteName - 笔记保存成功！")
            finish() // 返回列表页
        }
    }

    /** 动态添加附件预览图（适配Content URI和File URI） */
    private fun addAttachmentView(type: String, filePath: String? = null) {
        val ivIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(80, 80).apply {
                setMargins(5, 5, 10, 5)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP

            // 区分Content URI和File URI加载方式
            filePath?.let { path ->
                val uri = Uri.parse(path)
                when (uri.scheme) {
                    "content" -> {
                        // Content URI：通过ContentResolver加载
                        try {
                            val inputStream: InputStream? = contentResolver.openInputStream(uri)
                            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                            inputStream?.close()
                            setImageBitmap(bitmap)
                        } catch (e: Exception) {
                            // 加载失败显示默认图标
                            setDefaultIcon(type)
                            showToast("加载${type}失败：${e.message}")
                        }
                    }
                    "file" -> {
                        // File URI：直接加载
                        setImageURI(uri)
                    }
                    else -> {
                        // 其他情况显示默认图标
                        setDefaultIcon(type)
                    }
                }
            } ?: run {
                // 无路径时显示默认图标
                setDefaultIcon(type)
            }

            // 点击预览附件
            setOnClickListener {
                filePath?.let { path ->
                    val uri = Uri.parse(path)
                    val previewIntent = Intent(Intent.ACTION_VIEW).apply {
                        when (type) {
                            "图片" -> setDataAndType(uri, "image/*")
                            "视频" -> setDataAndType(uri, "video/*")
                            "语音" -> setDataAndType(uri, "audio/*")
                            else -> return@apply
                        }
                        // 授予临时URI访问权限
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    if (packageManager.resolveActivity(previewIntent, 0) != null) {
                        startActivity(previewIntent)
                    } else {
                        showToast("暂无应用可预览该${type}文件")
                    }
                } ?: showToast("${type}文件路径为空")
            }
        }
        llAttachment.addView(ivIcon)
    }

    /** 设置默认图标 */
    private fun ImageView.setDefaultIcon(type: String) {
        setImageResource(
            when (type) {
                "图片" -> android.R.drawable.ic_menu_gallery
                "视频" -> android.R.drawable.ic_menu_camera
                "语音" -> android.R.drawable.ic_btn_speak_now
                else -> android.R.drawable.ic_menu_add
            }
        )
    }

    // ====================== 工具方法 ======================
    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(currentFocus?.windowToken, 0)
    }

    // 销毁时释放录音资源
    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) {
            stopVoiceRecording()
        }
    }
}

