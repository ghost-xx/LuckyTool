package com.luckyzyx.luckytool.hook.scopes.ota

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.highcapable.kavaref.KavaRef.Companion.asResolver
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge
import androidx.core.content.edit
import androidx.core.net.toUri
import com.highcapable.kavaref.extension.isSubclassOf
import com.highcapable.kavaref.extension.makeAccessible
import com.highcapable.kavaref.extension.classOf

@Obfuscate
class DisableSystemUpdate(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {

    private val otaPackage = "com.oplus.ota"

    private val messageEntries = "content://com.android.settings.outward.provider/message_entries"

    private val cachedUpdateKeys = arrayOf(
        "new_otaVersion",
        "new_otaVersion_id",
        "new_romVer",
        "new_colorosVersion",
        "new_androidVersion",
        "new_firstTitle",
        "new_description",
        "panel_description",
        "realOtaVersion",
        "realRomVersion",
        "realOsVersion",
        "realAndroidVersion",
        "key_target_component_info"
    )

    override fun onHook() {
        //Source OTAService.IOTAUICtrl
        $$"com.oplus.ota.service.OTAService$d".toClassOrNull()?.resolve()?.apply {
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

        keepLatestVersionPage()

        //Source QueryOTAUpdateRunnable，自动和手动检查都在这里请求 /update/v6
        dexKitBridge.findMethod {
            matcher {
                name = "run"
                paramCount(0)
                usingStrings("QueryOTAUpdateRunnable")
            }
        }.apply {
            if (size != 1) return@apply
            single().className.toClass().resolve().firstMethod {
                name = "run"
                emptyParameters()
            }.hook { intercept() }
        }

        //Source MessageEntryAgent，设置首页的软件更新卡片
        ContentResolver::class.resolve().firstMethod {
            name = "insert"
            parameters(Uri::class, ContentValues::class, Bundle::class)
        }.hook {
            before {
                if (args().first().any()?.toString() != messageEntries) return@before
                val values = args(1).cast<ContentValues>() ?: return@before
                if (values.getAsString("package_name") != otaPackage) return@before
                resultNull()
            }
        }

        //Source LoadingTextView，检测会被……拦住停在转圈
        //文字和进度圈的字段名会随版本变，按类型找
        "com.oplus.otaui.view.LoadingTextView".toClassOrNull()?.resolve()?.optional()?.apply {
            constructor {}.hookAll {
                after {
                    val root = instance<View>()
                    firstFieldOrNull { type = TextView::class }?.of(instance)
                        ?.get<TextView>()?.text = noUpdateText(root.context)
                    progressView(instance)?.isVisible = false
                }
            }
        }

        onAppLifecycle {
            onCreate { clearCachedUpdate(this) }
        }
    }

    /**
     * 检查被拦住后，界面会把结果当成网络失败。
     * 0x3e8 是停留在检测页约 10 秒后的弱网提示，3 会切到无网络页。
     * hasUpdate 的 -2 和 3 也会直接打开无网络页，改成 2，配合 update_state=0x11 进入已是最新版本。
     */
    private fun keepLatestVersionPage() {
        dexKitBridge.findMethod {
            matcher {
                name = "handleMessage"
                paramCount(1)
                usingStrings("UiHandler state = ")
            }
        }.apply {
            if (size != 1) return@apply
            val method = single()
            method.className.toClass().resolve().firstMethod {
                name = method.methodName
                parameters(Message::class)
            }.hook {
                before {
                    val what = args().first().cast<Message>()?.what ?: return@before
                    if (what == 0x3e8 || what == 3) result = null
                }
            }
        }
        dexKitBridge.findMethod {
            matcher {
                name = "hasUpdate"
                paramCount(1)
                usingStrings("hasUpdate=")
            }
        }.apply {
            if (size != 1) return@apply
            val method = single()
            method.className.toClass().resolve().firstMethod {
                name = method.methodName
                parameters(Int::class)
            }.hook {
                before {
                    val code = args().first().int()
                    if (code == -2 || code == 3) args().first().set(2)
                }
            }
        }
    }

    /** no_update 在软件更新包里，本模块没有对应的 R */
    @SuppressLint("DiscouragedApi")
    private fun noUpdateText(context: Context): String {
        val textId = context.resources.getIdentifier(
            "no_update", "string", context.packageName
        )
        return if (textId != 0) context.getString(textId) else "已是最新版本"
    }

    /** 进度圈是 TextView 以外的那个 View，当前类型是 CUICompProgressIndicator */
    private fun progressView(instance: Any): View? {
        instance.asResolver().optional().firstFieldOrNull {
            type = "com.coui.appcompat.progressbar.COUICompProgressIndicator"
        }?.get<View>()?.let { return it }
        return instance.javaClass.declaredFields.firstOrNull { field ->
            field.type isSubclassOf classOf<View>() &&
                !(field.type isSubclassOf classOf<TextView>())
        }?.let { field ->
            field.makeAccessible()
            field.get(instance) as? View
        }
    }

    /** 已经查到的新版本会留在 state_info 和设置卡片里，只在主进程清理 */
    private fun clearCachedUpdate(app: Application) {
        if (Application.getProcessName() != app.packageName) return
        app.getSharedPreferences("state_info", Context.MODE_PRIVATE).edit(commit = true) {
            cachedUpdateKeys.forEach { remove(it) }
            putInt("update_state", 0x11)
        }
        runCatching {
            app.contentResolver.delete(
                messageEntries.toUri(), "package_name = ?", arrayOf(app.packageName)
            )
        }
    }
}
