// Package rega définit l'unité fondamentale du serveur : le rega (רגע),
// 1/76 de chelek, et le type Rega, nombre de rega'im écoulés depuis l'epoch
// (début de la semaine du molad Tohu, samedi soir 18:00 temps moyen de
// Jérusalem).
//
// Ce package ne connaît ni la seconde, ni le grégorien, ni l'UTC.
package rega

// Constantes structurelles du jour halachique. Elles sont vraies par
// définition, quelle que soit la durée réelle de la rotation terrestre.
const (
	RegaimPerChelek = 76   // Rambam, Hilchot Kiddush HaChodesh 10:1
	ChalakimPerHour = 1080 // חלקים
	HoursPerDay     = 24

	RegaimPerHour  = RegaimPerChelek * ChalakimPerHour // 82 080
	ChalakimPerDay = ChalakimPerHour * HoursPerDay     // 25 920
	RegaimPerDay   = RegaimPerChelek * ChalakimPerDay  // 1 969 920
)

// Rega est un nombre de rega'im depuis l'epoch.
type Rega int64

// Split décompose r en (jour depuis l'epoch, heure, chelek, rega).
// Les composantes sont toujours dans [0, borne) même pour r négatif
// (division plancher).
func (r Rega) Split() (day int64, hour, chelek, rega int) {
	day = floorDiv(int64(r), RegaimPerDay)
	rem := int64(r) - day*RegaimPerDay // 0 <= rem < RegaimPerDay
	hour = int(rem / RegaimPerHour)
	rem -= int64(hour) * RegaimPerHour
	chelek = int(rem / RegaimPerChelek)
	rega = int(rem - int64(chelek)*RegaimPerChelek)
	return
}

// Day renvoie le numéro du jour (depuis l'epoch) contenant r.
func (r Rega) Day() int64 {
	return floorDiv(int64(r), RegaimPerDay)
}

// FromParts compose un Rega à partir de ses composantes. Aucune
// normalisation n'est imposée : des composantes hors borne sont
// simplement additionnées.
func FromParts(day int64, hour, chelek, rega int) Rega {
	return Rega(day*RegaimPerDay + int64(hour)*RegaimPerHour + int64(chelek)*RegaimPerChelek + int64(rega))
}

// DayStart renvoie le premier rega du jour donné.
func DayStart(day int64) Rega {
	return Rega(day * RegaimPerDay)
}

// FromChalakim convertit un nombre absolu de chalakim en Rega.
func FromChalakim(chalakim int64) Rega {
	return Rega(chalakim * RegaimPerChelek)
}

func floorDiv(a, b int64) int64 {
	q := a / b
	if (a%b != 0) && ((a < 0) != (b < 0)) {
		q--
	}
	return q
}
