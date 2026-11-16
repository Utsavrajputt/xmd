package com.invictus.xmd.domain.browser.adblock

import android.content.Context
import java.io.File

/**
 * The user's own filter rules ("My rules"): one rule per line in the same
 * syntax the lists use, plus two conveniences -- a bare domain means
 * `||domain^` and a hosts line (`0.0.0.0 ads.example.com`) means the same.
 * They are compiled after every list and win over them (see
 * [EngineBuilder.userMode]). The element picker appends `host##selector`
 * lines here too.
 */
object UserRules {

    /** Upper bound so a runaway page script can't fill the disk through the picker. */
    private const val MAX_BYTES = 512 * 1024

    private fun file(context: Context): File =
        File(File(context.filesDir, "adblock").also { it.mkdirs() }, "user-rules.txt")

    fun read(context: Context): String =
        runCatching { file(context).takeIf { it.exists() }?.readText() ?: "" }.getOrDefault("")

    fun write(context: Context, text: String) {
        runCatching {
            val f = file(context)
            if (text.isBlank()) f.delete() else f.writeText(text.trimEnd() + "\n")
        }
    }

    private fun isComment(line: String): Boolean {
        val t = line.trim()
        return t.isEmpty() || t[0] == '!' || t[0] == '['
    }

    /** Number of actual rules (comments and blank lines left out). */
    fun count(text: String): Int = text.lineSequence().count { !isComment(it) }

    /** Adds [line] unless it is already there. False if it was refused (invalid, or the file is full). */
    fun add(context: Context, line: String): Boolean {
        val rule = line.trim()
        if (rule.isEmpty() || rule.contains('\n') || !isValid(rule)) return false
        val current = read(context)
        if (current.length > MAX_BYTES) return false
        if (current.lineSequence().any { it.trim() == rule }) return true
        write(context, if (current.isBlank()) rule else current.trimEnd() + "\n" + rule)
        return true
    }

    /** Removes every line equal to [line]. */
    fun remove(context: Context, line: String) {
        val rule = line.trim()
        val current = read(context)
        if (current.isEmpty()) return
        val kept = current.lineSequence().filter { it.trim() != rule }.joinToString("\n")
        write(context, kept)
    }

    /** True if the engine understands [line] (and it is a rule, not a comment). */
    fun isValid(line: String): Boolean {
        if (isComment(line)) return true
        return invalidLines(line).isEmpty()
    }

    /**
     * 1-based numbers of the lines the engine does not understand: unknown
     * options, unsafe selectors, unsupported procedural operators.
     */
    fun invalidLines(text: String): List<Int> {
        val out = ArrayList<Int>()
        val builder = EngineBuilder()
        builder.userMode = true
        builder.startList("check")
        val seen = HashSet<String>()
        var lineNo = 0
        for (raw in text.lineSequence()) {
            lineNo++
            if (isComment(raw)) continue
            if (!seen.add(raw.trim())) continue // a repeated rule is harmless
            // A `$badfilter` line only disables another rule; it is valid without adding one.
            if (raw.contains("\$badfilter")) continue
            val before = builder.ruleTotal()
            builder.addAbpLine(raw)
            if (builder.ruleTotal() == before) out.add(lineNo)
        }
        return out
    }

    /** Feeds every user rule to [builder] (call after the lists; [EngineBuilder.collectBad] is done by the caller). */
    internal fun feed(context: Context, builder: EngineBuilder) {
        val text = read(context)
        if (text.isBlank()) return
        builder.userMode = true
        builder.startList("user")
        text.lineSequence().forEach { builder.addAbpLine(it) }
        builder.userMode = false
    }

    internal fun collectBad(context: Context, builder: EngineBuilder) {
        val text = read(context)
        if (text.contains("badfilter")) text.lineSequence().forEach { builder.collectBad(it) }
    }
}
