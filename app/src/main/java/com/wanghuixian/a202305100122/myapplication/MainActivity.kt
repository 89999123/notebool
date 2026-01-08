package com.wanghuixian.a202305100122.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.wanghuixian.a202305100122.myapplication.room.NoteDatabase
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var etAccount: EditText
    private lateinit var etPwd: EditText
    private lateinit var ivEye: ImageView
    private var isPwdShow = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initView()
    }

    private fun initView() {
        etAccount = findViewById(R.id.et_account)
        etPwd = findViewById(R.id.et_pwd)
        ivEye = findViewById(R.id.iv_eye)
        findViewById<Button>(R.id.btn_login).setOnClickListener(this)
        findViewById<Button>(R.id.btn_clear).setOnClickListener(this)
        ivEye.setOnClickListener(this)
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_login -> doLogin() // 执行登录
            R.id.btn_clear -> goRegister() // 跳转到注册页
            R.id.iv_eye -> togglePwdShow() // 密码可见切换
        }
    }

    // ✅ 核心：登录校验（数据库查询用户）
    private fun doLogin() {
        val account = etAccount.text.toString().trim()
        val pwd = etPwd.text.toString().trim()
        if (account.isEmpty() || pwd.isEmpty()) {
            showToast("账号/密码不能为空！")
            return
        }

        val userDao = NoteDatabase.getInstance(this).userDao()
        lifecycleScope.launch {
            val user = userDao.getUserByAccount(account)
            when {
                user == null -> showToast("当前用户不存在！") // 用户不存在
                user.password != pwd -> showToast("密码错误，请重新输入！") // 密码错误
                else -> { // 登录成功，跳转记事本并传递昵称
                    val intent = Intent(this@MainActivity, NoteListActivity::class.java)
                    intent.putExtra("USER_NICKNAME", user.nickname)
                    startActivity(intent)
                    finish() // 关闭登录页，防止返回
                }
            }
        }
    }

    // 跳转到注册页面
    private fun goRegister() {
        startActivity(Intent(this, RegisterActivity::class.java))
    }

    // 密码可见/隐藏切换
    private fun togglePwdShow() {
        isPwdShow = !isPwdShow
        etPwd.inputType = if (isPwdShow) {
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD or android.text.InputType.TYPE_CLASS_TEXT
        }
        ivEye.setImageResource(if (isPwdShow) R.drawable.ic_eye_open else R.drawable.ic_eye_close)
        etPwd.setSelection(etPwd.text.length)
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}