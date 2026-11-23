package com.example.plugin.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.SuccessGreen
import com.example.R
import com.example.Res

enum class PluginCategory(val displayName: String, val color: Color) {
    NETWORK(Res.get(R.string.rede_conectividade), CyanNeon),
    UTILITIES(Res.get(R.string.utilitarios_ferramentas), ElectricViolet),
    PRODUCTIVITY(Res.get(R.string.produtividade_gestao), AmberGold),
    SECURITY(Res.get(R.string.seguranca_privacidade), SuccessGreen),
    COMMUNITY(Res.get(R.string.comunidade_terceiros), Color(0xFFFF007F));

    companion object {
        fun fromString(value: String?): PluginCategory {
            return entries.firstOrNull {
                it.name.equals(value, ignoreCase = true) || it.displayName.equals(value, ignoreCase = true)
            } ?: COMMUNITY
        }
    }
}
