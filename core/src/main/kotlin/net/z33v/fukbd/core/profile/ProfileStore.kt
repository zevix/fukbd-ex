package net.z33v.fukbd.core.profile

import java.io.File
import java.io.IOException

/** A profile shipped inside the app (Android assets). [source] names it in error reports. */
data class BundledProfile(val source: String, val json: String)

sealed interface ImportResult {
    /** Stored as `<dir>/<id>.json`. [overridesBundled] is true when a bundled profile has the same id. */
    data class Imported(val profile: Profile, val overridesBundled: Boolean) : ImportResult
    /** Nothing was written; any previous file for the same id is untouched. */
    data class Rejected(val errors: List<ProfileError>) : ImportResult
}

/** The profiles in effect, plus every file that could not be used and why. */
data class LoadReport(val profiles: List<Profile>, val problems: Map<String, List<ProfileError>>)

/**
 * Bundled profiles (read-only) plus user imports in [dir], one file per profile id.
 * An import with a bundled profile's id overrides it; [reset] deletes the override, so the
 * bundled original is always recoverable. Uses java.io only (FUTO's minSdk is 24).
 */
class ProfileStore(private val dir: File, private val bundled: List<BundledProfile>) {

    fun load(): LoadReport {
        val problems = linkedMapOf<String, List<ProfileError>>()
        val byId = linkedMapOf<String, Profile>()
        for (b in bundled) {
            when (val r = ProfileParser.parse(b.json)) {
                is ParseResult.Ok -> byId[r.profile.id] = r.profile
                is ParseResult.Rejected -> problems["bundled:${b.source}"] = r.errors
            }
        }
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.name } ?: emptyList()
        for (f in files) {
            val text = try { f.readText(Charsets.UTF_8) } catch (e: IOException) {
                problems[f.name] = listOf(ProfileError("", "cannot read: ${e.message}")); continue
            }
            when (val r = ProfileParser.parse(text)) {
                is ParseResult.Ok ->
                    if (f.name != "${r.profile.id}.json") {
                        problems[f.name] = listOf(ProfileError("/id", "file name does not match id \"${r.profile.id}\""))
                    } else {
                        byId[r.profile.id] = r.profile
                    }
                is ParseResult.Rejected -> problems[f.name] = r.errors
            }
        }
        return LoadReport(byId.values.toList(), problems)
    }

    /** Validates [json] and, only if it is valid, stores it atomically. */
    fun import(json: String): ImportResult {
        val profile = when (val r = ProfileParser.parse(json)) {
            is ParseResult.Rejected -> return ImportResult.Rejected(r.errors)
            is ParseResult.Ok -> r.profile
        }
        // The id matched ^[a-z0-9]+([._-][a-z0-9]+)*$ in the parser, so it is a safe file name.
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("cannot create $dir")
        val target = File(dir, "${profile.id}.json")
        val tmp = File.createTempFile(".import-", ".tmp", dir)
        try {
            tmp.writeText(json.removePrefix("﻿"), Charsets.UTF_8)
            if (!tmp.renameTo(target)) throw IOException("cannot replace $target")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        val overridesBundled = bundled.any { (ProfileParser.parse(it.json) as? ParseResult.Ok)?.profile?.id == profile.id }
        return ImportResult.Imported(profile, overridesBundled)
    }

    /** Deletes the imported profile [id]; a bundled profile with that id takes effect again. */
    fun reset(id: String): Boolean {
        val f = File(dir, "$id.json")
        return f.parentFile == dir && f.isFile && f.delete()
    }
}
