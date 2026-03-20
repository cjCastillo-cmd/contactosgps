package com.example.contactosgps

import android.app.Activity
import android.os.Build

/**
 * Funciones de extension para aplicar animaciones al abrir/cerrar Activities.
 * Compatibles con Android 14+ (API 34) y versiones anteriores.
 */

/** Aplica animacion al ABRIR una nueva Activity */
fun Activity.transicionAbrir(enterAnim: Int, exitAnim: Int) {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, enterAnim, exitAnim)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}

/** Aplica animacion al CERRAR/volver de una Activity */
fun Activity.transicionCerrar(enterAnim: Int, exitAnim: Int) {
    if (Build.VERSION.SDK_INT >= 34) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, enterAnim, exitAnim)
    } else {
        @Suppress("DEPRECATION")
        overridePendingTransition(enterAnim, exitAnim)
    }
}