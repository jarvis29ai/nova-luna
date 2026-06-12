package com.nova.luna.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.nova.luna.R
import io.flutter.embedding.android.FlutterFragment

class AssistantFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = FrameLayout(requireContext())
        root.id = View.generateViewId()
        
        val flutterFragment = FlutterFragment.withCachedEngine("assistant_engine")
            .build<FlutterFragment>()

        childFragmentManager.beginTransaction()
            .replace(root.id, flutterFragment)
            .commit()

        return root
    }
}
