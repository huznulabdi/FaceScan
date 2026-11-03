package com.facescan.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var list: ListView
    private lateinit var tvCount: TextView
    private var people = listOf<Person>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        list = findViewById(R.id.list)
        tvCount = findViewById(R.id.tvCount)
        list.emptyView = findViewById(R.id.tvEmpty)

        findViewById<View>(R.id.btnEnroll).setOnClickListener {
            startActivity(Intent(this, EnrollActivity::class.java))
        }
        findViewById<View>(R.id.btnScan).setOnClickListener {
            if (FaceStore.load(this).isEmpty()) {
                toast("Daftarkan minimal satu wajah dulu")
            } else {
                startActivity(Intent(this, ScanActivity::class.java))
            }
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            confirmDelete(people[pos])
            true
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        people = FaceStore.load(this)
        list.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            people.map { "${it.name}  (${it.embeddings.size} foto)" }
        )
        tvCount.text = "${people.size} orang terdaftar"
    }

    private fun confirmDelete(p: Person) {
        AlertDialog.Builder(this)
            .setTitle("Hapus ${p.name}?")
            .setMessage("Semua data wajah orang ini akan dihapus dari HP.")
            .setPositiveButton("Hapus") { _, _ ->
                FaceStore.delete(this, p.name)
                refresh()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
