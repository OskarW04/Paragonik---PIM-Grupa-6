package com.example.paragonik.logic

import com.example.paragonik.model.Person
import com.example.paragonik.model.ReceiptItem

data class Share(
    val item: ReceiptItem,
    val amount: Long,
    val splitBetween: Int, // na ile osób podzielono pozycję
)

data class PersonShare(val person: Person, val shares: List<Share>) {
    val total: Long get() = shares.sumOf { it.amount }
}

object SplitCalculator {

    // Każdą pozycję dzielimy po równo między przypisane osoby (brak wpisu w assigned = wszyscy).
    // Resztę z dzielenia rozdajemy po 1 gr: najpierw tym, którzy dostali dotąd najmniej
    // dodatkowych groszy, a przy remisie wg kolejności na liście. Dzięki temu suma udziałów
    // zawsze równa się sumie pozycji.
    fun split(
        items: List<ReceiptItem>,
        people: List<Person>,
        assigned: Map<Long, Set<Long>>,
    ): List<PersonShare> {
        val sharesOf = people.associate { it.id to mutableListOf<Share>() }
        val extraGrosze = people.associate { it.id to 0 }.toMutableMap()

        for (item in items) {
            val ids = assigned[item.id]
            val selected = people.filter { ids == null || it.id in ids }.ifEmpty { people }
            if (selected.isEmpty()) continue
            val count = selected.size.toLong()
            // floorDiv/floorMod dają dobry wynik też dla kwot ujemnych (np. zwrot kaucji)
            val base = Math.floorDiv(item.price, count)
            val rest = Math.floorMod(item.price, count).toInt()
            // sortedBy jest stabilne, więc przy remisie wygrywa kolejność z listy
            val withExtra = selected.sortedBy { extraGrosze.getValue(it.id) }.take(rest).map { it.id }.toSet()
            for (person in selected) {
                val amount = if (person.id in withExtra) base + 1 else base
                if (person.id in withExtra) extraGrosze[person.id] = extraGrosze.getValue(person.id) + 1
                sharesOf.getValue(person.id).add(Share(item, amount, selected.size))
            }
        }
        return people.map { PersonShare(it, sharesOf.getValue(it.id)) }
    }
}
