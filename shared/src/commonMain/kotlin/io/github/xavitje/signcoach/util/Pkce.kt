package io.github.xavitje.signcoach.util

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/**
 * PKCE (Proof Key for Code Exchange, RFC 7636) hulpfuncties voor de Google-OAuth-flow via
 * Supabase Auth (zie [io.github.xavitje.signcoach.data.remote.SupabaseAuthApi]). De code_verifier
 * blijft alleen op het apparaat (in-memory in [io.github.xavitje.signcoach.presentation.AuthViewModel]);
 * alleen de onomkeerbare code_challenge (SHA-256-hash) gaat mee in de autorisatie-URL die in de
 * browser wordt geopend.
 */
@OptIn(ExperimentalEncodingApi::class)
object Pkce {
    private val urlSafeNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** Genereert een cryptografisch willekeurige code_verifier (RFC 7636 eist 43-128 tekens). */
    fun generateCodeVerifier(): String {
        val bytes = Random.nextBytes(64)
        return urlSafeNoPadding.encode(bytes)
    }

    /** Berekent de S256 code_challenge voor een gegeven code_verifier. */
    fun codeChallenge(verifier: String): String {
        val hash = sha256(verifier.encodeToByteArray())
        return urlSafeNoPadding.encode(hash)
    }
}
