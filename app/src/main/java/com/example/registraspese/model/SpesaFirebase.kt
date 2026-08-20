package com.example.registraspese.model

/**
 * Modello dati per rappresentare una spesa su Firebase Cloud Firestore.
 */
data class SpesaFirebase(
    val id: String = "",
    val importo: Double = 0.0,
    val descrizione: String = "",
    val categoria: String = "",
    val utenteEmail: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val codiceGruppo: String = "" // Isola la spesa per proteggere la privacy
)