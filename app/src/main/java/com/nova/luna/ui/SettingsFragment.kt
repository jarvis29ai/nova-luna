package com.nova.luna.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.nova.luna.MainActivity
import com.nova.luna.R

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val mainActivity = activity as? MainActivity ?: return
        
        val brainStatusSummary = view.findViewById<TextView>(R.id.brainStatusSummary)
        brainStatusSummary.text = mainActivity.settingsController.getBrainDetailedStatus()

        view.findViewById<View>(R.id.clearMemoryButton).setOnClickListener {
            mainActivity.settingsController.clearAllMemory()
            android.widget.Toast.makeText(context, "Memory cleared", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
