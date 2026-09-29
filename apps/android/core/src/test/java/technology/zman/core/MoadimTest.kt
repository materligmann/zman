package technology.zman.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Mêmes vecteurs que MoadimTests côté iOS (ZmanCoreTests.swift).

class MoadimTest {
    private fun first(f: Feast, y: Long, israel: Boolean = false): Observance =
        Moadim.year(y, israel).first { it.feast == f }

    private fun date(o: Observance): HebrewDate = Luach.dateOf(o.start)!!

    @Test fun fixedFeasts() {
        assertEquals(Luach.THURSDAY, Luach.weekday(first(Feast.TZOM_GEDALIA, 5786).start))
        assertEquals(Luach.MONDAY, Luach.weekday(first(Feast.CHANUKAH, 5786).start))
        assertEquals(8, first(Feast.CHANUKAH, 5786).length)
        assertEquals(Luach.TUESDAY, Luach.weekday(first(Feast.PURIM, 5786).start))
        assertEquals(Luach.TUESDAY, Luach.weekday(first(Feast.YOM_HASHOAH, 5786).start))
        assertEquals(Luach.WEDNESDAY, Luach.weekday(first(Feast.YOM_HAATZMAUT, 5786).start))
    }

    @Test fun postponements() {
        // 9 Av 5782 est un Chabbat : jeûne le dimanche 10 Av.
        assertEquals(HebrewDate(5782, Luach.AV, 10), date(first(Feast.TISHA_BE_AV, 5782)))
        // Pourim 5784 un dimanche : Ta'anit Esther avancé au jeudi 11 Adar II.
        assertEquals(HebrewDate(5784, Luach.ADAR_II, 11), date(first(Feast.TAANIT_ESTHER, 5784)))
        // 5 Iyar 5785 un Chabbat : Yom HaAtsmaout le jeudi 3 Iyar.
        assertEquals(HebrewDate(5785, Luach.IYAR, 3), date(first(Feast.YOM_HAATZMAUT, 5785)))
        assertEquals(HebrewDate(5785, Luach.IYAR, 2), date(first(Feast.YOM_HAZIKARON, 5785)))
        // 5 Iyar 5784 un lundi : Yom HaAtsmaout le mardi 6 Iyar.
        assertEquals(HebrewDate(5784, Luach.IYAR, 6), date(first(Feast.YOM_HAATZMAUT, 5784)))
    }

    @Test fun israelAndDiaspora() {
        assertEquals(2, first(Feast.PESACH, 5786).length)
        assertEquals(1, first(Feast.PESACH, 5786, israel = true).length)
        assertEquals(first(Feast.SHVII_SHEL_PESACH, 5786).start, first(Feast.CHOL_HAMOED_PESACH, 5786).end)
        assertEquals(
            first(Feast.SHVII_SHEL_PESACH, 5786, israel = true).start,
            first(Feast.CHOL_HAMOED_PESACH, 5786, israel = true).end,
        )
        assertTrue(Moadim.year(5786, israel = true).all { it.feast != Feast.SIMCHAT_TORAH })
    }

    @Test fun roshChodesh() {
        for (y in 5780L..5800L) {
            val rc = Moadim.year(y, israel = false).filter { it.feast == Feast.ROSH_CHODESH }
            assertEquals(Luach.monthsInYear(y) - 1, rc.size)
            for (o in rc) assertEquals(1, Luach.dateOf(o.end - 1)!!.day)
        }
        // Tous les jours d'une fête tombent dans l'année.
        val o = Moadim.on(Luach.dayOf(HebrewDate(5786, Luach.TEVET, 1))!!, israel = false)
        assertEquals(listOf(Feast.CHANUKAH, Feast.ROSH_CHODESH), o.map { it.feast })
    }

    @Test fun omer() {
        val d16 = Luach.dayOf(HebrewDate(5786, Luach.NISSAN, 16))!!
        assertEquals(1, Moadim.omer(d16))
        assertEquals(49, Moadim.omer(d16 + 48))
        assertNull(Moadim.omer(d16 + 49))
        assertNull(Moadim.omer(d16 - 1))
        assertEquals(HebrewDate(5786, Luach.SIVAN, 6), Luach.dateOf(d16 + 49))
    }

    @Test fun yearInfo() {
        val y = YearInfo(5786)
        assertEquals(354, y.length)
        assertEquals("גכה", y.keviah)
        assertEquals(12, y.months.size)
        assertEquals(354, y.months.sumOf { it.length })
        assertEquals(11, YearInfo(5787).yearInCycle)
        assertTrue(YearInfo(5787).isLeap)
    }

    @Test fun moladAnnouncement() {
        // Molad Tichri 5786 : lundi, 18 h 187 ch = « lundi 12:10 et 7 chalakim ».
        val a = MoladAnnouncement(Luach.molad(5786, Luach.TISHREI)!!)
        assertEquals(Luach.MONDAY, a.weekday); assertFalse(a.evening)
        assertEquals(12, a.clockHour); assertEquals(10, a.minute); assertEquals(7, a.chalakim)
    }

    @Test fun names() {
        val rc = first(Feast.ROSH_CHODESH, 5786)
        assertEquals("Roch Hodech Hechvan", rc.name(Lang.FR, 5786))
        assertEquals("ראש חודש חשון", rc.nameHe(5786))
        Feast.entries.forEach { f -> Lang.entries.forEach { assertTrue(f.name(it).isNotEmpty()) } }
    }
}
