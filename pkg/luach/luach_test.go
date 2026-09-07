package luach

import (
	"testing"

	"zman/pkg/rega"
)

func TestMoladTohu(t *testing.T) {
	if MoladTohuChalakim != 31524 {
		t.Fatalf("MoladTohuChalakim = %d, want 31524", MoladTohuChalakim)
	}
	if MoladTishreiChalakim(1) != MoladTohuChalakim {
		t.Fatalf("molad Tishrei 1 = %d, want %d", MoladTishreiChalakim(1), MoladTohuChalakim)
	}
	m, err := Molad(1, Tishrei)
	if err != nil {
		t.Fatal(err)
	}
	day, hour, chelek, rg := m.Split()
	// Jour 2 de la semaine (lundi = index 1), 5 h, 204 ch.
	if day != 1 || hour != 5 || chelek != 204 || rg != 0 {
		t.Fatalf("molad Tohu = (%d,%d,%d,%d), want (1,5,204,0)", day, hour, chelek, rg)
	}
	if Weekday(day) != Monday {
		t.Fatalf("molad Tohu weekday = %d, want Monday", Weekday(day))
	}
	if LunarMonthChalakim != 765433 {
		t.Fatalf("LunarMonthChalakim = %d, want 765433", LunarMonthChalakim)
	}
}

func TestYearOne(t *testing.T) {
	// 1 Tishrei de l'an 1 = jour 1 = lundi.
	if rh := RoshHashana(1); rh != 1 {
		t.Fatalf("RoshHashana(1) = %d, want 1", rh)
	}
	if Weekday(1) != Monday {
		t.Fatalf("Weekday(1) = %d, want Monday", Weekday(1))
	}
	d, err := DayFromDate(1, Tishrei, 1)
	if err != nil || d != 1 {
		t.Fatalf("DayFromDate(1,7,1) = %d, %v", d, err)
	}
	y, m, dd, err := DateFromDay(1)
	if err != nil || y != 1 || m != Tishrei || dd != 1 {
		t.Fatalf("DateFromDay(1) = (%d,%d,%d), %v", y, m, dd, err)
	}
	if _, _, _, err := DateFromDay(0); err == nil {
		t.Fatal("DateFromDay(0) should fail")
	}
}

func TestIsLeap(t *testing.T) {
	leapInCycle := map[int64]bool{3: true, 6: true, 8: true, 11: true, 14: true, 17: true, 19: true}
	for y := int64(1); y <= 19; y++ {
		if IsLeap(y) != leapInCycle[y] {
			t.Errorf("IsLeap(%d) = %v", y, IsLeap(y))
		}
	}
	// 5784 embolismique, 5785 et 5786 communes, 5787 embolismique.
	cases := map[int64]bool{5784: true, 5785: false, 5786: false, 5787: true}
	for y, want := range cases {
		if IsLeap(y) != want {
			t.Errorf("IsLeap(%d) = %v, want %v", y, IsLeap(y), want)
		}
	}
}

// Dates de référence vérifiées contre hebcal.
func TestReferenceDates(t *testing.T) {
	cases := []struct {
		year       int64
		month, day int
		weekday    int
	}{
		{5784, Tishrei, 1, Shabbat},   // 16 IX 2023
		{5785, Tishrei, 1, Thursday},  // 3 X 2024
		{5786, Tishrei, 1, Tuesday},   // 23 IX 2025
		{5787, Tishrei, 1, Shabbat},   // 12 IX 2026
		{5784, Nissan, 15, Tuesday},   // Pessah 2024
		{5785, Nissan, 15, Sunday},    // Pessah 2025
		{5786, Nissan, 15, Thursday},  // Pessah 2026
		{5786, Tishrei, 10, Thursday}, // Yom Kippour 2025
		{5786, Elul, 25, Monday},      // 7 IX 2026
		{5783, Tishrei, 1, Monday},    // 26 IX 2022
		{5782, Tishrei, 1, Tuesday},   // 7 IX 2021
	}
	for _, c := range cases {
		d, err := DayFromDate(c.year, c.month, c.day)
		if err != nil {
			t.Errorf("DayFromDate(%d,%d,%d): %v", c.year, c.month, c.day, err)
			continue
		}
		if wd := Weekday(d); wd != c.weekday {
			t.Errorf("%d %s %d: weekday = %s, want %s", c.day, MonthName(c.year, c.month), c.year, WeekdayName(wd), WeekdayName(c.weekday))
		}
	}
}

func TestReferenceYearLengths(t *testing.T) {
	cases := map[int64]int{5782: 384, 5783: 355, 5784: 383, 5785: 355, 5786: 354, 5787: 385}
	for y, want := range cases {
		if got := YearLength(y); got != want {
			t.Errorf("YearLength(%d) = %d, want %d", y, got, want)
		}
	}
}

func TestYearLengthsValid(t *testing.T) {
	valid := map[int]bool{353: true, 354: true, 355: true, 383: true, 384: true, 385: true}
	for y := int64(1); y <= 10000; y++ {
		l := YearLength(y)
		if !valid[l] {
			t.Fatalf("YearLength(%d) = %d", y, l)
		}
		if IsLeap(y) != (l > 360) {
			t.Fatalf("year %d: leap=%v but length %d", y, IsLeap(y), l)
		}
		// Somme des mois = longueur de l'année.
		sum := 0
		for _, m := range MonthsInOrder(y) {
			sum += MonthLength(y, m)
		}
		if sum != l {
			t.Fatalf("year %d: months sum to %d, year length %d", y, sum, l)
		}
	}
}

func TestRoshHashanaNeverADU(t *testing.T) {
	for y := int64(1); y <= 10000; y++ {
		switch Weekday(RoshHashana(y)) {
		case Sunday, Wednesday, Friday:
			t.Fatalf("Rosh Hashana %d falls on %s", y, WeekdayName(Weekday(RoshHashana(y))))
		}
	}
}

func TestRoundTrip(t *testing.T) {
	start, _ := DayFromDate(5700, Tishrei, 1)
	end, _ := DayFromDate(5900, Tishrei, 1)
	prevYear, prevMonth, prevDay := int64(0), 0, 0
	for d := start; d < end; d++ {
		y, m, dd, err := DateFromDay(d)
		if err != nil {
			t.Fatalf("DateFromDay(%d): %v", d, err)
		}
		back, err := DayFromDate(y, m, dd)
		if err != nil || back != d {
			t.Fatalf("DayFromDate(DateFromDay(%d)) = %d (%d,%d,%d), %v", d, back, y, m, dd, err)
		}
		// Continuité : soit le jour suivant du même mois, soit le 1er du mois suivant.
		if prevDay != 0 {
			if y == prevYear && m == prevMonth {
				if dd != prevDay+1 {
					t.Fatalf("day %d: %d/%d/%d after %d/%d/%d", d, y, m, dd, prevYear, prevMonth, prevDay)
				}
			} else if dd != 1 || prevDay != MonthLength(prevYear, prevMonth) {
				t.Fatalf("day %d: %d/%d/%d after %d/%d/%d", d, y, m, dd, prevYear, prevMonth, prevDay)
			}
		}
		prevYear, prevMonth, prevDay = y, m, dd
	}
	// Début de la numérotation.
	for d := int64(1); d < 3000; d++ {
		y, m, dd, err := DateFromDay(d)
		if err != nil {
			t.Fatalf("DateFromDay(%d): %v", d, err)
		}
		if back, _ := DayFromDate(y, m, dd); back != d {
			t.Fatalf("round trip failed for day %d", d)
		}
	}
}

func TestInvalidDates(t *testing.T) {
	bad := []struct {
		y    int64
		m, d int
	}{
		{5786, AdarII, 1}, // année commune
		{5786, Adar, 30},
		{5786, Cheshvan, 30}, // 5786 = 354 jours, Cheshvan 29
		{5786, Tishrei, 0},
		{5786, 0, 1},
		{5786, 14, 1},
		{0, Tishrei, 1},
	}
	for _, b := range bad {
		if _, err := DayFromDate(b.y, b.m, b.d); err == nil {
			t.Errorf("DayFromDate(%d,%d,%d) should fail", b.y, b.m, b.d)
		}
	}
	if _, err := DayFromDate(5784, AdarII, 29); err != nil {
		t.Errorf("29 Adar II 5784 should exist: %v", err)
	}
	if _, err := DayFromDate(5785, Cheshvan, 30); err != nil {
		t.Errorf("30 Cheshvan 5785 (355 days) should exist: %v", err)
	}
}

func TestMolad(t *testing.T) {
	// Le molad de Tishrei d'une année, exprimé en chalakim, est cohérent
	// avec l'arithmétique des mois écoulés.
	m5786, err := Molad(5786, Tishrei)
	if err != nil {
		t.Fatal(err)
	}
	m5786c, _ := Molad(5786, Cheshvan)
	if m5786c-m5786 != rega.FromChalakim(LunarMonthChalakim) {
		t.Fatalf("Cheshvan - Tishrei molad = %d rega'im", m5786c-m5786)
	}
	// Molad Tishrei 5786 annoncé : lundi, 12:10 et 7 chalakim. Les heures
	// halachiques comptent depuis 18:00 la veille : 12:10 = 18 h 180 ch,
	// donc (Monday, 18 h, 187 ch). Le molad est ≥ 18 h → molad zaken →
	// Rosh Hashana repoussé au mardi, ce qui concorde avec 1 Tishrei 5786.
	day, hour, chelek, _ := m5786.Split()
	if Weekday(day) != Monday || hour != 18 || chelek != 187 {
		t.Fatalf("molad Tishrei 5786 = (%s, %d h, %d ch), want (Monday, 18 h, 187 ch)", WeekdayName(Weekday(day)), hour, chelek)
	}
	if RoshHashana(5786) != day+1 {
		t.Fatalf("RH 5786 should be the day after the molad (molad zaken)")
	}
	// Le molad de Tishrei tombe toujours dans les deux jours précédant RH
	// (ou le jour même), jamais après.
	for y := int64(5000); y < 6000; y++ {
		m, _ := Molad(y, Tishrei)
		rh := RoshHashana(y)
		md := m.Day()
		if md > rh || rh-md > 2 {
			t.Fatalf("year %d: molad day %d, RH %d", y, md, rh)
		}
	}
	if _, err := Molad(5786, AdarII); err == nil {
		t.Fatal("Molad(5786, Adar II) should fail")
	}
}

func TestNames(t *testing.T) {
	if MonthName(5784, Adar) != "Adar I" || MonthName(5784, AdarII) != "Adar II" || MonthName(5786, Adar) != "Adar" {
		t.Error("Adar naming")
	}
	if MonthNameHe(5786, Elul) != "אלול" || WeekdayNameHe(Monday) != "יום שני" || WeekdayNameHe(Shabbat) != "שבת" {
		t.Error("hebrew names")
	}
	if MonthName(5786, 14) != "" || WeekdayNameHe(7) != "" {
		t.Error("out of range names")
	}
}
