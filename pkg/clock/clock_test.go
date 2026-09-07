package clock

import (
	"os"
	"testing"
	"time"

	"zman/pkg/iers"
	"zman/pkg/luach"
	"zman/pkg/rega"
)

// Ce fichier de test est le seul endroit où des dates grégoriennes
// apparaissent : c'est ici que le pont est vérifié.

func utc(y int, m time.Month, d, hh, mm, ss, ns int) time.Time {
	return time.Date(y, m, d, hh, mm, ss, ns, time.UTC)
}

func TestEpochConstant(t *testing.T) {
	// MJD de l'epoch en échelle JMT : −2052004.25 = 40587 − 2092591.25.
	const epochMJD = -2052004.25
	if got := (unixEpochMJD - epochMJD) * secondsPerDay; got != epochToUnixSeconds {
		t.Fatalf("epoch offset = %v, want %v", got, epochToUnixSeconds)
	}
	// RD du 1 Tishrei an 1 (Reingold & Dershowitz) = −1373427 ; RD du
	// 17 XI 1858 (MJD 0) = 678576. Le jour halachique 1 commence 6 h avant
	// le début civil de RD −1373427, et le jour 0 un jour plus tôt.
	rd := -1373427.0
	mjdOfRD := rd - 678576
	if mjdOfRD-1-0.25 != epochMJD {
		t.Fatalf("epoch from RD = %v", mjdOfRD-1-0.25)
	}
}

func TestDayBoundary(t *testing.T) {
	// 15 IX 2023, 15:39:08.712 UTC = 18:00:00.000 temps moyen de Jérusalem
	// = début du 1 Tishrei 5784 (samedi).
	start := utc(2023, time.September, 15, 15, 39, 8, 712000000)
	r := FromUTC(start, 0)
	day, h, ch, rg := r.Split()
	if h != 0 || ch != 0 || rg != 0 {
		t.Fatalf("boundary = (%d,%d,%d,%d)", day, h, ch, rg)
	}
	y, m, d, err := luach.DateFromDay(day)
	if err != nil || y != 5784 || m != luach.Tishrei || d != 1 {
		t.Fatalf("day %d = %d/%d/%d, want 5784/7/1 (%v)", day, y, m, d, err)
	}
	if luach.Weekday(day) != luach.Shabbat {
		t.Fatalf("weekday = %d", luach.Weekday(day))
	}
	// Une nanoseconde avant : dernier rega du 29 Elul 5783.
	before := FromUTC(start.Add(-time.Nanosecond), 0)
	if before != r-1 {
		t.Fatalf("before = %d, r = %d", before, r)
	}
	bd, h, ch, rg := before.Split()
	if bd != day-1 || h != 23 || ch != 1079 || rg != 75 {
		t.Fatalf("before = (%d,%d,%d,%d)", bd, h, ch, rg)
	}
	y, m, d, _ = luach.DateFromDay(bd)
	if y != 5783 || m != luach.Elul || d != 29 {
		t.Fatalf("day before = %d/%d/%d", y, m, d)
	}
}

func TestReferenceInstants(t *testing.T) {
	cases := []struct {
		t          time.Time
		year       int64
		month, day int
	}{
		{utc(2023, time.September, 16, 12, 0, 0, 0), 5784, luach.Tishrei, 1},
		{utc(2023, time.September, 15, 20, 0, 0, 0), 5784, luach.Tishrei, 1}, // vendredi soir, déjà Shabbat
		{utc(2023, time.September, 15, 15, 0, 0, 0), 5783, luach.Elul, 29},   // 17:20 JMT, avant 18:00
		{utc(2024, time.October, 3, 9, 0, 0, 0), 5785, luach.Tishrei, 1},
		{utc(2025, time.September, 23, 9, 0, 0, 0), 5786, luach.Tishrei, 1},
		{utc(2026, time.September, 7, 12, 0, 0, 0), 5786, luach.Elul, 25},
		{utc(2026, time.September, 12, 9, 0, 0, 0), 5787, luach.Tishrei, 1},
		{utc(2024, time.April, 23, 9, 0, 0, 0), 5784, luach.Nissan, 15},
	}
	for _, c := range cases {
		r := FromUTC(c.t, 0)
		y, m, d, err := luach.DateFromDay(r.Day())
		if err != nil || y != c.year || m != c.month || d != c.day {
			t.Errorf("%v → %d/%d/%d, want %d/%d/%d (%v)", c.t, y, m, d, c.year, c.month, c.day, err)
		}
	}
}

func TestSecondsToRegaim(t *testing.T) {
	base := utc(2026, time.January, 1, 0, 0, 0, 0)
	r0 := FromUTC(base, 0)
	// 1 heure moyenne = 82 080 rega'im ; 1 jour = 1 969 920.
	if d := FromUTC(base.Add(time.Hour), 0) - r0; d != rega.RegaimPerHour {
		t.Errorf("1 h = %d rega'im", d)
	}
	if d := FromUTC(base.Add(24*time.Hour), 0) - r0; d != rega.RegaimPerDay {
		t.Errorf("1 j = %d rega'im", d)
	}
	// 1 rega = 86400/1969920 s ≈ 43,86 ms ; 5 s = 114 rega'im exactement.
	if d := FromUTC(base.Add(5*time.Second), 0) - r0; d != 114 {
		t.Errorf("5 s = %d rega'im", d)
	}
	// DUT1 décale le résultat : +0,5 s = +11,4 rega'im.
	if d := FromUTC(base, 0.5) - r0; d != 11 {
		t.Errorf("DUT1 +0.5 s = %d rega'im", d)
	}
	if d := FromUTC(base, -0.5) - r0; d != -12 && d != -11 {
		t.Errorf("DUT1 -0.5 s = %d rega'im", d)
	}
	// Monotone.
	prev := r0
	for i := 1; i < 1000; i++ {
		r := FromUTC(base.Add(time.Duration(i)*7*time.Millisecond), 0)
		if r < prev {
			t.Fatalf("not monotonic at %d", i)
		}
		prev = r
	}
}

func TestMJD(t *testing.T) {
	if m := MJD(utc(1970, time.January, 1, 0, 0, 0, 0)); m != 40587 {
		t.Errorf("MJD(unix epoch) = %v", m)
	}
	if m := MJD(utc(2026, time.September, 7, 0, 0, 0, 0)); m != 61290 {
		t.Errorf("MJD(2026-09-07) = %v", m)
	}
}

type tableSource struct{ tb *iers.Table }

func (s tableSource) Table() *iers.Table { return s.tb }

func TestUT1Clock(t *testing.T) {
	f, err := os.Open("../iers/testdata/finals.sample")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	tb, err := iers.Parse(f)
	if err != nil {
		t.Fatal(err)
	}
	now := utc(2026, time.September, 7, 12, 0, 0, 0) // MJD 61290.5
	c := NewUT1WithNow(tableSource{tb}, func() time.Time { return now })
	q := c.Quality()
	if q.Source != "iers" || !q.Predicted {
		t.Errorf("quality = %+v", q)
	}
	if q.DUT1AgeDays < 4.4 || q.DUT1AgeDays > 4.6 { // 61290.5 − 61286
		t.Errorf("age = %v", q.DUT1AgeDays)
	}
	if got, want := c.Now(), FromUTC(now, 0); got < want-1 || got > want+1 {
		// DUT1 ≈ 0 en septembre 2026 : l'écart doit être inférieur à un rega.
		t.Errorf("Now = %d, plain = %d", got, want)
	}
	y, m, d, _ := luach.DateFromDay(c.Now().Day())
	if y != 5786 || m != luach.Elul || d != 25 {
		t.Errorf("date = %d/%d/%d", y, m, d)
	}

	// Sans table : fallback.
	c2 := NewUT1WithNow(tableSource{nil}, func() time.Time { return now })
	if q := c2.Quality(); q.Source != "fallback" || !q.Predicted {
		t.Errorf("fallback quality = %+v", q)
	}
	if c2.Now() != FromUTC(now, 0) {
		t.Error("fallback should use DUT1 = 0")
	}
	c3 := NewUT1WithNow(nil, func() time.Time { return now })
	if c3.Quality().Source != "fallback" {
		t.Error("nil source")
	}
}
