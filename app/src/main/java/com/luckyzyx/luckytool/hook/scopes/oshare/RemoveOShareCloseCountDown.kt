package com.luckyzyx.luckytool.hook.scopes.oshare

import android.content.Context
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import com.luckyzyx.luckytool.utils.getOSVersionCode
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge

@Obfuscate
class RemoveOShareCloseCountDown(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {

    override fun onHook() {
        val osCode = getOSVersionCode

        if (osCode >= 27) loadHooker(HookOShareFeatureConfig(dexKitBridge))
        loadHooker(HookOShareSpUtils(dexKitBridge))
    }

    @Obfuscate
    class HookOShareFeatureConfig(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        override fun onHook() {
            //Source OShareFeatureConfig
            dexKitBridge.findClass {
                matcher {
                    usingStrings("OShareFeatureConfig")
                }
            }.apply {
                checkDataList("OShareFeatureConfig")
                findMethod {
                    matcher {
                        paramTypes(Context::class.java)
                        returnType(Long::class.java)
                        usingStrings("getSwitchTimeOut")
                    }
                }.let { found ->
                    if (found.size == 1) found
                    else findMethod {
                        matcher {
                            paramTypes(Context::class.java)
                            returnType(Long::class.java)
                            usingStrings("getSwitchTimeOut = ")
                        }
                    }
                }.apply {
                    checkDataList("OShareFeatureConfig getSwitchTimeOut")
                    if (size != 1) return@apply
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            name = single().name
                            parameters(Context::class)
                            returnType = Long::class
                        }.hook {
                            replaceTo(0L)
                        }
                    }
                }
            }
        }
    }

    @Obfuscate
    class HookOShareSpUtils(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        override fun onHook() {
            //Source SpUtils
            dexKitBridge.findClass {
                matcher {
                    usingStrings("SpUtils", "share_config")
                }
            }.apply {
                checkDataList("SpUtils")
                findMethod {
                    matcher {
                        paramTypes(Context::class.java, Long::class.java)
                        usingStrings("updateLastTurnOnTime", "key_last_turn_on_time")
                    }
                }.let { found ->
                    if (found.size == 1) found
                    else findMethod {
                        matcher {
                            paramTypes(Context::class.java, Long::class.java)
                            usingStrings("updateLastTurnOnTime time  = ", "key_last_turn_on_time")
                        }
                    }
                }.apply {
                    checkDataList("SpUtils updateLastTurnOnTime")
                    if (size != 1) return@apply
                    single().className.toClass().resolve().apply {
                        firstMethod {
                            parameters(Context::class, Long::class)
                        }.hook {
                            before {
                                args().last().set(0L)
                            }
                        }
                    }
                }
            }
        }
    }
}