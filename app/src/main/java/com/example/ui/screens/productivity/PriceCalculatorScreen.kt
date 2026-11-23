package com.example.ui.screens.productivity
// PROD-R11: calculator works fully offline, no network calls

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import com.example.R
import com.example.Res

sealed class CompareItem {
    abstract val name: String
    abstract val price: Double
    abstract val unitPrice: Double // PROD-R09
    abstract val unitLabel: String

    data class WeightProduct(
        override val name: String,
        override val price: Double,
        val weight: Double, // in grams
        override val unitPrice: Double, // price per kg
        override val unitLabel: String = "kg"
    ) : CompareItem()

    data class ToiletPaperProduct(
        override val name: String,
        override val price: Double,
        val rolls: Int,
        val metersPerRoll: Double,
        override val unitPrice: Double, // price per meter
        override val unitLabel: String = "metro"
    ) : CompareItem()

    data class VolumeProduct(
        override val name: String,
        override val price: Double,
        val volume: Double, // in ml
        override val unitPrice: Double, // price per L
        override val unitLabel: String = "L"
    ) : CompareItem()

    data class UnitProduct(
        override val name: String,
        override val price: Double,
        val units: Int,
        override val unitPrice: Double, // price per unit
        override val unitLabel: String = "un"
    ) : CompareItem()

    data class PromoProduct(
        override val name: String,
        override val price: Double,
        val promoType: String,
        override val unitPrice: Double, // price per unit with promo
        override val unitLabel: String = "un"
    ) : CompareItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceCalculatorScreen(onBack: () -> Unit) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf(Res.get(R.string.por_peso), Res.get(R.string.por_volume), Res.get(R.string.por_unidade), Res.get(R.string.promocoes), Res.get(R.string.papel_higienico_2), "Comparador")
    
    // Comparison list state
    val comparisonList = remember { mutableStateListOf<CompareItem>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Res.get(R.string.calculadora_de_precos)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Res.get(R.string.voltar))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 0.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> WeightCalculatorTab(onAddToCompare = { comparisonList.add(it) })
                1 -> VolumeCalculatorTab(onAddToCompare = { comparisonList.add(it) })
                2 -> UnitCalculatorTab(onAddToCompare = { comparisonList.add(it) })
                3 -> PromoCalculatorTab(onAddToCompare = { comparisonList.add(it) })
                4 -> ToiletPaperCalculatorTab(onAddToCompare = { comparisonList.add(it) })
                5 -> ComparisonTab(
                    comparisonList = comparisonList,
                    onRemoveItem = { comparisonList.remove(it) },
                    onClearAll = { comparisonList.clear() }
                )
            }
        }
    }
}

@Composable
fun WeightCalculatorTab(onAddToCompare: (CompareItem.WeightProduct) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var weightText by remember { mutableStateOf("") }
    var isKilos by remember { mutableStateOf(false) } // false = grams, true = kg

    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val weight = weightText.replace(',', '.').toDoubleOrNull() ?: 0.0

    val pricePerKg = if (price > 0.0 && weight > 0.0) {
        if (isKilos) {
            price / weight
        } else {
            (price / weight) * 1000.0
        }
    } else {
        0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Res.get(R.string.calcular_preco_por_kg),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Res.get(R.string.descubra_quanto_custaria_1kg_do_produto),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Res.get(R.string.nome_do_produto_opcional)) },
            placeholder = { Text(Res.get(R.string.ex_sabao_em_po_a)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(Res.get(R.string.preco_r)) },
                placeholder = { Text("0,00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text(if (isKilos) Res.get(R.string.peso_kg) else Res.get(R.string.peso_g)) },
                placeholder = { Text(if (isKilos) "0.0" else "0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(Res.get(R.string.unidade_de_peso))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                RadioButton(
                    selected = !isKilos,
                    onClick = { isKilos = false }
                )
                Text(Res.get(R.string.gramas_g))
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = isKilos,
                    onClick = { isKilos = true }
                )
                Text(Res.get(R.string.quilos_kg))
            }
        }

        if (pricePerKg > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = Res.get(R.string.preco_por_kg),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = String.format(Locale("pt", "BR"), Res.get(R.string.r_2f), pricePerKg),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val weightInGrams = if (isKilos) weight * 1000.0 else weight
                            val productName = name.ifBlank { Res.get(R.string.produto_xg, (weightInGrams.toInt())) }
                            onAddToCompare(
                                CompareItem.WeightProduct(
                                    name = productName,
                                    price = price,
                                    weight = weightInGrams,
                                    unitPrice = pricePerKg
                                )
                            )
                            name = ""
                            priceText = ""
                            weightText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Res.get(R.string.adicionar_ao_comparador))
                    }
                }
            }
        }
    }
}

@Composable
fun ToiletPaperCalculatorTab(onAddToCompare: (CompareItem.ToiletPaperProduct) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var rollsText by remember { mutableStateOf("") }
    var metersText by remember { mutableStateOf("30") } // default to 30m

    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val rolls = rollsText.toIntOrNull() ?: 0
    val metersPerRoll = metersText.replace(',', '.').toDoubleOrNull() ?: 0.0

    val totalMeters = rolls * metersPerRoll
    val pricePerRoll = if (price > 0.0 && rolls > 0) price / rolls else 0.0
    val pricePerMeter = if (price > 0.0 && totalMeters > 0.0) price / totalMeters else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Res.get(R.string.calcular_preco_do_papel_higienico),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Res.get(R.string.compare_rolos_de_marcas_diferentes_muita),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Res.get(R.string.marca_modelo_opcional)) },
            placeholder = { Text(Res.get(R.string.ex_marca_x_folha_dupla)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(Res.get(R.string.preco_total_r)) },
                placeholder = { Text("0,00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1.2f)
            )

            OutlinedTextField(
                value = rollsText,
                onValueChange = { rollsText = it },
                label = { Text(Res.get(R.string.qtd_rolos)) },
                placeholder = { Text("16") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.9f)
            )

            OutlinedTextField(
                value = metersText,
                onValueChange = { metersText = it },
                label = { Text(Res.get(R.string.metros_rolo)) },
                placeholder = { Text("30") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(0.9f)
            )
        }

        if (pricePerMeter > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = Res.get(R.string.resultados),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Res.get(R.string.total_metros), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(String.format(Locale("pt", "BR"), "%.1fm", totalMeters), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Res.get(R.string.por_rolo), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(String.format(Locale("pt", "BR"), Res.get(R.string.r_2f), pricePerRoll), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Res.get(R.string.por_metro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(String.format(Locale("pt", "BR"), Res.get(R.string.r_3f), pricePerMeter), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val productName = name.ifBlank { Res.get(R.string.papel_x_rolos_x_xm, rolls, (metersPerRoll.toInt())) }
                            onAddToCompare(
                                CompareItem.ToiletPaperProduct(
                                    name = productName,
                                    price = price,
                                    rolls = rolls,
                                    metersPerRoll = metersPerRoll,
                                    unitPrice = pricePerMeter
                                )
                            )
                            name = ""
                            priceText = ""
                            rollsText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Res.get(R.string.adicionar_ao_comparador))
                    }
                }
            }
        }
    }
}

@Composable
fun VolumeCalculatorTab(onAddToCompare: (CompareItem.VolumeProduct) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var volumeText by remember { mutableStateOf("") }
    var isLiters by remember { mutableStateOf(false) } // false = ml, true = L

    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val volume = volumeText.replace(',', '.').toDoubleOrNull() ?: 0.0

    val pricePerLiter = if (price > 0.0 && volume > 0.0) {
        if (isLiters) {
            price / volume
        } else {
            (price / volume) * 1000.0
        }
    } else {
        0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Res.get(R.string.calcular_preco_por_litro),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Res.get(R.string.descubra_o_custo_por_litro_de_bebidas_pr),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Res.get(R.string.nome_do_produto_opcional)) },
            placeholder = { Text(Res.get(R.string.ex_refrigerante_2l)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(Res.get(R.string.preco_r)) },
                placeholder = { Text("0,00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = volumeText,
                onValueChange = { volumeText = it },
                label = { Text(if (isLiters) Res.get(R.string.volume_l) else Res.get(R.string.volume_ml)) },
                placeholder = { Text(if (isLiters) "0.0" else "0") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(Res.get(R.string.unidade_de_volume))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                RadioButton(
                    selected = !isLiters,
                    onClick = { isLiters = false }
                )
                Text(Res.get(R.string.mililitros_ml))
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = isLiters,
                    onClick = { isLiters = true }
                )
                Text(Res.get(R.string.litros_l))
            }
        }

        if (pricePerLiter > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = Res.get(R.string.preco_por_litro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = String.format(Locale("pt", "BR"), Res.get(R.string.r_2f), pricePerLiter),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val volumeInMl = if (isLiters) volume * 1000.0 else volume
                            val productName = name.ifBlank { Res.get(R.string.liquido_xml, (volumeInMl.toInt())) }
                            onAddToCompare(
                                CompareItem.VolumeProduct(
                                    name = productName,
                                    price = price,
                                    volume = volumeInMl,
                                    unitPrice = pricePerLiter
                                )
                            )
                            name = ""
                            priceText = ""
                            volumeText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Res.get(R.string.adicionar_ao_comparador))
                    }
                }
            }
        }
    }
}

@Composable
fun UnitCalculatorTab(onAddToCompare: (CompareItem.UnitProduct) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var unitsText by remember { mutableStateOf("") }

    val price = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    val units = unitsText.toIntOrNull() ?: 0

    val pricePerUnit = if (price > 0.0 && units > 0) price / units else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Res.get(R.string.calcular_preco_por_unidade),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Res.get(R.string.ideal_para_pacotes_de_fraldas_cervejas_c),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Res.get(R.string.nome_do_produto_opcional)) },
            placeholder = { Text(Res.get(R.string.ex_fralda_x)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text(Res.get(R.string.preco_do_pacote_r)) },
                placeholder = { Text("0,00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = unitsText,
                onValueChange = { unitsText = it },
                label = { Text(Res.get(R.string.quantas_unidades_vem)) },
                placeholder = { Text(Res.get(R.string.ex_36)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        if (pricePerUnit > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = Res.get(R.string.preco_por_unidade),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = String.format(Locale("pt", "BR"), Res.get(R.string.r_3f), pricePerUnit),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val productName = name.ifBlank { Res.get(R.string.pacote_x_unidades, units) }
                            onAddToCompare(
                                CompareItem.UnitProduct(
                                    name = productName,
                                    price = price,
                                    units = units,
                                    unitPrice = pricePerUnit
                                )
                            )
                            name = ""
                            priceText = ""
                            unitsText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Res.get(R.string.adicionar_ao_comparador))
                    }
                }
            }
        }
    }
}

@Composable
fun PromoCalculatorTab(onAddToCompare: (CompareItem.PromoProduct) -> Unit) {
    var name by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var selectedPromo by remember { mutableStateOf(0) }
    
    val promos = listOf(
        Res.get(R.string.leve_3_pague_2),
        Res.get(R.string.leve_4_pague_3),
        Res.get(R.string.n_50_desc_na_2a_unidade),
        Res.get(R.string.n_20_desc_na_2a_unidade),
        Res.get(R.string.compre_1_leve_outro_com_50_desc)
    )

    val basePrice = priceText.replace(',', '.').toDoubleOrNull() ?: 0.0
    
    // Computes real price and final quantity
    val (finalPrice, units) = when (selectedPromo) {
        0 -> Pair(basePrice * 2, 3) // Leve 3 Pague 2
        1 -> Pair(basePrice * 3, 4) // Leve 4 Pague 3
        2 -> Pair(basePrice * 1.5, 2) // 50% na 2a -> 1 normal + 1 pela metade
        3 -> Pair(basePrice * 1.8, 2) // 20% na 2a -> 1 normal + 1 por 80%
        4 -> Pair(basePrice * 1.5, 2) // Compre 1 leve outro 50%
        else -> Pair(basePrice, 1)
    }

    val pricePerUnit = if (finalPrice > 0.0 && units > 0) finalPrice / units else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = Res.get(R.string.calculadora_de_promocoes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = Res.get(R.string.descubra_qual_e_o_preco_real_unitario_de),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Res.get(R.string.nome_do_produto_opcional)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it },
            label = { Text(Res.get(R.string.preco_de_1_unidade_normal_r)) },
            placeholder = { Text("0,00") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Text(Res.get(R.string.tipo_de_promocao))
        Column {
            promos.forEachIndexed { index, promo ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = selectedPromo == index,
                        onClick = { selectedPromo = index }
                    )
                    Text(promo, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (pricePerUnit > 0.0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = Res.get(R.string.preco_real_por_unidade),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = String.format(Locale("pt", "BR"), Res.get(R.string.r_3f), pricePerUnit),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = Res.get(R.string.voce_pagara_rs_x_por_x_unidades, String.format(Locale("pt", "BR"), "%.2f", finalPrice), units),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            val productName = name.ifBlank { "Promo (${promos[selectedPromo]})" }
                            onAddToCompare(
                                CompareItem.PromoProduct(
                                    name = productName,
                                    price = finalPrice,
                                    promoType = promos[selectedPromo],
                                    unitPrice = pricePerUnit
                                )
                            )
                            name = ""
                            priceText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(Res.get(R.string.adicionar_ao_comparador))
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonTab(
    comparisonList: List<CompareItem>,
    onRemoveItem: (CompareItem) -> Unit,
    onClearAll: () -> Unit
) {
    if (comparisonList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CompareArrows,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    Res.get(R.string.nenhum_produto_adicionado_para_comparar),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    Res.get(R.string.calcule_precos_nas_abas_anteriores_e_cli),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        // Group and sort
        val weightItems = comparisonList.filterIsInstance<CompareItem.WeightProduct>()
            .sortedBy { it.unitPrice }
        val volumeItems = comparisonList.filterIsInstance<CompareItem.VolumeProduct>()
            .sortedBy { it.unitPrice }
        val unitItems = comparisonList.filterIsInstance<CompareItem.UnitProduct>()
            .sortedBy { it.unitPrice }
        val promoItems = comparisonList.filterIsInstance<CompareItem.PromoProduct>()
            .sortedBy { it.unitPrice }
        val paperItems = comparisonList.filterIsInstance<CompareItem.ToiletPaperProduct>()
            .sortedBy { it.unitPrice }

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Res.get(R.string.comparacao_de_produtos),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onClearAll) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(Res.get(R.string.limpar_tudo))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (weightItems.isNotEmpty()) {
                    item {
                        Text(
                            Res.get(R.string.por_peso_melhor_preco_por_kg_primeiro),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(weightItems) { item ->
                        val isBest = item == weightItems.first()
                        ComparisonItemCard(
                            item = item,
                            isBest = isBest,
                            detailText = Res.get(R.string.preco_total_rs_x_por_x, String.format("%.2f", item.price), if (item.weight >= 1000.0) String.format("%.1fkg", item.weight/1000.0) else Res.get(R.string.xg, (item.weight.toInt()))),
                            unitPriceText = "R$ ${String.format("%.2f", item.unitPrice)} / kg",
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (volumeItems.isNotEmpty()) {
                    item {
                        Text(
                            Res.get(R.string.por_volume_melhor_preco_por_litro_primei),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(volumeItems) { item ->
                        val isBest = item == volumeItems.first()
                        ComparisonItemCard(
                            item = item,
                            isBest = isBest,
                            detailText = Res.get(R.string.preco_total_rs_x_por_x, String.format("%.2f", item.price), if (item.volume >= 1000.0) String.format("%.1fL", item.volume/1000.0) else Res.get(R.string.xml, (item.volume.toInt()))),
                            unitPriceText = "R$ ${String.format("%.2f", item.unitPrice)} / L",
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (unitItems.isNotEmpty()) {
                    item {
                        Text(
                            Res.get(R.string.por_unidade_melhor_preco_por_unidade_pri),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(unitItems) { item ->
                        val isBest = item == unitItems.first()
                        ComparisonItemCard(
                            item = item,
                            isBest = isBest,
                            detailText = Res.get(R.string.preco_total_rs_x_por_x_unidades, String.format("%.2f", item.price), item.units),
                            unitPriceText = "R$ ${String.format("%.3f", item.unitPrice)} / un",
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (promoItems.isNotEmpty()) {
                    item {
                        Text(
                            Res.get(R.string.promocoes_melhor_preco_real_por_unidade),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(promoItems) { item ->
                        val isBest = item == promoItems.first()
                        ComparisonItemCard(
                            item = item,
                            isBest = isBest,
                            detailText = Res.get(R.string.preco_total_rs_x_x, String.format("%.2f", item.price), item.promoType),
                            unitPriceText = "R$ ${String.format("%.3f", item.unitPrice)} / un real",
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }

                if (paperItems.isNotEmpty()) {
                    item {
                        Text(
                            Res.get(R.string.papel_higienico_melhor_preco_por_metro_p),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    items(paperItems) { item ->
                        val isBest = item == paperItems.first()
                        ComparisonItemCard(
                            item = item,
                            isBest = isBest,
                            detailText = Res.get(R.string.preco_total_rs_x_x_rolos_x_m_x_m, String.format("%.2f", item.price), item.rolls, item.metersPerRoll.toInt(), item.rolls * item.metersPerRoll.toInt()),
                            unitPriceText = "R$ ${String.format("%.3f", item.unitPrice)} / m",
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonItemCard(
    item: CompareItem,
    isBest: Boolean,
    detailText: String,
    unitPriceText: String,
    onRemove: () -> Unit
) {
    val cardColor = if (isBest) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (isBest) {
                        Spacer(modifier = Modifier.width(8.dp))
                        SuggestionChip(
                            onClick = {},
                            label = { Text(Res.get(R.string.melhor_escolha), style = MaterialTheme.typography.bodySmall) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
                Text(
                    text = detailText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = unitPriceText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isBest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = Res.get(R.string.remover),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
