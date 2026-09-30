package com.luckyzyx.luckytool.hook.scopes.market

import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge
import java.util.concurrent.atomic.AtomicBoolean

@Obfuscate
class RemoveMarketSplashPageAppRecommend(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    override fun onHook() {
        val isV4 = "com.heytap.cdo.splash.domain.dto.v4.SplashDtoV4".toClassOrNull() != null
        if (isV4) loadHooker(MarketSplashPageV4(dexKitBridge))
        else loadHooker(MarketSplashPageV2(dexKitBridge))
    }

    @Obfuscate
    class MarketSplashPageV4(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        private val splashDto = "com.heytap.cdo.splash.domain.dto.v4.SplashDtoV4"
        private val mediaDto = "com.heytap.cdo.splash.domain.dto.v4.MediaComponentDtoV4"
        private val imageDto = "com.heytap.cdo.splash.domain.dto.v4.ImageComponentDtoV4"

        override fun onHook() {
            //Source SplashTransaction
            //26.1 still logs "getSplashData"; 26.9.4 logs the longer line
            val splash = listOf(
                "getSplashData",
                "getSplashData finished: isRequestByNet = "
            ).firstNotNullOfOrNull { marker ->
                findSplash(marker).takeIf { it.size == 1 }
            }
            if (splash == null) {
                findSplash("getSplashData finished: isRequestByNet = ")
                    .checkDataList("RemoveMarketSplashPageAppRecommend")
                return
            }
            splash.single().name.toClass().resolve().apply {
                firstMethod {
                    parameters(Boolean::class.java)
                    returnType(splashDto)
                }.hook {
                    intercept()
                }
            }
        }

        private fun findSplash(marker: String) = dexKitBridge.findClass {
            matcher {
                fields {
                    addForType(Int::class.java)
                    addForType(Long::class.java)
                    addForType(Boolean::class.java)
                    addForType(AtomicBoolean::class.java)
                }
                methods {
                    add { paramTypes(String::class.java); returnType(Boolean::class.java) }
                    add { paramTypes(Boolean::class.java); returnType(splashDto) }
                    add {
                        paramTypes(Boolean::class.java.name, Int::class.java.name, splashDto)
                        returnType(Void.TYPE)
                    }
                    add { paramTypes(splashDto, Boolean::class.java.name, mediaDto) }
                    add { paramTypes(splashDto, Boolean::class.java.name, imageDto) }
                }
                usingStrings(marker)
            }
        }
    }

    @Obfuscate
    class MarketSplashPageV2(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
        override fun onHook() {
            val splashDto = "com.heytap.cdo.splash.domain.dto.v2.SplashDto"
            val mediaDto = "com.heytap.cdo.splash.domain.dto.v2.MediaComponentDto"
            val imageDto = "com.heytap.cdo.splash.domain.dto.v2.ImageComponentDto"

            //Source SplashTransaction
            dexKitBridge.findClass {
                matcher {
                    fields {
                        addForType(Int::class.java)
                        addForType(Long::class.java)
                        addForType(Boolean::class.java)
                        addForType(AtomicBoolean::class.java)
                    }
                    methods {
                        add { paramTypes(String::class.java); returnType(Boolean::class.java) }
                        add { paramTypes(Boolean::class.java); returnType(splashDto) }
                        add {
                            paramTypes(Boolean::class.java.name, Int::class.java.name, splashDto)
                            returnType(Void.TYPE)
                        }
                        add { paramTypes(splashDto, Boolean::class.java.name, mediaDto) }
                        add { paramTypes(splashDto, Boolean::class.java.name, imageDto) }
                    }
                    usingStrings("getSplashData")
                }
            }.apply {
                checkDataList("RemoveMarketSplashPageAppRecommend")
                single().name.toClass().resolve().apply {
                    firstMethod {
                        parameters(Boolean::class.java)
                        returnType(splashDto)
                    }.hook {
                        intercept()
                    }
                }
            }
        }
    }
}