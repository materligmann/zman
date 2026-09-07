// Package luach implémente le calendrier fixe hébraïque (Rambam, Hilchot
// Kiddush HaChodesh chap. 6–8) par arithmétique pure sur des numéros de
// jours comptés depuis l'epoch (jour 0 = dimanche de la semaine du molad
// Tohu ; jour 1 = lundi = 1 Tishrei de l'an 1).
//
// Aucune date grégorienne, aucune seconde : uniquement des jours, des
// heures, des chalakim et des rega'im.
package luach

import (
	"errors"
	"fmt"

	"zman/pkg/rega"
)

// Constantes du calendrier, en chalakim.
const (
	ChalakimPerDay = rega.ChalakimPerDay // 25 920

	// Mois lunaire moyen : 29 j 12 h 793 ch.
	LunarMonthChalakim = 29*ChalakimPerDay + 12*rega.ChalakimPerHour + 793 // 765 433

	// Molad Tohu (BaHaRaD) : jour 2 (lundi), 5 h, 204 ch, comptés depuis
	// le début de la semaine (samedi soir 18:00).
	MoladTohuChalakim = 1*ChalakimPerDay + 5*rega.ChalakimPerHour + 204 // 31 524

	// Seuils des dehiyot, en chalakim dans le jour.
	moladZakenParts = 18 * rega.ChalakimPerHour     // ≥ 18 h
	gatradParts     = 9*rega.ChalakimPerHour + 204  // ≥ 9 h 204 ch
	betutekpatParts = 15*rega.ChalakimPerHour + 589 // ≥ 15 h 589 ch
)

// Numérotation biblique des mois : Nissan = 1 … Adar = 12, Adar II = 13.
const (
	Nissan   = 1
	Iyar     = 2
	Sivan    = 3
	Tammuz   = 4
	Av       = 5
	Elul     = 6
	Tishrei  = 7
	Cheshvan = 8
	Kislev   = 9
	Tevet    = 10
	Shevat   = 11
	Adar     = 12 // Adar I dans une année embolismique
	AdarII   = 13
)

// Jours de la semaine, dimanche = 0.
const (
	Sunday = iota
	Monday
	Tuesday
	Wednesday
	Thursday
	Friday
	Shabbat
)

var monthNames = [...]string{"", "Nissan", "Iyar", "Sivan", "Tammuz", "Av", "Elul",
	"Tishrei", "Cheshvan", "Kislev", "Tevet", "Shevat", "Adar", "Adar II"}

var monthNamesHe = [...]string{"", "ניסן", "אייר", "סיון", "תמוז", "אב", "אלול",
	"תשרי", "חשון", "כסלו", "טבת", "שבט", "אדר", "אדר ב׳"}

var weekdayNamesHe = [...]string{"יום ראשון", "יום שני", "יום שלישי", "יום רביעי", "יום חמישי", "יום שישי", "שבת"}

var weekdayNames = [...]string{"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Shabbat"}

// ErrInvalidDate est renvoyée pour une date qui n'existe pas.
var ErrInvalidDate = errors.New("luach: invalid date")

// ErrBeforeEpoch est renvoyée pour un jour antérieur au 1 Tishrei de l'an 1.
var ErrBeforeEpoch = errors.New("luach: day precedes 1 Tishrei 1")

// MonthName renvoie le nom translittéré du mois. Dans une année
// embolismique, le mois 12 s'appelle « Adar I ».
func MonthName(year int64, month int) string {
	if month < Nissan || month > AdarII {
		return ""
	}
	if month == Adar && IsLeap(year) {
		return "Adar I"
	}
	return monthNames[month]
}

// MonthNameHe renvoie le nom hébreu du mois.
func MonthNameHe(year int64, month int) string {
	if month < Nissan || month > AdarII {
		return ""
	}
	if month == Adar && IsLeap(year) {
		return "אדר א׳"
	}
	return monthNamesHe[month]
}

// WeekdayNameHe renvoie le nom hébreu du jour de la semaine (0 = dimanche).
func WeekdayNameHe(wd int) string {
	if wd < 0 || wd > 6 {
		return ""
	}
	return weekdayNamesHe[wd]
}

// WeekdayName renvoie le nom anglais du jour de la semaine (0 = dimanche).
func WeekdayName(wd int) string {
	if wd < 0 || wd > 6 {
		return ""
	}
	return weekdayNames[wd]
}

// IsLeap indique si l'année est embolismique (années 3, 6, 8, 11, 14, 17,
// 19 du cycle de 19 ans).
func IsLeap(year int64) bool {
	return mod(7*year+1, 19) < 7
}

// MonthsInYear renvoie 12 ou 13.
func MonthsInYear(year int64) int {
	if IsLeap(year) {
		return 13
	}
	return 12
}

// monthsElapsed renvoie le nombre de mois lunaires écoulés entre le molad
// Tohu et le molad de Tishrei de l'année donnée.
func monthsElapsed(year int64) int64 {
	return floorDiv(235*year-234, 19)
}

// MoladTishreiChalakim renvoie le molad de Tishrei de l'année, en chalakim
// depuis l'epoch.
func MoladTishreiChalakim(year int64) int64 {
	return MoladTohuChalakim + monthsElapsed(year)*LunarMonthChalakim
}

// RoshHashana renvoie le numéro du jour du 1 Tishrei de l'année, après
// application des quatre dehiyot.
func RoshHashana(year int64) int64 {
	m := MoladTishreiChalakim(year)
	day := floorDiv(m, ChalakimPerDay)
	parts := m - day*ChalakimPerDay
	wd := mod(day, 7)

	switch {
	case parts >= moladZakenParts:
		// Molad zaken : molad à 18 h ou plus → lendemain.
		day++
	case wd == Tuesday && parts >= gatradParts && !IsLeap(year):
		// GaTaRaD : année commune, mardi, ≥ 9 h 204 ch → repoussé
		// (mercredi, puis jeudi via lo ADU).
		day++
	case wd == Monday && parts >= betutekpatParts && IsLeap(year-1):
		// BeTU'TeKPaT : après une embolismique, lundi, ≥ 15 h 589 ch → mardi.
		day++
	}
	switch mod(day, 7) {
	case Sunday, Wednesday, Friday:
		// לא אד״ו ראש
		day++
	}
	return day
}

// YearLength renvoie la longueur de l'année en jours
// (353/354/355 ou 383/384/385).
func YearLength(year int64) int {
	return int(RoshHashana(year+1) - RoshHashana(year))
}

// MonthLength renvoie la longueur du mois dans l'année donnée, ou 0 si le
// mois n'existe pas cette année-là.
func MonthLength(year int64, month int) int {
	switch month {
	case Nissan, Sivan, Av, Tishrei, Shevat:
		return 30
	case Iyar, Tammuz, Elul, Tevet:
		return 29
	case Cheshvan:
		if YearLength(year)%10 == 5 { // année pleine
			return 30
		}
		return 29
	case Kislev:
		if YearLength(year)%10 == 3 { // année déficiente
			return 29
		}
		return 30
	case Adar:
		if IsLeap(year) {
			return 30 // Adar I
		}
		return 29
	case AdarII:
		if IsLeap(year) {
			return 29
		}
		return 0
	}
	return 0
}

// MonthsInOrder renvoie les mois de l'année dans l'ordre civil, à partir
// de Tishrei.
func MonthsInOrder(year int64) []int {
	if IsLeap(year) {
		return []int{Tishrei, Cheshvan, Kislev, Tevet, Shevat, Adar, AdarII, Nissan, Iyar, Sivan, Tammuz, Av, Elul}
	}
	return []int{Tishrei, Cheshvan, Kislev, Tevet, Shevat, Adar, Nissan, Iyar, Sivan, Tammuz, Av, Elul}
}

// monthIndex renvoie la position du mois dans l'année civile (Tishrei = 0),
// ou -1 si le mois n'existe pas cette année-là.
func monthIndex(year int64, month int) int {
	for i, m := range MonthsInOrder(year) {
		if m == month {
			return i
		}
	}
	return -1
}

// Weekday renvoie le jour de la semaine du jour donné (0 = dimanche).
func Weekday(day int64) int {
	return int(mod(day, 7))
}

// DayFromDate renvoie le numéro du jour depuis l'epoch pour une date.
func DayFromDate(year int64, month, day int) (int64, error) {
	if year < 1 {
		return 0, fmt.Errorf("%w: year %d", ErrInvalidDate, year)
	}
	idx := monthIndex(year, month)
	if idx < 0 {
		return 0, fmt.Errorf("%w: month %d does not exist in year %d", ErrInvalidDate, month, year)
	}
	if day < 1 || day > MonthLength(year, month) {
		return 0, fmt.Errorf("%w: day %d of month %d in year %d", ErrInvalidDate, day, month, year)
	}
	d := RoshHashana(year)
	for _, m := range MonthsInOrder(year)[:idx] {
		d += int64(MonthLength(year, m))
	}
	return d + int64(day) - 1, nil
}

// DateFromDay renvoie la date (année, mois, jour) du jour donné.
func DateFromDay(day int64) (year int64, month, dom int, err error) {
	if day < 1 {
		return 0, 0, 0, ErrBeforeEpoch
	}
	// Estimation initiale puis ajustement.
	year = day*19/6940 + 1 // 6940 jours ≈ 19 ans (cycle de Méton, borne haute)
	if year < 1 {
		year = 1
	}
	for RoshHashana(year) > day {
		year--
	}
	for RoshHashana(year+1) <= day {
		year++
	}
	rem := day - RoshHashana(year)
	for _, m := range MonthsInOrder(year) {
		l := int64(MonthLength(year, m))
		if rem < l {
			return year, m, int(rem) + 1, nil
		}
		rem -= l
	}
	// Impossible si YearLength est cohérent avec les longueurs de mois.
	return 0, 0, 0, fmt.Errorf("luach: internal error for day %d", day)
}

// Molad renvoie le molad du mois donné, en rega'im depuis l'epoch.
func Molad(year int64, month int) (rega.Rega, error) {
	if year < 1 {
		return 0, fmt.Errorf("%w: year %d", ErrInvalidDate, year)
	}
	idx := monthIndex(year, month)
	if idx < 0 {
		return 0, fmt.Errorf("%w: month %d does not exist in year %d", ErrInvalidDate, month, year)
	}
	ch := MoladTishreiChalakim(year) + int64(idx)*LunarMonthChalakim
	return rega.FromChalakim(ch), nil
}

func floorDiv(a, b int64) int64 {
	q := a / b
	if (a%b != 0) && ((a < 0) != (b < 0)) {
		q--
	}
	return q
}

func mod(a, b int64) int64 {
	return a - floorDiv(a, b)*b
}
