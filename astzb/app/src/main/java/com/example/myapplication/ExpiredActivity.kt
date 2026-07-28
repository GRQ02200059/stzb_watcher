package com.example.myapplication

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExpiredActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_expired)
        StatusBarInsetHelper.applyTopSafeSpacing(findViewById(R.id.expiredRoot), extraTopDp = 12)

        val titleView = findViewById<TextView>(R.id.expiredTitleView)
        val subtitleView = findViewById<TextView>(R.id.expiredSubtitleView)
        val detailView = findViewById<TextView>(R.id.expiredDetailView)

        val reason = intent.getStringExtra(EXTRA_REASON).orEmpty().ifBlank {
            "应用已过期，当前版本不可继续进入。"
        }
        val usedDays = intent.getLongExtra(EXTRA_USED_DAYS, 0L)
        val firstActivatedAt = intent.getLongExtra(EXTRA_FIRST_ACTIVATED_AT, 0L)

        titleView.text = "应用已过期"
        subtitleView.text = reason
        detailView.text = buildString {
            append("已使用天数：")
            append(usedDays)
            append(" 天\n")
            append("首次激活：")
            append(formatTime(firstActivatedAt))
            append("\n")
            append("当前应用已禁止进入主界面。")
        }
    }

    private fun formatTime(ts: Long): String {
        if (ts <= 0L) return "--"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(ts))
    }

    companion object {
        const val EXTRA_REASON = "expiry_reason"
        const val EXTRA_USED_DAYS = "expiry_used_days"
        const val EXTRA_FIRST_ACTIVATED_AT = "expiry_first_activated_at"
    }
}
