// Package api expose le temps juif en HTTP. Il ne connaît que des rega'im,
// des jours, des heures, des chalakim et le calendrier fixe. Aucune
// date grégorienne, aucun timestamp Unix, aucune seconde n'y apparaît.
package api

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"

	"zman/pkg/clock"
	"zman/pkg/luach"
	"zman/pkg/rega"
)

// DataStatus décrit l'état de la source de données de l'horloge, sans
// référence à une implémentation précise.
type DataStatus struct {
	Loaded          bool    `json:"loaded"`
	Records         int     `json:"records"`
	MeasuredRecords int     `json:"measured_records"`
	Source          string  `json:"source"`
	CacheAgeDays    float64 `json:"cache_age_days"`
	LastError       string  `json:"last_error,omitempty"`
}

// StatusFunc renvoie l'état de la source de données.
type StatusFunc func() DataStatus

// Server est le serveur HTTP.
type Server struct {
	clock   clock.Clock
	status  StatusFunc
	limiter *limiter
	prefix  string
	mux     *http.ServeMux
}

// Option configure le serveur.
type Option func(*Server)

// WithStatus branche la fonction d'état pour /health.
func WithStatus(f StatusFunc) Option { return func(s *Server) { s.status = f } }

// WithRateLimit active une limite par IP : rps requêtes par unité de
// temps de l'horloge système, avec une rafale de burst. rps ≤ 0 désactive.
func WithRateLimit(rps float64, burst int) Option {
	return func(s *Server) {
		if rps > 0 {
			s.limiter = newLimiter(rps, burst)
		}
	}
}

// WithPrefix monte les routes sous un préfixe, par exemple "/api".
func WithPrefix(p string) Option {
	return func(s *Server) { s.prefix = strings.TrimSuffix(p, "/") }
}

// Routes sont les chemins de l'API, relatifs au préfixe.
var Routes = []string{"/now", "/rega/{n}", "/date/{year}/{month}/{day}", "/molad/{year}/{month}", "/year/{year}", "/health"}

// New crée le serveur.
func New(c clock.Clock, opts ...Option) *Server {
	s := &Server{clock: c, mux: http.NewServeMux()}
	for _, o := range opts {
		o(s)
	}
	p := s.prefix
	s.mux.HandleFunc("GET "+p+"/now", s.handleNow)
	s.mux.HandleFunc("GET "+p+"/rega/{n}", s.handleRega)
	s.mux.HandleFunc("GET "+p+"/date/{year}/{month}/{day}", s.handleDate)
	s.mux.HandleFunc("GET "+p+"/molad/{year}/{month}", s.handleMolad)
	s.mux.HandleFunc("GET "+p+"/year/{year}", s.handleYear)
	s.mux.HandleFunc("GET "+p+"/health", s.handleHealth)
	s.mux.HandleFunc("GET "+p+"/{$}", s.handleIndex)
	return s
}

// Prefix renvoie le préfixe des routes.
func (s *Server) Prefix() string { return s.prefix }

// RegisterLegacyRedirects enregistre sur mux des redirections 301 des
// anciens chemins racine (/now, /rega/…) vers leurs équivalents sous
// prefix.
func RegisterLegacyRedirects(mux *http.ServeMux, prefix string) {
	prefix = strings.TrimSuffix(prefix, "/")
	if prefix == "" {
		return
	}
	for _, route := range Routes {
		mux.HandleFunc("GET "+route, func(w http.ResponseWriter, r *http.Request) {
			target := prefix + r.URL.Path
			if r.URL.RawQuery != "" {
				target += "?" + r.URL.RawQuery
			}
			http.Redirect(w, r, target, http.StatusMovedPermanently)
		})
	}
}

// Handler renvoie le handler complet (CORS, rate limit, routes).
func (s *Server) Handler() http.Handler {
	return s.cors(s.rateLimit(s.mux))
}

// ---- Réponses ------------------------------------------------------------

// Date est une date du calendrier fixe.
type Date struct {
	Year        int64  `json:"year"`
	Month       int    `json:"month"`
	MonthName   string `json:"month_name"`
	MonthNameHe string `json:"month_name_he"`
	Day         int    `json:"day"`
}

// ClockInfo est la qualité de l'horloge.
type ClockInfo struct {
	Source      string  `json:"source"`
	DUT1AgeDays float64 `json:"dut1_age_days"`
	Predicted   bool    `json:"predicted"`
}

// Moment est un instant décodé.
type Moment struct {
	Rega          int64      `json:"rega"`
	Day           int64      `json:"day"`
	Hour          int        `json:"hour"`
	Chelek        int        `json:"chelek"`
	RegaInChelek  int        `json:"rega_in_chelek"`
	Date          *Date      `json:"date"`
	Weekday       int        `json:"weekday"`
	WeekdayNameHe string     `json:"weekday_name_he"`
	Clock         *ClockInfo `json:"clock,omitempty"`
}

// DayRange est l'étendue d'un jour en rega'im ; End est exclusif.
type DayRange struct {
	Date          Date   `json:"date"`
	Day           int64  `json:"day"`
	Weekday       int    `json:"weekday"`
	WeekdayNameHe string `json:"weekday_name_he"`
	Start         int64  `json:"start"`
	End           int64  `json:"end"`
}

// MonthInfo décrit un mois dans /year.
type MonthInfo struct {
	Month    int    `json:"month"`
	Name     string `json:"name"`
	NameHe   string `json:"name_he"`
	Length   int    `json:"length"`
	StartDay int64  `json:"start_day"`
}

// RoshHashanaInfo décrit le 1 Tishrei dans /year.
type RoshHashanaInfo struct {
	Day           int64  `json:"day"`
	Weekday       int    `json:"weekday"`
	WeekdayNameHe string `json:"weekday_name_he"`
}

// YearInfo est la réponse de /year.
type YearInfo struct {
	Year        int64           `json:"year"`
	Length      int             `json:"length"`
	Leap        bool            `json:"leap"`
	RoshHashana RoshHashanaInfo `json:"rosh_hashana"`
	Months      []MonthInfo     `json:"months"`
}

// Health est la réponse de /health.
type Health struct {
	Status string      `json:"status"`
	Clock  ClockInfo   `json:"clock"`
	Data   *DataStatus `json:"data,omitempty"`
}

func dateOf(day int64) *Date {
	y, m, d, err := luach.DateFromDay(day)
	if err != nil {
		return nil
	}
	return &Date{Year: y, Month: m, MonthName: luach.MonthName(y, m), MonthNameHe: luach.MonthNameHe(y, m), Day: d}
}

func momentOf(r rega.Rega) Moment {
	day, h, ch, rg := r.Split()
	wd := luach.Weekday(day)
	return Moment{
		Rega: int64(r), Day: day, Hour: h, Chelek: ch, RegaInChelek: rg,
		Date: dateOf(day), Weekday: wd, WeekdayNameHe: luach.WeekdayNameHe(wd),
	}
}

func clockInfo(q clock.Quality) *ClockInfo {
	return &ClockInfo{Source: q.Source, DUT1AgeDays: round1(q.DUT1AgeDays), Predicted: q.Predicted}
}

func round1(f float64) float64 {
	return float64(int64(f*10+0.5)) / 10
}

// ---- Handlers ------------------------------------------------------------

func (s *Server) handleIndex(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "public, max-age=3600")
	if wantsText(r) {
		writeText(w, http.StatusOK, indexText)
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"name": "zman",
		"units": map[string]int{
			"regaim_per_chelek": rega.RegaimPerChelek,
			"chalakim_per_hour": rega.ChalakimPerHour,
			"hours_per_day":     rega.HoursPerDay,
			"regaim_per_day":    rega.RegaimPerDay,
		},
		"endpoints": s.endpoints(),
	})
}

func (s *Server) endpoints() []string {
	out := make([]string, len(Routes))
	for i, r := range Routes {
		out[i] = s.prefix + r
	}
	return out
}

const indexText = `zman — serveur de temps juif

  1 jour  = 24 heures, depuis 18:00 temps moyen de Jérusalem
  1 heure = 1080 chalakim
  1 chelek = 76 rega'im
  1 jour  = 1 969 920 rega'im

  GET /now
  GET /rega/{n}
  GET /date/{year}/{month}/{day}
  GET /molad/{year}/{month}
  GET /year/{year}
  GET /health
`

func (s *Server) handleNow(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "public, max-age=1")
	m := momentOf(s.clock.Now())
	m.Clock = clockInfo(s.clock.Quality())
	if wantsText(r) {
		writeText(w, http.StatusOK, momentText(m))
		return
	}
	writeJSON(w, http.StatusOK, m)
}

func (s *Server) handleRega(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "public, max-age=86400")
	n, err := strconv.ParseInt(r.PathValue("n"), 10, 64)
	if err != nil {
		writeError(w, r, http.StatusBadRequest, "rega must be an integer")
		return
	}
	m := momentOf(rega.Rega(n))
	if wantsText(r) {
		writeText(w, http.StatusOK, momentText(m))
		return
	}
	writeJSON(w, http.StatusOK, m)
}

func (s *Server) handleDate(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "public, max-age=86400")
	year, month, day, ok := parseYMD(w, r)
	if !ok {
		return
	}
	d, err := luach.DayFromDate(year, month, day)
	if err != nil {
		writeError(w, r, http.StatusBadRequest, err.Error())
		return
	}
	wd := luach.Weekday(d)
	resp := DayRange{
		Date: *dateOf(d), Day: d, Weekday: wd, WeekdayNameHe: luach.WeekdayNameHe(wd),
		Start: int64(rega.DayStart(d)), End: int64(rega.DayStart(d + 1)),
	}
	if wantsText(r) {
		writeText(w, http.StatusOK, fmt.Sprintf("%s\nday      %d\nweekday  %d %s\nstart    %d\nend      %d (exclusive)\n",
			dateText(&resp.Date), resp.Day, resp.Weekday, resp.WeekdayNameHe, resp.Start, resp.End))
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleMolad(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "public, max-age=86400")
	year, ok := parseInt64(w, r, "year")
	if !ok {
		return
	}
	month, ok := parseInt(w, r, "month")
	if !ok {
		return
	}
	m, err := luach.Molad(year, month)
	if err != nil {
		writeError(w, r, http.StatusBadRequest, err.Error())
		return
	}
	resp := momentOf(m)
	if wantsText(r) {
		writeText(w, http.StatusOK, fmt.Sprintf("molad %s %d\n%s", luach.MonthName(year, month), year, momentText(resp)))
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleYear(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "public, max-age=86400")
	year, ok := parseInt64(w, r, "year")
	if !ok {
		return
	}
	if year < 1 {
		writeError(w, r, http.StatusBadRequest, "year must be ≥ 1")
		return
	}
	rh := luach.RoshHashana(year)
	info := YearInfo{
		Year: year, Length: luach.YearLength(year), Leap: luach.IsLeap(year),
		RoshHashana: RoshHashanaInfo{Day: rh, Weekday: luach.Weekday(rh), WeekdayNameHe: luach.WeekdayNameHe(luach.Weekday(rh))},
	}
	d := rh
	for _, m := range luach.MonthsInOrder(year) {
		l := luach.MonthLength(year, m)
		info.Months = append(info.Months, MonthInfo{Month: m, Name: luach.MonthName(year, m), NameHe: luach.MonthNameHe(year, m), Length: l, StartDay: d})
		d += int64(l)
	}
	if wantsText(r) {
		var b strings.Builder
		fmt.Fprintf(&b, "year %d: %d days, leap=%v, Rosh Hashana day %d (%s)\n", info.Year, info.Length, info.Leap, rh, info.RoshHashana.WeekdayNameHe)
		for _, m := range info.Months {
			fmt.Fprintf(&b, "  %2d  %-9s %-7s %d days from day %d\n", m.Month, m.Name, m.NameHe, m.Length, m.StartDay)
		}
		writeText(w, http.StatusOK, b.String())
		return
	}
	writeJSON(w, http.StatusOK, info)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	q := s.clock.Quality()
	h := Health{Status: "ok", Clock: *clockInfo(q)}
	if s.status != nil {
		ds := s.status()
		h.Data = &ds
		if !ds.Loaded {
			h.Status = "degraded"
		}
	}
	if q.Source != "iers" {
		h.Status = "degraded"
	}
	code := http.StatusOK
	if h.Status != "ok" {
		code = http.StatusServiceUnavailable
	}
	if wantsText(r) {
		var b strings.Builder
		fmt.Fprintf(&b, "status   %s\nclock    source=%s dut1_age_days=%.1f predicted=%v\n", h.Status, h.Clock.Source, h.Clock.DUT1AgeDays, h.Clock.Predicted)
		if h.Data != nil {
			fmt.Fprintf(&b, "data     loaded=%v records=%d measured=%d source=%s cache_age_days=%.1f\n", h.Data.Loaded, h.Data.Records, h.Data.MeasuredRecords, h.Data.Source, h.Data.CacheAgeDays)
			if h.Data.LastError != "" {
				fmt.Fprintf(&b, "error    %s\n", h.Data.LastError)
			}
		}
		writeText(w, code, b.String())
		return
	}
	writeJSON(w, code, h)
}

// ---- Helpers -------------------------------------------------------------

func parseInt64(w http.ResponseWriter, r *http.Request, name string) (int64, bool) {
	v, err := strconv.ParseInt(r.PathValue(name), 10, 64)
	if err != nil {
		writeError(w, r, http.StatusBadRequest, name+" must be an integer")
		return 0, false
	}
	return v, true
}

func parseInt(w http.ResponseWriter, r *http.Request, name string) (int, bool) {
	v, err := strconv.Atoi(r.PathValue(name))
	if err != nil {
		writeError(w, r, http.StatusBadRequest, name+" must be an integer")
		return 0, false
	}
	return v, true
}

func parseYMD(w http.ResponseWriter, r *http.Request) (int64, int, int, bool) {
	year, ok := parseInt64(w, r, "year")
	if !ok {
		return 0, 0, 0, false
	}
	month, ok := parseInt(w, r, "month")
	if !ok {
		return 0, 0, 0, false
	}
	day, ok := parseInt(w, r, "day")
	if !ok {
		return 0, 0, 0, false
	}
	return year, month, day, true
}

func wantsText(r *http.Request) bool {
	if f := r.URL.Query().Get("format"); f != "" {
		return f == "text"
	}
	accept := r.Header.Get("Accept")
	text := strings.Index(accept, "text/plain")
	if text < 0 {
		return false
	}
	js := strings.Index(accept, "application/json")
	return js < 0 || text < js
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(code)
	enc := json.NewEncoder(w)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(v)
}

func writeText(w http.ResponseWriter, code int, s string) {
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.WriteHeader(code)
	_, _ = io.WriteString(w, s)
}

func writeError(w http.ResponseWriter, r *http.Request, code int, msg string) {
	if wantsText(r) {
		writeText(w, code, "error: "+msg+"\n")
		return
	}
	writeJSON(w, code, map[string]string{"error": msg})
}

func dateText(d *Date) string {
	if d == nil {
		return "date     (before 1 Tishrei 1)"
	}
	return fmt.Sprintf("date     %d %s %d (%s)", d.Day, d.MonthName, d.Year, d.MonthNameHe)
}

func momentText(m Moment) string {
	var b strings.Builder
	fmt.Fprintf(&b, "rega     %d\n", m.Rega)
	fmt.Fprintf(&b, "day      %d  hour %d  chelek %d  rega %d\n", m.Day, m.Hour, m.Chelek, m.RegaInChelek)
	b.WriteString(dateText(m.Date) + "\n")
	fmt.Fprintf(&b, "weekday  %d %s\n", m.Weekday, m.WeekdayNameHe)
	if m.Clock != nil {
		fmt.Fprintf(&b, "clock    source=%s dut1_age_days=%.1f predicted=%v\n", m.Clock.Source, m.Clock.DUT1AgeDays, m.Clock.Predicted)
	}
	return b.String()
}

// ErrRateLimited est renvoyée (en JSON) quand un client dépasse la limite.
var ErrRateLimited = errors.New("rate limit exceeded")
