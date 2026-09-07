package iers

import (
	"context"
	"errors"
	"io"
	"math"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

func loadSample(t *testing.T) *Table {
	t.Helper()
	f, err := os.Open("testdata/finals.sample")
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	tb, err := Parse(f)
	if err != nil {
		t.Fatal(err)
	}
	return tb
}

func TestParseSample(t *testing.T) {
	tb := loadSample(t)
	if tb.Len() < 100 {
		t.Fatalf("only %d records", tb.Len())
	}
	if tb.First() != 41684 {
		t.Errorf("first MJD = %v", tb.First())
	}
	if got := tb.LastMeasuredMJD(); got != 61286 {
		t.Errorf("last measured MJD = %v, want 61286", got)
	}
	// Valeurs exactes lues dans le fichier.
	cases := []struct {
		mjd       float64
		dut1      float64
		predicted bool
	}{
		{41684, 0.8084178, false},
		{61286, 0.0012337, false},
		{61287, 0.0008643, true},
		{61291, -0.0004337, true}, // valeur négative collée au flag
	}
	for _, c := range cases {
		v, p, err := tb.DUT1(c.mjd)
		if err != nil {
			t.Fatal(err)
		}
		if math.Abs(v-c.dut1) > 1e-9 || p != c.predicted {
			t.Errorf("DUT1(%v) = (%v,%v), want (%v,%v)", c.mjd, v, p, c.dut1, c.predicted)
		}
	}
}

func TestParseLine(t *testing.T) {
	cases := []struct {
		name string
		line string
		ok   bool
		err  bool
	}{
		{"measured", "73 1 2 41684.00 I  0.120733 0.009786  0.136966 0.015902  I 0.8084178 0.0002710  0.0000 0.1916  P", true, false},
		{"empty tail", "271029 61707.00                                                                                     ", false, false},
		{"short", "27", false, false},
		{"bad flag", "73 1 2 41684.00 I  0.120733 0.009786  0.136966 0.015902  X 0.8084178 0.0002710", false, true},
		{"bad number", "73 1 2 41684.00 I  0.120733 0.009786  0.136966 0.015902  I 0.80x4178 0.0002710", false, true},
	}
	for _, c := range cases {
		_, ok, err := parseLine(c.line)
		if ok != c.ok || (err != nil) != c.err {
			t.Errorf("%s: ok=%v err=%v", c.name, ok, err)
		}
	}
}

func TestInterpolation(t *testing.T) {
	tb := loadSample(t)
	a, _, _ := tb.DUT1(61285)
	b, _, _ := tb.DUT1(61286)
	mid, pred, err := tb.DUT1(61285.5)
	if err != nil {
		t.Fatal(err)
	}
	if math.Abs(mid-(a+b)/2) > 1e-12 {
		t.Errorf("mid = %v, want %v", mid, (a+b)/2)
	}
	if pred {
		t.Error("61285.5 lies between two measured points")
	}
	// Entre mesure et prédiction → prédit.
	if _, p, _ := tb.DUT1(61286.5); !p {
		t.Error("61286.5 should be flagged predicted")
	}
	// Hors table.
	before, p, _ := tb.DUT1(1000)
	if before != 0.8084178 || !p {
		t.Errorf("before table: (%v,%v)", before, p)
	}
	last, p, _ := tb.DUT1(1e6)
	if !p {
		t.Errorf("after table should be predicted, got %v", last)
	}
}

func TestLeapSecondNotInterpolated(t *testing.T) {
	tb := &Table{recs: []Record{{MJD: 100, DUT1: -0.6}, {MJD: 101, DUT1: 0.4}}}
	v, _, _ := tb.DUT1(100.25)
	if v != -0.6 {
		t.Errorf("before leap second: %v", v)
	}
	v, _, _ = tb.DUT1(100.75)
	if v != 0.4 {
		t.Errorf("after leap second: %v", v)
	}
}

func TestEmpty(t *testing.T) {
	if _, err := Parse(strings.NewReader("")); !errors.Is(err, ErrNoData) {
		t.Errorf("empty parse: %v", err)
	}
	var nilTable *Table
	if _, _, err := nilTable.DUT1(1); !errors.Is(err, ErrNoData) {
		t.Errorf("nil table: %v", err)
	}
}

func TestClientFetchAndCache(t *testing.T) {
	data, err := os.ReadFile("testdata/finals.sample")
	if err != nil {
		t.Fatal(err)
	}
	hits := 0
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits++
		w.Write(data)
	}))
	defer srv.Close()
	bad := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Error(w, "nope", http.StatusServiceUnavailable)
	}))
	defer bad.Close()

	cache := filepath.Join(t.TempDir(), "sub", "finals.all")
	c := NewClient(cache, WithURLs(bad.URL, srv.URL), WithRefresh(time.Hour))
	if err := c.Load(context.Background()); err != nil {
		t.Fatal(err)
	}
	st := c.Status()
	if !st.Loaded || st.Source != "network" || st.Records == 0 || hits != 1 {
		t.Fatalf("status after fetch: %+v, hits=%d", st, hits)
	}
	if _, err := os.Stat(cache); err != nil {
		t.Fatalf("cache not written: %v", err)
	}

	// Second client : le cache est frais, pas de réseau.
	c2 := NewClient(cache, WithURLs(bad.URL), WithRefresh(time.Hour))
	if err := c2.Load(context.Background()); err != nil {
		t.Fatal(err)
	}
	if st := c2.Status(); st.Source != "disk" || !st.Loaded {
		t.Fatalf("status from disk: %+v", st)
	}

	// Troisième : cache périmé, réseau en panne → on garde le cache.
	c3 := NewClient(cache, WithURLs(bad.URL), WithRefresh(0))
	if err := c3.Load(context.Background()); err != nil {
		t.Fatal(err)
	}
	if st := c3.Status(); st.Source != "disk" || st.LastError == "" {
		t.Fatalf("status stale+offline: %+v", st)
	}

	// Quatrième : ni cache ni réseau → erreur.
	c4 := NewClient(filepath.Join(t.TempDir(), "none"), WithURLs(bad.URL))
	if err := c4.Load(context.Background()); err == nil {
		t.Fatal("expected error with no cache and no network")
	}
	if st := c4.Status(); st.Loaded || st.Source != "none" {
		t.Fatalf("status nothing: %+v", st)
	}
}

// fakeGCS simule le serveur de métadonnées et l'API JSON de GCS.
type fakeGCS struct {
	mu   sync.Mutex
	obj  []byte
	mod  time.Time
	gets int
	puts int
}

func (f *fakeGCS) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/computeMetadata/v1/instance/service-accounts/default/token", func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Metadata-Flavor") != "Google" {
			http.Error(w, "flavor", 403)
			return
		}
		w.Write([]byte(`{"access_token":"tok","expires_in":3600}`))
	})
	mux.HandleFunc("GET /storage/v1/b/{b}/o/{o}", func(w http.ResponseWriter, r *http.Request) {
		f.mu.Lock()
		defer f.mu.Unlock()
		f.gets++
		if r.Header.Get("Authorization") != "Bearer tok" || r.PathValue("b") != "bkt" || r.PathValue("o") != "dir/finals.all" {
			http.Error(w, "auth/path", 403)
			return
		}
		if f.obj == nil {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Last-Modified", f.mod.UTC().Format(http.TimeFormat))
		w.Write(f.obj)
	})
	mux.HandleFunc("POST /upload/storage/v1/b/{b}/o", func(w http.ResponseWriter, r *http.Request) {
		f.mu.Lock()
		defer f.mu.Unlock()
		f.puts++
		if r.URL.Query().Get("name") != "dir/finals.all" {
			http.Error(w, "name", 400)
			return
		}
		f.obj, _ = io.ReadAll(r.Body)
		f.mod = time.Now()
		w.Write([]byte(`{}`))
	})
	return mux
}

func TestGCSRemote(t *testing.T) {
	data, _ := os.ReadFile("testdata/finals.sample")
	fake := &fakeGCS{}
	srv := httptest.NewServer(fake.handler())
	defer srv.Close()
	newGCS := func() *GCS {
		g, err := NewGCS("gs://bkt/dir/finals.all")
		if err != nil {
			t.Fatal(err)
		}
		g.StorageURL, g.MetadataURL = srv.URL, srv.URL
		return g
	}
	if _, err := NewGCS("nobucket"); err == nil {
		t.Error("bad spec accepted")
	}
	iersSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { w.Write(data) }))
	defer iersSrv.Close()
	down := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { http.Error(w, "down", 503) }))
	defer down.Close()

	// 1. Rien nulle part : réseau IERS, puis écriture disque + GCS.
	c1 := NewClient(filepath.Join(t.TempDir(), "finals.all"), WithURLs(iersSrv.URL), WithRemote(newGCS()))
	if err := c1.Load(context.Background()); err != nil {
		t.Fatal(err)
	}
	if fake.puts != 1 || fake.obj == nil || c1.Status().Source != "network" {
		t.Fatalf("after first load: puts=%d source=%s", fake.puts, c1.Status().Source)
	}
	// 2. Nouvelle instance sans disque, IERS en panne : le cache GCS suffit.
	c2 := NewClient(filepath.Join(t.TempDir(), "finals.all"), WithURLs(down.URL), WithRemote(newGCS()))
	if err := c2.Load(context.Background()); err != nil {
		t.Fatal(err)
	}
	if st := c2.Status(); st.Source != "remote" || !st.Loaded {
		t.Fatalf("status from remote: %+v", st)
	}
	if _, err := os.Stat(c2.CachePath); err != nil {
		t.Error("remote data should be written to disk")
	}
	// 3. Objet absent et IERS en panne : erreur propre.
	empty := &fakeGCS{}
	esrv := httptest.NewServer(empty.handler())
	defer esrv.Close()
	g := newGCS()
	g.StorageURL, g.MetadataURL = esrv.URL, esrv.URL
	c3 := NewClient(filepath.Join(t.TempDir(), "finals.all"), WithURLs(down.URL), WithRemote(g))
	if err := c3.Load(context.Background()); err == nil {
		t.Fatal("expected error")
	}
}
