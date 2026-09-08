package com.example.signcoach.data.model

import kotlinx.serialization.Serializable

/**
 * [frames]: voor statische gebaren de hoofdreferentiepose (frames.first()); voor bewegende
 * gebaren (MOTION_DIRECTION) het volledige bewegingstraject in volgorde.
 *
 * [referenceVariants]: optionele extra geldige referentieposes voor hetzelfde statische
 * gebaar (bv. een andere persoon, links- i.p.v. rechtshandig, een net iets andere hoek).
 * De scorer vergelijkt de live pose met elke variant (frames.first() + referenceVariants)
 * en gebruikt de best passende. Leeg = alleen frames.first() als referentie, zoals in de
 * MVP. Dit veld is de opstap naar een echte, groeiende referentiedataset per gebaar zonder
 * dat het opslagformaat opnieuw hoeft te veranderen.
 */
@Serializable
data class SignTemplate(
    val id: String,
    val name: String,
    val frames: List<HandPose>,
    val rules: List<GestureRule>,
    val referenceVariants: List<HandPose> = emptyList(),
)
