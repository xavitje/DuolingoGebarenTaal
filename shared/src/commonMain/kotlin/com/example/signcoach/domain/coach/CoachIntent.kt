package com.example.signcoach.domain.coach
sealed interface CoachIntent { data object LoadTemplates: CoachIntent; data class SelectTemplate(val id:String): CoachIntent; data object StartPractice: CoachIntent; data object StopPractice: CoachIntent; data object Retry: CoachIntent }
