package com.financemanager.listener

import org.junit.Assert.*
import org.junit.Test
import com.financemanager.listener.capture.NotificationContent
import com.financemanager.listener.data.CaptureObservation

class CaptureObservationTest {
    @Test fun eachDeliveryHasItsOwnObservationWhileSourceTimesArePreserved() {
        val content = NotificationContent(text = "Persona Demo te envió S/ 12")
        val original = CaptureObservation.posted("authorized", "one", 100, 100, 101, "owner-a", false, false, content)
        val delivery = CaptureObservation.posted("authorized", "one", 100, 100, 102, "owner-a", true, false, content)
        assertNotEquals(original.observationKey, delivery.observationKey)
        assertEquals(100L, delivery.eventTime)
        assertEquals(100L, delivery.postTime)
    }
}
