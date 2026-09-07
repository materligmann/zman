package api

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"zman/pkg/clock"
	"zman/pkg/luach"
	"zman/pkg/rega"
)

// Instant de test : 25 Elul 5786, 13 h 782 ch 6 rg (lundi).
func fixedNow(t *testing.T) rega.Rega {
	t.Helper()
	d, err := luach.DayFromDate(5786, luach.Elul, 25)
	if err != nil {
		t.Fatal(err)
	}
	return rega.FromParts(d, 13, 782, 6)
}

func newTestServer(t *testing.T, opts ...Option) *httptest.Server {
	t.Helper()
	c := clock.Fixed{R: fixedNow(t), Q: clock.Quality{Source: "iers", DUT1AgeDays: 4.5, Predicted: true}}
	base := []Option{WithStatus(func() DataStatus {
		return DataStatus{Loaded: true, Records: 140, MeasuredRecords: 100, Source: "disk", CacheAgeDays: 2.5}
	})}
	srv := httptest.NewServer(New(c, append(base, opts...)...).Handler())
	t.Cleanup(srv.Close)
	return srv
}

func get(t *testing.T, srv *httptest.Server, path string, accept string) (*http.Response, []byte) {
	t.Helper()
	req, _ := http.NewRequest(http.MethodGet, srv.URL+path, nil)
	if accept != "" {
		req.Header.Set("Accept", accept)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	body, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	return resp, body
}

func getJSON(t *testing.T, srv *httptest.Server, path string, v any) *http.Response {
	t.Helper()
	resp, body := get(t, srv, path, "")
	if err := json.Unmarshal(body, v); err != nil {
		t.Fatalf("%s: bad JSON %q: %v", path, body, err)
	}
	return resp
}

func TestNow(t *testing.T) {
	srv := newTestServer(t)
	var m Moment
	resp := getJSON(t, srv, "/now", &m)
	if resp.StatusCode != 200 {
		t.Fatalf("status %d", resp.StatusCode)
	}
	if resp.Header.Get("Cache-Control") != "public, max-age=1" {
		t.Errorf("Cache-Control = %q", resp.Header.Get("Cache-Control"))
	}
	if resp.Header.Get("Access-Control-Allow-Origin") != "*" {
		t.Error("missing CORS header")
	}
	if m.Rega != int64(fixedNow(t)) || m.Hour != 13 || m.Chelek != 782 || m.RegaInChelek != 6 {
		t.Errorf("moment = %+v", m)
	}
	if m.Date == nil || m.Date.Year != 5786 || m.Date.Month != 6 || m.Date.MonthName != "Elul" || m.Date.MonthNameHe != "אלול" || m.Date.Day != 25 {
		t.Errorf("date = %+v", m.Date)
	}
	if m.Weekday != luach.Monday || m.WeekdayNameHe != "יום שני" {
		t.Errorf("weekday = %d %s", m.Weekday, m.WeekdayNameHe)
	}
	if m.Clock == nil || m.Clock.Source != "iers" || m.Clock.DUT1AgeDays != 4.5 || !m.Clock.Predicted {
		t.Errorf("clock = %+v", m.Clock)
	}
}

func TestNowText(t *testing.T) {
	srv := newTestServer(t)
	resp, body := get(t, srv, "/now", "text/plain")
	if ct := resp.Header.Get("Content-Type"); !strings.HasPrefix(ct, "text/plain") {
		t.Errorf("Content-Type = %q", ct)
	}
	s := string(body)
	for _, want := range []string{"rega     ", "25 Elul 5786", "אלול", "יום שני", "source=iers"} {
		if !strings.Contains(s, want) {
			t.Errorf("text body missing %q:\n%s", want, s)
		}
	}
	// ?format=text aussi, et JSON prioritaire si listé avant.
	if resp, _ := get(t, srv, "/now?format=text", ""); !strings.HasPrefix(resp.Header.Get("Content-Type"), "text/plain") {
		t.Error("?format=text ignored")
	}
	if resp, _ := get(t, srv, "/now", "application/json, text/plain"); !strings.HasPrefix(resp.Header.Get("Content-Type"), "application/json") {
		t.Error("JSON should win when listed first")
	}
}

func TestRega(t *testing.T) {
	srv := newTestServer(t)
	var m Moment
	getJSON(t, srv, "/rega/2395824", &m) // molad Tohu
	if m.Day != 1 || m.Hour != 5 || m.Chelek != 204 || m.RegaInChelek != 0 || m.Weekday != luach.Monday {
		t.Errorf("molad Tohu = %+v", m)
	}
	if m.Date == nil || m.Date.Year != 1 || m.Date.Month != luach.Tishrei || m.Date.Day != 1 {
		t.Errorf("date = %+v", m.Date)
	}
	if m.Clock != nil {
		t.Error("/rega must not carry clock info")
	}
	// Jour 0 : avant le 1 Tishrei 1, date nulle mais réponse valide.
	getJSON(t, srv, "/rega/0", &m)
	if m.Day != 0 || m.Date != nil || m.Weekday != luach.Sunday {
		t.Errorf("rega 0 = %+v", m)
	}
	resp, _ := get(t, srv, "/rega/abc", "")
	if resp.StatusCode != 400 {
		t.Errorf("status %d for bad rega", resp.StatusCode)
	}
}

func TestDate(t *testing.T) {
	srv := newTestServer(t)
	var d DayRange
	getJSON(t, srv, "/date/5784/7/1", &d)
	if d.Weekday != luach.Shabbat || d.End-d.Start != rega.RegaimPerDay || d.Start%rega.RegaimPerDay != 0 {
		t.Errorf("date = %+v", d)
	}
	if d.Date.MonthName != "Tishrei" || d.Date.Year != 5784 {
		t.Errorf("date = %+v", d.Date)
	}
	var m Moment
	getJSON(t, srv, "/rega/"+itoa(d.Start), &m)
	if m.Date.Day != 1 || m.Date.Month != 7 || m.Date.Year != 5784 || m.Hour != 0 {
		t.Errorf("start decodes to %+v", m)
	}
	getJSON(t, srv, "/rega/"+itoa(d.End-1), &m)
	if m.Date.Day != 1 || m.Hour != 23 || m.Chelek != 1079 || m.RegaInChelek != 75 {
		t.Errorf("end-1 decodes to %+v", m)
	}
	for _, bad := range []string{"/date/5786/13/1", "/date/5786/7/31", "/date/0/7/1", "/date/x/7/1", "/date/5786/7/0"} {
		if resp, _ := get(t, srv, bad, ""); resp.StatusCode != 400 {
			t.Errorf("%s: status %d", bad, resp.StatusCode)
		}
	}
}

func TestMolad(t *testing.T) {
	srv := newTestServer(t)
	var m Moment
	getJSON(t, srv, "/molad/5786/7", &m)
	if m.Weekday != luach.Monday || m.Hour != 18 || m.Chelek != 187 || m.RegaInChelek != 0 {
		t.Errorf("molad Tishrei 5786 = %+v", m)
	}
	if m.Date == nil || m.Date.Year != 5785 || m.Date.Month != luach.Elul {
		t.Errorf("molad date = %+v (should be 29 Elul 5785)", m.Date)
	}
	if resp, _ := get(t, srv, "/molad/5786/13", ""); resp.StatusCode != 400 {
		t.Errorf("Adar II in common year: status %d", resp.StatusCode)
	}
	_, body := get(t, srv, "/molad/5786/7", "text/plain")
	if !strings.Contains(string(body), "molad Tishrei 5786") {
		t.Errorf("text: %s", body)
	}
}

func TestYear(t *testing.T) {
	srv := newTestServer(t)
	var y YearInfo
	getJSON(t, srv, "/year/5784", &y)
	if y.Length != 383 || !y.Leap || len(y.Months) != 13 || y.RoshHashana.Weekday != luach.Shabbat {
		t.Errorf("year 5784 = %+v", y)
	}
	if y.Months[5].Name != "Adar I" || y.Months[6].Name != "Adar II" || y.Months[6].Month != 13 {
		t.Errorf("months = %+v", y.Months)
	}
	sum := 0
	for _, m := range y.Months {
		sum += m.Length
	}
	if sum != y.Length {
		t.Errorf("months sum %d != %d", sum, y.Length)
	}
	getJSON(t, srv, "/year/5786", &y)
	if y.Length != 354 || y.Leap || len(y.Months) != 12 || y.Months[5].Name != "Adar" {
		t.Errorf("year 5786 = %+v", y)
	}
	if resp, _ := get(t, srv, "/year/0", ""); resp.StatusCode != 400 {
		t.Errorf("year 0: status %d", resp.StatusCode)
	}
}

func TestHealth(t *testing.T) {
	srv := newTestServer(t)
	var h Health
	resp := getJSON(t, srv, "/health", &h)
	if resp.StatusCode != 200 || h.Status != "ok" || h.Data == nil || !h.Data.Loaded || h.Data.Records != 140 {
		t.Errorf("health = %d %+v", resp.StatusCode, h)
	}
	// Dégradé sans données.
	c := clock.Fixed{R: fixedNow(t), Q: clock.Quality{Source: "fallback", Predicted: true}}
	deg := httptest.NewServer(New(c, WithStatus(func() DataStatus { return DataStatus{Source: "none", LastError: "boom"} })).Handler())
	defer deg.Close()
	resp = getJSON(t, deg, "/health", &h)
	if resp.StatusCode != 503 || h.Status != "degraded" || h.Data.LastError != "boom" {
		t.Errorf("degraded health = %d %+v", resp.StatusCode, h)
	}
}

func TestIndexAndOptions(t *testing.T) {
	srv := newTestServer(t)
	var idx map[string]any
	getJSON(t, srv, "/", &idx)
	if idx["name"] != "zman" {
		t.Errorf("index = %v", idx)
	}
	req, _ := http.NewRequest(http.MethodOptions, srv.URL+"/now", nil)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatal(err)
	}
	resp.Body.Close()
	if resp.StatusCode != 204 || resp.Header.Get("Access-Control-Allow-Methods") == "" {
		t.Errorf("OPTIONS: %d", resp.StatusCode)
	}
	if resp, _ := get(t, srv, "/nope", ""); resp.StatusCode != 404 {
		t.Errorf("unknown route: %d", resp.StatusCode)
	}
}

func TestRateLimit(t *testing.T) {
	srv := newTestServer(t, WithRateLimit(1, 3))
	codes := map[int]int{}
	for i := 0; i < 6; i++ {
		resp, _ := get(t, srv, "/now", "")
		codes[resp.StatusCode]++
	}
	if codes[200] != 3 || codes[429] != 3 {
		t.Errorf("codes = %v", codes)
	}
	// Le seau se remplit à 1 jeton par unité de cadence.
	l := newLimiter(10, 2)
	now := time.Unix(0, 0)
	if !l.allow("a", now) || !l.allow("a", now) || l.allow("a", now) {
		t.Error("burst")
	}
	if !l.allow("a", now.Add(100*time.Millisecond)) {
		t.Error("refill")
	}
	if !l.allow("b", now) {
		t.Error("other key")
	}
}

// TestNoForeignUnits vérifie qu'aucune réponse ne contient de champ ni de
// mot lié au calendrier grégorien, à l'UTC, à Unix ou à la seconde.
func TestNoForeignUnits(t *testing.T) {
	srv := newTestServer(t)
	forbidden := []string{"unix", "utc", "iso", "gregorian", "seconds", "second", "epoch_ms", "millis", "nanos", "timestamp"}
	paths := []string{"/", "/now", "/rega/2395824", "/rega/0", "/rega/x", "/date/5784/7/1", "/date/5786/13/1",
		"/molad/5786/7", "/year/5784", "/year/5786", "/health"}
	for _, p := range paths {
		for _, accept := range []string{"", "text/plain"} {
			_, body := get(t, srv, p, accept)
			lower := strings.ToLower(string(body))
			for _, f := range forbidden {
				if strings.Contains(lower, f) {
					t.Errorf("%s (%q): body contains %q:\n%s", p, accept, f, body)
				}
			}
			if accept != "" {
				continue
			}
			var v any
			if err := json.Unmarshal(body, &v); err != nil {
				t.Errorf("%s: not JSON: %v", p, err)
				continue
			}
			walkKeys(v, func(k string) {
				lk := strings.ToLower(k)
				for _, f := range forbidden {
					if strings.Contains(lk, f) {
						t.Errorf("%s: forbidden key %q", p, k)
					}
				}
			})
		}
	}
}

func TestPrefixAndLegacyRedirects(t *testing.T) {
	c := clock.Fixed{R: fixedNow(t), Q: clock.Quality{Source: "iers"}}
	s := New(c, WithPrefix("/api/"))
	mux := http.NewServeMux()
	mux.Handle("/api/", s.Handler())
	RegisterLegacyRedirects(mux, "/api")
	srv := httptest.NewServer(mux)
	defer srv.Close()
	var m Moment
	getJSON(t, srv, "/api/now", &m)
	if m.Rega != int64(fixedNow(t)) {
		t.Errorf("/api/now = %+v", m)
	}
	var idx map[string]any
	getJSON(t, srv, "/api/", &idx)
	if eps, _ := idx["endpoints"].([]any); len(eps) == 0 || eps[0] != "/api/now" {
		t.Errorf("index endpoints = %v", idx["endpoints"])
	}
	client := &http.Client{CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }}
	for from, to := range map[string]string{
		"/now":             "/api/now",
		"/rega/5":          "/api/rega/5",
		"/date/5784/7/1":   "/api/date/5784/7/1",
		"/molad/5784/7":    "/api/molad/5784/7",
		"/year/5784":       "/api/year/5784",
		"/health":          "/api/health",
		"/now?format=text": "/api/now?format=text",
	} {
		resp, err := client.Get(srv.URL + from)
		if err != nil {
			t.Fatal(err)
		}
		resp.Body.Close()
		if resp.StatusCode != 301 || resp.Header.Get("Location") != to {
			t.Errorf("%s → %d %s, want 301 %s", from, resp.StatusCode, resp.Header.Get("Location"), to)
		}
	}
}

func walkKeys(v any, f func(string)) {
	switch x := v.(type) {
	case map[string]any:
		for k, val := range x {
			f(k)
			walkKeys(val, f)
		}
	case []any:
		for _, val := range x {
			walkKeys(val, f)
		}
	}
}

func itoa(n int64) string {
	return strconv.FormatInt(n, 10)
}
