package rega

import "testing"

func TestConstants(t *testing.T) {
	if ChalakimPerDay != 25920 {
		t.Fatalf("ChalakimPerDay = %d, want 25920", ChalakimPerDay)
	}
	if RegaimPerDay != 1969920 {
		t.Fatalf("RegaimPerDay = %d, want 1969920", RegaimPerDay)
	}
	if RegaimPerHour != 82080 {
		t.Fatalf("RegaimPerHour = %d, want 82080", RegaimPerHour)
	}
}

func TestMoladTohu(t *testing.T) {
	// Molad Tohu : jour 2 de la semaine (lundi, index 1), 5 h, 204 ch.
	chalakim := int64(1*ChalakimPerDay + 5*ChalakimPerHour + 204)
	if chalakim != 31524 {
		t.Fatalf("molad Tohu = %d chalakim, want 31524", chalakim)
	}
	r := FromChalakim(chalakim)
	if r != 2395824 {
		t.Fatalf("molad Tohu = %d rega'im, want 2395824", r)
	}
	day, hour, chelek, rg := r.Split()
	if day != 1 || hour != 5 || chelek != 204 || rg != 0 {
		t.Fatalf("Split(molad Tohu) = (%d,%d,%d,%d), want (1,5,204,0)", day, hour, chelek, rg)
	}
}

func TestSplitFromParts(t *testing.T) {
	cases := []struct {
		r                  Rega
		day                int64
		hour, chelek, rega int
	}{
		{0, 0, 0, 0, 0},
		{1, 0, 0, 0, 1},
		{75, 0, 0, 0, 75},
		{76, 0, 0, 1, 0},
		{RegaimPerHour, 0, 1, 0, 0},
		{RegaimPerDay - 1, 0, 23, 1079, 75},
		{RegaimPerDay, 1, 0, 0, 0},
		{-1, -1, 23, 1079, 75},
		{-RegaimPerDay, -1, 0, 0, 0},
		{4548812345678, 2309135, 13, 782, 6},
	}
	for _, c := range cases {
		d, h, ch, rg := c.r.Split()
		if d != c.day || h != c.hour || ch != c.chelek || rg != c.rega {
			t.Errorf("Split(%d) = (%d,%d,%d,%d), want (%d,%d,%d,%d)", c.r, d, h, ch, rg, c.day, c.hour, c.chelek, c.rega)
		}
		if back := FromParts(d, h, ch, rg); back != c.r {
			t.Errorf("FromParts(Split(%d)) = %d", c.r, back)
		}
		if c.r.Day() != c.day {
			t.Errorf("Day(%d) = %d, want %d", c.r, c.r.Day(), c.day)
		}
	}
}

func TestRoundTripRange(t *testing.T) {
	for r := Rega(-3 * RegaimPerDay); r < 3*RegaimPerDay; r += 977 {
		d, h, ch, rg := r.Split()
		if h < 0 || h >= HoursPerDay || ch < 0 || ch >= ChalakimPerHour || rg < 0 || rg >= RegaimPerChelek {
			t.Fatalf("Split(%d) out of range: (%d,%d,%d,%d)", r, d, h, ch, rg)
		}
		if FromParts(d, h, ch, rg) != r {
			t.Fatalf("round trip failed for %d", r)
		}
	}
}
