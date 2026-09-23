package com.carpulse.obd.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.carpulse.obd.FileLogger
import com.carpulse.obd.data.prefs.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PurchaseState {
    object None : PurchaseState()
    object ProLifetime : PurchaseState()
    object SubscriptionActive : PurchaseState()
    object Pending : PurchaseState()
}

class BillingManager(
    private val context: Context,
    private val prefs: UserPreferences,
    private val scope: CoroutineScope
) : PurchasesUpdatedListener {

    companion object {
        const val TAG = "BILLING:"
        const val PRODUCT_PRO = "pro_unlock"
        const val PRODUCT_SUB = "cloud_sync_monthly"
    }

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.None)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    FileLogger.write("$TAG Connected")
                    queryProducts()
                    queryPurchases()
                } else {
                    FileLogger.write("$TAG Setup failed: ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                FileLogger.write("$TAG Disconnected")
            }
        })
    }

    private fun queryProducts() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_PRO)
                .setProductType(BillingClient.ProductType.INAPP)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_SUB)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _products.value = details
                FileLogger.write("$TAG Products loaded: ${details.size}")
            } else {
                FileLogger.write("$TAG Query products failed: ${result.debugMessage}")
            }
        }
    }

    private fun queryPurchases() {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        ) { _, purchases ->
            handlePurchases(purchases)
        }
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { _, purchases ->
            handlePurchases(purchases)
        }
    }

    fun purchase(activity: Activity, productId: String) {
        val product = _products.value.find { it.productId == productId } ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .build()
                )
            )
            .build()
        billingClient.launchBillingFlow(activity, params)
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            FileLogger.write("$TAG User canceled")
        } else {
            FileLogger.write("$TAG Purchase error: ${result.debugMessage}")
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        scope.launch {
            for (purchase in purchases) {
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    when (purchase.products.firstOrNull()) {
                        PRODUCT_PRO -> {
                            prefs.setPro(true)
                            _purchaseState.value = PurchaseState.ProLifetime
                            FileLogger.write("$TAG Pro unlocked")
                        }
                        PRODUCT_SUB -> {
                            prefs.setSubscription(true)
                            _purchaseState.value = PurchaseState.SubscriptionActive
                            FileLogger.write("$TAG Subscription active")
                        }
                    }
                    // Подтверждение покупки (обязательно для подписок и рекомендуется для INAPP)
                    if (!purchase.isAcknowledged) {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        billingClient.acknowledgePurchase(ackParams) { }
                    }
                } else if (purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                    _purchaseState.value = PurchaseState.Pending
                }
            }
        }
    }

    fun isPro(): Boolean = _purchaseState.value is PurchaseState.ProLifetime
}