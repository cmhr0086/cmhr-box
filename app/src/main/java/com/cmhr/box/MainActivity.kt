package com.cmhr.box

import com.cmhr.box.ui.HomeFragment
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.TelegramActivity

class MainActivity : TelegramActivity() {
    override fun createRootFragment(): BaseFragment = HomeFragment()
}
