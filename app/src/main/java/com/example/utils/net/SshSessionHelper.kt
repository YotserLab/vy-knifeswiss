package com.example.utils.net

import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import java.util.Properties
import com.example.R
import com.example.Res

/**
 * Configure JSch sessions to ask before trusting a new host key.
 * This prevents silent MITM (strict checking=ask) and still lets the user
 * connect to a new server the first time it is seen.
 */
object SshSessionHelper {

    fun applyConfig(session: Session) {
        val config = Properties().apply {
            put("StrictHostKeyChecking", "ask")
        }
        session.setConfig(config)
        session.userInfo = object : UserInfo {
            override fun getPassphrase(): String? = null
            override fun getPassword(): String? = null
            override fun promptPassphrase(message: String): Boolean = false
            override fun promptPassword(message: String): Boolean = false
            override fun promptYesNo(message: String): Boolean {
                return message.contains(Res.get(R.string.are_you_sure_you_want_to_continue_connec), ignoreCase = true)
            }
            override fun showMessage(message: String) {}
        }
    }
}
