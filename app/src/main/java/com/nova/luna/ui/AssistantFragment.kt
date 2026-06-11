package com.nova.luna.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.nova.luna.MainActivity
import com.nova.luna.R
import com.nova.luna.voice.VoiceInputState

class AssistantFragment : Fragment() {
    private lateinit var promptText: TextView
    private lateinit var examplePrompt: TextView
    private lateinit var micButton: FloatingActionButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_assistant, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        promptText = view.findViewById(R.id.promptText)
        examplePrompt = view.findViewById(R.id.examplePrompt)
        micButton = view.findViewById(R.id.micButton)

        val mainActivity = activity as? MainActivity ?: return

        micButton.setOnClickListener {
            mainActivity.assistantSession.onVoiceInputStarted()
            mainActivity.voiceInputController.startListening()
        }

        view.findViewById<View>(R.id.btnWakeLuna).setOnClickListener {
            mainActivity.popupController.handleEvent(AssistantPopupEvent.WAKE_LUNA_MOCK)
        }

        view.findViewById<View>(R.id.btnWakeNova).setOnClickListener {
            mainActivity.popupController.handleEvent(AssistantPopupEvent.WAKE_NOVA_MOCK)
        }

        view.findViewById<View>(R.id.closeButton).setOnClickListener {
            mainActivity.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)
                .selectedItemId = R.id.navigation_home
        }
        
        // Note: Real-time transcript updates would ideally be handled via a SharedViewModel
        // For now, we rely on the popup handled by AssistantPopupController for feedback.
    }
}
