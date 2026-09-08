package com.example.signcoach.data.repository

import com.example.signcoach.data.model.SignTemplate

/** Simpele [TemplateRepository] rond een al opgehaalde/samengevoegde lijst — de brug tussen
 * [SyncingTemplateRepository.syncAndLoad] (suspend) en [CoachViewModel] (verwacht een
 * synchrone [TemplateRepository]). */
class StaticTemplateRepository(private val templates: List<SignTemplate>) : TemplateRepository {
    override fun load(): Result<List<SignTemplate>> = Result.success(templates)
}
