package com.cardbudget.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.cardbudget.data.entity.TransactionCategory
import com.cardbudget.data.entity.TransactionEntity
import com.cardbudget.data.entity.TransactionSource
import com.cardbudget.data.repository.CardRepository
import com.cardbudget.data.repository.TransactionRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class SmsBroadcastReceiver : BroadcastReceiver() {

    @Inject lateinit var transactionRepository: TransactionRepository
    @Inject lateinit var cardRepository: CardRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullBody = messages.joinToString("") { it.messageBody }
        val sender = messages.firstOrNull()?.originatingAddress ?: ""

        val parsed = SmsParser.parse(sender, fullBody) ?: return

        scope.launch {
            val cards = cardRepository.getAllActiveCardsOnce()
            val matchedCard = cards.firstOrNull { card ->
                card.issuer.smsKeywords.any { kw -> fullBody.contains(kw) } &&
                    (card.lastFourDigits.isEmpty() || card.lastFourDigits == parsed.lastFourDigits)
            } ?: return@launch

            val fiveMinutes = 5 * 60 * 1000L
            val duplicate = transactionRepository.findDuplicate(
                merchantName = parsed.merchantName,
                amount = parsed.amount,
                from = parsed.transactionDate - fiveMinutes,
                to = parsed.transactionDate + fiveMinutes
            )
            if (duplicate != null) return@launch

            val billingMonth = calculateBillingMonth(
                matchedCard.paymentDay,
                matchedCard.billingCycleStartDay
            )

            val transaction = TransactionEntity(
                cardId = matchedCard.id,
                merchantName = parsed.merchantName,
                amount = parsed.amount,
                transactionDate = parsed.transactionDate,
                billingMonth = billingMonth,
                source = TransactionSource.SMS_AUTO,
                category = guessCategoryFromMerchant(parsed.merchantName),
                rawSmsBody = parsed.rawBody
            )
            transactionRepository.insertTransaction(transaction)
        }
    }

    private fun calculateBillingMonth(paymentDay: Int, cycleStartDay: Int): String {
        val today = LocalDate.now()
        val billingDate = if (today.dayOfMonth >= cycleStartDay) today else today.minusMonths(1)
        return billingDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    }

    private fun guessCategoryFromMerchant(merchant: String): TransactionCategory {
        val lower = merchant.lowercase()
        return when {
            listOf("스타벅스","커피","카페","투썸","할리스","이디야").any { lower.contains(it) } -> TransactionCategory.CAFE
            listOf("이마트","홈플러스","롯데마트","gs25","cu","세븐","편의점","마트").any { lower.contains(it) } -> TransactionCategory.MART
            listOf("주유","칼텍스","sk에너지","오일").any { lower.contains(it) } -> TransactionCategory.GAS
            listOf("지하철","버스","택시","카카오택시","티머니","교통").any { lower.contains(it) } -> TransactionCategory.TRANSPORT
            listOf("넷플릭스","유튜브","멜론","구독","애플","구글").any { lower.contains(it) } -> TransactionCategory.SUBSCRIPTION
            listOf("약국","병원","의원","클리닉","한의").any { lower.contains(it) } -> TransactionCategory.HEALTH
            listOf("영화","cgv","롯데시네마","메가박스","헬스","피트니스").any { lower.contains(it) } -> TransactionCategory.CULTURE
            listOf("쿠팡","배민","올리브영").any { lower.contains(it) } -> TransactionCategory.SHOPPING
            listOf("식당","김밥","치킨","피자","분식","한식","중식").any { lower.contains(it) } -> TransactionCategory.FOOD
            else -> TransactionCategory.OTHER
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // WorkManager 자동 재시작
    }
}
