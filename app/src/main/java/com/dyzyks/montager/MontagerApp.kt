package com.dyzyks.montager

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dyzyks.montager.ui.MontagerScreen
import com.dyzyks.montager.ui.MontagerViewModel
import com.dyzyks.montager.ui.theme.ResolveBackground

@Composable
fun MontagerApp(
    viewModel: MontagerViewModel = viewModel()
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ResolveBackground)
            .statusBarsPadding()
    ) {
        MontagerScreen(viewModel = viewModel)
    }
}

