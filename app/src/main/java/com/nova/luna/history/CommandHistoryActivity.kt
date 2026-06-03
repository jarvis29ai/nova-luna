package com.nova.luna.history

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.nova.luna.R
import com.nova.luna.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CommandHistoryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_command_history)
        supportActionBar?.setTitle(R.string.command_history_title)
        loadRecentHistory()
    }

    private fun loadRecentHistory() {
        val historyText = findViewById<TextView>(R.id.commandHistoryText)
        lifecycleScope.launch {
            val recent = withContext(Dispatchers.IO) {
                AppDatabase.getInstance(this@CommandHistoryActivity)
                    .commandHistoryDao()
                    .getRecent(50)
            }
            historyText.text = CommandHistoryFormatter.format(recent)
        }
    }
}
