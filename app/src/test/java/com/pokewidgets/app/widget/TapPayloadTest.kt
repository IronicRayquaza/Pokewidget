package com.pokewidgets.app.widget

import com.pokewidgets.app.data.TapAction
import com.pokewidgets.app.data.WidgetConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TapPayloadTest {

    private fun roundTrip(payload: TapPayload): TapPayload? {
        val extras = HashMap<String, Any>()
        payload.write({ k, v -> extras[k] = v }, { k, v -> extras[k] = v })
        return TapPayload.read({ extras[it] as? Int }, { extras[it] as? String })
    }

    @Test
    fun `every field survives the intent`() {
        for (action in TapAction.values()) {
            for (enabled in listOf(true, false)) {
                for (legacy in listOf(true, false)) {
                    val payload = TapPayload(10_034, enabled, legacy, action)
                    assertEquals(payload, roundTrip(payload))
                }
            }
        }
    }

    @Test
    fun `an intent from a widget drawn before the payload existed reads as absent`() {
        assertNull(TapPayload.read({ null }, { null }))
    }

    @Test
    fun `only cry and excite make a sound, and only when cries are on`() {
        val config = WidgetConfig(pokemonId = 54)
        assertTrue(TapPayload.of(config).cries)
        assertTrue(TapPayload.of(config.copy(tapAction = TapAction.EXCITE)).cries)
        assertFalse(TapPayload.of(config.copy(tapAction = TapAction.SHINY)).cries)
        assertFalse(TapPayload.of(config.copy(cryEnabled = false)).cries)
    }
}
