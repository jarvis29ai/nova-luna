package com.nova.luna.shopping

class ShoppingRequirementBuilder {
    fun updateProfile(current: ShoppingRequirementProfile, request: ShoppingRequest): ShoppingRequirementProfile {
        return current.copy(
            category = if (request.category != ShoppingProductCategory.UNKNOWN) request.category else current.category,
            productName = request.productName ?: current.productName,
            budget = request.budget ?: current.budget,
            purpose = if (request.purpose != ShoppingPurpose.UNKNOWN) request.purpose else current.purpose,
            preferredBrand = request.brand ?: current.preferredBrand,
            preferredWebsite = request.website ?: current.preferredWebsite
        )
    }

    fun hasRequiredDetails(profile: ShoppingRequirementProfile): Boolean {
        return profile.category != ShoppingProductCategory.UNKNOWN &&
               profile.budget != null &&
               profile.purpose != ShoppingPurpose.UNKNOWN
    }

    fun getMissingDetailQuestion(profile: ShoppingRequirementProfile): String {
        return when {
            profile.category == ShoppingProductCategory.UNKNOWN -> "What category of product are you looking for?"
            profile.budget == null -> "What is your budget for this ${profile.category}?"
            profile.purpose == ShoppingPurpose.UNKNOWN -> "What will you use it for: gaming, work, photography, study, or battery?"
            else -> "Could you tell me more about your requirements?"
        }
    }
}
