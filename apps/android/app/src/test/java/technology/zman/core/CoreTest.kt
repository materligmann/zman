package technology.zman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime
import java.time.ZoneOffset

// Vecteurs repris de pkg/luach/luach_test.go, pkg/clock/clock_test.go et
// web/examples/now.txt : les portages doivent donner les mêmes résultats.

class LuachTest {
    @Test fun moladTohu() {
        assertEquals(31524L, Luach.MOLAD_TOHU_CHALAKIM)
        assertEquals(765433L, Luach.LUNAR_MONTH_CHALAKIM)
        assertEquals(Rega.Parts(1, 5, 204, 0), Rega.split(Luach.molad(1, Luach.TISHREI)!!))
    }

    @Test fun yearOne() {
        assertEquals(1L, Luach.roshHashana(1))
        assertEquals(Luach.MONDAY, Luach.weekday(1))
        assertEquals(HebrewDate(1, Luach.TISHREI, 1), Luach.dateOf(1))
        assertNull(Luach.dateOf(0))
    }

    @Test fun leap() {
        assertTrue(Luach.isLeap(5784)); assertFalse(Luach.isLeap(5785))
        assertFalse(Luach.isLeap(5786)); assertTrue(Luach.isLeap(5787))
    }

    @Test fun referenceDates() {
        val cases = listOf(
            listOf(5784, Luach.TISHREI, 1, Luach.SHABBAT), listOf(5785, Luach.TISHREI, 1, Luach.THURSDAY),
            listOf(5786, Luach.TISHREI, 1, Luach.TUESDAY), listOf(5787, Luach.TISHREI, 1, Luach.SHABBAT),
            listOf(5784, Luach.NISSAN, 15, Luach.TUESDAY), listOf(5785, Luach.NISSAN, 15, Luach.SUNDAY),
            listOf(5786, Luach.NISSAN, 15, Luach.THURSDAY), listOf(5786, Luach.TISHREI, 10, Luach.THURSDAY),
            listOf(5786, Luach.ELUL, 25, Luach.MONDAY), listOf(5783, Luach.TISHREI, 1, Luach.MONDAY),
            listOf(5782, Luach.TISHREI, 1, Luach.TUESDAY),
        )
        for ((y, m, d, wd) in cases) {
            val day = Luach.dayOf(HebrewDate(y.toLong(), m, d))!!
            assertEquals("$d/$m/$y", wd, Luach.weekday(day))
        }
    }

    @Test fun yearLengths() {
        mapOf(5782L to 384, 5783L to 355, 5784L to 383, 5785L to 355, 5786L to 354, 5787L to 385)
            .forEach { (y, l) -> assertEquals("$y", l, Luach.yearLength(y)) }
    }

    @Test fun roundTrip() {
        val start = Luach.roshHashana(5700)
        for (day in start until start + 4000) assertEquals(day, Luach.dayOf(Luach.dateOf(day)!!))
    }
}

class BridgeTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int, ms: Int = 0): Long =
        ZonedDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test fun dayBoundary() {
        // 15 IX 2023 15:39:08.712 UTC = début du 1 Tishrei 5784 (samedi).
        val ms = utc(2023, 9, 15, 15, 39, 8, 712)
        val r = Bridge.rega(ms)
        val p = Rega.split(r)
        assertEquals(listOf(0, 0, 0), listOf(p.hour, p.chelek, p.rega))
        assertEquals(HebrewDate(5784, Luach.TISHREI, 1), Luach.dateOf(p.day))
        assertEquals(Luach.SHABBAT, Luach.weekday(p.day))
        assertEquals(r - 1, Bridge.rega(ms - 1))
        assertEquals(ms, Bridge.unixMillis(r))
    }

    @Test fun referenceInstants() {
        val cases = listOf(
            utc(2023, 9, 16, 12, 0, 0) to HebrewDate(5784, 7, 1),
            utc(2023, 9, 15, 20, 0, 0) to HebrewDate(5784, 7, 1),
            utc(2023, 9, 15, 15, 0, 0) to HebrewDate(5783, 6, 29),
            utc(2026, 9, 7, 12, 0, 0) to HebrewDate(5786, 6, 25),
            utc(2026, 9, 12, 9, 0, 0) to HebrewDate(5787, 7, 1),
            utc(2024, 4, 23, 9, 0, 0) to HebrewDate(5784, 1, 15),
        )
        for ((ms, want) in cases) assertEquals(want, Luach.dateOf(Rega.day(Bridge.rega(ms))))
    }

    @Test fun rates() {
        val base = utc(2026, 1, 1, 0, 0, 0)
        val r0 = Bridge.rega(base)
        assertEquals(Rega.PER_HOUR, Bridge.rega(base + 3_600_000) - r0)
        assertEquals(Rega.PER_DAY, Bridge.rega(base + 86_400_000) - r0)
        assertEquals(114L, Bridge.rega(base + 5000) - r0)
    }

    @Test fun inverse() {
        val r0 = Bridge.rega(utc(2026, 9, 26, 10, 0, 0))
        for (r in r0 until r0 + 500) {
            val ms = Bridge.unixMillis(r)
            assertEquals(r, Bridge.rega(ms))
            assertEquals(r - 1, Bridge.rega(ms - 1))
        }
    }

    @Test fun apiSample() {
        // web/examples/now.txt
        val m = Moment(4163021922875)
        assertEquals(2113294L, m.day)
        assertEquals(listOf(22, 8, 27), listOf(m.hour, m.chelek, m.regaInChelek))
        assertEquals(HebrewDate(5786, 6, 25), m.date)
        assertEquals(1, m.weekday)
    }

    @Test fun calibration() {
        val json = """{"rega":4163021922875,"clock":{"source":"iers","dut1_age_days":4.6,"predicted":true}}"""
        val t = 1_790_000_000_000L
        val c = ZmanApi.calibration(json, t, t)
        assertEquals(4163021922875, ZmanClock(c).rega(t))
        assertEquals("iers", c.source)
    }
}

class NamesTest {
    @Test fun gematria() {
        assertEquals("כ״ה", Names.gematria(25))
        assertEquals("ט״ו", Names.gematria(15))
        assertEquals("א׳", Names.gematria(1))
        assertEquals("ה׳תשפ״ו", Names.gematria(5786))
    }

    @Test fun dates() {
        val d = HebrewDate(5786, 6, 25)
        assertEquals("כ״ה אלול ה׳תשפ״ו", Names.dateHe(d))
        assertEquals("25 Eloul 5786", Names.dateTranslit(d, Lang.FR))
        assertEquals("Adar I", Names.month(HebrewDate(5787, 12, 1), Lang.EN))
        assertEquals("lundi · יום שני", Names.weekdayLine(1, Lang.FR))
        assertEquals("4 163 021 922 875", Names.group(4163021922875))
    }

    @Test fun lang() {
        assertEquals(Lang.HE, Lang.of("iw"))
        assertEquals(Lang.EN, Lang.of("en-US"))
        assertEquals(Lang.FR, Lang.of("de"))
    }
}
