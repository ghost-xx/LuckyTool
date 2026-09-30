package com.luckyzyx.luckytool.hook.scopes.quicksearchbox

import android.util.ArrayMap
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.ModulePrefs
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge

@Obfuscate
class HookQuickSearchBoxMMKV(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    override fun onHook() {
        val map = ArrayMap<String, Any>().apply {
            if (
                prefs(ModulePrefs)
                    .getBoolean("remove_searchbox_uninstalled_app_suggestions", false)
            ) {
                put("new_suggest_app_card", false)
            }
        }
        loadHooker(HookMMKVManager(dexKitBridge, map))
    }

    @Obfuscate
    class HookMMKVManager(val dexKitBridge: DexKitBridge, val map: ArrayMap<String, Any>) :
        YukiBaseHooker() {
        override fun onHook() {
            //Source MMKVManager
            dexKitBridge.findClass {
                matcher {
                    className("com.heytap.quicksearchbox.common.manager.MMKVManager")
                }
            }.apply {
                checkDataList("HookMMKV find clazz")
                findMethod {
                    matcher {
                        paramTypes(String::class.java, String::class.java)
                        returnType(String::class.java)
                        usingStrings("getString")
                    }
                }.let { found ->
                    if (found.size == 1) found
                    else findMethod {
                        matcher {
                            paramTypes(String::class.java, String::class.java)
                            returnType(String::class.java)
                            usingStrings("getString MMKV is null, return default")
                        }
                    }
                }.apply {
                    checkDataList("HookMMKV find getString")
                    if (size != 1) return@apply
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().methodName
                            parameters(String::class, String::class)
                            returnType = String::class
                        }.hook {
                            before {
                                val key = args().first().cast<String>()
                                if (key.isNullOrBlank()) return@before
                                when (val value = map[key]) {
                                    null -> return@before
                                    is Boolean -> result = value.toString()
                                    is String -> result = value
                                    is Int -> result = value
                                }
                            }
                        }
                    }
                }
                findMethod {
                    matcher {
                        paramTypes(String::class.java, Boolean::class.java)
                        returnType(Boolean::class.java)
                        usingStrings("getBoolean")
                    }
                }.let { found ->
                    if (found.size == 1) found
                    else findMethod {
                        matcher {
                            paramTypes(String::class.java, Boolean::class.java)
                            returnType(Boolean::class.java)
                            usingStrings("getBoolean MMKV is null, return default")
                        }
                    }
                }.apply {
                    checkDataList("HookMMKV find getBoolean")
                    if (size != 1) return@apply
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().methodName
                            parameters(String::class, Boolean::class)
                            returnType = Boolean::class
                        }.hook {
                            before {
                                val key = args().first().cast<String>()
                                if (key.isNullOrBlank()) return@before
                                when (val value = map[key]) {
                                    null -> return@before
                                    "1" -> resultTrue()
                                    "0" -> resultFalse()
                                    "true" -> resultTrue()
                                    "false" -> resultFalse()
                                    is Boolean -> result = value
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}