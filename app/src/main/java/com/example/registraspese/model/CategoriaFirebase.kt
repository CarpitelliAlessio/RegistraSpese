package com.example.registraspese.model

/**
 * Modello dati per rappresentare una categoria su Firebase Cloud Firestore.
 */
data class CategoriaFirebase(
    val id: String = "",
    val nome: String = "",
    val codiceGruppo: String = "" //
)