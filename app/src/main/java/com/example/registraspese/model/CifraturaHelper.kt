package com.example.registraspese.model

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


object CifraturaHelper {

    private const val ALGORITMO = "AES"
    // x retrocompatibilità
    private const val PREFISSO = "ENC:"

    private fun generaChiave(codiceGruppo: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(codiceGruppo.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(bytes, ALGORITMO)
    }

    // Funzione per nascondere (Cifrare) il testo
    fun cifra(testoInChiaro: String, codiceGruppo: String): String {
        if (testoInChiaro.isEmpty() || codiceGruppo.isEmpty()) return testoInChiaro

        return try {
            val cipher = Cipher.getInstance(ALGORITMO)
            cipher.init(Cipher.ENCRYPT_MODE, generaChiave(codiceGruppo))
            val bytesCifrati = cipher.doFinal(testoInChiaro.toByteArray(Charsets.UTF_8))

            // Usiamo Base64.NO_WRAP per evitare strani ritorni a capo
            val testoBase64 = Base64.encodeToString(bytesCifrati, Base64.NO_WRAP)

            // Aggiungiamo il nostro marchio all'inizio!
            PREFISSO + testoBase64
        } catch (e: Exception) {
            testoInChiaro
        }
    }

    // Funzione per rivelare (Decifrare) il testo
    fun decifra(testoDaDecifrare: String, codiceGruppo: String): String {
        if (testoDaDecifrare.isEmpty() || codiceGruppo.isEmpty()) return testoDaDecifrare

        // ==========================================================
        // CONTROLLO DI RETROCOMPATIBILITÀ (Per i vecchi dati)
        // ==========================================================
        if (!testoDaDecifrare.startsWith(PREFISSO)) {
            // Se non inizia con "ENC:", è un dato vecchio! Lo restituiamo intatto.
            return testoDaDecifrare
        }

        return try {
            // Rimuoviamo il marchio "ENC:" prima di decifrare
            val testoSenzaPrefisso = testoDaDecifrare.removePrefix(PREFISSO)

            val cipher = Cipher.getInstance(ALGORITMO)
            cipher.init(Cipher.DECRYPT_MODE, generaChiave(codiceGruppo))
            val bytesDecodificati = Base64.decode(testoSenzaPrefisso, Base64.NO_WRAP)
            val bytesDecifrati = cipher.doFinal(bytesDecodificati)
            String(bytesDecifrati, Charsets.UTF_8)
        } catch (e: Exception) {
            "🔒 [Errore decifratura]"
        }
    }
}