package com.joel.gta.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.joel.gta.R

/**
 * GTA Brand Logo:
 * Pure transparent background with the white guitar headstock and inner wrench motif.
 * Seamlessly blends onto any background or stage theme.
 */
@Composable
fun GtaBrandLogo(
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_gta_logo),
            contentDescription = "GTA Logo",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
