package com.example.ui.screens.productivity
// PROD-R11: calculator works fully offline, no network calls

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import org.json.JSONArray
import java.io.InputStreamReader
import androidx.compose.material.icons.automirrored.filled.*
import com.example.Res

data class FoodItem(
    val id: Int,
    val name: String,
    val caloriesPer100g: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double
)

data class DietEntry(
    val food: FoodItem,
    val weightGrams: Int
) {
    val totalCalories = food.caloriesPer100g * (weightGrams / 100.0)
    val totalProtein = food.protein * (weightGrams / 100.0)
    val totalCarbs = food.carbs * (weightGrams / 100.0)
    val totalFat = food.fat * (weightGrams / 100.0)
}

fun loadFoodDatabase(context: Context): List<FoodItem> {
    return try {
        val inputStream = context.resources.openRawResource(R.raw.veg_foods) // PROD-R10
        val reader = InputStreamReader(inputStream)
        val jsonString = reader.readText()
        reader.close()
        
        val jsonArray = JSONArray(jsonString)
        val list = mutableListOf<FoodItem>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(
                FoodItem(
                    id = obj.getInt("id"),
                    name = obj.getString("name"),
                    caloriesPer100g = obj.getDouble("calories_per_100g"),
                    protein = obj.getDouble("protein_g"),
                    carbs = obj.getDouble("carbs_g"),
                    fat = obj.getDouble("fat_g")
                )
            )
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalorieCalculatorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var foodDatabase by remember { mutableStateOf<List<FoodItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    val dietProject = remember { mutableStateListOf<DietEntry>() }
    
    var showAddDialog by remember { mutableStateOf<FoodItem?>(null) }
    var weightInput by remember { mutableStateOf("100") }

    LaunchedEffect(Unit) {
        foodDatabase = loadFoodDatabase(context)
    }

    val filteredFoods = remember(searchQuery, foodDatabase) {
        if (searchQuery.isBlank()) {
            foodDatabase.take(50)
        } else {
            foodDatabase.filter { it.name.contains(searchQuery, ignoreCase = true) }.take(50)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.calculadora_de_calorias), color = OnSurfaceLight, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar), tint = OnSurfaceLight)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepDark)
            )
        },
        containerColor = DeepDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Diet Summary
            DietSummary(dietProject)
            
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = DarkBorder)
            Spacer(Modifier.height(16.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(Res.get(R.string.pesquisar_alimentos_ex_soja_arroz)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanNeon) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanNeon,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = OnSurfaceLight,
                    unfocusedTextColor = OnSurfaceLight,
                    focusedLabelColor = CyanNeon
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(8.dp))

            // Food List
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredFoods) { food ->
                    FoodCard(food = food, onClick = {
                        showAddDialog = food
                        weightInput = "100"
                    })
                }
            }
        }
    }

    // Add Food Dialog
    val food = showAddDialog
    if (food != null) {
        AlertDialog(
            onDismissRequest = { showAddDialog = null },
            title = { Text(Res.get(R.string.adicionar_x, (food.name))) },
            text = {
                Column {
                    Text(Res.get(R.string.calorias_por_100g_x_kcal, (food.caloriesPer100g)))
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text(Res.get(R.string.peso_gramas)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val weight = weightInput.toIntOrNull() ?: 100
                    dietProject.add(DietEntry(food, weight))
                    showAddDialog = null
                }, colors = ButtonDefaults.buttonColors(containerColor = CyanNeon)) {
                    Text(Res.get(R.string.adicionar))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = null }) {
                    Text(Res.get(R.string.cancelar), color = OnSurfaceLight)
                }
            },
            containerColor = DarkNavy,
            titleContentColor = OnSurfaceLight,
            textContentColor = OnSurfaceLight
        )
    }
}

@Composable
fun DietSummary(dietProject: List<DietEntry>) {
    val totalCalories = dietProject.sumOf { it.totalCalories }
    val totalProtein = dietProject.sumOf { it.totalProtein }
    val totalCarbs = dietProject.sumOf { it.totalCarbs }
    val totalFat = dietProject.sumOf { it.totalFat }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkNavy)
            .padding(16.dp)
    ) {
        Text(Res.get(R.string.projeto_de_dieta_atual), color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Res.get(R.string.calorias), color = OnSurfaceLight)
            Text("${String.format("%.1f", totalCalories)} kcal", color = AmberGold, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Res.get(R.string.proteinas), color = OnSurfaceLight)
            Text("${String.format("%.1f", totalProtein)} g", color = SuccessGreen)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Res.get(R.string.carboidratos), color = OnSurfaceLight)
            Text("${String.format("%.1f", totalCarbs)} g", color = ElectricViolet)
        }
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(Res.get(R.string.gorduras), color = OnSurfaceLight)
            Text("${String.format("%.1f", totalFat)} g", color = androidx.compose.ui.graphics.Color.Red)
        }
    }
}

@Composable
fun FoodCard(food: FoodItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkNavy)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, color = OnSurfaceLight, fontWeight = FontWeight.SemiBold)
            Text(Res.get(R.string.proteina_xg_carbs_xg_gordura_xg_por_100g, (food.protein), (food.carbs), (food.fat)), 
                color = OnSurfaceDim, fontSize = 12.sp)
        }
        Text(Res.get(R.string.x_kcal, (food.caloriesPer100g)), color = AmberGold, fontWeight = FontWeight.Bold)
    }
}
