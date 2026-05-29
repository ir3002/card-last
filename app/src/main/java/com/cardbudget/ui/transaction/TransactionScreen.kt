package com.cardbudget.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cardbudget.data.entity.TransactionCategory
import com.cardbudget.data.entity.TransactionEntity
import com.cardbudget.data.entity.TransactionSource
import com.cardbudget.ui.TransactionViewModel
import com.cardbudget.ui.home.TransactionItem
import com.cardbudget.ui.home.toWon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(viewModel: TransactionViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("거래 내역") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.showAddDialog() }) {
                Icon(Icons.Default.Add, null)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // SMS 자동수집 배너
            Card(
                Modifier.fillMaxWidth().padding(16.dp, 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sms, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("SMS 자동 수집 활성화", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        val autoCount = state.transactions.count { it.source == TransactionSource.SMS_AUTO }
                        Text("이번 달 ${autoCount}건 자동 추가됨", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                    }
                }
            }

            // 카드 필터
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = state.selectedCardId == null,
                        onClick = { viewModel.selectCard(null) },
                        label = { Text("전체") }
                    )
                }
                items(state.cards) { card ->
                    FilterChip(
                        selected = state.selectedCardId == card.id,
                        onClick = { viewModel.selectCard(card.id) },
                        label = { Text(card.name) }
                    )
                }
            }

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val total = state.transactions.sumOf { it.amount }
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(Modifier.padding(12.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.yearMonth} 합계", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(total.toWon(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    }
                }

                LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                    items(state.transactions, key = { it.id }) { tx ->
                        TransactionItemWithDelete(
                            transaction = tx,
                            cards = state.cards,
                            onDelete = { viewModel.deleteTransaction(tx) }
                        )
                    }
                    if (state.transactions.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("거래 내역이 없습니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        if (state.showAddDialog) {
            AddTransactionDialog(
                cards = state.cards,
                onDismiss = { viewModel.hideAddDialog() },
                onConfirm = { cardId, merchant, amount, category, memo ->
                    viewModel.addManualTransaction(cardId, merchant, amount, category, memo, System.currentTimeMillis())
                }
            )
        }
    }
}

@Composable
private fun TransactionItemWithDelete(
    transaction: TransactionEntity,
    cards: List<com.cardbudget.data.entity.CardEntity>,
    onDelete: () -> Unit
) {
    var showConfirm by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            TransactionItem(transaction = transaction, cards = cards)
        }
        IconButton(onClick = { showConfirm = true }, modifier = Modifier.padding(end = 8.dp)) {
            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f), modifier = Modifier.size(18.dp))
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("거래 삭제") },
            text = { Text("${transaction.merchantName} 거래를 삭제할까요?") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("삭제") }
            },
            dismissButton = { OutlinedButton(onClick = { showConfirm = false }) { Text("취소") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    cards: List<com.cardbudget.data.entity.CardEntity>,
    onDismiss: () -> Unit,
    onConfirm: (Long, String, Long, TransactionCategory, String) -> Unit
) {
    var merchantName by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var selectedCardId by remember { mutableLongStateOf(cards.firstOrNull()?.id ?: 0L) }
    var selectedCategory by remember { mutableStateOf(TransactionCategory.OTHER) }
    var memo by remember { mutableStateOf("") }
    var expandCard by remember { mutableStateOf(false) }
    var expandCategory by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("거래 직접 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { merchantName = it },
                    label = { Text("가맹점명") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() } },
                    label = { Text("금액 (원)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                ExposedDropdownMenuBox(expanded = expandCard, onExpandedChange = { expandCard = it }) {
                    OutlinedTextField(
                        value = cards.find { it.id == selectedCardId }?.name ?: "카드 선택",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("카드") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandCard) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandCard, onDismissRequest = { expandCard = false }) {
                        cards.forEach { card ->
                            DropdownMenuItem(
                                text = { Text(card.name) },
                                onClick = { selectedCardId = card.id; expandCard = false }
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(expanded = expandCategory, onExpandedChange = { expandCategory = it }) {
                    OutlinedTextField(
                        value = "${selectedCategory.emoji} ${selectedCategory.displayName}",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("카테고리") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandCategory) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expandCategory, onDismissRequest = { expandCategory = false }) {
                        TransactionCategory.values().forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.emoji} ${cat.displayName}") },
                                onClick = { selectedCategory = cat; expandCategory = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toLongOrNull() ?: return@Button
                if (merchantName.isBlank() || amount <= 0 || selectedCardId == 0L) return@Button
                onConfirm(selectedCardId, merchantName, amount, selectedCategory, memo)
            }) { Text("추가") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } }
    )
}
