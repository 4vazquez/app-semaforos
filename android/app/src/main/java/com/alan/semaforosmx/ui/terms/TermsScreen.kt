package com.alan.semaforosmx.ui.terms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R

/** Modo de la pantalla de documentos legales. */
enum class TermsMode {
    /** Primera apertura: resumen + texto completo + Acepto / No acepto. */
    ACCEPTANCE,
    /** Relectura desde Ajustes: texto completo + Cerrar. */
    VIEW
}

/** Qué documento legal muestra la pantalla. */
enum class LegalDocument {
    /** Términos y Condiciones (el documento que se acepta). */
    TERMS,
    /** Aviso de Privacidad (se acepta junto con los Términos). */
    PRIVACY
}

/**
 * Pantalla de documentos legales (Términos y Aviso de Privacidad).
 *
 * En modo [TermsMode.ACCEPTANCE] (primera apertura) muestra primero un
 * resumen en lenguaje sencillo de las cláusulas clave y después el texto
 * completo de los Términos (verbatim, desde assets), con un botón para
 * leer el Aviso de Privacidad antes de decidir. El usuario debe presionar
 * «Acepto» para usar la app; «No acepto» cierra la app.
 *
 * En modo [TermsMode.VIEW] (desde Ajustes) solo muestra el texto completo
 * del documento indicado con un botón para cerrar.
 *
 * El texto se muestra sin formato Markdown (se quitan los caracteres de
 * formato, sin tocar las palabras). Un renderizador Markdown queda para
 * v1; el contenido legal es el mismo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsScreen(
    documentText: String,
    mode: TermsMode,
    document: LegalDocument = LegalDocument.TERMS,
    privacyText: String = "",
    onAccept: () -> Unit = {},
    onDecline: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // En la aceptación, el usuario puede ir a leer el Aviso de Privacidad
    // antes de decidir: acepta los dos documentos juntos, así que ambos
    // deben estar disponibles antes de presionar «Acepto».
    var showingPrivacy by remember { mutableStateOf(false) }
    val showingDocument =
        if (mode == TermsMode.ACCEPTANCE && showingPrivacy) LegalDocument.PRIVACY else document
    val visibleText =
        if (showingDocument == LegalDocument.PRIVACY) privacyText else documentText

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (showingDocument) {
                                LegalDocument.TERMS -> R.string.terms_title
                                LegalDocument.PRIVACY -> R.string.privacy_title
                            }
                        )
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                if (mode == TermsMode.ACCEPTANCE && showingDocument == LegalDocument.TERMS) {
                    // Resumen en lenguaje sencillo (no sustituye al texto).
                    Text(
                        text = stringResource(R.string.terms_summary_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(stringResource(R.string.terms_summary_driver))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.terms_summary_accuracy))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.terms_summary_liability))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.terms_summary_data))
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(stringResource(R.string.terms_summary_abuse))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.terms_read_full),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(
                    text = stripMarkdown(visibleText),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        when (showingDocument) {
                            LegalDocument.TERMS -> R.string.terms_version
                            LegalDocument.PRIVACY -> R.string.privacy_version
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Botones fijos al pie (fuera del scroll).
            when {
                showingDocument == LegalDocument.PRIVACY && mode == TermsMode.ACCEPTANCE -> {
                    Button(
                        onClick = { showingPrivacy = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.privacy_back))
                    }
                }
                mode == TermsMode.ACCEPTANCE -> {
                    Column {
                        TextButton(
                            onClick = { showingPrivacy = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.privacy_read))
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDecline,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.terms_decline))
                            }
                            Button(
                                onClick = onAccept,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.terms_accept))
                            }
                        }
                    }
                }
                else -> {
                    Button(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(stringResource(R.string.close_button))
                    }
                }
            }
        }
    }
}

/**
 * Quita los caracteres de formato Markdown para mostrar el texto plano.
 * Solo se eliminan símbolos de formato (#, **, >, ---); las palabras del
 * documento legal quedan intactas.
 */
internal fun stripMarkdown(markdown: String): String =
    markdown.lineSequence().joinToString("\n") { line ->
        line.trimStart()
            .removePrefix("### ")
            .removePrefix("## ")
            .removePrefix("# ")
            .removePrefix("> ")
            .replace("**", "")
            .let { if (it == "---") "" else it }
    }.replace(Regex("\n{3,}"), "\n\n")
