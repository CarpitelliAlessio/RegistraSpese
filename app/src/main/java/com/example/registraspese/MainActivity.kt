package com.example.registraspese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.registraspese.model.CategoriaFirebase
import com.example.registraspese.model.SpesaFirebase
import com.example.registraspese.viewmodel.SpeseViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val auth = FirebaseAuth.getInstance()

                    var utenteConnesso by remember { mutableStateOf(auth.currentUser != null) }
                    // 1. Leggiamo il codice gruppo dalla memoria
                    var codiceGruppo by remember { mutableStateOf(leggiCodiceLocale(context)) }

                    if (!utenteConnesso) {
                        SchermataLogin(
                            onLoginCompletato = { utenteConnesso = true }
                        )
                    } else if (codiceGruppo.isEmpty()) {
                        // 2. Se loggato ma senza gruppo, mostra la schermata Onboarding
                        SchermataOnboardingGruppo(
                            onCodiceImpostato = { nuovoCodice ->
                                salvaCodiceLocale(context, nuovoCodice)
                                codiceGruppo = nuovoCodice
                            }
                        )
                    } else {
                        // 3. Se loggato e con gruppo, avvia l'app normale
                        GestoreSchermateSpese(
                            codiceGruppo = codiceGruppo,
                            onLogout = {
                                auth.signOut()
                                utenteConnesso = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// NUOVA SCHERMATA: ONBOARDING (CREA O UNISCITI A UN GRUPPO)
// ============================================================================
@Composable
fun SchermataOnboardingGruppo(onCodiceImpostato: (String) -> Unit) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val emailUtente = auth.currentUser?.email ?: "Anonimo"

    var nomeDaCreare by remember { mutableStateOf("") }
    var codiceDaUnire by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Benvenuto!", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Crea un nuovo gruppo o unisciti a uno esistente.", style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(32.dp))

        // CREA NUOVO GRUPPO
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Crea Nuovo Gruppo", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = nomeDaCreare,
                    onValueChange = { nomeDaCreare = it },
                    label = { Text("Nome (es: Casa, Coinquilini)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val nomePulito = nomeDaCreare.trim().uppercase().replace(" ", "")
                        if (nomePulito.length < 3) {
                            Toast.makeText(context, "Il nome deve avere almeno 3 caratteri!", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        // Generazione Codice Sicuro (8 caratteri)
                        val suffissoSicuro = UUID.randomUUID().toString().replace("-", "").substring(0, 8).uppercase()
                        val codiceUnicoFinale = "$nomePulito-$suffissoSicuro"

                        val datiGruppo = mapOf(
                            "creatoIl" to System.currentTimeMillis(),
                            "nomeOriginale" to nomeDaCreare,
                            "admin" to emailUtente,
                            "partecipanti" to listOf(emailUtente)
                        )

                        db.collection("gruppi_registrati").document(codiceUnicoFinale)
                            .set(datiGruppo)
                            .addOnSuccessListener {
                                Toast.makeText(context, "Gruppo creato! Sei l'amministratore.", Toast.LENGTH_LONG).show()
                                onCodiceImpostato(codiceUnicoFinale)
                            }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Genera Gruppo Sicuro") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("OPPURE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(24.dp))

        // UNISCITI A UN GRUPPO ESISTENTE
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Unisciti a un Gruppo", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = codiceDaUnire,
                    onValueChange = { codiceDaUnire = it.uppercase() },
                    label = { Text("Codice esatto (es: CASA-A1B2C3D4)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val codicePulito = codiceDaUnire.trim()
                        if (codicePulito.isEmpty()) return@OutlinedButton

                        val docRef = db.collection("gruppi_registrati").document(codicePulito)
                        docRef.get().addOnSuccessListener { documento ->
                            if (documento.exists()) {
                                docRef.update("partecipanti", FieldValue.arrayUnion(emailUtente))
                                    .addOnSuccessListener {
                                        Toast.makeText(context, "Unito al gruppo con successo!", Toast.LENGTH_SHORT).show()
                                        onCodiceImpostato(codicePulito)
                                    }
                            } else {
                                Toast.makeText(context, "Codice '$codicePulito' errato o inesistente!", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Unisciti") }
            }
        }
    }
}

// ============================================================================
// GESTORE NAVIGAZIONE SCHERMATE
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestoreSchermateSpese(
    codiceGruppo: String,
    onLogout: () -> Unit,
    viewModel: SpeseViewModel = viewModel()
) {
    var schermataCorrente by remember { mutableStateOf("HOME") }

    // Diciamo al ViewModel quale gruppo scaricare!
    LaunchedEffect(codiceGruppo) {
        viewModel.cambiaGruppo(codiceGruppo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val titolo = when(schermataCorrente) {
                        "HOME" -> "Home - Spese"
                        "REPORT" -> "Report Spese"
                        else -> "Impostazioni"
                    }
                    Text(titolo)
                },
                navigationIcon = {
                    if (schermataCorrente != "HOME") {
                        IconButton(onClick = { schermataCorrente = "HOME" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Torna alla Home")
                        }
                    } else {
                        Icon(Icons.Default.Home, "Home", modifier = Modifier.padding(start = 12.dp))
                    }
                },
                actions = {
                    if (schermataCorrente == "HOME") {
                        Button(onClick = { schermataCorrente = "REPORT" }, modifier = Modifier.padding(end = 8.dp)) {
                            Text("Report")
                        }
                        IconButton(onClick = { schermataCorrente = "IMPOSTAZIONI" }) {
                            Icon(Icons.Default.Settings, "Impostazioni")
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, "Esci")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValori ->
        Box(modifier = Modifier.padding(paddingValori)) {
            when (schermataCorrente) {
                "REPORT" -> SchermataReportSpese(viewModel)
                "IMPOSTAZIONI" -> SchermataImpostazioni(viewModel, codiceGruppo) // Passiamo il codice!
                else -> SchermataSpeseCondivise(viewModel)
            }
        }
    }
}

// ============================================================================
// 1. SCHERMATA SPESE CONDIVISE (HOME) - AGGIORNATA
// ============================================================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataSpeseCondivise(viewModel: SpeseViewModel) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val emailUtente = auth.currentUser?.email ?: "Anonimo"

    // Leggiamo il codice gruppo dal ViewModel
    val codiceGruppo = viewModel.codiceGruppoAttuale

    var importo by remember { mutableStateOf("") }
    var descrizione by remember { mutableStateOf("") }
    var menuEspanso by remember { mutableStateOf(false) }
    var categoriaSelezionata by remember { mutableStateOf("") }
    var spesaDaCancellare by remember { mutableStateOf<SpesaFirebase?>(null) }

    val listaSpese = viewModel.listaSpeseRecenti
    val nomiCategorie = viewModel.listaCategorie.map { it.nome }

    if (categoriaSelezionata.isEmpty() && nomiCategorie.isNotEmpty()) {
        categoriaSelezionata = nomiCategorie[0]
    }

    spesaDaCancellare?.let { spesa ->
        AlertDialog(
            onDismissRequest = { spesaDaCancellare = null },
            title = { Text("Elimina Spesa") },
            text = { Text("Sei sicuro di voler eliminare definitivamente questa spesa da ${spesa.importo} €?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.eliminaSpesa(
                            idSpesa = spesa.id,
                            onSuccess = { Toast.makeText(context, "Spesa eliminata!", Toast.LENGTH_SHORT).show() },
                            onFailure = { e -> Toast.makeText(context, "Errore: ${e.message}", Toast.LENGTH_SHORT).show() }
                        )
                        spesaDaCancellare = null
                    }
                ) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { spesaDaCancellare = null }) { Text("Annulla") }
            }
        )
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Mostriamo Utente e Gruppo in una bella riga
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Utente: $emailUtente",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )

            // Il nome del gruppo in un piccolo badge colorato
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(
                    text = "Gruppo: $codiceGruppo",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Divider()

        OutlinedTextField(
            value = importo,
            onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*[.,]?\\d*\$"))) importo = it },
            label = { Text("Importo (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = descrizione,
            onValueChange = { descrizione = it },
            label = { Text("Descrizione (es. Spesa per cena)") },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = menuEspanso,
            onExpandedChange = { menuEspanso = !menuEspanso },
        ) {
            OutlinedTextField(
                value = categoriaSelezionata,
                onValueChange = {},
                readOnly = true,
                label = { Text("Categoria") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuEspanso) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = menuEspanso,
                onDismissRequest = { menuEspanso = false }
            ) {
                nomiCategorie.forEach { singolaCategoria ->
                    DropdownMenuItem(
                        text = { Text(singolaCategoria) },
                        onClick = {
                            categoriaSelezionata = singolaCategoria
                            menuEspanso = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                val importoNumerico = importo.replace(",", ".").trim().toDoubleOrNull()
                val descrizionePulita = descrizione.trim()

                if (importoNumerico == null || importoNumerico <= 0.0) {
                    Toast.makeText(context, "Inserisci un importo valido (> 0)!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (importoNumerico > 100000.0) {
                    Toast.makeText(context, "Importo troppo elevato!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (descrizionePulita.length < 3) {
                    Toast.makeText(context, "Descrizione troppo corta!", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val nuovaSpesa = SpesaFirebase(
                    importo = importoNumerico,
                    descrizione = descrizionePulita,
                    categoria = categoriaSelezionata,
                    utenteEmail = emailUtente,
                    timestamp = System.currentTimeMillis()
                )

                viewModel.salvaSpesa(
                    spesa = nuovaSpesa,
                    onSuccess = {
                        Toast.makeText(context, "Spesa salvata!", Toast.LENGTH_SHORT).show()
                        importo = ""
                        descrizione = ""
                    },
                    onFailure = { e -> Toast.makeText(context, "Errore salvataggio: ${e.message}", Toast.LENGTH_LONG).show() }
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Salva Spesa Condivisa") }

        Divider(modifier = Modifier.padding(vertical = 4.dp))
        Text(text = "Ultime 50 Spese Inserite (${listaSpese.size})", style = MaterialTheme.typography.titleMedium)

        // ==========================================================
        // NUOVO CODICE: GESTIONE CARICAMENTO E STATO VUOTO
        // ==========================================================
        if (viewModel.staCaricando) {
            // 1. Mostriamo la rotellina mentre Firebase comunica con il server
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (listaSpese.isEmpty()) {
            // 2. Se ha finito di caricare e non ci sono spese, mostriamo il messaggio vuoto
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "📭", style = MaterialTheme.typography.displayLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Nessuna spesa registrata.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.outline)
                    Text(text = "Aggiungi la prima spesa qui in alto!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            // 3. Al altrimenti mostriamo la lista vera e propria
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listaSpese) { spesa ->
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = spesa.descrizione, style = MaterialTheme.typography.titleMedium)
                                Text(text = "€ ${String.format("%.2f", spesa.importo)}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Cat: ${spesa.categoria}", style = MaterialTheme.typography.bodySmall)
                                Text(text = formattaTimestamp(spesa.timestamp), style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "Da: ${spesa.utenteEmail}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                                if (spesa.utenteEmail == emailUtente) {
                                    IconButton(onClick = { spesaDaCancellare = spesa }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. SCHERMATA IMPOSTAZIONI: CONDIVISIONE, ADMIN E CATEGORIE
// ============================================================================
@Composable
fun SchermataImpostazioni(viewModel: SpeseViewModel, codiceGruppo: String) {
    val context = LocalContext.current
    val db = FirebaseFirestore.getInstance()
    val auth = FirebaseAuth.getInstance()
    val emailUtenteAttuale = auth.currentUser?.email ?: ""

    var nuovaCategoria by remember { mutableStateOf("") }
    val listaCategorie = viewModel.listaCategorie

    // Dati per il pannello Admin
    var adminGruppo by remember { mutableStateOf("") }
    val listaPartecipanti = remember { mutableStateListOf<String>() }

    // Scarica i dati del gruppo in tempo reale
    LaunchedEffect(codiceGruppo) {
        db.collection("gruppi_registrati").document(codiceGruppo)
            .addSnapshotListener { snapshot, errore ->
                if (errore != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    adminGruppo = snapshot.getString("admin") ?: ""
                    val partecipanti = snapshot.get("partecipanti") as? List<String> ?: emptyList()
                    listaPartecipanti.clear()
                    listaPartecipanti.addAll(partecipanti)
                }
            }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // CARD DEL GRUPPO E CONDIVISIONE
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Il tuo Gruppo", style = MaterialTheme.typography.labelLarge)
                Text(text = codiceGruppo, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val testoCondivisione = "Unisciti al mio gruppo per registrare le spese!\n\nScarica l'app e inserisci il codice esatto: $codiceGruppo"
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, testoCondivisione)
                        }
                        context.startActivity(Intent.createChooser(intent, "Condividi codice con..."))
                    }
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Condividi", modifier = Modifier.padding(end = 8.dp))
                    Text("Invita amici")
                }
            }
        }

        // PANNELLO AMMINISTRATORE (Visibile solo all'Admin)
        if (emailUtenteAttuale == adminGruppo) {
            Divider()
            Text("Pannello Amministratore (Membri)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    listaPartecipanti.forEach { emailPartecipante ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, contentDescription = "Utente", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(emailPartecipante, style = MaterialTheme.typography.bodyMedium)
                            }

                            // Cestino per espellere (NON appare per l'Admin stesso)
                            if (emailPartecipante != adminGruppo) {
                                IconButton(
                                    onClick = {
                                        db.collection("gruppi_registrati").document(codiceGruppo)
                                            .update("partecipanti", FieldValue.arrayRemove(emailPartecipante))
                                            .addOnSuccessListener { Toast.makeText(context, "Membro rimosso!", Toast.LENGTH_SHORT).show() }
                                    }
                                ) { Icon(Icons.Default.Delete, contentDescription = "Espelli", tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }

        Divider()

        // SEZIONE CATEGORIE (La tua logica originale)
        Text("Aggiungi nuova categoria condivisa", style = MaterialTheme.typography.titleMedium)

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = nuovaCategoria,
                onValueChange = { nuovaCategoria = it },
                label = { Text("Nome categoria") },
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = {
                    val nomePulito = nuovaCategoria.trim()
                    if (nomePulito.length >= 2) {
                        viewModel.aggiungiCategoria(
                            nomeCategoria = nomePulito,
                            onSuccess = {
                                nuovaCategoria = ""
                                Toast.makeText(context, "Categoria salvata!", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { Toast.makeText(context, "Errore salvataggio", Toast.LENGTH_SHORT).show() }
                        )
                    } else {
                        Toast.makeText(context, "Nome troppo corto", Toast.LENGTH_SHORT).show()
                    }
                }
            ) { Text("Aggiungi") }
        }

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listaCategorie) { categoria ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = categoria.nome, style = MaterialTheme.typography.bodyLarge)
                        IconButton(
                            onClick = {
                                viewModel.eliminaCategoria(
                                    idCategoria = categoria.id,
                                    onSuccess = { Toast.makeText(context, "Eliminata", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        ) { Icon(Icons.Default.Delete, "Elimina", tint = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. SCHERMATA REPORT SPESE - Invariata
// ============================================================================
@Composable
fun SchermataReportSpese(viewModel: SpeseViewModel) {
    val context = LocalContext.current

    val listaSpese = viewModel.listaTutteSpese
    val spesePerAnno: Map<Int, List<SpesaFirebase>> = listaSpese.groupBy { estraiAnnoData(it.timestamp) }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Button(
            onClick = { esportaDettaglioCSV(context, listaSpese, "Report_Tutte_Le_Spese") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Scarica Tutto il Report in CSV") }
        Divider()

        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(spesePerAnno.keys.toList().sortedDescending()) { anno ->
                val speseDellAnno = spesePerAnno[anno] ?: emptyList()
                val totaleAnno = speseDellAnno.sumOf { it.importo }
                val spesePerUtenteInAnno = speseDellAnno.groupBy { it.utenteEmail }

                Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "ANNO $anno", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                            Text(text = "Totale: € ${String.format("%.2f", totaleAnno)}", style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(modifier = Modifier.height(12.dp)); Divider(); Spacer(modifier = Modifier.height(12.dp))

                        spesePerUtenteInAnno.forEach { (utenteEmail, speseUtente) ->
                            val totaleUtenteInAnno = speseUtente.sumOf { it.importo }
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = utenteEmail, style = MaterialTheme.typography.titleSmall)
                                            Text(text = "${speseUtente.size} spese", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Text(text = "€ ${String.format("%.2f", totaleUtenteInAnno)}", style = MaterialTheme.typography.titleMedium)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { esportaDettaglioCSV(context, speseUtente, "Report_${anno}_${utenteEmail.replace("@", "_")}") },
                                        modifier = Modifier.align(Alignment.End)
                                    ) { Text("Scarica CSV Utente", style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. SCHERMATA DI LOGIN CON GOOGLE E HELPER
// ============================================================================
@Composable
fun SchermataLogin(onLoginCompletato: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val auth = FirebaseAuth.getInstance()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { coroutineScope.launch { eseguiNuovoLoginGoogle(context, auth, onLoginCompletato) } }) {
            Text("Accedi con Google")
        }
    }
}

private suspend fun eseguiNuovoLoginGoogle(context: Context, auth: FirebaseAuth, onLoginCompletato: () -> Unit) {
    val credentialManager = CredentialManager.create(context)
    val googleIdOption = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(context.getString(R.string.default_web_client_id)).setAutoSelectEnabled(false).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    try {
        val result = credentialManager.getCredential(request = request, context = context)
        val credential = result.credential
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val credenzialeFirebase = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            auth.signInWithCredential(credenzialeFirebase).addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(context, "Login effettuato!", Toast.LENGTH_SHORT).show()
                    onLoginCompletato()
                } else {
                    Toast.makeText(context, "Errore Firebase: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Errore: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

// HELPER: Salvataggio locale del codice
fun salvaCodiceLocale(context: Context, codice: String) {
    val prefs = context.getSharedPreferences("SpeseAppPrefs", Context.MODE_PRIVATE)
    prefs.edit().putString("CODICE_GRUPPO", codice).apply()
}

fun leggiCodiceLocale(context: Context): String {
    val prefs = context.getSharedPreferences("SpeseAppPrefs", Context.MODE_PRIVATE)
    return prefs.getString("CODICE_GRUPPO", "") ?: ""
}

// HELPER: Data e CSV
fun formattaTimestamp(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ITALY).format(Date(timestamp))
fun estraiAnnoData(timestamp: Long): Int = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.YEAR)

fun esportaDettaglioCSV(context: Context, spese: List<SpesaFirebase>, nomeFile: String) {
    try {
        val file = File(context.cacheDir, "$nomeFile.csv")
        val writer = FileWriter(file)
        writer.append("ID,Data,Descrizione,Categoria,Importo,Utente\n")
        for (spesa in spese) {
            writer.append("${spesa.id},\"${formattaTimestamp(spesa.timestamp)}\",\"${spesa.descrizione}\",\"${spesa.categoria}\",${spesa.importo},\"${spesa.utenteEmail}\"\n")
        }
        writer.flush(); writer.close()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Scarica CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Errore file: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
