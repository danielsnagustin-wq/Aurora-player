// Aurora Player — build raíz (Etapa 1)
// Versiones: verificar en el primer run de CI; si una versión ya no existe,
// se fija la última estable disponible y se anota en el commit.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
