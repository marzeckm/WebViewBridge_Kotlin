package com.example.webviewbridge_kotlin

import android.R
import android.os.Bundle
import android.webkit.WebView
import androidx.appcompat.app.AppCompatActivity
import com.example.webviewbridge_kotlin.databinding.ActivityMainBinding


class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Own code can be added here
        webView = binding.webView1
        val webViewBride:WebViewBridge = WebViewBridge(webView, this)
        webViewBride.loadUrl("file:///android_asset/index.html")
    }
}