package dev.aligator.homecraft

class HaEntitySuggestionProvider(private val homeAssistant: HomeAssistant) {

    /**
     * Get suggestions for entity IDs based on a search term.
     *
     * @param searchTerm The search term to look for in the entity IDs.
     * @return List of suggested entity IDs that match the search term.
     */
    fun getSuggestions(links: LinkStore, searchTerm: String): List<String> {
        return homeAssistant.searchedEntities(searchTerm)
    }
}