package pl.syntaxdevteam.plotsx.protection

import org.junit.Assert.assertEquals
import org.junit.Test

class PlotNotificationModeTest {
    @Test fun `actionbar is the default for missing and invalid configuration`() {
        assertEquals(PlotNotificationMode.ACTIONBAR, PlotNotificationMode.fromConfig(null))
        assertEquals(PlotNotificationMode.ACTIONBAR, PlotNotificationMode.fromConfig("title"))
    }

    @Test fun `configuration is parsed without case or surrounding whitespace`() {
        assertEquals(PlotNotificationMode.ACTIONBAR, PlotNotificationMode.fromConfig(" ActionBar "))
        assertEquals(PlotNotificationMode.CHAT, PlotNotificationMode.fromConfig("CHAT"))
    }
}
