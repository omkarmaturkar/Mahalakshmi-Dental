package com.mahalakshmi.dental

import android.database.sqlite.SQLiteDatabase

// Tables added in version 4. "IF NOT EXISTS" makes this safe to run on every upgrade.
fun createExtraTables(db: SQLiteDatabase) {
    db.execSQL("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT)")
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS prescriptions (id INTEGER PRIMARY KEY AUTOINCREMENT, patient_id INTEGER NOT NULL, " +
            "visit_id INTEGER NOT NULL DEFAULT 0, date INTEGER, diagnosis TEXT, medicines TEXT, advice TEXT)"
    )
    db.execSQL(
        "CREATE TABLE IF NOT EXISTS rx_templates (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
            "medicines TEXT, advice TEXT)"
    )
}

// Called when a visit is deleted
fun ClinicDb.deleteVisitExtras(visitId: Long) {
    writableDatabase.delete("prescriptions", "visit_id = ?", arrayOf(visitId.toString()))
}

// Called when a patient is deleted
fun ClinicDb.deletePatientExtras(patientId: Long) {
    writableDatabase.delete("prescriptions", "patient_id = ?", arrayOf(patientId.toString()))
}
