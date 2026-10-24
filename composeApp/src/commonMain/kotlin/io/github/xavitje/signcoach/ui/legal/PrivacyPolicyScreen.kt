package io.github.xavitje.signcoach.ui.legal
import io.github.xavitje.signcoach.resources.*
import io.github.xavitje.signcoach.resources.Res
import org.jetbrains.compose.resources.stringResource

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.xavitje.signcoach.ui.theme.Plum

/**
 * Privacybeleid (AVG/GDPR), bereikbaar vanaf ProfileScreen. Dit is bewust een STATISCH scherm
 * (geen aparte vertaalstrings per zin) — een juridische tekst als deze moet als geheel consistent
 * blijven, en per-zin-vertaalstrings maken dat lastiger te onderhouden. De tekst is momenteel
 * alleen in het Nederlands beschikbaar (de primaire doelgroep); Engelstalige gebruikers kunnen
 * via het onderstaande contactadres om een vertaling vragen.
 *
 * BELANGRIJK: dit is een ontwerptekst, opgesteld als praktisch startpunt — geen vervanging voor
 * een daadwerkelijke juridische toets voordat de app breed in de EU gepubliceerd wordt. Zie de
 * paragraaf "Over dit document" onderaan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.privacy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.coach_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Plum),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Laatst bijgewerkt: september 2026",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section(
                title = "1. Wie is verantwoordelijk voor je gegevens?",
                body = "SignCoach is een project van Rafi Elidrissi, als particulier ontwikkeld " +
                    "(geen bedrijf/rechtspersoon). Voor alle vragen over je gegevens of dit " +
                    "privacybeleid kun je contact opnemen via:\n\n" +
                    "E-mail: contact@rafimaaktwebsites.nl",
            )

            Section(
                title = "2. Welke gegevens verzamelt SignCoach?",
                body = "• Accountgegevens: je e-mailadres en (versleuteld) wachtwoord, of — als je " +
                    "inlogt met Google — de gegevens die Google bij het inloggen deelt (e-mailadres). " +
                    "Dit wordt beheerd via Supabase Auth.\n\n" +
                    "• Profiel- en voortgangsgegevens: avatar, XP, oefenstreak, welke gebaren je " +
                    "geoefend hebt en met welk resultaat.\n\n" +
                    "• Camera- en handtracking-gegevens: tijdens het oefenen analyseert de app je " +
                    "handbewegingen via de camera. Dit gebeurt volledig op je eigen toestel — er " +
                    "wordt geen camerabeeld of video verstuurd naar onze servers. Alleen numerieke " +
                    "\"landmark\"-coördinaten van je hand (geen herkenbaar beeld) kunnen tijdelijk " +
                    "lokaal gebruikt worden om je oefening te beoordelen.\n\n" +
                    "• Bijdrage-opnames: als je zelf vrijwillig een voorbeeldopname van een gebaar " +
                    "instuurt (\"Bijdragen\"-functie) om de gebarenherkenning te verbeteren, wordt " +
                    "die video/afbeelding wél naar onze servers geüpload en door een beheerder " +
                    "beoordeeld vóór gebruik. Dit gebeurt alleen als je hier zelf voor kiest.\n\n" +
                    "• Crashrapporten: als de app onverwacht vastloopt, wordt automatisch een " +
                    "technisch rapport opgeslagen (foutmelding, stack trace, apparaatmodel, " +
                    "besturingssysteemversie, app-versie, en — als je ingelogd was — je gebruikers-ID). " +
                    "Dit helpt ons de app te verbeteren en bevat geen inhoud van je oefeningen.\n\n" +
                    "• Pushmeldingstoken: als je toestemming geeft voor meldingen, slaan we een " +
                    "technisch \"devicetoken\" op (via Firebase Cloud Messaging) om je een " +
                    "herinnering te kunnen sturen als je een dag niet geoefend hebt.",
            )

            Section(
                title = "3. Waarom verwerken we deze gegevens? (grondslag)",
                body = "• Om de app te laten werken en je voortgang te bewaren: noodzakelijk voor de " +
                    "uitvoering van de overeenkomst die ontstaat zodra je een account aanmaakt.\n\n" +
                    "• Crashrapporten en technisch beheer: gerechtvaardigd belang (een werkende, " +
                    "stabiele app aanbieden).\n\n" +
                    "• Bijdrage-opnames en pushmeldingen: uitdrukkelijke toestemming — je kiest hier " +
                    "zelf actief voor (opname insturen / notificatietoestemming geven) en kunt dit " +
                    "op elk moment intrekken.",
            )

            Section(
                title = "4. Met wie delen we gegevens?",
                body = "We verkopen je gegevens nooit. We maken gebruik van de volgende " +
                    "verwerkers (subverwerkers), die uitsluitend in onze opdracht gegevens " +
                    "verwerken:\n\n" +
                    "• Supabase (database, authenticatie en bestandsopslag) — gehost in de EU " +
                    "(Frankfurt, Duitsland).\n\n" +
                    "• Google Firebase Cloud Messaging (alleen als je pushmeldingen aanzet) — " +
                    "voor het bezorgen van herinneringsmeldingen. Google kan gegevens buiten de EER " +
                    "verwerken; Google conformeert zich aan de EU-standaardcontractbepalingen " +
                    "hiervoor.\n\n" +
                    "We geven geen gegevens door aan derden voor marketingdoeleinden.",
            )

            Section(
                title = "5. Hoe lang bewaren we je gegevens?",
                body = "Zolang je account bestaat. Verwijder je je account (via Profiel → Account " +
                    "verwijderen), dan worden je profiel- en voortgangsgegevens verwijderd. " +
                    "Crashrapporten die al verstuurd zijn kunnen nog een beperkte periode bewaard " +
                    "blijven voor technische analyse, zonder dat deze nog aan jou te koppelen zijn " +
                    "zodra je account weg is.",
            )

            Section(
                title = "6. Jouw rechten",
                body = "Je hebt onder de AVG het recht op inzage, correctie en verwijdering van je " +
                    "gegevens, het recht om bezwaar te maken tegen verwerking, en het recht op " +
                    "gegevensoverdraagbaarheid. Je kunt je account en de bijbehorende gegevens zelf " +
                    "verwijderen via Profiel → Account verwijderen, of contact opnemen via " +
                    "contact@rafimaaktwebsites.nl voor andere verzoeken. Je hebt ook het recht een " +
                    "klacht in te dienen bij de Autoriteit Persoonsgegevens (autoriteitpersoonsgegevens.nl).",
            )

            Section(
                title = "7. Beveiliging",
                body = "Gegevens worden versleuteld verzonden (HTTPS/TLS). Toegang tot gegevens in " +
                    "de database is beperkt via Row Level Security: gebruikers kunnen normaliter " +
                    "alleen hun eigen gegevens inzien, en alleen de beheerder heeft toegang tot " +
                    "bijdrage-beoordelingen en crashrapporten.",
            )

            Section(
                title = "8. Kinderen",
                body = "SignCoach kan door kinderen gebruikt worden, bijvoorbeeld om gebarentaal te " +
                    "leren. Voor kinderen jonger dan 16 jaar is toestemming van een ouder of " +
                    "voogd vereist voor het aanmaken van een account, in lijn met de AVG. Ouders die " +
                    "menen dat hun kind zonder toestemming een account heeft aangemaakt kunnen " +
                    "contact opnemen via contact@rafimaaktwebsites.nl om het account te laten " +
                    "verwijderen.",
            )

            Section(
                title = "9. Wijzigingen",
                body = "Dit privacybeleid kan worden aangepast, bijvoorbeeld bij nieuwe functies. " +
                    "Belangrijke wijzigingen worden in de app aangekondigd.",
            )

            Section(
                title = "Over dit document",
                body = "Deze tekst is opgesteld als praktisch startpunt om aan de AVG te voldoen en " +
                    "is geen vervanging voor juridisch advies. Voor een brede publicatie in de EU " +
                    "(bijv. in app stores) wordt aangeraden dit document te laten toetsen door een " +
                    "jurist, met name de precieze bewaartermijnen en de Firebase-doorgifteclausule.",
            )

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
