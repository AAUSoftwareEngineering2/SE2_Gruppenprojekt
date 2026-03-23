package at.aau.serg.websocketbrokerdemo

import android.os.Handler
import org.junit.Test
import org.mockito.Mockito // Wir importieren NUR die Klasse
import org.mockito.ArgumentMatchers // Wir importieren NUR die Matcher-Klasse

class BasicCoverageUnitTests {

    @Test
    fun onResponseUpdatesTextView() {
        // Wir rufen die Funktionen DIREKT über die Klasse auf:
        val handler = Mockito.mock(Handler::class.java)
        
        // ... (dein restlicher Code wie MyStomp etc.) ...
        
        // Auch hier: Expliziter Aufruf über die Klassen
        Mockito.verify(handler).sendMessage(ArgumentMatchers.any())
    }
}