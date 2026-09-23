package io.github.xavitje.signcoach.data.repository

import io.github.xavitje.signcoach.data.model.SignTemplate

interface TemplateRepository {
    fun load(): Result<List<SignTemplate>>
}
