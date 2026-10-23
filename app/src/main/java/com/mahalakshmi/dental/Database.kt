package com.mahalakshmi.dental

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File

fun photoDir(context: Context): File = File(context.filesDir, "photos").apply { mkdirs() }

data class Patient(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val age: String = "",
    val gender: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val totalFees: Double = 0.0,
    val totalPaid: Double = 0.0
) {
    val remaining: Double get() = totalFees - totalPaid
}

data class Visit(
    val id: Long = 0,
    val patientId: Long,
    val date: Long = System.currentTimeMillis(),
    val reason: String = "",
    val problem: String = "",
    val fees: Double = 0.0,
    val paid: Double = 0.0,
    val photos: List<String> = emptyList()
) {
    val remaining: Double get() = fees - paid
}

class ClinicDb(private val context: Context) : SQLiteOpenHelper(context, NAME, null, 1) {
    companion object { const val NAME = "clinic.db" }

    // Keeps all data in one file, which makes backups simple and safe
    init { setWriteAheadLoggingEnabled(false) }

    private val photoFolder by lazy { photoDir(context) }
    private val visitCols = "id, patient_id, date, reason, problem, fees, paid, photos"

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE patients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "phone TEXT, age TEXT, gender TEXT, created_at INTEGER)"
        )
        db.execSQL(
            "CREATE TABLE visits (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
                "date INTEGER, reason TEXT, problem TEXT, fees REAL, paid REAL, photos TEXT)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun queryPatients(where: String, args: Array<String>): List<Patient> {
        val sql = """
            SELECT p.id, p.name, p.phone, p.age, p.gender, p.created_at,
                   IFNULL(SUM(v.fees), 0), IFNULL(SUM(v.paid), 0)
            FROM patients p LEFT JOIN visits v ON v.patient_id = p.id
            WHERE $where
            GROUP BY p.id
            ORDER BY IFNULL(MAX(v.date), p.created_at) DESC
        """
        val list = mutableListOf<Patient>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                list.add(
                    Patient(
                        id = c.getLong(0), name = c.getString(1) ?: "",
                        phone = c.getString(2) ?: "", age = c.getString(3) ?: "",
                        gender = c.getString(4) ?: "", createdAt = c.getLong(5),
                        totalFees = c.getDouble(6), totalPaid = c.getDouble(7)
                    )
                )
            }
        }
        return list
    }

    fun getPatients(search: String): List<Patient> {
        val q = "%${search.trim()}%"
        return queryPatients("p.name LIKE ? OR p.phone LIKE ?", arrayOf(q, q))
    }

    fun getPatient(id: Long): Patient? = queryPatients("p.id = ?", arrayOf(id.toString())).firstOrNull()

    fun patientCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM patients", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    fun savePatient(p: Patient): Long {
        val cv = ContentValues().apply {
            put("name", p.name); put("phone", p.phone); put("age", p.age); put("gender", p.gender)
        }
        return if (p.id == 0L) {
            cv.put("created_at", p.createdAt)
            writableDatabase.insert("patients", null, cv)
        } else {
            writableDatabase.update("patients", cv, "id = ?", arrayOf(p.id.toString()))
            p.id
        }
    }

    fun deletePatient(id: Long) {
        getVisits(id).forEach { deleteVisit(it) }
        writableDatabase.delete("patients", "id = ?", arrayOf(id.toString()))
    }

    private fun toVisit(c: Cursor) = Visit(
        id = c.getLong(0), patientId = c.getLong(1), date = c.getLong(2),
        reason = c.getString(3) ?: "", problem = c.getString(4) ?: "",
        fees = c.getDouble(5), paid = c.getDouble(6),
        photos = (c.getString(7) ?: "").split("\n").filter { it.isNotBlank() }
            .map { File(photoFolder, File(it).name).absolutePath }
    )

    fun getVisits(patientId: Long): List<Visit> {
        val list = mutableListOf<Visit>()
        readableDatabase.rawQuery(
            "SELECT $visitCols FROM visits WHERE patient_id = ? ORDER BY date DESC",
            arrayOf(patientId.toString())
        ).use { c -> while (c.moveToNext()) list.add(toVisit(c)) }
        return list
    }

    fun getVisit(id: Long): Visit? =
        readableDatabase.rawQuery("SELECT $visitCols FROM visits WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) toVisit(c) else null }

    fun saveVisit(v: Visit): Long {
        val cv = ContentValues().apply {
            put("patient_id", v.patientId); put("date", v.date)
            put("reason", v.reason); put("problem", v.problem)
            put("fees", v.fees); put("paid", v.paid)
            put("photos", v.photos.joinToString("\n") { File(it).name })
        }
        return if (v.id == 0L) writableDatabase.insert("visits", null, cv)
        else { writableDatabase.update("visits", cv, "id = ?", arrayOf(v.id.toString())); v.id }
    }

    fun deleteVisit(v: Visit) {
        v.photos.forEach { File(it).delete() }
        writableDatabase.delete("visits", "id = ?", arrayOf(v.id.toString()))
    }
}
