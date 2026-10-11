package com.pokewidgets.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceQueryTest {

    // What Open-Meteo really returns for "Richmond", in its order (population first).
    private val richmonds = listOf(
        GeoResult("Richmond", 37.55, -77.46, "Virginia", "United States", "US"),
        GeoResult("Richmond", 49.17, -123.14, "British Columbia", "Canada", "CA"),
        GeoResult("Richmond", 37.94, -122.35, "California", "United States", "US"),
        GeoResult("Richmond", 39.83, -84.89, "Indiana", "United States", "US"),
        GeoResult("Richmond", -41.33, 173.18, "Tasman District", "New Zealand", "NZ"),
    )

    @Test
    fun `a state abbreviation without a comma is read as a qualifier`() {
        val first = PlaceQuery.attempts("Richmond VA").first()
        assertEquals(PlaceQuery.Attempt("Richmond", "VA"), first)
        assertEquals("Virginia", PlaceQuery.filter(richmonds, first.qualifier)!!.single().admin1)
    }

    @Test
    fun `a comma splits the name from the qualifier`() {
        assertEquals(listOf(PlaceQuery.Attempt("Richmond", "Virginia")), PlaceQuery.attempts("Richmond, Virginia"))
        assertEquals(listOf(PlaceQuery.Attempt("Richmond", null)), PlaceQuery.attempts("Richmond,"))
    }

    @Test
    fun `full state names, country names and codes all qualify`() {
        assertEquals("Virginia", PlaceQuery.filter(richmonds, "Virginia")!!.single().admin1)
        assertEquals("Virginia", PlaceQuery.filter(richmonds, "virg")!!.single().admin1)
        assertEquals(3, PlaceQuery.filter(richmonds, "USA")!!.size)
        assertEquals(3, PlaceQuery.filter(richmonds, "United States")!!.size)
        assertEquals("Virginia", PlaceQuery.filter(richmonds, "Virginia, United States")!!.single().admin1)
        assertEquals("British Columbia", PlaceQuery.filter(richmonds, "BC")!!.single().admin1)
        assertEquals("Indiana", PlaceQuery.filter(richmonds, "IN")!!.single().admin1)
    }

    @Test
    fun `a multi-word city keeps its whole name first`() {
        val attempts = PlaceQuery.attempts("New York NY")
        assertEquals(PlaceQuery.Attempt("New York", "NY"), attempts.first())
        assertEquals(PlaceQuery.Attempt("New York NY", null), attempts.last())
    }

    @Test
    fun `a qualifier that fits nothing is reported, so the next reading is tried`() {
        assertNull(PlaceQuery.filter(richmonds, "Francisco"))
        assertNull(PlaceQuery.filter(emptyList(), null))
    }

    @Test
    fun `a single word is sent as it is`() {
        assertEquals(listOf(PlaceQuery.Attempt("Kanpur", null)), PlaceQuery.attempts("  Kanpur "))
        assertTrue(PlaceQuery.attempts("K").isEmpty())
    }

    @Test
    fun `WA means Washington in the US and Western Australia in Australia`() {
        val perths = listOf(
            GeoResult("Perth", -31.95, 115.86, "Western Australia", "Australia", "AU"),
            GeoResult("Perth", 56.39, -3.43, "Scotland", "United Kingdom", "GB"),
        )
        assertEquals("AU", PlaceQuery.filter(perths, "WA")!!.single().countryCode)
        val spokane = listOf(GeoResult("Spokane", 47.66, -117.43, "Washington", "United States", "US"))
        assertEquals(1, PlaceQuery.filter(spokane, "WA")!!.size)
    }

    @Test
    fun `labels name the region and country`() {
        assertEquals("Richmond, Virginia, United States", richmonds.first().toPlace().label)
        assertEquals("Atlantis", GeoResult("Atlantis", 0.0, 0.0).toPlace().label)
    }
}
