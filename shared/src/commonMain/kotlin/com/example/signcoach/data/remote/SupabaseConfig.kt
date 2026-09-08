package com.example.signcoach.data.remote

/**
 * Verbindingsgegevens voor het Supabase-project dat als "referentiedatabase" dient:
 * goedgekeurde community-samples die de app bij opstarten synct (zie [GestureContributionApi]
 * en [com.example.signcoach.data.repository.SyncingTemplateRepository]).
 *
 * De ANON_KEY is de publieke Supabase "anon"-sleutel, beveiligd door Row Level Security
 * (zie de migratie `gesture_reference_dataset`): deze sleutel mag alleen nieuwe samples als
 * 'pending' aanmaken en alleen samples met status 'approved' lezen. Nooit rechtstreeks
 * schrijven naar de reviewstatus. Dit is de standaard, veilige manier om Supabase vanuit een
 * client-app te gebruiken - de sleutel hoort thuis in de client, de RLS-policies zijn de
 * eigenlijke beveiliging.
 */
internal object SupabaseConfig {
    const val URL = "https://zjedxlvdeedpkbxzqkpo.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpqZWR4bHZkZWVkcGtieHpxa3BvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg4NzYzNDIsImV4cCI6MjEwNDQ1MjM0Mn0.qAs07pzMWGuQsqY1SFV2VGvs_UT0h0m6-1ftj14Lcp4"
}
