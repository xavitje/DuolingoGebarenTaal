package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.SignTemplate

/** Simpele [TemplateRepository] rond een al opgehaalde/samengevoegde lijst — de brug tussen
 * [SyncingTemplateRepository.syncAndLoad] (suspend) en [CoachViewModel] (verwacht een
 * synchrone [TemplateRepository]). */
class StaticTemplateRepository(private val templates: List<SignTemplate>) : TemplateRepository {
    override fun load(): Result<List<SignTemplate>> = Result.success(templates)
}
