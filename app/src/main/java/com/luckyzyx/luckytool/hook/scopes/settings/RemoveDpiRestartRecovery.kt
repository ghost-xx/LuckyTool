package com.luckyzyx.luckytool.hook.scopes.settings

import android.content.Context
import android.provider.Settings
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge
import kotlin.math.max
import kotlin.math.min

@Obfuscate
class RemoveDpiRestartRecovery(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    override fun onHook() {
        //Source OplusDensityPreference
        "com.oplus.settings.widget.preference.OplusDensityPreference".toClass().resolve().apply {
            firstMethod {
                name = "onPreferenceChange"
                parameterCount = 2
            }.hook {
                after {
                    val newValue = args().last().string()
                    val context = firstMethod { name = "getContext";superclass() }.of(instance)
                        .invoke<Context>() ?: return@after
                    val displayMetrics = context.applicationContext.resources.displayMetrics
                    val min = min(displayMetrics.widthPixels, displayMetrics.heightPixels) *
                            160 / max(newValue.toInt(), 320)
                    val max = max(min, 120)
                    Settings.Secure.putString(
                        context.contentResolver, "display_density_forced", max.toString()
                    )
                    firstMethod { name = "notifyChanged";superclass() }.of(instance).invoke()
                }
            }
        }

        loadHooker(HookSettingsUtils(dexKitBridge))
    }

    @Obfuscate
    class HookSettingsUtils(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        override fun onHook() {
            //Source SettingsUtils
            val markers = listOf(
                "restoreCompassPhoneDisplayDensity" to "restorePhoneDisplayDensity",
                "restoreCompassPhoneDisplayDensity, defaultDensity = " to
                        "restorePhoneDisplayDensity, initDensityIndex = "
            )
            val matched = markers.firstNotNullOfOrNull { (compassLog, phoneLog) ->
                val found = findSettings(compassLog, phoneLog)
                if (found.size == 1) Triple(found, compassLog, phoneLog) else null
            }
            if (matched == null) {
                findSettings(
                    "restoreCompassPhoneDisplayDensity, defaultDensity = ",
                    "restorePhoneDisplayDensity, initDensityIndex = "
                ).checkDataList("RemoveDpiRestartRecovery Clazz")
                return
            }
            val (settings, compassLog, phoneLog) = matched
            settings.apply {
                findMethod {
                    matcher {
                        paramTypes(Context::class.java, Boolean::class.java)
                        addInvoke {
                            paramTypes(
                                String::class.java, Int::class.java,
                                Int::class.java, Boolean::class.java
                            )
                            usingStrings(compassLog)
                        }
                        addInvoke {
                            paramTypes(Context::class.java, String::class.java, Int::class.java)
                            usingStrings(phoneLog)
                        }
                    }
                }.apply {
                    checkDataList("RemoveDpiRestartRecovery Method")
                    if (size != 1) return@apply
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().methodName
                            parameters(Context::class, Boolean::class)
                        }.hook {
                            intercept()
                        }
                    }
                }
            }
        }

        private fun findSettings(compassLog: String, phoneLog: String) = dexKitBridge.findClass {
            matcher {
                addMethod {
                    paramTypes(Context::class.java, Boolean::class.java)
                }
                addMethod {
                    paramTypes(
                        String::class.java,
                        Int::class.java,
                        Int::class.java,
                        Boolean::class.java
                    )
                    usingStrings(compassLog)
                }
                addMethod {
                    paramTypes(Context::class.java, String::class.java, Int::class.java)
                    usingStrings(phoneLog)
                }
                usingStrings("SettingsUtils")
            }
        }
    }
}