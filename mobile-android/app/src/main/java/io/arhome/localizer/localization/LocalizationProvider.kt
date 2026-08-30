package io.arhome.localizer.localization

import com.google.ar.core.Frame
import io.arhome.localizer.map.PersistentMap

interface LocalizationProvider {
    fun localize(map: PersistentMap, frame: Frame): LocalizationResult?
}
