package com.luckyzyx.luckytool.hook.scopes.pictorial

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.kavaref.condition.type.VagueType
import com.highcapable.yukihookapi.hook.entity.YukiBaseHooker
import com.luckyzyx.luckytool.utils.DexkitUtils.checkDataList
import org.lsposed.lsparanoid.Obfuscate
import org.luckypray.dexkit.DexKitBridge
import java.io.File

@Obfuscate
class RemoveImageSaveWaterMark(val dexKitBridge: DexKitBridge) : YukiBaseHooker() {
    private val savingImage = ThreadLocal<Boolean>()

    override fun onHook() {
        if (hookLegacy()) return
        hookSaveCopyright()
    }

    private fun hookLegacy(): Boolean {
        //Search ImageSaveManager
        //Search getWaterMaskBitmap -> standard_water_mask_template / high_quality_water_mask_template
        val classes = dexKitBridge.findClass {
            matcher {
                fields {
                    addForType(File::class.java)
                    addForType(Handler::class.java)
                    addForType(Long::class.java)
                    addForType(Boolean::class.java)
                    addForType(String::class.java)
                }
                methods {
                    add { returnType(Handler::class.java) }
                    add { returnType(Bitmap::class.java) }
                    add { returnType(Boolean::class.java) }
                    add { paramTypes(Context::class.java) }
                    add { paramCount(5);returnType(Bitmap::class.java) }
                    add { paramTypes("com.heytap.pictorial.core.bean.BasePictorialData") }
                }
            }
        }
        if (classes.isEmpty()) return false
        classes.checkDataList("RemoveImageSaveWaterMark")
        classes.single().name.toClass().resolve().apply {
            firstMethod {
                parameters(Boolean::class, VagueType, Bitmap::class, Boolean::class)
                returnType = Bitmap::class
            }.hook {
                after {
                    result = args(2).cast<Bitmap>() ?: return@after
                }
            }
        }
        return true
    }

    private fun hookSaveCopyright() {
        //Source SavePictureManager.saveBitmap -> BitmapUtils.k
        //v40.10.15.1 draws getCopyrightDesc() onto the bitmap, then recycles that result
        val saveMethods = dexKitBridge.findMethod {
            matcher {
                paramCount(6)
                returnType(Void.TYPE)
                usingStrings("[saveBitmap] save image success, url = %s")
            }
        }
        val drawMethods = dexKitBridge.findMethod {
            matcher {
                returnType(Bitmap::class.java)
                usingStrings("bitmap width:", " bitmap height:")
            }
        }
        if (saveMethods.size != 1 || drawMethods.size != 1) {
            saveMethods.checkDataList("RemoveImageSaveWaterMark saveBitmap")
            drawMethods.checkDataList("RemoveImageSaveWaterMark drawText")
            return
        }
        val saveMethod = saveMethods.single()
        saveMethod.className.toClass().resolve().apply {
            firstMethod {
                name = saveMethod.methodName
                parameterCount = saveMethod.paramCount
            }.hook {
                before { savingImage.set(true) }
                after { savingImage.remove() }
            }
        }
        val drawMethod = drawMethods.single()
        drawMethod.className.toClass().resolve().apply {
            firstMethod {
                name = drawMethod.methodName
                parameterCount = drawMethod.paramCount
                returnType = Bitmap::class
            }.hook {
                before {
                    if (savingImage.get() != true) return@before
                    val source = args(1).cast<Bitmap>() ?: return@before
                    if (source.isRecycled) return@before
                    val config = when (val current = source.config) {
                        null, Bitmap.Config.HARDWARE -> Bitmap.Config.ARGB_8888
                        else -> current
                    }
                    val copied = source.copy(config, false) ?: return@before
                    result = copied
                }
            }
        }
    }
}
