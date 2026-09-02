package com.example.signcoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.signcoach.platform.AndroidCameraPlatform

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidCameraPlatform.initialize(this)
        setContent { App() }
    }
}
