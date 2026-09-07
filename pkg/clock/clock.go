// Package clock fournit l'heure courante en rega'im depuis l'epoch.
//
// L'interface Clock est la seule chose que le reste du programme voit.
// L'implémentation UT1 est le seul endroit (avec pkg/iers) où l'horloge
// système, la seconde SI et l'UTC existent : elle lit l'horloge, corrige
// avec DUT1 (rotation terrestre mesurée), décale au temps moyen de
// Jérusalem, et convertit la durée écoulée depuis l'epoch en rega'im.
//
// Elle est prévue pour être remplacée un jour par un capteur direct de la
// rotation (solaire ou stellaire) : rien dans pkg/api ne dépend de l'IERS.
package clock

import (
	"math/big"
	"time"

	"zman/pkg/iers"
	"zman/pkg/rega"
)

// Quality décrit la provenance et la fraîcheur de la mesure.
type Quality struct {
	Source      string  // "iers" ou "fallback" (DUT1 = 0, UT1 ≈ UTC)
	DUT1AgeDays float64 // jours depuis la dernière valeur mesurée de DUT1
	Predicted   bool    // DUT1 issu d'une prédiction, pas d'une mesure
}

// Clock est l'horloge abstraite du serveur.
type Clock interface {
	Now() rega.Rega
	Quality() Quality
}

// Constantes du pont entre le temps des nations et l'epoch.
//
// L'epoch (jour 0, samedi soir 18:00 temps moyen de Jérusalem, veille du
// 1 Tishrei de l'an 1) tombe à MJD −2052004.25 en échelle JMT. L'epoch
// Unix est à MJD 40587. L'écart vaut donc 2 092 591,25 jours, soit
// 180 799 884 000 secondes.
const (
	epochToUnixSeconds = 180799884000

	// Longitude de Jérusalem : 35,2137° E → 35,2137 × 240 s = 8 451,288 s
	// (+2 h 20 min 51,288 s).
	jerusalemOffsetNanos = 8451288000000

	nanosPerSecond = 1000000000
	unixEpochMJD   = 40587.0
	secondsPerDay  = 86400
)

var (
	bigEpochNanos = new(big.Int).Mul(big.NewInt(epochToUnixSeconds), big.NewInt(nanosPerSecond))
	// rega'im par nanoseconde = 1 969 920 / 86 400e9 = 228 / 1e10.
	bigRegaNum = big.NewInt(rega.RegaimPerDay)
	bigRegaDen = new(big.Int).Mul(big.NewInt(secondsPerDay), big.NewInt(nanosPerSecond))
)

// FromUTC convertit un instant UTC et une valeur DUT1 (UT1 − UTC, en
// secondes) en rega'im depuis l'epoch, par arithmétique entière
// (division plancher).
func FromUTC(t time.Time, dut1Seconds float64) rega.Rega {
	n := new(big.Int).SetInt64(t.Unix())
	n.Mul(n, big.NewInt(nanosPerSecond))
	n.Add(n, big.NewInt(int64(t.Nanosecond())))
	n.Add(n, big.NewInt(int64(dut1Seconds*nanosPerSecond)))
	n.Add(n, big.NewInt(jerusalemOffsetNanos))
	n.Add(n, bigEpochNanos)
	n.Mul(n, bigRegaNum)
	n.Div(n, bigRegaDen) // division euclidienne : plancher pour un diviseur positif
	return rega.Rega(n.Int64())
}

// MJD renvoie la date julienne modifiée (UTC) d'un instant.
func MJD(t time.Time) float64 {
	return unixEpochMJD + (float64(t.Unix())+float64(t.Nanosecond())/nanosPerSecond)/secondsPerDay
}

// DUT1Source fournit la table IERS courante (nil si rien n'est chargé).
type DUT1Source interface {
	Table() *iers.Table
}

// UT1 est l'horloge fondée sur l'horloge système corrigée par l'IERS.
type UT1 struct {
	src DUT1Source
	now func() time.Time
}

// NewUT1 crée une horloge UT1. src peut être un *iers.Client.
func NewUT1(src DUT1Source) *UT1 {
	return &UT1{src: src, now: time.Now}
}

// NewUT1WithNow permet d'injecter l'horloge système (tests).
func NewUT1WithNow(src DUT1Source, now func() time.Time) *UT1 {
	return &UT1{src: src, now: now}
}

func (c *UT1) sample() (rega.Rega, Quality) {
	t := c.now().UTC()
	mjd := MJD(t)
	var tb *iers.Table
	if c.src != nil {
		tb = c.src.Table()
	}
	if tb == nil {
		return FromUTC(t, 0), Quality{Source: "fallback", Predicted: true}
	}
	dut1, predicted, err := tb.DUT1(mjd)
	if err != nil {
		return FromUTC(t, 0), Quality{Source: "fallback", Predicted: true}
	}
	q := Quality{Source: "iers", Predicted: predicted}
	if last := tb.LastMeasuredMJD(); last > 0 {
		q.DUT1AgeDays = mjd - last
	}
	return FromUTC(t, dut1), q
}

// Now renvoie l'instant courant en rega'im.
func (c *UT1) Now() rega.Rega {
	r, _ := c.sample()
	return r
}

// Quality renvoie la qualité de la mesure courante.
func (c *UT1) Quality() Quality {
	_, q := c.sample()
	return q
}

// Fixed est une horloge immobile, pour les tests.
type Fixed struct {
	R rega.Rega
	Q Quality
}

func (f Fixed) Now() rega.Rega   { return f.R }
func (f Fixed) Quality() Quality { return f.Q }
