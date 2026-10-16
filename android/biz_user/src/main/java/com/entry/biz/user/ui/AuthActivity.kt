package com.entry.biz.user.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.entry.biz.user.ui.navigation.AuthNavHost
import com.entry.lib.base.mvvm.BaseActivity

class AuthActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AuthNavHost()
        }
    }
}