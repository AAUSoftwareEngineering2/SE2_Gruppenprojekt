package at.aau.serg.websocketbrokerdemo

import android.widget.TextView
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class BasicCoverageUnitTests {

    @Test
    fun `onResponse updates TextView with correct text`() {
        // Arrange: MainActivity instanziieren
        val mainActivity = MainActivity()
        
        // Arrange: Eine "gefälschte" TextView erstellen (Mock), da wir kein echtes Android haben
        val mockTextView = mock(TextView::class.java)
        
        // Die lateinit Variable 'response' manuell mit unserem Mock befüllen.
        // So umgehen wir die fehleranfällige onCreate() Methode.
        mainActivity.response = mockTextView 

        // Act: Die zu testende Methode aufrufen
        val testMessage = "Nachricht vom WebSocket-Server"
        mainActivity.onResponse(testMessage)

        // Assert: Überprüfen, ob unsere gefälschte TextView den Text empfangen hat
        verify(mockTextView).text = testMessage 
    }
}