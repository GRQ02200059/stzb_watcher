package com.local.stzb.feature.battlefield

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.local.stzb.domain.battlefield.BattlefieldHero
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BattlefieldHeroPortrait(hero: BattlefieldHero, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(initialValue = HeroPortraitCache.get(hero.iconId), hero.iconId) {
        if (value == null && hero.iconId > 0) {
            value = withContext(Dispatchers.IO) { HeroPortraitCache.load(hero.iconId) }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    val accessibleModifier = modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .clip(shape)
        .semantics { contentDescription = "${hero.positionLabel} ${hero.name}" }
    val image = bitmap
    if (image != null) {
        Image(
            bitmap = image.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = accessibleModifier,
        )
    } else {
        Box(
            modifier = accessibleModifier.background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = hero.name.trim().firstOrNull()?.toString() ?: "将",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private object HeroPortraitCache {
    private val bitmaps = ConcurrentHashMap<Long, Bitmap>()

    fun get(iconId: Long): Bitmap? = bitmaps[iconId]

    fun load(iconId: Long): Bitmap? {
        bitmaps[iconId]?.let { return it }
        val url = "https://g0.gph.netease.com/ngsocial/community/stzb/cn/cards/cut/card_medium_${iconId}.jpg?gameid=g10"
        val bitmap = runCatching {
            URL(url).openConnection().apply {
                connectTimeout = 4_000
                readTimeout = 6_000
            }.getInputStream().use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return null
        bitmaps[iconId] = bitmap
        return bitmap
    }
}
