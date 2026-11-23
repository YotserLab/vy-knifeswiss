package com.example.spectests

import java.io.File

/** Lê arquivo-fonte do módulo (testes unitários rodam com workdir = app/). */
fun src(path: String): String = File("src/main/java/$path").readText()

fun srcContains(path: String, needle: String): Boolean = src(path).contains(needle)
