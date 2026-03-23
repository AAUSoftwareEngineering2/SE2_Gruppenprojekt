package at.aau.serg.websocketbrokerdemo

import android.os.Handler
import org.junit.Test
import org.mockito.Mockito // Wir importieren die Hauptklasse
import org.mockito.ArgumentMatchers.any // DIESER Import hat im Log gefehlt!

class BasicCoverageUnitTests {

    @Test
    fun onResponseUpdatesTextView() {
        // Wir nutzen "Mockito.mock" statt nur "mock"
        val handler = Mockito.mock(Handler::class.java)
        
        // Hier kommt dein restlicher Logik-Code hin (MyStomp Instanz etc.)
        // ...

        // Wir nutzen "Mockito.verify" und "any()" explizit
        Mockito.verify(handler).sendMessage(any())
    }
}