package com.varuna.openfuel.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.varuna.openfuel.core.model.Brand
import com.varuna.openfuel.ui.map.MarkerIcons

/** The same icon the map draws, for lists and the station sheet. */
@Composable
fun BrandIcon(brand: Brand, logo: ByteArray?, size: Dp = 40.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(brand.key, logo, px) { MarkerIcons.forBrand(brand, logo, px).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = brand.displayName, modifier = Modifier.size(size))
}
