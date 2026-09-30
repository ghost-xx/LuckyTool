package com.luckyzyx.luckytool.hook.scopes.ota

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.lsposed.lsparanoid.Obfuscate

@Obfuscate
object DisableSystemUpdate : YukiBaseHooker() {
    override fun onHook() {
        //Source OTAService.IOTAUICtrl
        "com.oplus.ota.service.OTAService\$d".toClassOrNull()?.resolve()?.apply {
            listOf("startQueryUpdate", "startABUpdate").forEach { methodName ->
                firstMethodOrNull {
                    optional()
                    name = methodName
                    emptyParameters()
                }?.hook { intercept() }
            }
            firstMethodOrNull {
                optional()
                name = "startDownload"
            }?.hook { intercept() }
            firstMethodOrNull {
                optional()
                name = "startInstall"
            }?.hook { intercept() }
        }

        //Source LoadingTextView，检测被拦住后界面会停在转圈
        "com.oplus.otaui.view.LoadingTextView".toClassOrNull()?.resolve()?.apply {
            constructor {}.hookAll {
                after {
                    val root = instance<View>()
                    val context = root.context
                    val textId = context.resources.getIdentifier(
                        "no_update", "string", context.packageName
                    )
                    val text = if (textId != 0) context.getString(textId) else "已是最新版本"
                    firstField { name = "c" }.of(instance).get<TextView>()?.text = text
                    firstField { name = "d" }.of(instance).get<View>()?.isVisible = false
                }
            }
        }
    }
}
