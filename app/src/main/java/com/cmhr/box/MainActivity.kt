package com.cmhr.box

import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.TelegramActivity

class MainActivity : TelegramActivity() {
    override fun createRootFragment(): BaseFragment = PlaceholderFragment()
}
