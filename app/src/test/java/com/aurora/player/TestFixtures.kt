package com.aurora.player

import android.net.Uri
import org.mockito.Mockito.mock

/**
 * Tests JVM: las clases de Android son stubs — Uri.EMPTY es null y
 * Uri.parse tira "Stub!". Para construir un Song en tests se mockea
 * un Uri con Mockito (instancia sin constructor; la lógica pura solo
 * guarda el Uri, nunca llama a sus métodos). El intento con
 * sun.misc.Unsafe no compiló: el paquete sun no se resuelve en la
 * compilación de tests de este proyecto (runs #388-#390).
 */
fun fakeUri(): Uri = mock(Uri::class.java)
