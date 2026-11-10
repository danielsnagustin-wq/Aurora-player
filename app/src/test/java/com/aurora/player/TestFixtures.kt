package com.aurora.player

import android.net.Uri

/**
 * Tests JVM: las clases de Android son stubs — Uri.EMPTY es null y
 * Uri.parse tira "Stub!". Para construir un Song en tests se
 * instancia la subclase oculta de Uri sin llamar a su constructor.
 * Solo se guarda en el Song; la lógica pura nunca llama a ningún
 * método de Uri, así que los campos a null no importan.
 */
fun fakeUri(): Uri {
    val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe")
        .also { it.isAccessible = true }.get(null) as sun.misc.Unsafe
    val clazz = Class.forName("android.net.Uri\$HierarchicalUri")
    return unsafe.allocateInstance(clazz) as Uri
}
