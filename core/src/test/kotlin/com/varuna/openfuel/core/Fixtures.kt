package com.varuna.openfuel.core

object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/fixtures/$name")) { "fixture $name missing" }
            .readBytes().toString(Charsets.UTF_8)
}
