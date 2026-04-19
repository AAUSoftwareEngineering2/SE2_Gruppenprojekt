package at.aau.kuhhandel.app.ui.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import at.aau.kuhhandel.app.ui.components.MenuBackground
import at.aau.kuhhandel.app.ui.components.MenuCard

@Composable
fun RulesScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    MenuBackground(modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 64.dp, bottom = 32.dp, start = 32.dp, end = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            MenuCard(onBack = onBack) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "Kuhhandel - Spielregeln",
                        style = MaterialTheme.typography.headlineMedium,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    RuleSection(
                        title = "Spielziel",
                        content =
                            "Ziel des Spiels ist es, die wertvollsten Tiere und Geldkarten " +
                                "zu sammeln um am Ende das höchste Vermögen zu haben.",
                    )

                    RuleSection(
                        title = "Spielstart",
                        content =
                            "Jeder Spieler erhält eine bestimmte Anzahl von Karten. " +
                                "Die verbleibenden Karten bilden den Nachziehstapel.",
                    )

                    RuleSection(
                        title = "Spielablauf",
                        content =
                            "In jeder Runde kann ein Spieler eine Aktion durchführen: " +
                                "Karten tauschen, bieten oder passen.",
                    )

                    RuleSection(
                        title = "Bieten",
                        content =
                            "Spieler können um Tiere und Geldkarten bieten. " +
                                "Das höchste Gebot gewinnt die Karte.",
                    )

                    RuleSection(
                        title = "Ende",
                        content =
                            "Das Spiel endet nach einer festgelegten Anzahl von Runden. " +
                                "Der Spieler mit dem höchsten Vermögen gewinnt.",
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        "Vollständige Regeln",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleSection(
    title: String,
    content: String,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            content,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
