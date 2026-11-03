package com.facescan.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Person(val name: String, val embeddings: MutableList<FloatArray>)

class Match(val name: String, val score: Float) {
    val recognized get() = score >= THRESHOLD
    val percent get() = (score * 100).toInt().coerceIn(0, 100)

    companion object {
        /** Ambang kemiripan (cosine). Naikkan jika terlalu sering salah kenal, turunkan jika sulit mengenali. */
        const val THRESHOLD = 0.55f
    }
}

/** Penyimpanan lokal wajah terdaftar (file JSON di memori internal aplikasi). */
object FaceStore {
    private const val FILE = "faces.json"

    @Synchronized
    fun load(ctx: Context): MutableList<Person> {
        val f = File(ctx.filesDir, FILE)
        val result = mutableListOf<Person>()
        if (!f.exists()) return result
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val embs = o.getJSONArray("embs")
                val list = mutableListOf<FloatArray>()
                for (j in 0 until embs.length()) {
                    val e = embs.getJSONArray(j)
                    list.add(FloatArray(e.length()) { e.getDouble(it).toFloat() })
                }
                result.add(Person(o.getString("name"), list))
            }
        } catch (e: Exception) {
            // file rusak: abaikan
        }
        return result
    }

    @Synchronized
    private fun save(ctx: Context, people: List<Person>) {
        val arr = JSONArray()
        for (p in people) {
            val embs = JSONArray()
            for (e in p.embeddings) {
                val a = JSONArray()
                for (x in e) a.put(x.toDouble())
                embs.put(a)
            }
            arr.put(JSONObject().put("name", p.name).put("embs", embs))
        }
        File(ctx.filesDir, FILE).writeText(arr.toString())
    }

    /** Menambah foto untuk [name]; mengembalikan jumlah foto orang itu sekarang. */
    @Synchronized
    fun addEmbedding(ctx: Context, name: String, emb: FloatArray): Int {
        val people = load(ctx)
        val person = people.firstOrNull { it.name.equals(name, ignoreCase = true) }
            ?: Person(name, mutableListOf()).also { people.add(it) }
        person.embeddings.add(emb)
        save(ctx, people)
        return person.embeddings.size
    }

    @Synchronized
    fun delete(ctx: Context, name: String) {
        val people = load(ctx)
        people.removeAll { it.name == name }
        save(ctx, people)
    }

    /** Mencari orang paling mirip. Kembalikan null jika belum ada data. */
    fun bestMatch(emb: FloatArray, people: List<Person>): Match? {
        var bestName: String? = null
        var best = -1f
        for (p in people) {
            for (e in p.embeddings) {
                var dot = 0f
                for (i in emb.indices) dot += emb[i] * e[i]
                if (dot > best) {
                    best = dot
                    bestName = p.name
                }
            }
        }
        return bestName?.let { Match(it, best) }
    }
}
