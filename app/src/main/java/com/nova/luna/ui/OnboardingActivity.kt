package com.nova.luna.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.nova.luna.MainActivity
import com.nova.luna.R

class OnboardingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)

        findViewById<MaterialButton>(R.id.getStartedButton).setOnClickListener {
            OnboardingController(this).markComplete()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
