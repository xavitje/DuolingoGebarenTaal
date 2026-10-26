package io.github.xavitje.signcoach.platform

/**
 * Opent het printvenster van het systeem voor een HTML-document (bv. de inlogkaartjes van een
 * klas). Op Android via PrintManager (ook "Opslaan als PDF"), op iOS via UIPrintInteractionController.
 * Geeft false als printen nu niet kan.
 */
expect fun printHtml(jobName: String, html: String): Boolean
