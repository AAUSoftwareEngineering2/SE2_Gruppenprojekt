package at.aau.serg.websocketbrokerdemo

import android.os.Handler
import org.junit.Test
import org.mockito.Mockito
import org.mockito.ArgumentMatchers

class BasicCoverageUnitTests {
    @Test
    fun onResponseUpdatesTextView() {
        val handler = Mockito.mock(Handler::class.java)
        
        // ... (dein restlicher Code wie MyStomp etc.) ...
        
        // Auch hier: Expliziter Aufruf über die Klassen
        Mockito.verify(handler).sendMessage(ArgumentMatchers.any())
    }
}