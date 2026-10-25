package com.entrytask.shell.host

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** 唯一安全区适配；Compose与RN都使用已裁剪的宿主视口，不能二次留白。 */
class ShellInsetsAdapter(root: View, host: View) {
    init {
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
            view.setPadding(0, 0, 0, safe.bottom)
            host.setPadding(safe.left, safe.top, safe.right, 0)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }
}
