package com.example.signcoach.data.repository

import com.example.signcoach.data.model.SignTemplate

interface TemplateRepository {
    fun load(): Result<List<SignTemplate>>
}
