package org.ergoplatform.desktop.wallet

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.ScaffoldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.pop
import com.arkivanov.decompose.router.push
import kotlinx.coroutines.CoroutineScope
import org.ergoplatform.Application
import org.ergoplatform.WalletStateSyncManager
import org.ergoplatform.desktop.ui.navigation.NavClientScreenComponent
import org.ergoplatform.desktop.ui.navigation.NavHostComponent
import org.ergoplatform.desktop.ui.navigation.ScreenConfig
import org.ergoplatform.desktop.wallet.addresses.ChooseAddressesListDialog
import org.ergoplatform.persistance.TokenInformation
import org.ergoplatform.persistance.WalletConfig
import org.ergoplatform.transactions.TransactionListManager
import org.ergoplatform.uilogic.wallet.WalletDetailsUiLogic

class WalletDetailsComponent(
    private val componentContext: ComponentContext,
    private val navHost: NavHostComponent,
    private val walletConfig: WalletConfig,
) : NavClientScreenComponent(navHost), ComponentContext by componentContext {
    override val appBarLabel: String
        get() = walletConfig.displayName ?: ""

    override val actions: @Composable RowScope.() -> Unit
        get() = {
            IconButton({ uiLogic.refreshByUser(Application.prefs, Application.database) }) {
                Icon(Icons.Default.Refresh, null)
            }

            IconButton({
                router.push(ScreenConfig.WalletConfiguration(walletConfig))
            }) {
                Icon(
                    Icons.Default.Settings,
                    null,
                )
            }
        }

    private val uiLogic = DesktopWalletDetailsUiLogic().apply {
        setUpWalletStateFlowCollector(Application.database, walletConfig.id)
    }

    private val chooseAddressDialog = mutableStateOf(false)

    @Composable
    override fun renderScreenContents(scaffoldState: ScaffoldState?) {
        val syncingState = WalletStateSyncManager.getInstance().isRefreshing.collectAsState()
        val downloadingTransactionsState = TransactionListManager.isDownloading.collectAsState()

        if (syncingState.value || downloadingTransactionsState.value) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        uiLogic.wallet?.let { wallet ->
            WalletDetailsScreen(
                wallet,
                uiLogic.walletAddress,
                onChooseAddressClicked = { chooseAddressDialog.value = true },
                onScanClicked = {
                    router.push(ScreenConfig.QrCodeScanner { qrCode -> handleQrCode(qrCode) })
                },
                onReceiveClicked = {
                    router.push(
                        ScreenConfig.ReceiveToWallet(
                            walletConfig,
                            uiLogic.addressIdx ?: 0
                        )
                    )
                },
                onSendClicked = {
                    router.push(
                        ScreenConfig.SendFunds(walletConfig, derivationIndex = uiLogic.addressIdx)
                    )
                },
                onAddressesClicked = { router.push(ScreenConfig.WalletAddressesList(walletConfig)) },
            )
        }

        if (chooseAddressDialog.value) {
            ChooseAddressesListDialog(
                uiLogic.wallet!!,
                true,
                onAddressChosen = { walletAddress ->
                    chooseAddressDialog.value = false
                    uiLogic.newAddressIdxChosen(
                        walletAddress?.derivationIndex,
                        Application.prefs,
                        Application.database
                    )
                },
                onDismiss = { chooseAddressDialog.value = false },
            )
        }
    }

    private fun handleQrCode(qrCode: String) {
        uiLogic.qrCodeScanned(
            qrCode,
            Application.texts,
            navigateToColdWalletSigning = { data ->
                router.push(ScreenConfig.ColdSigning(walletConfig.id, data))
            },
            navigateToErgoPaySigning = { ergoPayRequest ->
                router.push(
                    ScreenConfig.ErgoPay(
                        ergoPayRequest,
                        walletConfig.id,
                        uiLogic.addressIdx
                    )
                )
            },
            navigateToSendFundsScreen = { paymentRequest ->
                router.push(
                    ScreenConfig.SendFunds(
                        walletConfig,
                        paymentRequest,
                        uiLogic.addressIdx
                    )
                )
            },
            navigateToAuthentication = { authRequest ->
                // TODO ErgoAuth
            },
            showErrorMessage = { message -> navHost.showErrorDialog(message) }
        )
    }

    private inner class DesktopWalletDetailsUiLogic : WalletDetailsUiLogic() {
        override val coroutineScope: CoroutineScope
            get() = componentScope()

        override fun onDataChanged() {
            if (uiLogic.wallet == null) {
                // wallet was deleted from config screen
                router.pop()
                return
            }

            // TODO refresh
        }

        override fun onNewTokenInfoGathered(tokenInformation: TokenInformation) {
            // TODO refresh tokens list
        }
    }
}