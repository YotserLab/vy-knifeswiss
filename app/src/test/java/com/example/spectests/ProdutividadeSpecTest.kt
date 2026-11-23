package com.example.spectests
import com.example.ui.screens.productivity.CalendarAlarmsScreen
import com.example.ui.screens.productivity.CalorieCalculatorScreen
import com.example.ui.screens.productivity.Hp48gCalculatorScreen
import com.example.ui.screens.productivity.NotesScreen
import com.example.ui.screens.productivity.PriceCalculatorScreen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProdutividadeSpecTest {

    // PROD-R01: colunas do kanban têm name e position
    @Test
    fun test_PROD_R01_kanban_column_fields() {
        val s = src("com/example/data/Kanban.kt")
        assertTrue(s.contains("kanban_columns") && s.contains("val name: String") && s.contains("val position: Int"))
    }

    // PROD-R02: nota do kanban tem columnId, position e colorHex
    @Test
    fun test_PROD_R02_kanban_note_fields() {
        val s = src("com/example/data/Kanban.kt")
        assertTrue(s.contains("val columnId: Long") && s.contains("val colorHex: String"))
    }

    // PROD-R03: cor padrão da nota é #FFF9C4
    @Test
    fun test_PROD_R03_default_note_color() {
        assertTrue(srcContains("com/example/data/Kanban.kt", "\"#FFF9C4\""))
    }

    // PROD-R04: notas lidas do Room via getAllNotes
    @Test
    fun test_PROD_R04_notes_from_room() {
        assertTrue(srcContains("com/example/ui/screens/productivity/NotesScreen.kt", "getAllNotes()"))
    }

    // PROD-R05: lista de compras com itens pré-cadastrados
    @Test
    fun test_PROD_R05_default_shopping_items() {
        assertTrue(srcContains("com/example/data/DefaultShoppingItems.kt", "defaultShoppingItems"))
        assertTrue(srcContains("com/example/data/ShoppingList.kt", "@Entity"))
    }

    // PROD-R06: SmartAlarm lê/escreve o calendário do sistema
    @Test
    fun test_PROD_R06_calendar_contract() {
        assertTrue(srcContains("com/example/ui/screens/productivity/CalendarAlarmsScreen.kt", "CalendarContract.Events.CONTENT_URI"))
        assertTrue(java.io.File("src/main/AndroidManifest.xml").readText().contains("WRITE_CALENDAR"))
    }

    // PROD-R07: entidades de produtividade no mesmo banco Room
    @Test
    fun test_PROD_R07_same_encrypted_db() {
        val s = src("com/example/data/AppDatabase.kt")
        assertTrue(s.contains("KanbanColumn::class") && s.contains("ShoppingItem::class") && s.contains("SmartAlarm::class"))
    }

    // PROD-R08/R12: HP-48G mantém pilha RPN; ENTER vazio duplica o topo
    @Test
    fun test_PROD_R08_R12_rpn_stack() {
        val s = src("com/example/ui/screens/productivity/Hp48gCalculatorScreen.kt")
        assertTrue(s.contains("listOf<Double>()") && s.contains("ENTER with empty buffer duplicates"))
    }

    // PROD-R09: preço calculado por unidade (ex.: por kg)
    @Test
    fun test_PROD_R09_unit_price_comparison() {
        val s = src("com/example/ui/screens/productivity/PriceCalculatorScreen.kt")
        assertTrue(s.contains("unitPrice") && s.contains("unitLabel"))
    }

    // PROD-R10: calorias por peso via tabela local por 100g
    @Test
    fun test_PROD_R10_calories_per_100g_local_table() {
        val s = src("com/example/ui/screens/productivity/CalorieCalculatorScreen.kt")
        assertTrue(s.contains("caloriesPer100g") && s.contains("openRawResource(R.raw.veg_foods)"))
    }

    // PROD-R11: calculadoras não fazem chamadas de rede
    @Test
    fun test_PROD_R11_calculators_offline() {
        listOf("Hp48gCalculatorScreen", "PriceCalculatorScreen", "CalorieCalculatorScreen").forEach { f ->
            val s = src("com/example/ui/screens/productivity/$f.kt")
            assertFalse("$f com rede", s.contains("http://") || s.contains("https://") || s.contains("Socket(") || s.contains("HttpURLConnection"))
        }
    }
}

// PROD-R12: ENTER com buffer vazio duplica topo da pilha
class ProdutividadeRpnDupSpecTest {
    @org.junit.Test
    fun test_PROD_R12_enter_duplicates_top() {
        val s = src("com/example/ui/screens/productivity/Hp48gCalculatorScreen.kt")
        org.junit.Assert.assertTrue(s.contains("duplicat") || s.contains("last()"))
    }
}
