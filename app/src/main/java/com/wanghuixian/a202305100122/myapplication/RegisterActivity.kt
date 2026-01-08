package com.wanghuixian.a202305100122.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wanghuixian.a202305100122.myapplication.room.NoteDatabase
import com.wanghuixian.a202305100122.myapplication.room.User
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var etRegAccount: EditText
    private lateinit var etRegNickname: EditText // ✅ 新增昵称输入框
    private lateinit var etRegPwd: EditText
    private lateinit var etRegPwdConfirm: EditText
    private lateinit var ivEye1: ImageView
    private lateinit var ivEye2: ImageView
    private var isPwd1Show = false
    private var isPwd2Show = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        initView()
    }

    private fun initView() {
        etRegAccount = findViewById(R.id.et_reg_account)
        etRegNickname = findViewById(R.id.et_reg_nickname) // ✅ 绑定昵称控件
        etRegPwd = findViewById(R.id.et_reg_pwd)
        etRegPwdConfirm = findViewById(R.id.et_reg_pwd_confirm)
        ivEye1 = findViewById(R.id.iv_reg_eye1)
        ivEye2 = findViewById(R.id.iv_reg_eye2)

        findViewById<Button>(R.id.btn_reg_confirm).setOnClickListener(this)
        findViewById<Button>(R.id.btn_reg_back).setOnClickListener(this)
        ivEye1.setOnClickListener(this)
        ivEye2.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_reg_confirm -> doRegister() // 执行注册
            R.id.btn_reg_back -> finish() // 返回登录页
            R.id.iv_reg_eye1 -> togglePwd1Show()
            R.id.iv_reg_eye2 -> togglePwd2Show()
        }
    }

    // ✅ 核心：注册逻辑（校验+入库）
    private fun doRegister() {
        val account = etRegAccount.text.toString().trim()
        val nickname = etRegNickname.text.toString().trim() // ✅ 获取昵称
        val pwd = etRegPwd.text.toString().trim()
        val pwdConfirm = etRegPwdConfirm.text.toString().trim()

        // 输入校验
        when {
            account.isEmpty() -> showToast("账号不能为空！")
            nickname.isEmpty() -> showToast("昵称不能为空！") // ✅ 昵称校验
            pwd.isEmpty() || pwd.length <6 || pwd.length>16 -> showToast("请输入6-16位密码！")
            pwd != pwdConfirm -> showToast("两次密码输入不一致！")
            else -> {
                val userDao = NoteDatabase.getInstance(this).userDao()
                lifecycleScope.launch {
                    // 校验账号是否已存在
                    val existUser = userDao.getUserByAccount(account)
                    if (existUser != null) {
                        showToast("该账号已注册，请直接登录！")
                    } else {
                        // 注册成功，插入数据库
                        userDao.insertUser(User(account = account, nickname = nickname, password = pwd))
                        showToast("注册成功！请返回登录")
                        finish()
                    }
                }
            }
        }
    }

    // 密码可见切换
    private fun togglePwd1Show() {
        isPwd1Show = !isPwd1Show
        etRegPwd.inputType = getPwdInputType(isPwd1Show)
        ivEye1.setImageResource(if (isPwd1Show) R.drawable.ic_eye_open else R.drawable.ic_eye_close)
    }

    private fun togglePwd2Show() {
        isPwd2Show = !isPwd2Show
        etRegPwdConfirm.inputType = getPwdInputType(isPwd2Show)
        ivEye2.setImageResource(if (isPwd2Show) R.drawable.ic_eye_open else R.drawable.ic_eye_close)
    }

    private fun getPwdInputType(isShow: Boolean): Int {
        return if (isShow) {
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_CLASS_TEXT
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}