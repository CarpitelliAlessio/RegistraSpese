package com.example.registraspese.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.registraspese.model.CategoriaFirebase
import com.example.registraspese.model.CifraturaHelper
import com.example.registraspese.model.SpesaFirebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SpeseViewModel : ViewModel() {

    private val db = FirebaseFirestore.getInstance()

    val listaSpeseRecenti = mutableStateListOf<SpesaFirebase>()
    val listaTutteSpese = mutableStateListOf<SpesaFirebase>()
    val listaCategorie = mutableStateListOf<CategoriaFirebase>()

    var codiceGruppoAttuale by mutableStateOf("")
    var staCaricando by mutableStateOf(true)
        private set

    private var listenerSpese: ListenerRegistration? = null
    private var listenerTutteSpese: ListenerRegistration? = null
    private var listenerCategorie: ListenerRegistration? = null

    fun cambiaGruppo(nuovoCodice: String) {
        if (nuovoCodice.isNotBlank() && nuovoCodice != codiceGruppoAttuale) {
            codiceGruppoAttuale = nuovoCodice
            avviaCaricamentoDati()
        }
    }

    private fun avviaCaricamentoDati() {
        listenerSpese?.remove()
        listenerTutteSpese?.remove()
        listenerCategorie?.remove()

        caricaSpeseRecenti()
        caricaTutteSpese()
        caricaCategorie()
    }

    private fun caricaSpeseRecenti() {
        listenerSpese = db.collection("spese_condivise")
            .whereEqualTo("codiceGruppo", codiceGruppoAttuale)
            .addSnapshotListener { snapshot, errore ->
                if (errore != null) return@addSnapshotListener
                if (snapshot != null) {
                    val speseTemporanee = mutableListOf<SpesaFirebase>()
                    for (doc in snapshot.documents) {
                        val spesaRaw = doc.toObject(SpesaFirebase::class.java)
                        if (spesaRaw != null) {
                            // Decifriamo SIA la descrizione CHE la categoria
                            val spesaDecifrata = spesaRaw.copy(
                                descrizione = CifraturaHelper.decifra(spesaRaw.descrizione, codiceGruppoAttuale),
                                categoria = CifraturaHelper.decifra(spesaRaw.categoria, codiceGruppoAttuale)
                            )
                            speseTemporanee.add(spesaDecifrata)
                        }
                    }

                    speseTemporanee.sortByDescending { it.timestamp }
                    val ultime50 = speseTemporanee.take(50)

                    listaSpeseRecenti.clear()
                    listaSpeseRecenti.addAll(ultime50)
                    staCaricando = false //spegnimento rotellina
                }
            }
    }

    private fun caricaTutteSpese() {
        listenerTutteSpese = db.collection("spese_condivise")
            .whereEqualTo("codiceGruppo", codiceGruppoAttuale)
            .addSnapshotListener { snapshot, errore ->
                if (errore != null) return@addSnapshotListener
                if (snapshot != null) {
                    val speseTemporanee = mutableListOf<SpesaFirebase>()
                    for (doc in snapshot.documents) {
                        val spesaRaw = doc.toObject(SpesaFirebase::class.java)
                        if (spesaRaw != null) {
                            // Decifriamo SIA la descrizione CHE la categoria
                            val spesaDecifrata = spesaRaw.copy(
                                descrizione = CifraturaHelper.decifra(spesaRaw.descrizione, codiceGruppoAttuale),
                                categoria = CifraturaHelper.decifra(spesaRaw.categoria, codiceGruppoAttuale)
                            )
                            speseTemporanee.add(spesaDecifrata)
                        }
                    }

                    speseTemporanee.sortByDescending { it.timestamp }

                    listaTutteSpese.clear()
                    listaTutteSpese.addAll(speseTemporanee)
                }
            }
    }

    private fun caricaCategorie() {
        listenerCategorie = db.collection("categorie_condivise")
            .whereEqualTo("codiceGruppo", codiceGruppoAttuale)
            .addSnapshotListener { snapshot, errore ->
                if (errore != null) return@addSnapshotListener
                if (snapshot != null) {
                    val categorieTemporanee = mutableListOf<CategoriaFirebase>()
                    for (doc in snapshot.documents) {
                        val catRaw = doc.toObject(CategoriaFirebase::class.java)
                        if (catRaw != null) {
                            // Decifriamo il nome della categoria
                            val catDecifrata = catRaw.copy(
                                nome = CifraturaHelper.decifra(catRaw.nome, codiceGruppoAttuale)
                            )
                            categorieTemporanee.add(catDecifrata)
                        }
                    }

                    categorieTemporanee.sortBy { it.nome }

                    if (categorieTemporanee.isEmpty()) {
                        categorieTemporanee.add(CategoriaFirebase(nome = "Generale", codiceGruppo = codiceGruppoAttuale))
                    }

                    listaCategorie.clear()
                    listaCategorie.addAll(categorieTemporanee)
                }
            }
    }

    // ============================================================================
    // SCRITTURA DEI DATI CON CRITTOGRAFIA (Categoria e Descrizione)
    // ============================================================================

    fun salvaSpesa(spesa: SpesaFirebase, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val nuovaRef = db.collection("spese_condivise").document()

        // Cifriamo i testi sensibili
        val descrizioneCifrata = CifraturaHelper.cifra(spesa.descrizione, codiceGruppoAttuale)
        val categoriaCifrata = CifraturaHelper.cifra(spesa.categoria, codiceGruppoAttuale)

        val spesaDaSalvare = spesa.copy(
            id = nuovaRef.id,
            codiceGruppo = codiceGruppoAttuale,
            descrizione = descrizioneCifrata,
            categoria = categoriaCifrata
            // L'importo (Double) rimane in chiaro in automatico!
        )
        nuovaRef.set(spesaDaSalvare)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun eliminaSpesa(idSpesa: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        db.collection("spese_condivise").document(idSpesa).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun aggiungiCategoria(nomeCategoria: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val ref = db.collection("categorie_condivise").document()

        // Cifriamo il nome della nuova categoria
        val nomeCifrato = CifraturaHelper.cifra(nomeCategoria, codiceGruppoAttuale)

        val nuovaCategoria = CategoriaFirebase(
            id = ref.id,
            nome = nomeCifrato,
            codiceGruppo = codiceGruppoAttuale
        )
        ref.set(nuovaCategoria)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun eliminaCategoria(idCategoria: String, onSuccess: () -> Unit) {
        db.collection("categorie_condivise").document(idCategoria).delete()
            .addOnSuccessListener { onSuccess() }
    }
}